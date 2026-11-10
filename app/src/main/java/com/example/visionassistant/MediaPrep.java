package com.example.visionassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaMetadataRetriever;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** Extracts frames from a video, drops near-duplicates, shrinks them and tiles into one JPEG. */
public final class MediaPrep {
    private static final int MAX_FRAMES = 12;
    private static final int CELL = 480;

    private MediaPrep() {}

    public static byte[] videoToGrid(Context c, byte[] video, int intervalSec) {
        File f = new File(c.getCacheDir(), "prep_" + System.currentTimeMillis() + ".mp4");
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(video); }
            r.setDataSource(f.getAbsolutePath());
            long durMs = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            long step = Math.max(1, intervalSec) * 1000L;
            if (durMs / step + 1 > MAX_FRAMES) step = Math.max(step, durMs / (MAX_FRAMES - 1));
            java.util.List<Bitmap> frames = new java.util.ArrayList<>();
            int[] prev = null;
            for (long t = 0; t <= durMs && frames.size() < MAX_FRAMES; t += step) {
                Bitmap b = r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (b == null) continue;
                Bitmap sig = Bitmap.createScaledBitmap(b, 8, 8, true);
                int[] px = new int[64]; sig.getPixels(px, 0, 8, 0, 0, 8, 8);
                long diff = 0;
                if (prev != null) { for (int i = 0; i < 64; i++) diff += Math.abs((px[i] & 0xff) - (prev[i] & 0xff)) + Math.abs(((px[i] >> 8) & 0xff) - ((prev[i] >> 8) & 0xff)); }
                if (prev != null && diff < 64 * 6) { b.recycle(); continue; }
                prev = px;
                float sc = (float) CELL / Math.max(b.getWidth(), b.getHeight());
                frames.add(Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * sc)), Math.max(1, Math.round(b.getHeight() * sc)), true));
            }
            if (frames.isEmpty()) return null;
            int cols = frames.size() <= 1 ? 1 : (frames.size() <= 4 ? 2 : 3);
            int rows = (frames.size() + cols - 1) / cols;
            Bitmap out = Bitmap.createBitmap(cols * CELL, rows * CELL, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawColor(0xFF000000);
            for (int i = 0; i < frames.size(); i++) {
                Bitmap b = frames.get(i);
                cv.drawBitmap(b, (i % cols) * CELL + (CELL - b.getWidth()) / 2f, (i / cols) * CELL + (CELL - b.getHeight()) / 2f, null);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, 65, bo);
            return bo.toByteArray();
        } catch (Exception e) {
            ErrorLog.add(c, "MediaPrep: " + e);
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
            f.delete();
        }
    }
}
