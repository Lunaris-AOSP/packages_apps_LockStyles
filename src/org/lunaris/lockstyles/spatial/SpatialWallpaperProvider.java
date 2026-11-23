/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.spatial;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.lunaris.clockmaker.spatial.SpatialWallpaperScene;

public final class SpatialWallpaperProvider extends ContentProvider {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "SpatialWallpaperPrepare");
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private Context storage;

    @Override
    public boolean onCreate() {
        storage = getContext().createDeviceProtectedStorageContext();
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !"/scene".equals(uri.getPath())) {
            throw new FileNotFoundException("Read-only spatial scene");
        }
        try {
            String subject = uri.getQueryParameter("subject");
            if (subject == null || !subject.equals(Settings.System.getString(
                    storage.getContentResolver(), "depth_wallpaper_subject_image_uri"))) {
                throw new IOException("Subject changed");
            }
            int wallpaper = integer(uri, "wallpaper");
            int width = integer(uri, "width");
            int height = integer(uri, "height");
            int x = integer(uri, "x");
            int y = integer(uri, "y");
            float density = Float.parseFloat(uri.getQueryParameter("density"));
            if (width < 1 || height < 1 || width > 4096 || height > 4096
                    || !Float.isFinite(density) || density <= 0f || density > 10f
                    || Math.abs((long) x) > 1000 || Math.abs((long) y) > 1000) {
                throw new IOException("Invalid spatial viewport");
            }
            File subjectFile = new File(subject).getCanonicalFile();
            if (!subjectFile.isFile()) throw new IOException("Subject file missing");
            String identity = "|scene-v3|" + subjectFile.length() + "|" + subjectFile.lastModified();
            String key = digest(SpatialWallpaperScene.request(subjectFile.getPath(), wallpaper,
                    width, height, density, x, y).toString() + identity);
            String base = digest(SpatialWallpaperScene.request(subjectFile.getPath(), wallpaper,
                    width, height, density, 0, 0).toString() + identity);
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            try {
                PipeTask task = new PipeTask(pipe[1], () -> prepare(pipe[1], key, base,
                        subjectFile.getPath(), wallpaper, width, height, density, x, y));
                synchronized (worker) {
                    for (Runnable queued : worker.getQueue()) {
                        if (queued instanceof PipeTask && ((PipeTask) queued).canceled()) {
                            if (worker.remove(queued)) ((PipeTask) queued).cancel();
                        }
                    }
                    worker.execute(task);
                }
            } catch (java.util.concurrent.RejectedExecutionException e) {
                pipe[0].close();
                pipe[1].close();
                throw new IOException("Spatial preparation queue is full", e);
            }
            return pipe[0];
        } catch (IOException | RuntimeException e) {
            FileNotFoundException error = new FileNotFoundException(e.getMessage());
            error.initCause(e);
            throw error;
        }
    }

    private static final class PipeTask implements Runnable {
        private final ParcelFileDescriptor pipe;
        private final Runnable action;

        PipeTask(ParcelFileDescriptor pipe, Runnable action) {
            this.pipe = pipe;
            this.action = action;
        }

        @Override
        public void run() {
            try {
                pipe.checkError();
                action.run();
            } catch (IOException e) {
                cancel();
            }
        }

        void cancel() {
            try {
                pipe.closeWithError("Spatial request superseded");
            } catch (IOException ignored) { }
        }

        boolean canceled() {
            try {
                pipe.checkError();
                return false;
            } catch (IOException e) {
                return true;
            }
        }
    }

    private void prepare(ParcelFileDescriptor pipe, String key, String base, String subject, int wallpaper,
            int width, int height, float density, int x, int y) {
        File pending = null;
        try {
            if (SpatialSceneGenerator.wallpaperId(android.app.WallpaperManager.getInstance(storage))
                    != wallpaper) throw new IOException("Wallpaper changed");
            File directory = new File(storage.getCacheDir(), "spatial-scenes-v3");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create spatial cache");
            }
            File original = new File(directory, base + "-base.scene");
            if (!original.isFile()) {
                pending = new File(directory, base + ".tmp");
                SpatialSceneGenerator.generate(storage, pending, subject, wallpaper,
                        width, height, density, 0, 0);
                if (!pending.renameTo(original)) throw new IOException("Cannot publish base scene");
                pending = null;
            }
            original.setLastModified(System.currentTimeMillis());
            pipe.checkError();
            File scene = x == 0 && y == 0 ? original : new File(directory, key + ".scene");
            if (!scene.isFile()) {
                pending = new File(directory, key + ".tmp");
                SpatialSceneGenerator.shift(original, pending, x * density / width, y * density / height);
                if (!pending.renameTo(scene)) throw new IOException("Cannot publish spatial scene");
                pending = null;
            }
            scene.setLastModified(System.currentTimeMillis());
            if (SpatialSceneGenerator.wallpaperId(android.app.WallpaperManager.getInstance(storage))
                    != wallpaper) throw new IOException("Wallpaper changed");
            File[] cached = directory.listFiles((folder, name) -> name.endsWith(".scene"));
            if (cached != null && cached.length > 6) {
                java.util.Arrays.sort(cached, java.util.Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < cached.length - 6; i++) {
                    if (!cached[i].equals(scene) && !cached[i].equals(original)) cached[i].delete();
                }
            }
            try (FileInputStream input = new FileInputStream(scene);
                    OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe)) {
                byte[] block = new byte[65536];
                int size;
                while ((size = input.read(block)) != -1) output.write(block, 0, size);
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            Log.w("SpatialWallpaper", "Cannot prepare spatial scene", e);
            try {
                pipe.closeWithError("Spatial scene unavailable");
            } catch (IOException ignored) { }
        } finally {
            if (pending != null) pending.delete();
            try {
                pipe.close();
            } catch (IOException ignored) { }
        }
    }

    private static int integer(Uri uri, String name) {
        return Integer.parseInt(uri.getQueryParameter(name));
    }

    private static String digest(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Override public String getType(Uri uri) { return "application/vnd.lunaris.spatial-wallpaper"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] arguments, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] arguments) { throw new UnsupportedOperationException(); }
}
