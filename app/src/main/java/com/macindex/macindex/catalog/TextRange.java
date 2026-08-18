package com.macindex.macindex.catalog;

import java.util.Objects;

/** A half-open character range used for model-name formatting. */
public final class TextRange {

    private final int startInclusive;
    private final int endExclusive;

    TextRange(final int startInclusive, final int endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    public int startInclusive() {
        return startInclusive;
    }

    public int endExclusive() {
        return endExclusive;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextRange)) {
            return false;
        }
        final TextRange that = (TextRange) other;
        return startInclusive == that.startInclusive && endExclusive == that.endExclusive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startInclusive, endExclusive);
    }
}
