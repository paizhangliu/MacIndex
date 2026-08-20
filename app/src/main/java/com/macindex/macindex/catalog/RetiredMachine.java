package com.macindex.macindex.catalog;

import androidx.annotation.Nullable;

/** One cumulative old-UID resolution entry carried by the current Catalog. */
public final class RetiredMachine {
    private final String previousName;
    private final String replacementUid;

    RetiredMachine(final String thisPreviousName,
                   @Nullable final String thisReplacementUid) {
        previousName = thisPreviousName;
        replacementUid = thisReplacementUid;
    }

    public String previousName() {
        return previousName;
    }

    @Nullable
    public String replacementUid() {
        return replacementUid;
    }
}
