package com.macindex.macindex.catalog;

/** Typed grouping offered by the main catalog browser. */
public enum BrowseGrouping {
    NAMES("names"),
    PROCESSORS("processors"),
    YEARS("years");

    private final String catalogKey;

    BrowseGrouping(final String catalogKey) {
        this.catalogKey = catalogKey;
    }

    static BrowseGrouping fromCatalogKey(final String key) {
        for (BrowseGrouping grouping : values()) {
            if (grouping.catalogKey.equals(key)) {
                return grouping;
            }
        }
        throw new CatalogFormatException("Unknown browse grouping " + key);
    }
}
