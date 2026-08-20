package com.macindex.macindex.catalog;

import java.util.Objects;

/** A catalog introduction date with an optional configuration qualifier. */
public final class IntroductionDate implements Comparable<IntroductionDate> {

    private final int year;
    private final int month;
    private final String qualifier;

    IntroductionDate(final int year, final int month, final String qualifier) {
        this.year = year;
        this.month = month;
        this.qualifier = qualifier;
    }

    public int year() {
        return year;
    }

    public String dateText() {
        return year + "." + month;
    }

    public String displayText() {
        final String date = dateText();
        return qualifier == null ? date : date + " (" + qualifier + ")";
    }

    int sortKey() {
        return year * 100 + month;
    }

    @Override
    public int compareTo(final IntroductionDate other) {
        return Integer.compare(sortKey(), other.sortKey());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IntroductionDate)) {
            return false;
        }
        final IntroductionDate that = (IntroductionDate) other;
        return year == that.year && month == that.month
                && Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, qualifier);
    }
}
