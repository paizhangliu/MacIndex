package com.macindex.macindex.catalog;

import android.content.res.AssetManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.macindex.macindex.catalog.proto.CatalogPayload;

import java.io.IOException;
import java.io.InputStream;

/** Loads the Catalog compiled into the APK. */
public final class CatalogLoader {

    private static final String PAYLOAD_ASSET = "catalog/catalog.pb";

    private CatalogLoader() {
    }

    public static MachineCatalog load(final AssetManager assets) throws IOException {
        try (InputStream input = assets.open(PAYLOAD_ASSET)) {
            try {
                return new MachineCatalog(CatalogPayload.parseFrom(input));
            } catch (InvalidProtocolBufferException error) {
                throw new CatalogFormatException("Unable to parse the bundled Catalog", error);
            }
        }
    }
}
