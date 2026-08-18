package com.macindex.macindex.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One machine search result together with the evidence which made it relevant. */
public final class SearchHit {

    /** Semantic relationship between one query token and its smallest matching unit. */
    public enum Relation {
        COMPLETE_UNIT,
        UNIT_PREFIX,
        UNIT_INTERNAL
    }

    /** Searchable catalog field. */
    public enum Field {
        NAME,
        CODENAME,
        MODEL_NUMBER,
        MODEL_IDENTIFIER,
        GESTALT_ID,
        PART_NUMBER,
        EMC_NUMBER
    }

    /** One query token and the exact UTF-16 display range which proves that it matched. */
    public static final class Evidence {
        private final Relation relation;
        private final Field field;
        private final String matchedValue;
        private final int matchStartInclusive;
        private final int matchEndExclusive;
        private final int queryCodePointCount;
        private final int unitCodePointCount;
        private final int normalizedMatchPosition;

        Evidence(final Relation relation, final Field field, final String matchedValue,
                 final int matchStartInclusive, final int matchEndExclusive,
                 final int queryCodePointCount, final int unitCodePointCount,
                 final int normalizedMatchPosition) {
            validateMatchRange(matchedValue, matchStartInclusive, matchEndExclusive);
            if (relation == null || field == null || queryCodePointCount <= 0
                    || unitCodePointCount < queryCodePointCount
                    || normalizedMatchPosition < 0
                    || (relation == Relation.COMPLETE_UNIT
                        && unitCodePointCount != queryCodePointCount)
                    || (relation != Relation.UNIT_INTERNAL && normalizedMatchPosition != 0)) {
                throw new IllegalArgumentException("Invalid search relevance "
                        + queryCodePointCount + "/" + unitCodePointCount
                        + " at " + normalizedMatchPosition);
            }
            this.relation = relation;
            this.field = field;
            this.matchedValue = matchedValue;
            this.matchStartInclusive = matchStartInclusive;
            this.matchEndExclusive = matchEndExclusive;
            this.queryCodePointCount = queryCodePointCount;
            this.unitCodePointCount = unitCodePointCount;
            this.normalizedMatchPosition = normalizedMatchPosition;
        }

        public Relation relation() {
            return relation;
        }

        public Field field() {
            return field;
        }

        public String matchedValue() {
            return matchedValue;
        }

        /** UTF-16 start offset in {@link #matchedValue()}, at a code-point boundary. */
        public int matchStartInclusive() {
            return matchStartInclusive;
        }

        /** Exclusive UTF-16 end offset, also at a code-point boundary. */
        public int matchEndExclusive() {
            return matchEndExclusive;
        }

        int queryCodePointCount() {
            return queryCodePointCount;
        }

        int unitCodePointCount() {
            return unitCodePointCount;
        }

        int normalizedMatchPosition() {
            return normalizedMatchPosition;
        }

    }

    private final Machine machine;
    private final boolean wholeQueryMatch;
    private final List<Evidence> evidence;
    private final int worstRelation;
    private final int relationPenalty;
    private final int totalQueryCodePointCount;
    private final int totalUnitCodePointCount;
    private final int totalNormalizedMatchPosition;

    SearchHit(final Machine machine, final boolean wholeQueryMatch,
              final List<Evidence> orderedEvidence) {
        if (machine == null || orderedEvidence == null || orderedEvidence.isEmpty()) {
            throw new IllegalArgumentException("A search hit requires machine evidence");
        }
        this.machine = machine;
        this.wholeQueryMatch = wholeQueryMatch;
        if (orderedEvidence.size() == 1) {
            evidence = Collections.singletonList(orderedEvidence.get(0));
        } else {
            evidence = Collections.unmodifiableList(new ArrayList<>(orderedEvidence));
        }

        int computedWorstRelation = 0;
        int computedRelationPenalty = 0;
        int computedQueryCount = 0;
        int computedUnitCount = 0;
        int computedPosition = 0;
        for (Evidence item : evidence) {
            computedWorstRelation = Math.max(computedWorstRelation, item.relation().ordinal());
            computedRelationPenalty += item.relation().ordinal();
            computedQueryCount += item.queryCodePointCount();
            computedUnitCount += item.unitCodePointCount();
            computedPosition += item.normalizedMatchPosition();
        }
        worstRelation = computedWorstRelation;
        relationPenalty = computedRelationPenalty;
        totalQueryCodePointCount = computedQueryCount;
        totalUnitCodePointCount = computedUnitCount;
        totalNormalizedMatchPosition = computedPosition;
    }

    private static void validateMatchRange(final String matchedValue,
                                           final int matchStartInclusive,
                                           final int matchEndExclusive) {
        if (matchedValue == null || matchStartInclusive < 0
                || matchStartInclusive >= matchEndExclusive
                || matchEndExclusive > matchedValue.length()
                || Character.isLowSurrogate(matchedValue.charAt(matchStartInclusive))
                || (matchEndExclusive < matchedValue.length()
                    && Character.isLowSurrogate(matchedValue.charAt(matchEndExclusive)))) {
            throw new IllegalArgumentException("Invalid match range "
                    + matchStartInclusive + ".." + matchEndExclusive
                    + " for value of length "
                    + (matchedValue == null ? -1 : matchedValue.length()));
        }
    }

    public Machine machine() {
        return machine;
    }

    /** Best single explanation, retained for simple one-token presentation code. */
    public Relation relation() {
        return evidence.get(0).relation();
    }

    /** Field of the best single explanation. */
    public Field field() {
        return evidence.get(0).field();
    }

    public String matchedValue() {
        return evidence.get(0).matchedValue();
    }

    public int matchStartInclusive() {
        return evidence.get(0).matchStartInclusive();
    }

    public int matchEndExclusive() {
        return evidence.get(0).matchEndExclusive();
    }

    /** All token evidence, with the best visible explanation first. */
    public List<Evidence> evidence() {
        return evidence;
    }

    boolean isWholeQueryMatch() {
        return wholeQueryMatch;
    }

    int worstRelation() {
        return worstRelation;
    }

    int relationPenalty() {
        return relationPenalty;
    }

    int totalQueryCodePointCount() {
        return totalQueryCodePointCount;
    }

    int totalUnitCodePointCount() {
        return totalUnitCodePointCount;
    }

    int totalNormalizedMatchPosition() {
        return totalNormalizedMatchPosition;
    }
}
