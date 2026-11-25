/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.spatial;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

final class SpatialSceneGenerator {
    static final int DEPTH_SIZE = 256;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    static void shift(File source, File output, float x, float y) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(source), 65536));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output), 65536))) {
            int[] header = new int[6];
            for (int i = 0; i < header.length; i++) header[i] = in.readInt();
            if (header[0] != 0x53505731 || header[1] != 1 || header[4] < 2 || header[5] < 2
                    || header[4] > 512 || header[5] > 512) throw new IOException("Invalid cached scene");
            for (int value : header) out.writeInt(value);
            int count = header[4] * header[5];
            byte[] background = new byte[count * 2];
            in.readFully(background);
            out.write(background);
            float[] depth = new float[count];
            for (int i = 0; i < count; i++) depth[i] = in.readUnsignedShort() / 65535f;
            depth = SpatialDepthMath.translate(depth, header[4], header[5], x, y);
            for (float value : depth) out.writeShort(Math.round(value * 65535f));
            byte[] block = new byte[65536];
            int size;
            while ((size = in.read(block)) != -1) out.write(block, 0, size);
        }
    }

    static int wallpaperId(WallpaperManager wm) {
        int id = wm.getWallpaperId(WallpaperManager.FLAG_LOCK);
        return id > 0 ? id : wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
    }

    static void generate(Context context, File output, String subjectPath, int wallpaperId,
            int width, int height, float density, int offsetX, int offsetY) throws IOException {
        WallpaperManager wm = WallpaperManager.getInstance(context);
        if (wallpaperId(wm) != wallpaperId) throw new IOException("Wallpaper changed");
        float reduction = Math.min(1f, (float) Math.sqrt(2_000_000d / ((long) width * height)));
        int imageWidth = Math.max(1, Math.round(width * reduction));
        int imageHeight = Math.max(1, Math.round(height * reduction));
        Bitmap image = null;
        Bitmap subject = null;
        Bitmap modelImage = null;
        Bitmap modelSubject = null;
        Bitmap fillImage = null;
        try {
            Bitmap source = loadWallpaper(wm, imageWidth, imageHeight);
            try {
                image = cover(source, imageWidth, imageHeight, 0f, 0f);
            } finally {
                source.recycle();
            }
            source = decode(subjectPath, imageWidth, imageHeight);
            if (source == null) throw new IOException("Cannot read manual subject");
            try {
                subject = cover(source, imageWidth, imageHeight, 0f, 0f);
            } finally {
                source.recycle();
            }
            modelImage = Bitmap.createScaledBitmap(image, DEPTH_SIZE, DEPTH_SIZE, true);
            modelSubject = Bitmap.createScaledBitmap(subject, DEPTH_SIZE, DEPTH_SIZE, true);
            int count = DEPTH_SIZE * DEPTH_SIZE;
            int[] colors = new int[count];
            int[] alpha = new int[count];
            modelImage.getPixels(colors, 0, DEPTH_SIZE, 0, 0, DEPTH_SIZE, DEPTH_SIZE);
            modelSubject.getPixels(alpha, 0, DEPTH_SIZE, 0, 0, DEPTH_SIZE, DEPTH_SIZE);
            int occupied = 0;
            for (int i = 0; i < count; i++) {
                alpha[i] >>>= 24;
                if (alpha[i] > 6) occupied++;
            }
            if (occupied < 16 || occupied > count * 0.95) {
                throw new IOException("Use a transparent, aligned subject cutout");
            }
            float[] depth = estimate(context, colors);
            SpatialDepthMath.normalize(depth);
            float[] foregroundDepth = SpatialDepthMath.refine(depth, colors, alpha,
                    DEPTH_SIZE, DEPTH_SIZE);
            float[] backgroundDepth = foregroundDepth.clone();
            boolean[] hole = SpatialDepthMath.dilate(alpha, DEPTH_SIZE, DEPTH_SIZE, 2);
            SpatialDepthMath.fill(colors, backgroundDepth, hole, DEPTH_SIZE, DEPTH_SIZE);
            boolean[] outsideSubject = new boolean[count];
            int solid = 0;
            for (int value : alpha) if (value > 127) solid++;
            int threshold = solid > 0 ? 127 : 6;
            for (int i = 0; i < count; i++) outsideSubject[i] = alpha[i] <= threshold;
            SpatialDepthMath.fill(new int[count], foregroundDepth, outsideSubject,
                    DEPTH_SIZE, DEPTH_SIZE);
            foregroundDepth = SpatialDepthMath.translate(foregroundDepth, DEPTH_SIZE, DEPTH_SIZE,
                    offsetX * density / width, offsetY * density / height);
            for (int i = 0; i < count; i++) colors[i] = hole[i] ? colors[i] | 0xff000000 : 0;
            fillImage = Bitmap.createBitmap(colors, DEPTH_SIZE, DEPTH_SIZE, Bitmap.Config.ARGB_8888);
            new Canvas(image).drawBitmap(fillImage, null, new Rect(0, 0, imageWidth, imageHeight),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            if (wallpaperId(wm) != wallpaperId) throw new IOException("Wallpaper changed");
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                throw new IOException("Cannot encode spatial background");
            }
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(output), 65536))) {
                out.writeInt(0x53505731);
                out.writeInt(1);
                out.writeInt(imageWidth);
                out.writeInt(imageHeight);
                out.writeInt(DEPTH_SIZE);
                out.writeInt(DEPTH_SIZE);
                for (float value : backgroundDepth) out.writeShort(Math.round(value * 65535f));
                for (float value : foregroundDepth) out.writeShort(Math.round(value * 65535f));
                out.writeInt(png.size());
                png.writeTo(out);
            }
        } catch (RuntimeException e) {
            throw new IOException("Spatial preparation failed", e);
        } finally {
            if (modelImage != null && modelImage != image) modelImage.recycle();
            if (modelSubject != null && modelSubject != subject) modelSubject.recycle();
            if (fillImage != null) fillImage.recycle();
            if (subject != null) subject.recycle();
            if (image != null) image.recycle();
        }
    }

    private static float[] estimate(Context context, int[] pixels) throws IOException {
        File model = org.lunaris.lockstyles.models.DepthModels.INSTANCE.open(context, "depth_model.tflite");
        ByteBuffer input = ByteBuffer.allocateDirect(pixels.length * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        for (int color : pixels) {
            input.putFloat((((color >> 16) & 255) / 255f - MEAN[0]) / STD[0]);
            input.putFloat((((color >> 8) & 255) / 255f - MEAN[1]) / STD[1]);
            input.putFloat(((color & 255) / 255f - MEAN[2]) / STD[2]);
        }
        input.rewind();
        ByteBuffer output = ByteBuffer.allocateDirect(pixels.length * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        try (FileInputStream file = new FileInputStream(model)) {
            ByteBuffer mapped = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.getChannel().size());
            try (Interpreter interpreter = new Interpreter(mapped,
                    new Interpreter.Options().setNumThreads(2))) {
                int[] expected = {1, DEPTH_SIZE, DEPTH_SIZE, 3};
                if (!Arrays.equals(interpreter.getInputTensor(0).shape(), expected)
                        || interpreter.getOutputTensor(0).numElements() != pixels.length) {
                    throw new IOException("Unexpected spatial model tensors");
                }
                interpreter.run(input, output);
            }
        }
        float[] result = new float[pixels.length];
        output.rewind();
        output.asFloatBuffer().get(result);
        return result;
    }

    private static Bitmap loadWallpaper(WallpaperManager wm, int w, int h) throws IOException {
        for (int flag : new int[]{WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM}) {
            try (ParcelFileDescriptor descriptor = wm.getWallpaperFile(flag)) {
                if (descriptor == null) continue;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, options);
                options.inSampleSize = sample(options.outWidth, options.outHeight, w, h);
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(),
                        null, options);
                if (bitmap != null) return bitmap;
            }
        }
        throw new IOException("Cannot read a still wallpaper");
    }

    private static Bitmap decode(String path, int w, int h) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = sample(options.outWidth, options.outHeight, w, h);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path, options);
    }

    private static int sample(int sw, int sh, int w, int h) {
        int sample = 1;
        while (sw / (sample * 2) >= w && sh / (sample * 2) >= h) sample *= 2;
        return sample;
    }

    private static Bitmap cover(Bitmap source, int w, int h, float x, float y) {
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float scale = Math.max(w / (float) source.getWidth(), h / (float) source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((w - source.getWidth() * scale) * 0.5f + x,
                (h - source.getHeight() * scale) * 0.5f + y);
        new Canvas(output).drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return output;
    }
}
