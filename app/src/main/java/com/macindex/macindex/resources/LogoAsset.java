package com.macindex.macindex.resources;

import androidx.annotation.DrawableRes;

/** Generated-registry value describing one logo resource and its night rendering policy. */
public final class LogoAsset {

    public enum NightTreatment {
        DARKEN,
        WHITE_TINT,
        MONOCHROME
    }

    @DrawableRes
    private final int drawableRes;
    private final NightTreatment nightTreatment;

    LogoAsset(@DrawableRes final int drawableRes,
              final NightTreatment nightTreatment) {
        this.drawableRes = drawableRes;
        this.nightTreatment = nightTreatment;
    }

    @DrawableRes
    public int drawableRes() {
        return drawableRes;
    }

    public NightTreatment nightTreatment() {
        return nightTreatment;
    }
}
