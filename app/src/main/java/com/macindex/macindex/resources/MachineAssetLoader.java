package com.macindex.macindex.resources;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.macindex.macindex.BitmapLoadingHelper;
import com.macindex.macindex.catalog.CatalogFormatException;
import com.macindex.macindex.catalog.Machine;

import java.io.IOException;

/** Loads immutable machine artwork named by a trusted catalog asset key. */
public final class MachineAssetLoader {

    private static final String MACHINE_ASSET_DIRECTORY = "machines/";
    private static final String MACHINE_ASSET_EXTENSION = ".webp";

    private MachineAssetLoader() {
    }

    @NonNull
    public static Bitmap loadPicture(@NonNull final AssetManager assets,
                                     @NonNull final Machine machine,
                                     final int requestedWidth,
                                     final int requestedHeight) {
        final String assetPath = MACHINE_ASSET_DIRECTORY + machine.pictureAssetKey()
                + MACHINE_ASSET_EXTENSION;
        try {
            final Bitmap picture = BitmapLoadingHelper.decodeSampledBitmapFromAsset(
                    assets, assetPath, requestedWidth, requestedHeight);
            if (picture == null) {
                throw new CatalogFormatException(
                        "Unable to decode picture for " + machine.uid() + " at " + assetPath);
            }
            return picture;
        } catch (IOException exception) {
            throw new CatalogFormatException(
                    "Unable to load picture for " + machine.uid() + " at " + assetPath,
                    exception);
        }
    }
}
