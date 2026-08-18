package com.macindex.macindex.catalog;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** One non-empty, ordered group in a catalog browse result. */
public final class BrowseGroup {

    private final String key;
    private final String label;
    private final String sectionKey;
    private final List<Machine> machines;

    BrowseGroup(final String key, final String label, @Nullable final String sectionKey,
                final List<Machine> machines) {
        this.key = key;
        this.label = label;
        this.sectionKey = sectionKey;
        this.machines = Collections.unmodifiableList(machines);
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    @Nullable
    public String sectionKey() {
        return sectionKey;
    }

    public List<Machine> machines() {
        return machines;
    }
}
