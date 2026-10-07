package com.example.lostandfound;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

// Firebase Storage needs the Blaze (billing) plan on new projects, so instead of
// uploading photos to Storage, we shrink them and save them as Base64 strings
// directly inside Realtime Database (which stays on the free Spark plan).
public class ImageUtils {

    // Resize + compress a picked image and return it as a Base64 string.
    // maxDimension keeps the encoded string small enough for Realtime Database
    // (e.g. 600px + 55% JPEG quality is usually 50-150KB, which is safe).
    public static String encodeImageToBase64(Context context, Uri uri, int maxDimension, int quality) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            Bitmap original = BitmapFactory.decodeStream(input);
            if (original == null) return null;

            Bitmap resized = resizeBitmap(original, maxDimension);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap resizeBitmap(Bitmap source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        if (ratio >= 1) return source; // already small enough, don't upscale
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    public static Bitmap decodeBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    // Shows an item's photo whether it's a new Base64 string or an old Storage
    // https:// URL (kept for backward compatibility with any old test data).
    public static void loadItemImage(Context context, String imageData, ImageView target, int placeholderRes) {
        if (imageData == null || imageData.isEmpty()) {
            target.setImageDrawable(null);
            target.setBackgroundResource(placeholderRes);
            return;
        }
        if (imageData.startsWith("http")) {
            target.setBackground(null);
            Glide.with(context).load(imageData).into(target);
            return;
        }
        Bitmap bmp = decodeBase64(imageData);
        if (bmp != null) {
            target.setBackground(null);
            target.setImageBitmap(bmp);
        } else {
            target.setImageDrawable(null);
            target.setBackgroundResource(placeholderRes);
        }
    }

    // Circular profile-photo variant (keeps the same circleCrop look Glide gave before,
    // now working for local Base64 photos as well as any old Storage https:// URLs).
    public static void loadProfileImage(Context context, String imageData, ImageView target) {
        if (imageData == null || imageData.isEmpty()) return;
        if (imageData.startsWith("http")) {
            Glide.with(context).load(imageData).circleCrop().into(target);
            return;
        }
        Bitmap bmp = decodeBase64(imageData);
        if (bmp != null) {
            Glide.with(context).load(bmp).circleCrop().into(target);
        }
    }
}
