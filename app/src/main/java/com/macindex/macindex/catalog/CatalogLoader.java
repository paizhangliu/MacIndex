package com.macindex.macindex.catalog;

import android.content.res.AssetManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.macindex.macindex.catalog.proto.CatalogPayload;

import java.io.IOException;
import java.io.InputStream;

/** Loads the one trusted catalog bundled in the APK. */
public final class CatalogLoader {

    public static final String ASSET_NAME = "catalog.pb";

    private CatalogLoader() {
    }

    public static MachineCatalog load(final AssetManager assets) throws IOException {
        try (InputStream input = assets.open(ASSET_NAME)) {
            return load(input);
        }
    }

    /** Parses a catalog without taking ownership of the supplied stream. */
    public static MachineCatalog load(final InputStream input) throws IOException {
        try {
            return new MachineCatalog(CatalogPayload.parseFrom(input));
        } catch (InvalidProtocolBufferException e) {
            throw new CatalogFormatException("Unable to parse bundled catalog", e);
        }
    }

    public static MachineCatalog load(final byte[] payload) {
        try {
            return new MachineCatalog(CatalogPayload.parseFrom(payload));
        } catch (InvalidProtocolBufferException e) {
            throw new CatalogFormatException("Unable to parse bundled catalog", e);
        }
    }
}
