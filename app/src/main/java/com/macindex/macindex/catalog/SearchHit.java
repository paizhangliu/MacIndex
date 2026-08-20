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
        EMC_NUMBER,
        PROCESSOR,
        INTRODUCTION
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
        private final int candidateCodePointCount;
        private final int normalizedMatchPosition;
        private final int sourceTokenCount;

        Evidence(final Relation relation, final Field field, final String matchedValue,
                 final int matchStartInclusive, final int matchEndExclusive,
                 final int queryCodePointCount, final int unitCodePointCount,
                 final int candidateCodePointCount,
                 final int normalizedMatchPosition, final int sourceTokenCount) {
            this.relation = relation;
            this.field = field;
            this.matchedValue = matchedValue;
            this.matchStartInclusive = matchStartInclusive;
            this.matchEndExclusive = matchEndExclusive;
            this.queryCodePointCount = queryCodePointCount;
            this.unitCodePointCount = unitCodePointCount;
            this.candidateCodePointCount = candidateCodePointCount;
            this.normalizedMatchPosition = normalizedMatchPosition;
            this.sourceTokenCount = sourceTokenCount;
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

        int candidateCodePointCount() {
            return candidateCodePointCount;
        }

        int normalizedMatchPosition() {
            return normalizedMatchPosition;
        }

        int sourceTokenCount() {
            return sourceTokenCount;
        }

    }

    private final Machine machine;
    private final List<Evidence> evidence;
    private final int worstRelation;
    private final int relationPenalty;
    private final int totalQueryCodePointCount;
    private final int totalUnitCodePointCount;
    private final int totalCandidateCodePointCount;
    private final int totalNormalizedMatchPosition;
    private final int longestPhraseTokenCount;
    private final int mostTokensInOneValue;

    SearchHit(final Machine machine, final List<Evidence> orderedEvidence) {
        this.machine = machine;
        evidence = orderedEvidence.size() == 1
                ? Collections.singletonList(orderedEvidence.get(0))
                : Collections.unmodifiableList(new ArrayList<>(orderedEvidence));

        int computedWorstRelation = 0;
        int computedRelationPenalty = 0;
        int computedQueryCount = 0;
        int computedUnitCount = 0;
        int computedCandidateCount = 0;
        int computedPosition = 0;
        int computedLongestPhrase = 0;
        for (Evidence item : evidence) {
            computedWorstRelation = Math.max(computedWorstRelation, item.relation().ordinal());
            computedRelationPenalty += item.relation().ordinal();
            computedQueryCount += item.queryCodePointCount();
            computedUnitCount += item.unitCodePointCount();
            computedCandidateCount += item.candidateCodePointCount();
            computedPosition += item.normalizedMatchPosition();
            computedLongestPhrase = Math.max(
                    computedLongestPhrase, item.sourceTokenCount());
        }
        worstRelation = computedWorstRelation;
        relationPenalty = computedRelationPenalty;
        totalQueryCodePointCount = computedQueryCount;
        totalUnitCodePointCount = computedUnitCount;
        totalCandidateCodePointCount = computedCandidateCount;
        totalNormalizedMatchPosition = computedPosition;
        longestPhraseTokenCount = computedLongestPhrase;
        int computedMostTokensInOneValue = 0;
        for (Evidence candidate : evidence) {
            int tokensInValue = 0;
            for (Evidence item : evidence) {
                if (candidate.field() == item.field()
                        && candidate.matchedValue().equals(item.matchedValue())) {
                    tokensInValue += item.sourceTokenCount();
                }
            }
            computedMostTokensInOneValue = Math.max(
                    computedMostTokensInOneValue, tokensInValue);
        }
        mostTokensInOneValue = computedMostTokensInOneValue;
    }

    public Machine machine() {
        return machine;
    }

    /** All token evidence, with the best visible explanation first. */
    public List<Evidence> evidence() {
        return evidence;
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

    int totalCandidateCodePointCount() {
        return totalCandidateCodePointCount;
    }

    int totalNormalizedMatchPosition() {
        return totalNormalizedMatchPosition;
    }

    int longestPhraseTokenCount() {
        return longestPhraseTokenCount;
    }

    int mostTokensInOneValue() {
        return mostTokensInOneValue;
    }

}
