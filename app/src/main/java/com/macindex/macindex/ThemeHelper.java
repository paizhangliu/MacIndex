package com.macindex.macindex;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

class ThemeHelper {

    static final int APPEARANCE_SYSTEM = 0;
    static final int APPEARANCE_LIGHT = 1;
    static final int APPEARANCE_DARK = 2;

    static int getAppearance(final Context context) {
        final int appearance = PrefsHelper.getIntPrefsSafe("appearanceMode", context);
        if (appearance < APPEARANCE_SYSTEM || appearance > APPEARANCE_DARK) {
            return APPEARANCE_SYSTEM;
        }
        return appearance;
    }

    static void applySaved(final Context context) {
        apply(getAppearance(context));
    }

    static void apply(final int appearance) {
        if (appearance == APPEARANCE_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (appearance == APPEARANCE_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    static void applyProcessorTypeLogo(final Context context, final ImageView image,
                                       final int drawableID) {
        image.clearColorFilter();
        if (!isNight(context)) {
            return;
        }
        if (drawableID == R.drawable.motorola) {
            applyMonochromeLogo(context, image);
        } else if (drawableID == R.drawable.intel
                || drawableID == R.drawable.powerpc
                || drawableID == R.drawable.applelogo) {
            image.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }

    static void applyMonochromeLogo(final Context context, final ImageView image) {
        image.clearColorFilter();
        if (!isNight(context)) {
            return;
        }
        final int background = ContextCompat.getColor(context, R.color.colorBackground);
        final float redScale = (Color.red(background) - 255) / 255f;
        final float greenScale = (Color.green(background) - 255) / 255f;
        final float blueScale = (Color.blue(background) - 255) / 255f;
        final ColorMatrix monochrome = new ColorMatrix(new float[]{
                redScale * 0.213f, redScale * 0.715f, redScale * 0.072f, 0, 255,
                greenScale * 0.213f, greenScale * 0.715f, greenScale * 0.072f, 0, 255,
                blueScale * 0.213f, blueScale * 0.715f, blueScale * 0.072f, 0, 255,
                0, 0, 0, 1, 0
        });
        image.setColorFilter(new ColorMatrixColorFilter(monochrome));
    }

    static void applyDarkAppLogo(final Context context, final ImageView image) {
        image.clearColorFilter();
        if (!isNight(context)) {
            return;
        }
        final ColorMatrix darkLogo = new ColorMatrix(new float[]{
                0.362f, 1.216f, 0.122f, 0, -179,
                0.362f, 1.216f, 0.122f, 0, -179,
                0.362f, 1.216f, 0.122f, 0, -179,
                0, 0, 0, 1, 0
        });
        image.setColorFilter(new ColorMatrixColorFilter(darkLogo));
    }

    static void applyInvertedLogo(final Context context, final ImageView image) {
        image.clearColorFilter();
        if (!isNight(context)) {
            return;
        }
        final int background = ContextCompat.getColor(context, R.color.colorBackground);
        final float redScale = (Color.red(background) - 255) / 255f;
        final float greenScale = (Color.green(background) - 255) / 255f;
        final float blueScale = (Color.blue(background) - 255) / 255f;
        final ColorMatrix inverted = new ColorMatrix(new float[]{
                redScale, 0, 0, 0, 255,
                0, greenScale, 0, 0, 255,
                0, 0, blueScale, 0, 255,
                0, 0, 0, 1, 0
        });
        image.setColorFilter(new ColorMatrixColorFilter(inverted));
    }

    static void applyMachineImage(final Context context, final ImageView image) {
        image.setPadding(0, 0, 0, 0);
        image.setBackgroundColor(ContextCompat.getColor(context, R.color.colorMachineSurface));
        applyImageMask(context, image);
    }

    static void applyImageMask(final Context context, final ImageView image) {
        image.clearColorFilter();
        if (isNight(context)) {
            image.setColorFilter(Color.argb(90, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
        }
    }

    private static boolean isNight(final Context context) {
        final int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
