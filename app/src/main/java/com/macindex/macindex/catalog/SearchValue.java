package com.macindex.macindex.catalog;

import com.macindex.macindex.catalog.proto.CatalogSearchDisplayMapping;
import com.macindex.macindex.catalog.proto.CatalogSearchField;
import com.macindex.macindex.catalog.proto.CatalogSearchValue;

import java.util.Collections;
import java.util.List;

/** One searchable value and the information needed to rank and explain its matches. */
final class SearchValue {

    private enum DisplayMapping {
        DIRECT,
        COMPACT_WHITESPACE,
        FIXED
    }

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
    private final String displayValue;
    private final SearchHit.Field field;
    private final boolean canonicalName;
    private final String qualifier;
    private final List<String> revisions;
    private final boolean exactTokenOnly;
    private final DisplayMapping displayMapping;
    private final String normalizedDisplaySource;
    private final int fixedDisplayStart;
    private final int fixedDisplayEnd;

    SearchValue(final String value, final String qualifier, final List<String> sourceRevisions,
                final SearchHit.Field field, final boolean canonicalName) {
        revisions = sourceRevisions;
        this.value = value;
        normalizedValue = Machine.normalize(value);
        this.field = field;
        this.canonicalName = canonicalName;
        this.qualifier = qualifier;
        final String authoredDisplay = field == SearchHit.Field.PART_NUMBER
                ? partNumberDisplay(value, revisions) : value;
        displayValue = qualifier == null
                ? authoredDisplay : authoredDisplay + " (" + qualifier + ")";
        exactTokenOnly = false;
        displayMapping = DisplayMapping.DIRECT;
        normalizedDisplaySource = null;
        fixedDisplayStart = -1;
        fixedDisplayEnd = -1;
    }

    SearchValue(final CatalogSearchValue source) {
        value = source.getValue();
        normalizedValue = Machine.normalize(value);
        displayValue = source.hasDisplayValue() ? source.getDisplayValue() : value;
        field = searchField(source.getField());
        canonicalName = source.getCanonicalName();
        qualifier = null;
        revisions = Collections.emptyList();
        exactTokenOnly = source.getExactTokenOnly();
        displayMapping = displayMapping(source.getDisplayMapping());
        normalizedDisplaySource = displayMapping == DisplayMapping.COMPACT_WHITESPACE
                ? Machine.normalize(displayValue) : null;
        if (displayMapping == DisplayMapping.FIXED) {
            if (!source.hasFixedDisplayRange()) {
                throw new CatalogFormatException("Missing fixed search display range");
            }
            fixedDisplayStart = source.getFixedDisplayRange().getStartInclusive();
            fixedDisplayEnd = source.getFixedDisplayRange().getEndExclusive();
        } else {
            fixedDisplayStart = -1;
            fixedDisplayEnd = -1;
        }
    }

    String displayValue() {
        return displayValue;
    }

    private static SearchHit.Field searchField(
            final CatalogSearchField source) {
        switch (source) {
            case CATALOG_SEARCH_FIELD_NAME:
                return SearchHit.Field.NAME;
            case CATALOG_SEARCH_FIELD_INTRODUCTION:
                return SearchHit.Field.INTRODUCTION;
            case CATALOG_SEARCH_FIELD_PROCESSOR:
                return SearchHit.Field.PROCESSOR;
            default:
                throw new CatalogFormatException("Missing derived search field");
        }
    }

    private static DisplayMapping displayMapping(final CatalogSearchDisplayMapping source) {
        switch (source) {
            case CATALOG_SEARCH_DISPLAY_MAPPING_DIRECT:
                return DisplayMapping.DIRECT;
            case CATALOG_SEARCH_DISPLAY_MAPPING_COMPACT_WHITESPACE:
                return DisplayMapping.COMPACT_WHITESPACE;
            case CATALOG_SEARCH_DISPLAY_MAPPING_FIXED:
                return DisplayMapping.FIXED;
            default:
                throw new CatalogFormatException("Missing search display mapping");
        }
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

    boolean isExactTokenOnly() {
        return exactTokenOnly;
    }

    boolean supportsRevision(final String revision) {
        return revisions.contains(revision);
    }

    String partNumberEvidenceValue(final String requestedRevision) {
        final String revision = requestedRevision == null
                ? revisions.get(0) : requestedRevision;
        final String display = value + "*/" + revision;
        return qualifier == null ? display : display + " (" + qualifier + ")";
    }

    private static String partNumberDisplay(final String stem, final List<String> revisions) {
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
        switch (displayMapping) {
            case DIRECT:
                return new TextRange(normalizedStart, normalizedEnd);
            case FIXED:
                return new TextRange(fixedDisplayStart, fixedDisplayEnd);
            case COMPACT_WHITESPACE:
                return compactDisplayRange(normalizedStart, normalizedEnd);
            default:
                throw new IllegalStateException("Unknown display mapping " + displayMapping);
        }
    }

    private TextRange compactDisplayRange(final int compactStart, final int compactEnd) {
        int sourceOffset = 0;
        int compactOffset = 0;
        int displayStart = -1;
        int displayEnd = -1;
        while (sourceOffset < normalizedDisplaySource.length()) {
            final int codePoint = normalizedDisplaySource.codePointAt(sourceOffset);
            final int width = Character.charCount(codePoint);
            if (!Machine.isSearchSpace(codePoint)) {
                if (compactOffset == compactStart) {
                    displayStart = sourceOffset;
                }
                compactOffset += width;
                sourceOffset += width;
                if (compactOffset == compactEnd) {
                    displayEnd = sourceOffset;
                }
            } else {
                sourceOffset += width;
            }
        }
        if (displayStart < 0 || displayEnd < 0) {
            throw new IllegalStateException("Invalid compact-name match range");
        }
        return new TextRange(displayStart, displayEnd);
    }

    private static boolean isHumanField(final SearchHit.Field field) {
        return field == SearchHit.Field.NAME
                || field == SearchHit.Field.CODENAME
                || field == SearchHit.Field.PROCESSOR;
    }

    private static boolean isHumanTextBoundary(final String value, final int boundary) {
        final int current = value.codePointAt(boundary);
        final int previous = value.codePointBefore(boundary);
        final boolean previousWord = isHumanWordCodePoint(previous);
        final boolean currentWord = isHumanWordCodePoint(current);
        if (!previousWord || !currentWord) {
            return true;
        }
        if (Character.isLowerCase(previous) && Character.isUpperCase(current)) {
            return true;
        }
        if (Character.isUpperCase(previous) && Character.isUpperCase(current)) {
            final int nextOffset = boundary + Character.charCount(current);
            return hasAcronymPrefix(value, boundary)
                    && nextOffset < value.length()
                    && Character.isLowerCase(value.codePointAt(nextOffset));
        }
        return false;
    }

    private static boolean hasAcronymPrefix(final String value, final int boundary) {
        int offset = boundary;
        int uppercaseCount = 0;
        while (offset > 0) {
            final int codePoint = value.codePointBefore(offset);
            if (!Character.isUpperCase(codePoint)) {
                break;
            }
            uppercaseCount++;
            offset -= Character.charCount(codePoint);
            if (uppercaseCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHumanWordCodePoint(final int codePoint) {
        return Character.isLetterOrDigit(codePoint);
    }

}
