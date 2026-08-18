package com.macindex.macindex.catalog;

/** Typed manufacturer scope defined by the generated catalog taxonomy. */
public enum BrowseScope {
    ALL("all"),
    APPLE_68K("apple68k"),
    POWERPC("appleppc"),
    INTEL("appleintel"),
    APPLE_SILICON("applearm");

    private final String catalogKey;

    BrowseScope(final String catalogKey) {
        this.catalogKey = catalogKey;
    }

    boolean includes(final String manufacturerKey) {
        return this == ALL || catalogKey.equals(manufacturerKey);
    }
}
