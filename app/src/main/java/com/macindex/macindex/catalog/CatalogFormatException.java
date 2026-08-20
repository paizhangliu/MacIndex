package com.macindex.macindex.catalog;

/** Indicates that the Catalog bundled with this app cannot be used. */
public final class CatalogFormatException extends IllegalStateException {

    public CatalogFormatException(final String message) {
        super(message);
    }

    public CatalogFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
