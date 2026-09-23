package com.example.visionassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

// توابع مشترک تبدیل عکس (از دوربین یا از فایل انتخاب‌شده) به بایت‌های jpeg فشرده
public class ImageUtils {

    public static byte[] imageProxyToJpegBytes(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return bytes;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static byte[] fileUriToJpegBytes(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return new byte[0];
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
