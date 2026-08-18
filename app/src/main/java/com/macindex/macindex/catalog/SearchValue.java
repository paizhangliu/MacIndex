package com.macindex.macindex.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One authored search value and the information needed to rank and explain its matches. */
final class SearchValue {

    static final class Geometry {
        final SearchHit.Relation relation;
        final int unitCodePointCount;
        final int matchPosition;

        private Geometry(final SearchHit.Relation relation,
                         final int unitCodePointCount,
                         final int matchPosition) {
            this.relation = relation;
            this.unitCodePointCount = unitCodePointCount;
            this.matchPosition = matchPosition;
        }
    }

    private final String value;
    private final String normalizedValue;
    private final SearchHit.Field field;
    private final boolean canonicalName;
    private final String qualifier;
    private final List<String> revisions;

    SearchValue(final String value, final String qualifier, final List<String> sourceRevisions,
                final SearchHit.Field field, final boolean canonicalName) {
        revisions = Collections.unmodifiableList(new ArrayList<>(sourceRevisions));
        this.value = value;
        normalizedValue = Machine.normalize(value);
        this.field = field;
        this.canonicalName = canonicalName;
        this.qualifier = qualifier;
    }

    String displayValue() {
        final String authoredDisplay = field == SearchHit.Field.PART_NUMBER
                ? partNumberDisplay(value, revisions) : value;
        return qualifier == null
                ? authoredDisplay : authoredDisplay + " (" + qualifier + ")";
    }

    String normalizedValue() {
        return normalizedValue;
    }

    SearchHit.Field field() {
        return field;
    }

    boolean isCanonicalName() {
        return canonicalName;
    }

    boolean supportsRevision(final String revision) {
        return revisions.contains(revision);
    }

    String partNumberEvidenceValue(final String requestedRevision) {
        if (field != SearchHit.Field.PART_NUMBER) {
            throw new IllegalStateException("Only Part Numbers have revisions");
        }
        final String revision = requestedRevision == null
                ? revisions.get(0) : requestedRevision;
        if (!supportsRevision(revision)) {
            throw new IllegalArgumentException("Unknown Part Number revision " + revision);
        }
        final String display = value + "*/" + revision;
        return qualifier == null ? display : display + " (" + qualifier + ")";
    }

    private static String partNumberDisplay(final String stem, final List<String> revisions) {
        if (revisions.isEmpty()) {
            throw new CatalogFormatException("Missing Part Number revision for " + stem);
        }
        final StringBuilder result = new StringBuilder();
        for (String revision : revisions) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(stem).append("*/").append(revision);
        }
        return result.toString();
    }

    Geometry geometry(final int normalizedStart, final int normalizedEnd) {
        final int codePointCount = normalizedValue.codePointCount(
                0, normalizedValue.length());
        if (!isHumanField(field)) {
            final SearchHit.Relation relation = normalizedStart == 0
                    && normalizedEnd == normalizedValue.length()
                    ? SearchHit.Relation.COMPLETE_UNIT
                    : normalizedStart == 0
                            ? SearchHit.Relation.UNIT_PREFIX
                            : SearchHit.Relation.UNIT_INTERNAL;
            return new Geometry(relation, codePointCount,
                    normalizedValue.codePointCount(0, normalizedStart));
        }

        int unitStart = 0;
        int unitEnd = normalizedValue.length();
        boolean startsAtBoundary = normalizedStart == 0;
        boolean endsAtBoundary = normalizedEnd == normalizedValue.length();
        int boundary = 0;
        while (boundary < normalizedValue.length()) {
            boundary += Character.charCount(value.codePointAt(boundary));
            if (boundary < normalizedValue.length()
                    && !isHumanTextBoundary(value, boundary)) {
                continue;
            }
            if (boundary == normalizedStart) {
                startsAtBoundary = true;
            }
            if (boundary == normalizedEnd) {
                endsAtBoundary = true;
            }
            if (boundary <= normalizedStart && boundary > unitStart) {
                unitStart = boundary;
            }
            if (boundary >= normalizedEnd && boundary < unitEnd) {
                unitEnd = boundary;
            }
        }
        if (unitStart > normalizedStart || unitEnd < normalizedEnd || unitStart >= unitEnd) {
            throw new CatalogFormatException(
                    "Cannot locate semantic unit in " + normalizedValue);
        }
        final SearchHit.Relation relation = startsAtBoundary && endsAtBoundary
                ? SearchHit.Relation.COMPLETE_UNIT
                : startsAtBoundary
                        ? SearchHit.Relation.UNIT_PREFIX
                        : SearchHit.Relation.UNIT_INTERNAL;
        return new Geometry(relation,
                normalizedValue.codePointCount(unitStart, unitEnd),
                normalizedValue.codePointCount(unitStart, normalizedStart));
    }

    TextRange displayRange(final int normalizedStart, final int normalizedEnd) {
        if (normalizedStart < 0 || normalizedStart >= normalizedEnd
                || normalizedEnd > normalizedValue.length()) {
            throw new IllegalStateException("Selected search range is outside its value");
        }
        if (normalizedEnd > value.length()) {
            throw new CatalogFormatException(
                    "Cannot map search match into display value " + displayValue());
        }
        return new TextRange(normalizedStart, normalizedEnd);
    }

    private static boolean isHumanField(final SearchHit.Field field) {
        return field == SearchHit.Field.NAME || field == SearchHit.Field.CODENAME;
    }

    private static boolean isHumanTextBoundary(final String value, final int boundary) {
        final int current = value.codePointAt(boundary);
        if (isCombiningMark(current)) {
            return false;
        }
        final int previous = previousNonMarkCodePoint(value, boundary);
        final boolean previousWord = isHumanWordCodePoint(previous);
        final boolean currentWord = isHumanWordCodePoint(current);
        if (!previousWord || !currentWord) {
            return true;
        }
        if (Character.isLetter(previous) && Character.isDigit(current)
                || Character.isDigit(previous) && Character.isLetter(current)) {
            return true;
        }
        if (Character.isLowerCase(previous) && Character.isUpperCase(current)) {
            return true;
        }
        if (Character.isUpperCase(previous) && Character.isUpperCase(current)) {
            final int next = nextNonMarkCodePoint(
                    value, boundary + Character.charCount(current));
            return next >= 0 && Character.isLowerCase(next);
        }
        return false;
    }

    private static int previousNonMarkCodePoint(final String value, final int boundary) {
        int offset = boundary;
        int codePoint;
        do {
            codePoint = value.codePointBefore(offset);
            offset -= Character.charCount(codePoint);
        } while (offset > 0 && isCombiningMark(codePoint));
        return codePoint;
    }

    private static int nextNonMarkCodePoint(final String value, final int start) {
        int offset = start;
        while (offset < value.length()) {
            final int codePoint = value.codePointAt(offset);
            if (!isCombiningMark(codePoint)) {
                return codePoint;
            }
            offset += Character.charCount(codePoint);
        }
        return -1;
    }

    private static boolean isHumanWordCodePoint(final int codePoint) {
        return Character.isLetterOrDigit(codePoint) || isCombiningMark(codePoint);
    }

    private static boolean isCombiningMark(final int codePoint) {
        final int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

}
