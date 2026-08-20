package com.macindex.macindex;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;

public class BitmapLoadingHelper {
    /* https://stackoverflow.com/questions/25719620/how-to-solve-java-lang-outofmemoryerror-trouble-in-android */
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromAsset(final AssetManager assets,
                                                      final String assetPath,
                                                      final int reqWidth,
                                                      final int reqHeight) throws IOException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = assets.open(assetPath)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        if (reqWidth > 0 && reqHeight > 0) {
            final float displayScale = Math.min((float) reqWidth / options.outWidth,
                    (float) reqHeight / options.outHeight);
            final int displayedWidth = displayScale < 1
                    ? Math.max(1, Math.round(options.outWidth * displayScale))
                    : options.outWidth;
            final int displayedHeight = displayScale < 1
                    ? Math.max(1, Math.round(options.outHeight * displayScale))
                    : options.outHeight;
            options.inSampleSize = calculateInSampleSize(
                    options, displayedWidth, displayedHeight);
        }
        options.inScaled = false;
        options.inJustDecodeBounds = false;
        try (InputStream input = assets.open(assetPath)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }
}
