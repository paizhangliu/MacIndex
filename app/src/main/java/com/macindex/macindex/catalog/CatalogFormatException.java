package com.macindex.macindex.catalog;

/** Indicates that the trusted catalog bundled with the application is invalid. */
public final class CatalogFormatException extends IllegalStateException {

    public CatalogFormatException(final String message) {
        super(message);
    }

    public CatalogFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
