/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.spatial;

import java.util.Arrays;

final class SpatialDepthMath {
    static float[] translate(float[] depth, int w, int h, float horizontal, float vertical) {
        float[] result = new float[depth.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sx = Math.max(0f, Math.min(w - 1f, x - horizontal * (w - 1)));
                float sy = Math.max(0f, Math.min(h - 1f, y - vertical * (h - 1)));
                int x0 = (int) sx;
                int y0 = (int) sy;
                int x1 = Math.min(w - 1, x0 + 1);
                int y1 = Math.min(h - 1, y0 + 1);
                float fx = sx - x0;
                float fy = sy - y0;
                float a = depth[y0 * w + x0] * (1f - fx) + depth[y0 * w + x1] * fx;
                float b = depth[y1 * w + x0] * (1f - fx) + depth[y1 * w + x1] * fx;
                result[y * w + x] = a * (1f - fy) + b * fy;
            }
        }
        return result;
    }

    static void normalize(float[] depth) {
        float[] sorted = depth.clone();
        for (float value : sorted) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite depth");
        }
        Arrays.sort(sorted);
        float low = sorted[sorted.length * 2 / 100];
        float high = sorted[sorted.length * 98 / 100];
        if (high - low < 0.00001f) throw new IllegalArgumentException("No usable depth contrast");
        for (int i = 0; i < depth.length; i++) {
            depth[i] = Math.max(0f, Math.min(1f, (depth[i] - low) / (high - low)));
        }
    }

    static float[] refine(float[] depth, int[] colors, int[] alpha, int w, int h) {
        float[] result = new float[depth.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float total = 0f;
                float weight = 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int n = ny * w + nx;
                        if ((alpha[i] > 127) != (alpha[n] > 127)) continue;
                        int a = colors[i];
                        int b = colors[n];
                        int difference = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                                + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                                + Math.abs((a & 255) - (b & 255));
                        float k = 1f / (1f + difference * 0.08f + dx * dx + dy * dy);
                        total += depth[n] * k;
                        weight += k;
                    }
                }
                result[i] = total / weight;
            }
        }
        return result;
    }

    static boolean[] dilate(int[] alpha, int w, int h, int radius) {
        boolean[] hole = new boolean[alpha.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (alpha[y * w + x] <= 6) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) hole[ny * w + nx] = true;
                    }
                }
            }
        }
        return hole;
    }

    static void fill(int[] colors, float[] depth, boolean[] hole, int w, int h) {
        int[] queue = new int[hole.length];
        boolean[] visited = new boolean[hole.length];
        int tail = 0;
        for (int i = 0; i < hole.length; i++) {
            if (!hole[i]) {
                visited[i] = true;
                queue[tail++] = i;
            }
        }
        if (tail == 0) throw new IllegalArgumentException("Subject covers the entire image");
        for (int head = 0; head < tail; head++) {
            int i = queue[head];
            int x = i % w;
            int y = i / w;
            for (int direction = 0; direction < 4; direction++) {
                int n;
                if (direction == 0) {
                    if (x == 0) continue;
                    n = i - 1;
                } else if (direction == 1) {
                    if (x == w - 1) continue;
                    n = i + 1;
                } else if (direction == 2) {
                    if (y == 0) continue;
                    n = i - w;
                } else {
                    if (y == h - 1) continue;
                    n = i + w;
                }
                if (visited[n]) continue;
                visited[n] = true;
                colors[n] = colors[i];
                depth[n] = depth[i];
                queue[tail++] = n;
            }
        }
        for (int pass = 0; pass < 8; pass++) {
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int i = y * w + x;
                    if (!hole[i]) continue;
                    int a = colors[i - 1];
                    int b = colors[i + 1];
                    int c = colors[i - w];
                    int d = colors[i + w];
                    int red = (((a >> 16) & 255) + ((b >> 16) & 255)
                            + ((c >> 16) & 255) + ((d >> 16) & 255)) / 4;
                    int green = (((a >> 8) & 255) + ((b >> 8) & 255)
                            + ((c >> 8) & 255) + ((d >> 8) & 255)) / 4;
                    int blue = ((a & 255) + (b & 255) + (c & 255) + (d & 255)) / 4;
                    colors[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
                    depth[i] = (depth[i - 1] + depth[i + 1] + depth[i - w] + depth[i + w]) * 0.25f;
                }
            }
        }
    }
}
