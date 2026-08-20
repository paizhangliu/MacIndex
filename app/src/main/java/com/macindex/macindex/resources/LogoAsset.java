package com.macindex.macindex.resources;

import com.macindex.macindex.catalog.LogoNightTreatment;

/** One logo asset and its Catalog-authored night rendering policy. */
public final class LogoAsset {

    private final String assetPath;
    private final LogoNightTreatment nightTreatment;

    LogoAsset(final String thisAssetPath,
              final LogoNightTreatment thisNightTreatment) {
        assetPath = thisAssetPath;
        nightTreatment = thisNightTreatment;
    }

    public String assetPath() {
        return assetPath;
    }

    public LogoNightTreatment nightTreatment() {
        return nightTreatment;
    }
}
