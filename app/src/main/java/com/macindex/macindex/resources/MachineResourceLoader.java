package com.macindex.macindex.resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;

import java.util.List;

/** Resolves media directly from the Catalog bundled in the APK. */
public final class MachineResourceLoader {

    private static final String ROOT = "catalog/";

    private MachineResourceLoader() {
    }

    @Nullable
    public static LogoAsset processorTypeLogo(@NonNull final MachineCatalog catalog,
                                               @NonNull final Machine machine) {
        return machine.processorFamilyKeys().isEmpty()
                ? null : logo(catalog, machine.typeLogoKey());
    }

    @NonNull
    public static LogoAsset[] processorLogos(@NonNull final MachineCatalog catalog,
                                              @NonNull final Machine machine) {
        return logos(catalog, machine.processorLogoKeys());
    }

    @NonNull
    public static LogoAsset[] graphicsLogos(@NonNull final MachineCatalog catalog,
                                             @NonNull final Machine machine) {
        return logos(catalog, machine.graphicsLogoKeys());
    }

    @NonNull
    public static String pictureAsset(@NonNull final Machine machine) {
        return ROOT + "machines/" + machine.pictureAssetKey() + ".webp";
    }

    @Nullable
    public static String startupSoundAsset(@NonNull final Machine machine) {
        return soundAsset(machine.startupSoundKey());
    }

    @Nullable
    public static String deathSoundAsset(@NonNull final Machine machine) {
        return soundAsset(machine.deathSoundKey());
    }

    private static LogoAsset[] logos(final MachineCatalog catalog,
                                      final List<String> keys) {
        final LogoAsset[] assets = new LogoAsset[keys.size()];
        for (int index = 0; index < keys.size(); index++) {
            assets[index] = logo(catalog, keys.get(index));
        }
        return assets;
    }

    private static LogoAsset logo(final MachineCatalog catalog, final String key) {
        return new LogoAsset(ROOT + "logos/" + key + ".webp",
                catalog.logoNightTreatment(key));
    }

    @Nullable
    private static String soundAsset(@Nullable final String key) {
        return key == null ? null : ROOT + "sounds/" + key + ".flac";
    }
}
