package com.macindex.macindex.catalog;

import androidx.annotation.Nullable;

import com.macindex.macindex.catalog.proto.CatalogBrowseDefinition;
import com.macindex.macindex.catalog.proto.CatalogBrowseGroup;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable, process-owned catalog and its UID/search indexes. */
public final class MachineCatalog {

    private static final Comparator<Machine> INTRODUCTION_ORDER =
            (left, right) -> Integer.compare(
                    left.introductionSortKey(), right.introductionSortKey());
    private static final SearchHit.Field[] SEARCH_FIELDS = SearchHit.Field.values();
    private static final Comparator<SearchHit> SEARCH_ORDER = MachineCatalog::compareSearchHits;
    private static final Set<SearchHit.Field> ALL_SEARCH_FIELDS = Collections.unmodifiableSet(
            EnumSet.allOf(SearchHit.Field.class));
    private static final Set<SearchHit.Field> IDENTIFIER_SEARCH_FIELDS =
            Collections.unmodifiableSet(EnumSet.of(
                    SearchHit.Field.MODEL_NUMBER,
                    SearchHit.Field.MODEL_IDENTIFIER,
                    SearchHit.Field.GESTALT_ID,
                    SearchHit.Field.PART_NUMBER,
                    SearchHit.Field.EMC_NUMBER));
    private static final Set<SearchHit.Field> PART_NUMBER_SEARCH_FIELDS =
            Collections.singleton(SearchHit.Field.PART_NUMBER);

    private final List<Machine> machines;
    private final Map<String, Machine> machinesByUid;
    private final Map<BrowseGrouping, BrowseDefinition> browseDefinitions;

    MachineCatalog(final CatalogPayload payload) {
        final List<Machine> loadedMachines = new ArrayList<>(payload.getMachinesCount());
        final Map<String, Machine> loadedByUid = new HashMap<>();
        for (CatalogMachine record : payload.getMachinesList()) {
            final Machine machine = new Machine(record);
            if (loadedByUid.put(machine.uid(), machine) != null) {
                throw new CatalogFormatException("Duplicate machine UID " + machine.uid());
            }
            loadedMachines.add(machine);
        }
        machines = Collections.unmodifiableList(loadedMachines);
        machinesByUid = Collections.unmodifiableMap(loadedByUid);
        browseDefinitions = loadBrowseDefinitions(payload.getBrowseDefinitionsList());
    }

    public List<Machine> machines() {
        return machines;
    }

    @Nullable
    public Machine findByUid(@Nullable final String uid) {
        return uid == null ? null : machinesByUid.get(uid.toUpperCase(Locale.ROOT));
    }

    public Machine requireByUid(final String uid) {
        final Machine machine = findByUid(uid);
        if (machine == null) {
            throw new IllegalArgumentException("Unknown machine UID " + uid);
        }
        return machine;
    }

    /** Uses the catalog's own normalization contract to classify an empty UI query. */
    public static boolean isBlankSearchText(@Nullable final String text) {
        return text == null || Machine.normalize(text).isEmpty();
    }

    /** Resolves one historical preference/share name through the existing exact search names. */
    @Nullable
    public Machine resolveLegacyName(@Nullable final String name) {
        if (name == null) {
            return null;
        }
        final String normalizedName = Machine.normalize(name);
        for (Machine machine : machines) {
            for (SearchValue value : machine.searchValues()) {
                if (value.field() != SearchHit.Field.NAME) {
                    break;
                }
                if (value.normalizedValue().equals(normalizedName)) {
                    return machine;
                }
            }
        }
        return null;
    }

    /** Temporary result scope selected from the facets of one submitted query. */
    public enum SearchScope {
        ALL(null),
        NAME(SearchHit.Field.NAME),
        CODENAME(SearchHit.Field.CODENAME),
        MODEL_NUMBER(SearchHit.Field.MODEL_NUMBER),
        MODEL_IDENTIFIER(SearchHit.Field.MODEL_IDENTIFIER),
        GESTALT_ID(SearchHit.Field.GESTALT_ID),
        PART_NUMBER(SearchHit.Field.PART_NUMBER),
        EMC_NUMBER(SearchHit.Field.EMC_NUMBER);

        @Nullable
        private final SearchHit.Field field;

        SearchScope(@Nullable final SearchHit.Field field) {
            this.field = field;
        }

        @Nullable
        public SearchHit.Field field() {
            return field;
        }

        public static SearchScope forField(final SearchHit.Field field) {
            if (field == null) {
                throw new NullPointerException("Search field is required");
            }
            switch (field) {
                case NAME:
                    return NAME;
                case CODENAME:
                    return CODENAME;
                case MODEL_NUMBER:
                    return MODEL_NUMBER;
                case MODEL_IDENTIFIER:
                    return MODEL_IDENTIFIER;
                case GESTALT_ID:
                    return GESTALT_ID;
                case PART_NUMBER:
                    return PART_NUMBER;
                case EMC_NUMBER:
                    return EMC_NUMBER;
                default:
                    throw new IllegalStateException("Unknown search field " + field);
            }
        }
    }

    /** One trustworthy field refinement and the exact result count after selecting it. */
    public static final class Facet {
        private final SearchHit.Field field;
        private final int count;

        private Facet(final SearchHit.Field field, final int count) {
            this.field = field;
            this.count = count;
        }

        public SearchHit.Field field() {
            return field;
        }

        public int count() {
            return count;
        }
    }

    /** Ranked results plus the field refinements derived from the same immutable query plan. */
    public static final class SearchResponse {
        private final SearchScope scope;
        private final List<SearchHit> hits;
        private final int allCount;
        private final List<Facet> facets;

        private SearchResponse(final SearchScope scope, final List<SearchHit> hits,
                               final int allCount, final List<Facet> facets) {
            this.scope = scope;
            this.hits = hits;
            this.allCount = allCount;
            this.facets = facets;
        }

        /** Effective scope after any recognized Part Number syntax has been applied. */
        public SearchScope scope() {
            return scope;
        }

        /** Results after the temporary field scope has been applied. */
        public List<SearchHit> hits() {
            return hits;
        }

        /** Number of results before a temporary field facet is applied. */
        public int allCount() {
            return allCount;
        }

        public List<Facet> facets() {
            return facets;
        }
    }

    /**
     * Searches one normalized phrase first, then falls back to whitespace-token AND matching.
     * A field scope filters the current query only; it never changes the catalog search index.
     */
    public SearchResponse search(final String text, final SearchScope requestedScope) {
        if (text == null || requestedScope == null) {
            throw new NullPointerException("Search text and scope are required");
        }
        final PartNumberQuery partNumber = parsePartNumberQuery(text);
        if (partNumber != null && !partNumber.valid) {
            return new SearchResponse(SearchScope.ALL, Collections.emptyList(), 0,
                    Collections.emptyList());
        }
        final boolean recognizedPartNumber = partNumber != null;
        final QueryPlan query = QueryPlan.from(
                recognizedPartNumber ? partNumber.stem : text);
        final Set<SearchHit.Field> allowedFields = !recognizedPartNumber
                ? ALL_SEARCH_FIELDS
                : partNumber.hasSuffix
                        ? PART_NUMBER_SEARCH_FIELDS : IDENTIFIER_SEARCH_FIELDS;
        final SearchHit.Field requestedField = requestedScope.field();
        final SearchScope effectiveScope = recognizedPartNumber
                && requestedField != null
                && !allowedFields.contains(requestedField)
                ? SearchScope.ALL : requestedScope;

        final SearchHit.Field selectedField = effectiveScope.field();
        final List<SearchHit> allMatches = new ArrayList<>();
        final List<SearchHit> fieldMatches = new ArrayList<>();
        final EnumMap<SearchHit.Field, Integer> fieldMatchCounts =
                new EnumMap<>(SearchHit.Field.class);

        for (Machine machine : machines) {
            final MachineMatch match = matchMachine(
                    machine, query, selectedField, allowedFields,
                    recognizedPartNumber ? partNumber : null);
            if (match.all != null) {
                allMatches.add(match.all);
            }
            if (match.selectedField != null) {
                fieldMatches.add(match.selectedField);
            }
            for (SearchHit.Field field : match.matchingFields) {
                final Integer count = fieldMatchCounts.get(field);
                fieldMatchCounts.put(field, count == null ? 1 : count + 1);
            }
        }

        final List<SearchHit> frozenAll = sortAndFreeze(allMatches);
        final List<SearchHit> frozenField = sortAndFreeze(fieldMatches);
        final List<Facet> facets = new ArrayList<>();
        for (SearchHit.Field field : SEARCH_FIELDS) {
            final Integer count = fieldMatchCounts.get(field);
            if (allowedFields.contains(field) && count != null && count > 0) {
                facets.add(new Facet(field, count));
            }
        }
        return new SearchResponse(effectiveScope,
                selectedField == null ? frozenAll : frozenField,
                frozenAll.size(),
                Collections.unmodifiableList(facets));
    }

    @Nullable
    private PartNumberQuery parsePartNumberQuery(@Nullable final String text) {
        if (text == null) {
            return null;
        }
        final String normalized = Machine.normalize(text);
        String stem = null;
        for (Machine machine : machines) {
            for (SearchValue value : machine.searchValues()) {
                if (value.field() == SearchHit.Field.PART_NUMBER
                        && normalized.startsWith(value.normalizedValue())
                        && (stem == null
                            || value.normalizedValue().length() > stem.length())) {
                    stem = value.normalizedValue();
                }
            }
        }
        if (stem == null) {
            return null;
        }
        final String suffix = normalized.substring(stem.length());
        if (suffix.isEmpty()) {
            return new PartNumberQuery(stem, false, false, true, null);
        }

        final int slash = suffix.indexOf('/');
        final String region = slash < 0 ? suffix : suffix.substring(0, slash);
        if (!isAsciiLetters(region, 1, 2)
                || slash >= 0 && suffix.indexOf('/', slash + 1) >= 0) {
            return new PartNumberQuery(stem, true, slash >= 0, false, null);
        }
        if (slash < 0 || slash == suffix.length() - 1) {
            return new PartNumberQuery(stem, true, slash >= 0, true, null);
        }
        final String revision = suffix.substring(slash + 1);
        return new PartNumberQuery(stem, true, true,
                isAsciiLetters(revision, 1, 1), revision.toUpperCase(Locale.ROOT));
    }

    private static boolean isAsciiLetters(final String value, final int minimum,
                                          final int maximum) {
        if (value.length() < minimum || value.length() > maximum) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character < 'a' || character > 'z') {
                return false;
            }
        }
        return true;
    }

    private static List<SearchHit> sortAndFreeze(final List<SearchHit> matches) {
        if (matches.size() > 1) {
            Collections.sort(matches, SEARCH_ORDER);
        }
        return Collections.unmodifiableList(matches);
    }

    private static List<String> splitSearchTokens(final String value) {
        final List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            while (index < value.length()) {
                final int codePoint = value.codePointAt(index);
                if (!isSearchSpace(codePoint)) {
                    break;
                }
                index += Character.charCount(codePoint);
            }
            final int start = index;
            while (index < value.length()) {
                final int codePoint = value.codePointAt(index);
                if (isSearchSpace(codePoint)) {
                    break;
                }
                index += Character.charCount(codePoint);
            }
            if (start < index) {
                tokens.add(value.substring(start, index));
            }
        }
        return tokens;
    }

    private static boolean isSearchSpace(final int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** Returns every machine in a browse scope exactly once, in authored catalog order. */
    public List<Machine> scopeMachines(final BrowseScope scope) {
        if (scope == null) {
            throw new NullPointerException("Browse scope is required");
        }
        final List<Machine> scoped = new ArrayList<>();
        for (Machine machine : machines) {
            if (scope.includes(machine.manufacturerKey())) {
                scoped.add(machine);
            }
        }
        return Collections.unmodifiableList(scoped);
    }

    /** Returns non-empty groups in catalog-authored order for the requested browse view. */
    public List<BrowseGroup> browseGroups(final BrowseScope scope,
                                          final BrowseGrouping grouping) {
        if (scope == null || grouping == null) {
            throw new NullPointerException("Browse scope and grouping are required");
        }
        final BrowseDefinition definition = browseDefinitions.get(grouping);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown browse grouping " + grouping);
        }
        final List<BrowseGroup> result = new ArrayList<>();
        String activeSection = null;
        final Set<String> emittedSections = new LinkedHashSet<>();
        for (BrowseGroupDefinition group : definition.groups) {
            if (group.sectionKey != null) {
                activeSection = group.sectionKey;
            }
            final List<Machine> matches = new ArrayList<>();
            for (Machine machine : machines) {
                if (scope.includes(machine.manufacturerKey())
                        && matchesBrowseGroup(machine, grouping, group.key)) {
                    matches.add(machine);
                }
            }
            if (!matches.isEmpty()) {
                Collections.sort(matches, INTRODUCTION_ORDER);
                result.add(new BrowseGroup(
                        group.key, group.label,
                        activeSection != null && emittedSections.add(activeSection)
                                ? activeSection : null,
                        matches));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Product sequence used by fixed previous/next navigation. */
    public List<Machine> sequenceForProductType(final String productTypeKey) {
        if (productTypeKey == null || productTypeKey.isEmpty()) {
            throw new IllegalArgumentException("Missing product type key");
        }
        for (BrowseGroup group : browseGroups(BrowseScope.ALL, BrowseGrouping.NAMES)) {
            if (group.key().equals(productTypeKey)) {
                return group.machines();
            }
        }
        throw new IllegalArgumentException("Unknown product type key " + productTypeKey);
    }

    private static MachineMatch matchMachine(final Machine machine, final QueryPlan query,
                                             @Nullable final SearchHit.Field selectedField,
                                             final Set<SearchHit.Field> allowedFields,
                                             @Nullable final PartNumberQuery partNumber) {
        final EnumMap<SearchHit.Field, CandidateMatch> wholeByField =
                new EnumMap<>(SearchHit.Field.class);
        final boolean singleTokenIsWholeQuery = query.tokens.size() == 1
                && query.whole.normalized.equals(query.tokens.get(0).normalized);
        final List<EnumMap<SearchHit.Field, CandidateMatch>> tokensByField =
                singleTokenIsWholeQuery ? Collections.emptyList()
                        : new ArrayList<>(query.tokens.size());
        for (int index = 0; index < query.tokens.size() && !singleTokenIsWholeQuery; index++) {
            tokensByField.add(new EnumMap<>(SearchHit.Field.class));
        }

        final List<SearchValue> values = machine.searchValues();
        for (int index = 0; index < values.size(); index++) {
            final SearchValue value = values.get(index);
            if (!allowedFields.contains(value.field())) {
                continue;
            }
            if (partNumber != null && partNumber.revision != null
                    && value.field() == SearchHit.Field.PART_NUMBER
                    && !value.supportsRevision(partNumber.revision)) {
                continue;
            }
            if (!query.hasRepeatedTokens || query.tokens.size() != 1) {
                putBest(wholeByField, matchCandidate(value, index, query.whole));
            }
            for (int tokenIndex = 0; tokenIndex < tokensByField.size(); tokenIndex++) {
                putBest(tokensByField.get(tokenIndex),
                        matchCandidate(value, index, query.tokens.get(tokenIndex)));
            }
        }

        final EnumSet<SearchHit.Field> matchingFields =
                EnumSet.noneOf(SearchHit.Field.class);
        SearchHit selectedHit = null;
        for (SearchHit.Field field : SEARCH_FIELDS) {
            final CandidateMatch whole = wholeByField.get(field);
            final List<CandidateMatch> tokens = fieldTokenEvidence(tokensByField, field);
            if (whole == null && tokens == null) {
                continue;
            }
            matchingFields.add(field);
            final boolean useWhole = whole != null
                    && (tokens == null || compareFieldWholeToTokens(whole, tokens) <= 0);
            if (field == selectedField) {
                selectedHit = useWhole
                        ? buildHit(machine, true, whole, partNumber)
                        : buildHit(machine, false, tokens, partNumber);
            }
        }

        final CandidateMatch bestWhole = bestCandidate(wholeByField);
        final SearchHit wholeHit = bestWhole == null
                ? null : buildHit(machine, true, bestWhole, partNumber);
        SearchHit tokenHit = null;
        if (!tokensByField.isEmpty()) {
            final List<CandidateMatch> tokenEvidence = new ArrayList<>(tokensByField.size());
            for (EnumMap<SearchHit.Field, CandidateMatch> matches : tokensByField) {
                final CandidateMatch bestToken = bestCandidate(matches);
                if (bestToken == null) {
                    tokenEvidence.clear();
                    break;
                }
                tokenEvidence.add(bestToken);
            }
            if (!tokenEvidence.isEmpty()) {
                tokenHit = buildHit(machine, false, tokenEvidence, partNumber);
            }
        }
        return new MachineMatch(preferredHit(wholeHit, tokenHit), selectedHit,
                matchingFields);
    }

    @Nullable
    private static List<CandidateMatch> fieldTokenEvidence(
            final List<EnumMap<SearchHit.Field, CandidateMatch>> tokenMatches,
            final SearchHit.Field field) {
        if (tokenMatches.isEmpty()) {
            return null;
        }
        final List<CandidateMatch> evidence = new ArrayList<>(tokenMatches.size());
        for (EnumMap<SearchHit.Field, CandidateMatch> matches : tokenMatches) {
            final CandidateMatch candidate = matches.get(field);
            if (candidate == null) {
                return null;
            }
            evidence.add(candidate);
        }
        return evidence;
    }

    private static int compareFieldWholeToTokens(
            final CandidateMatch whole, final List<CandidateMatch> tokens) {
        int tokenWorstRelation = 0;
        int tokenRelationPenalty = 0;
        for (CandidateMatch token : tokens) {
            tokenWorstRelation = Math.max(tokenWorstRelation, token.relation.ordinal());
            tokenRelationPenalty += token.relation.ordinal();
        }
        int comparison = Integer.compare(whole.relation.ordinal(), tokenWorstRelation);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(whole.relation.ordinal(), tokenRelationPenalty);
        return comparison != 0 ? comparison : comparePreferredBoolean(true, false);
    }

    @Nullable
    private static SearchHit preferredHit(@Nullable final SearchHit left,
                                          @Nullable final SearchHit right) {
        if (left == null) {
            return right;
        }
        return right == null || compareSearchHits(left, right) <= 0 ? left : right;
    }

    @Nullable
    private static CandidateMatch matchCandidate(final SearchValue value, final int index,
                                                 final QueryToken query) {
        final String candidate = value.normalizedValue();

        int occurrence = candidate.indexOf(query.normalized);
        CandidateMatch best = null;
        while (occurrence >= 0) {
            final int occurrenceEnd = occurrence + query.normalized.length();
            final SearchValue.Geometry geometry = value.geometry(occurrence, occurrenceEnd);
            final CandidateMatch match = new CandidateMatch(
                    value, index, occurrence, geometry, query,
                    candidate.equals(query.normalized));
            if (best == null || compareOccurrenceMatches(match, best) < 0) {
                best = match;
            }
            final int nextStart = occurrence
                    + Character.charCount(candidate.codePointAt(occurrence));
            occurrence = candidate.indexOf(query.normalized, nextStart);
        }
        return best;
    }

    private static void putBest(
            final EnumMap<SearchHit.Field, CandidateMatch> matches,
            @Nullable final CandidateMatch candidate) {
        if (candidate == null) {
            return;
        }
        final CandidateMatch previous = matches.get(candidate.value.field());
        if (previous == null || compareCandidateMatches(candidate, previous) < 0) {
            matches.put(candidate.value.field(), candidate);
        }
    }

    @Nullable
    private static CandidateMatch bestCandidate(
            final EnumMap<SearchHit.Field, CandidateMatch> matches) {
        CandidateMatch best = null;
        for (CandidateMatch candidate : matches.values()) {
            if (best == null || compareCandidateMatches(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static SearchHit buildHit(final Machine machine, final boolean wholeQueryMatch,
                                      final List<CandidateMatch> tokenEvidence,
                                      @Nullable final PartNumberQuery partNumber) {
        if (tokenEvidence.size() == 1) {
            return buildHit(machine, wholeQueryMatch, tokenEvidence.get(0), partNumber);
        }
        int primaryIndex = 0;
        for (int index = 1; index < tokenEvidence.size(); index++) {
            if (compareCandidateMatches(
                    tokenEvidence.get(index), tokenEvidence.get(primaryIndex)) < 0) {
                primaryIndex = index;
            }
        }
        final List<SearchHit.Evidence> evidence = new ArrayList<>(tokenEvidence.size());
        evidence.add(toEvidence(tokenEvidence.get(primaryIndex), partNumber));
        for (int index = 0; index < tokenEvidence.size(); index++) {
            if (index != primaryIndex) {
                evidence.add(toEvidence(tokenEvidence.get(index), partNumber));
            }
        }
        return new SearchHit(machine, wholeQueryMatch, evidence);
    }

    private static SearchHit buildHit(final Machine machine, final boolean wholeQueryMatch,
                                      final CandidateMatch evidence,
                                      @Nullable final PartNumberQuery partNumber) {
        return new SearchHit(machine, wholeQueryMatch,
                Collections.singletonList(toEvidence(evidence, partNumber)));
    }

    private static SearchHit.Evidence toEvidence(
            final CandidateMatch candidate, @Nullable final PartNumberQuery partNumber) {
        if (partNumber != null
                && candidate.value.field() == SearchHit.Field.PART_NUMBER
                && candidate.value.normalizedValue().equals(partNumber.stem)) {
            final String matchedValue = candidate.value.partNumberEvidenceValue(
                    partNumber.revision);
            int matchEnd = partNumber.stem.length();
            if (partNumber.hasSuffix) {
                matchEnd++;
            }
            if (partNumber.hasSlash) {
                matchEnd++;
            }
            if (partNumber.revision != null) {
                matchEnd++;
            }
            return new SearchHit.Evidence(
                    candidate.relation, candidate.value.field(), matchedValue,
                    0, matchEnd, candidate.query.codePointCount,
                    candidate.unitCodePointCount, candidate.matchPosition);
        }
        final TextRange range = candidate.value.displayRange(
                candidate.normalizedStart,
                candidate.normalizedStart + candidate.query.normalized.length());
        return new SearchHit.Evidence(
                candidate.relation, candidate.value.field(), candidate.value.displayValue(),
                range.startInclusive(), range.endExclusive(), candidate.query.codePointCount,
                candidate.unitCodePointCount, candidate.matchPosition);
    }

    private static int compareCandidateMatches(final CandidateMatch left,
                                               final CandidateMatch right) {
        int comparison = compareMatchQuality(
                left.relation, left.query.codePointCount, left.unitCodePointCount,
                right.relation, right.query.codePointCount, right.unitCodePointCount);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                explanationPreference(left.value.field()),
                explanationPreference(right.value.field()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = comparePreferredBoolean(
                left.value.isCanonicalName(), right.value.isCanonicalName());
        if (comparison != 0) {
            return comparison;
        }
        comparison = comparePreferredBoolean(left.wholeValueExact, right.wholeValueExact);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.matchPosition, right.matchPosition);
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareNaturalText(
                left.value.normalizedValue(), right.value.normalizedValue());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.index, right.index);
        return comparison != 0 ? comparison
                : left.value.field().compareTo(right.value.field());
    }

    private static int compareOccurrenceMatches(final CandidateMatch left,
                                                final CandidateMatch right) {
        int comparison = compareMatchQuality(
                left.relation, left.query.codePointCount, left.unitCodePointCount,
                right.relation, right.query.codePointCount, right.unitCodePointCount);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.matchPosition, right.matchPosition);
        return comparison != 0 ? comparison
                : Integer.compare(left.normalizedStart, right.normalizedStart);
    }

    static int compareSearchHits(final SearchHit left, final SearchHit right) {
        int comparison = Integer.compare(left.worstRelation(), right.worstRelation());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.relationPenalty(), right.relationPenalty());
        if (comparison != 0) {
            return comparison;
        }
        comparison = comparePreferredBoolean(
                left.isWholeQueryMatch(), right.isWholeQueryMatch());
        if (comparison != 0) {
            return comparison;
        }
        final long leftCoverage = (long) left.totalQueryCodePointCount()
                * right.totalUnitCodePointCount();
        final long rightCoverage = (long) right.totalQueryCodePointCount()
                * left.totalUnitCodePointCount();
        comparison = Long.compare(rightCoverage, leftCoverage);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                explanationPreference(left.field()), explanationPreference(right.field()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.totalNormalizedMatchPosition(), right.totalNormalizedMatchPosition());
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareNaturalText(
                Machine.normalize(left.machine().name()),
                Machine.normalize(right.machine().name()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = left.machine().name().compareTo(right.machine().name());
        if (comparison != 0) {
            return comparison;
        }
        return left.machine().uid().compareTo(right.machine().uid());
    }

    private static int comparePreferredBoolean(final boolean left, final boolean right) {
        return left == right ? 0 : left ? -1 : 1;
    }

    private static int compareMatchQuality(
            final SearchHit.Relation leftRelation, final int leftQueryCodePointCount,
            final int leftUnitCodePointCount, final SearchHit.Relation rightRelation,
            final int rightQueryCodePointCount, final int rightUnitCodePointCount) {
        int comparison = leftRelation.compareTo(rightRelation);
        if (comparison != 0) {
            return comparison;
        }
        final long leftCoverage = (long) leftQueryCodePointCount * rightUnitCodePointCount;
        final long rightCoverage = (long) rightQueryCodePointCount * leftUnitCodePointCount;
        return Long.compare(rightCoverage, leftCoverage);
    }

    private static int explanationPreference(final SearchHit.Field field) {
        switch (field) {
            case NAME:
                return 0;
            case CODENAME:
                return 1;
            default:
                return 2;
        }
    }

    /** Locale-independent natural order for the normalized Latin product names in the catalog. */
    private static int compareNaturalText(final String left, final String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            final int leftCodePoint = left.codePointAt(leftIndex);
            final int rightCodePoint = right.codePointAt(rightIndex);
            if (Character.isDigit(leftCodePoint) && Character.isDigit(rightCodePoint)) {
                final int leftEnd = digitRunEnd(left, leftIndex);
                final int rightEnd = digitRunEnd(right, rightIndex);
                final int comparison = compareDigitRuns(
                        left, leftIndex, leftEnd, right, rightIndex, rightEnd);
                if (comparison != 0) {
                    return comparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    private static int digitRunEnd(final String value, final int start) {
        int end = start;
        while (end < value.length()) {
            final int codePoint = value.codePointAt(end);
            if (!Character.isDigit(codePoint)) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        return end;
    }

    private static int compareDigitRuns(
            final String left, final int leftStart, final int leftEnd,
            final String right, final int rightStart, final int rightEnd) {
        final int leftSignificant = skipLeadingZeroes(left, leftStart, leftEnd);
        final int rightSignificant = skipLeadingZeroes(right, rightStart, rightEnd);
        final int leftSignificantLength = left.codePointCount(leftSignificant, leftEnd);
        final int rightSignificantLength = right.codePointCount(rightSignificant, rightEnd);
        int comparison = Integer.compare(leftSignificantLength, rightSignificantLength);
        if (comparison != 0) {
            return comparison;
        }
        int leftIndex = leftSignificant;
        int rightIndex = rightSignificant;
        while (leftIndex < leftEnd && rightIndex < rightEnd) {
            final int leftDigit = Character.digit(left.codePointAt(leftIndex), 10);
            final int rightDigit = Character.digit(right.codePointAt(rightIndex), 10);
            comparison = Integer.compare(leftDigit, rightDigit);
            if (comparison != 0) {
                return comparison;
            }
            leftIndex += Character.charCount(left.codePointAt(leftIndex));
            rightIndex += Character.charCount(right.codePointAt(rightIndex));
        }
        return Integer.compare(
                left.codePointCount(leftStart, leftEnd),
                right.codePointCount(rightStart, rightEnd));
    }

    private static int skipLeadingZeroes(final String value, final int start, final int end) {
        int index = start;
        while (index < end) {
            final int codePoint = value.codePointAt(index);
            if (Character.digit(codePoint, 10) != 0) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static Map<BrowseGrouping, BrowseDefinition> loadBrowseDefinitions(
            final List<CatalogBrowseDefinition> rawDefinitions) {
        final EnumMap<BrowseGrouping, BrowseDefinition> converted =
                new EnumMap<>(BrowseGrouping.class);
        for (CatalogBrowseDefinition rawDefinition : rawDefinitions) {
            final BrowseGrouping grouping = BrowseGrouping.fromCatalogKey(rawDefinition.getKey());
            final List<BrowseGroupDefinition> groups = new ArrayList<>();
            for (CatalogBrowseGroup rawGroup : rawDefinition.getGroupsList()) {
                groups.add(new BrowseGroupDefinition(
                        rawGroup.getKey(), rawGroup.getLabel(),
                        rawGroup.hasSectionKey() ? rawGroup.getSectionKey() : null));
            }
            if (converted.put(grouping,
                    new BrowseDefinition(Collections.unmodifiableList(groups))) != null) {
                throw new CatalogFormatException(
                        "Duplicate browse definition " + rawDefinition.getKey());
            }
        }
        return Collections.unmodifiableMap(converted);
    }

    private static boolean matchesBrowseGroup(final Machine machine,
                                              final BrowseGrouping grouping,
                                              final String key) {
        switch (grouping) {
            case NAMES:
                return machine.productTypeKey().equals(key);
            case PROCESSORS:
                return machine.processorFamilyKeys().contains(key);
            case YEARS:
                for (IntroductionDate introduction : machine.introductions()) {
                    if (Integer.toString(introduction.year()).equals(key)) {
                        return true;
                    }
                }
                return false;
            default:
                throw new IllegalStateException("Unknown browse grouping " + grouping);
        }
    }

    private static final class QueryPlan {
        private final QueryToken whole;
        private final List<QueryToken> tokens;
        private final boolean hasRepeatedTokens;

        private QueryPlan(final QueryToken whole, final List<QueryToken> tokens,
                          final boolean hasRepeatedTokens) {
            this.whole = whole;
            this.tokens = tokens;
            this.hasRepeatedTokens = hasRepeatedTokens;
        }

        private static QueryPlan from(final String rawText) {
            final String normalizedText = Machine.normalize(rawText);
            if (normalizedText.isEmpty()) {
                throw new IllegalArgumentException("Search text is empty");
            }
            final List<QueryToken> tokens = new ArrayList<>();
            final Set<String> seen = new LinkedHashSet<>();
            final StringBuilder normalizedWhole = new StringBuilder();
            boolean hasRepeatedTokens = false;
            for (String normalizedToken : splitSearchTokens(normalizedText)) {
                if (normalizedWhole.length() > 0) {
                    normalizedWhole.append(' ');
                }
                normalizedWhole.append(normalizedToken);
                if (seen.add(normalizedToken)) {
                    tokens.add(new QueryToken(normalizedToken));
                } else {
                    hasRepeatedTokens = true;
                }
            }
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("Search text is empty");
            }
            final QueryToken whole = new QueryToken(normalizedWhole.toString());
            return new QueryPlan(whole, Collections.unmodifiableList(tokens),
                    hasRepeatedTokens);
        }
    }

    private static final class PartNumberQuery {
        private final String stem;
        private final boolean hasSuffix;
        private final boolean hasSlash;
        private final boolean valid;
        @Nullable
        private final String revision;

        private PartNumberQuery(final String stem, final boolean hasSuffix,
                                final boolean hasSlash, final boolean valid,
                                @Nullable final String revision) {
            this.stem = stem;
            this.hasSuffix = hasSuffix;
            this.hasSlash = hasSlash;
            this.valid = valid;
            this.revision = revision;
        }
    }

    private static final class QueryToken {
        private final String normalized;
        private final int codePointCount;

        private QueryToken(final String normalized) {
            this.normalized = normalized;
            codePointCount = normalized.codePointCount(0, normalized.length());
        }
    }

    private static final class CandidateMatch {
        private final SearchValue value;
        private final int index;
        private final int normalizedStart;
        private final SearchHit.Relation relation;
        private final int unitCodePointCount;
        private final int matchPosition;
        private final QueryToken query;
        private final boolean wholeValueExact;

        private CandidateMatch(final SearchValue value, final int index,
                               final int normalizedStart,
                               final SearchValue.Geometry geometry,
                               final QueryToken query, final boolean wholeValueExact) {
            this.value = value;
            this.index = index;
            this.normalizedStart = normalizedStart;
            relation = geometry.relation;
            unitCodePointCount = geometry.unitCodePointCount;
            matchPosition = geometry.matchPosition;
            this.query = query;
            this.wholeValueExact = wholeValueExact;
        }
    }

    private static final class MachineMatch {
        @Nullable
        private final SearchHit all;
        @Nullable
        private final SearchHit selectedField;
        private final EnumSet<SearchHit.Field> matchingFields;

        private MachineMatch(@Nullable final SearchHit all,
                             @Nullable final SearchHit selectedField,
                             final EnumSet<SearchHit.Field> matchingFields) {
            this.all = all;
            this.selectedField = selectedField;
            this.matchingFields = matchingFields;
        }
    }

    private static final class BrowseDefinition {
        private final List<BrowseGroupDefinition> groups;

        private BrowseDefinition(final List<BrowseGroupDefinition> groups) {
            this.groups = groups;
        }
    }

    private static final class BrowseGroupDefinition {
        private final String key;
        private final String label;
        private final String sectionKey;

        private BrowseGroupDefinition(final String key, final String label,
                                      @Nullable final String sectionKey) {
            this.key = key;
            this.label = label;
            this.sectionKey = sectionKey;
        }
    }
}
