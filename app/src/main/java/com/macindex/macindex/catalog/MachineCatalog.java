package com.macindex.macindex.catalog;

import androidx.annotation.Nullable;

import com.macindex.macindex.catalog.proto.CatalogBrowseDefinition;
import com.macindex.macindex.catalog.proto.CatalogBrowseGroup;
import com.macindex.macindex.catalog.proto.CatalogLogoAsset;
import com.macindex.macindex.catalog.proto.CatalogLogoNightTreatment;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogPayload;
import com.macindex.macindex.catalog.proto.CatalogRetiredMachine;
import com.macindex.macindex.catalog.proto.CatalogSearchLexicon;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final SearchHit.Field[] SEARCH_FIELDS = SearchHit.Field.values();
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
    private final SearchLexicon searchLexicon;
    private final Map<String, RetiredMachine> retiredMachines;
    private final Map<String, LogoNightTreatment> logoTreatments;

    MachineCatalog(final CatalogPayload payload) {
        final List<Machine> loadedMachines = new ArrayList<>(payload.getMachinesCount());
        final Map<String, Machine> loadedByUid = new HashMap<>();
        for (CatalogMachine record : payload.getMachinesList()) {
            final Machine machine = new Machine(record);
            loadedByUid.put(machine.uid(), machine);
            loadedMachines.add(machine);
        }
        machines = Collections.unmodifiableList(loadedMachines);
        machinesByUid = Collections.unmodifiableMap(loadedByUid);
        browseDefinitions = loadBrowseDefinitions(
                payload.getBrowseDefinitionsList(), machinesByUid);
        searchLexicon = new SearchLexicon(payload.getSearchLexicon());
        retiredMachines = loadRetiredMachines(payload.getRetiredMachinesList());
        logoTreatments = loadLogoTreatments(payload.getLogoAssetsList());
    }

    private static Map<String, RetiredMachine> loadRetiredMachines(
            final List<CatalogRetiredMachine> source) {
        final Map<String, RetiredMachine> loaded = new HashMap<>();
        for (CatalogRetiredMachine raw : source) {
            final String uid = raw.getUid();
            final String replacement = raw.hasReplacementUid()
                    ? raw.getReplacementUid() : null;
            loaded.put(uid, new RetiredMachine(raw.getPreviousName(), replacement));
        }
        return Collections.unmodifiableMap(loaded);
    }

    private static Map<String, LogoNightTreatment> loadLogoTreatments(
            final List<CatalogLogoAsset> source) {
        final Map<String, LogoNightTreatment> loaded = new HashMap<>();
        for (CatalogLogoAsset raw : source) {
            final LogoNightTreatment treatment = convertLogoTreatment(raw.getNightTreatment());
            loaded.put(raw.getKey(), treatment);
        }
        return Collections.unmodifiableMap(loaded);
    }

    private static LogoNightTreatment convertLogoTreatment(
            final CatalogLogoNightTreatment raw) {
        switch (raw) {
            case CATALOG_LOGO_NIGHT_TREATMENT_DARKEN:
                return LogoNightTreatment.DARKEN;
            case CATALOG_LOGO_NIGHT_TREATMENT_WHITE_TINT:
                return LogoNightTreatment.WHITE_TINT;
            case CATALOG_LOGO_NIGHT_TREATMENT_MONOCHROME:
                return LogoNightTreatment.MONOCHROME;
            default:
                throw new CatalogFormatException("Missing logo night treatment");
        }
    }

    @Nullable
    public RetiredMachine retiredMachine(@Nullable final String uid) {
        return uid == null ? null : retiredMachines.get(uid.toUpperCase(Locale.ROOT));
    }

    public LogoNightTreatment logoNightTreatment(final String key) {
        final LogoNightTreatment treatment = logoTreatments.get(key);
        if (treatment == null) {
            throw new CatalogFormatException("Unknown logo " + key);
        }
        return treatment;
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
        return text == null || normalizeSearchText(text).isEmpty();
    }

    private static String normalizeSearchText(final String text) {
        final String normalized = Machine.normalize(text);
        final StringBuilder result = new StringBuilder(normalized.length());
        int offset = 0;
        while (offset < normalized.length()) {
            final int codePoint = normalized.codePointAt(offset);
            final int width = Character.charCount(codePoint);
            if (isQuerySeparator(normalized, offset, codePoint, width)) {
                result.append(' ');
            } else {
                result.appendCodePoint(codePoint);
            }
            offset += width;
        }
        return result.toString().trim();
    }

    private static boolean isNumericComma(final String value, final int offset,
                                          final int width) {
        return offset > 0 && offset + width < value.length()
                && Character.isDigit(value.codePointBefore(offset))
                && Character.isDigit(value.codePointAt(offset + width));
    }

    private static boolean isQuerySeparator(final String value, final int offset,
                                            final int codePoint, final int width) {
        if (codePoint == '/' || codePoint == '-'
                || codePoint == ',' && isNumericComma(value, offset, width)) {
            return false;
        }
        switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
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

    /** Temporary result scope selected from the facets of the current query. */
    public enum SearchScope {
        ALL(null),
        NAME(SearchHit.Field.NAME),
        CODENAME(SearchHit.Field.CODENAME),
        MODEL_NUMBER(SearchHit.Field.MODEL_NUMBER),
        MODEL_IDENTIFIER(SearchHit.Field.MODEL_IDENTIFIER),
        GESTALT_ID(SearchHit.Field.GESTALT_ID),
        PART_NUMBER(SearchHit.Field.PART_NUMBER),
        EMC_NUMBER(SearchHit.Field.EMC_NUMBER),
        PROCESSOR(SearchHit.Field.PROCESSOR),
        INTRODUCTION(SearchHit.Field.INTRODUCTION);

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
            for (SearchScope scope : values()) {
                if (scope.field == field) {
                    return scope;
                }
            }
            throw new IllegalStateException("Unknown search field " + field);
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
                recognizedPartNumber ? partNumber.stem : text, searchLexicon);
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
        final String normalized = normalizeSearchText(text);
        final String stem = searchLexicon.partNumberStem(normalized);
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

    private List<SearchHit> sortAndFreeze(final List<SearchHit> matches) {
        if (matches.size() > 1) {
            Collections.sort(matches, this::compareSearchHits);
        }
        return Collections.unmodifiableList(matches);
    }

    private static List<String> splitSearchTokens(final String value) {
        final List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            while (index < value.length()) {
                final int codePoint = value.codePointAt(index);
                if (!Machine.isSearchSpace(codePoint)) {
                    break;
                }
                index += Character.charCount(codePoint);
            }
            final int start = index;
            while (index < value.length()) {
                final int codePoint = value.codePointAt(index);
                if (Machine.isSearchSpace(codePoint)) {
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
            for (Machine machine : group.machines) {
                if (scope.includes(machine.manufacturerKey())) {
                    matches.add(machine);
                }
            }
            if (!matches.isEmpty()) {
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

    private MachineMatch matchMachine(final Machine machine, final QueryPlan query,
                                      @Nullable final SearchHit.Field selectedField,
                                      final Set<SearchHit.Field> allowedFields,
                                      @Nullable final PartNumberQuery partNumber) {
        final List<SearchValue> values = machine.searchValues();
        final EnumSet<SearchHit.Field> matchingFields =
                EnumSet.noneOf(SearchHit.Field.class);
        SearchHit bestAll = null;
        SearchHit selectedHit = null;
        for (List<QueryToken> alternative : query.tokenAlternatives) {
            final List<EnumMap<SearchHit.Field, CandidateMatch>> tokensByField =
                    matchTokensByField(values, alternative, allowedFields, partNumber);

            final List<CandidateMatch> allEvidence = tokenEvidence(tokensByField);
            if (allEvidence != null) {
                bestAll = preferredHit(bestAll,
                        buildHit(machine, allEvidence, partNumber));
            }
            for (SearchHit.Field field : SEARCH_FIELDS) {
                final List<CandidateMatch> fieldEvidence =
                        fieldTokenEvidence(tokensByField, field);
                if (fieldEvidence == null) {
                    continue;
                }
                matchingFields.add(field);
                if (field == selectedField) {
                    selectedHit = preferredHit(selectedHit,
                            buildHit(machine, fieldEvidence, partNumber));
                }
            }
        }
        return new MachineMatch(bestAll, selectedHit, matchingFields);
    }

    private static List<EnumMap<SearchHit.Field, CandidateMatch>> matchTokensByField(
            final List<SearchValue> values, final List<QueryToken> tokens,
            final Set<SearchHit.Field> allowedFields,
            @Nullable final PartNumberQuery partNumber) {
        final List<EnumMap<SearchHit.Field, CandidateMatch>> result =
                new ArrayList<>(tokens.size());
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            result.add(new EnumMap<>(SearchHit.Field.class));
        }
        for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
            final SearchValue value = values.get(valueIndex);
            if (!allowedFields.contains(value.field())
                    || partNumber != null && partNumber.revision != null
                    && value.field() == SearchHit.Field.PART_NUMBER
                    && !value.supportsRevision(partNumber.revision)) {
                continue;
            }
            for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
                putBest(result.get(tokenIndex),
                        matchCandidate(value, valueIndex, tokens.get(tokenIndex)));
            }
        }
        return result;
    }

    @Nullable
    private static List<CandidateMatch> tokenEvidence(
            final List<EnumMap<SearchHit.Field, CandidateMatch>> tokenMatches) {
        final List<CandidateMatch> evidence = new ArrayList<>(tokenMatches.size());
        for (EnumMap<SearchHit.Field, CandidateMatch> matches : tokenMatches) {
            final CandidateMatch candidate = bestCandidate(matches);
            if (candidate == null) {
                return null;
            }
            evidence.add(candidate);
        }
        return evidence;
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

    @Nullable
    private SearchHit preferredHit(@Nullable final SearchHit left,
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
        if (value.isExactTokenOnly() && !candidate.equals(query.normalized)) {
            return null;
        }

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

    private static SearchHit buildHit(final Machine machine,
                                      final List<CandidateMatch> tokenEvidence,
                                      @Nullable final PartNumberQuery partNumber) {
        if (tokenEvidence.size() == 1) {
            return buildHit(machine, tokenEvidence.get(0), partNumber);
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
        return new SearchHit(machine, evidence);
    }

    private static SearchHit buildHit(final Machine machine,
                                      final CandidateMatch evidence,
                                      @Nullable final PartNumberQuery partNumber) {
        return new SearchHit(machine,
                Collections.singletonList(toEvidence(evidence, partNumber)));
    }

    private static SearchHit.Evidence toEvidence(
            final CandidateMatch candidate, @Nullable final PartNumberQuery partNumber) {
        final String candidateValue = candidate.value.normalizedValue();
        final int candidateCodePointCount = candidateValue.codePointCount(
                0, candidateValue.length());
        if (partNumber != null
                && candidate.value.field() == SearchHit.Field.PART_NUMBER
                && candidateValue.equals(partNumber.stem)) {
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
                    candidate.unitCodePointCount, candidateCodePointCount,
                    candidate.matchPosition, candidate.query.sourceTokenCount);
        }
        final TextRange range = candidate.value.displayRange(
                candidate.normalizedStart,
                candidate.normalizedStart + candidate.query.normalized.length());
        return new SearchHit.Evidence(
                candidate.relation, candidate.value.field(), candidate.value.displayValue(),
                range.startInclusive(), range.endExclusive(), candidate.query.codePointCount,
                candidate.unitCodePointCount, candidateCodePointCount,
                candidate.matchPosition, candidate.query.sourceTokenCount);
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
        return Integer.compare(left.index, right.index);
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

    private int compareSearchHits(final SearchHit left, final SearchHit right) {
        int comparison = Integer.compare(left.worstRelation(), right.worstRelation());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.relationPenalty(), right.relationPenalty());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                right.longestPhraseTokenCount(), left.longestPhraseTokenCount());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                right.mostTokensInOneValue(), left.mostTokensInOneValue());
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
                explanationPreference(left.evidence().get(0).field()),
                explanationPreference(right.evidence().get(0).field()));
        if (comparison != 0) {
            return comparison;
        }
        final long leftCandidateCoverage = (long) left.totalQueryCodePointCount()
                * right.totalCandidateCodePointCount();
        final long rightCandidateCoverage = (long) right.totalQueryCodePointCount()
                * left.totalCandidateCodePointCount();
        comparison = Long.compare(rightCandidateCoverage, leftCandidateCoverage);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.totalNormalizedMatchPosition(), right.totalNormalizedMatchPosition());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                homepageCategoryRank(left.machine()), homepageCategoryRank(right.machine()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.machine().introductionSortKey(), right.machine().introductionSortKey());
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

    private int homepageCategoryRank(final Machine machine) {
        final BrowseDefinition definition = browseDefinitions.get(BrowseGrouping.NAMES);
        if (definition == null) {
            return Integer.MAX_VALUE;
        }
        for (int index = 0; index < definition.groups.size(); index++) {
            if (machine.productTypeKey().equals(definition.groups.get(index).key)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
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
        final int leftSignificantLength = left.codePointCount(leftStart, leftEnd);
        final int rightSignificantLength = right.codePointCount(rightStart, rightEnd);
        int comparison = Integer.compare(leftSignificantLength, rightSignificantLength);
        if (comparison != 0) {
            return comparison;
        }
        int leftIndex = leftStart;
        int rightIndex = rightStart;
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
        return 0;
    }

    private static Map<BrowseGrouping, BrowseDefinition> loadBrowseDefinitions(
            final List<CatalogBrowseDefinition> rawDefinitions,
            final Map<String, Machine> machinesByUid) {
        final EnumMap<BrowseGrouping, BrowseDefinition> converted =
                new EnumMap<>(BrowseGrouping.class);
        for (CatalogBrowseDefinition rawDefinition : rawDefinitions) {
            final BrowseGrouping grouping = BrowseGrouping.fromCatalogKey(rawDefinition.getKey());
            final List<BrowseGroupDefinition> groups = new ArrayList<>();
            for (CatalogBrowseGroup rawGroup : rawDefinition.getGroupsList()) {
                final List<Machine> groupMachines = new ArrayList<>();
                for (String uid : rawGroup.getMachineUidsList()) {
                    final Machine machine = machinesByUid.get(uid);
                    if (machine == null) {
                        throw new CatalogFormatException(
                                "Unknown browse machine UID " + uid);
                    }
                    groupMachines.add(machine);
                }
                groups.add(new BrowseGroupDefinition(
                        rawGroup.getKey(), rawGroup.getLabel(),
                        rawGroup.hasSectionKey() ? rawGroup.getSectionKey() : null,
                        Collections.unmodifiableList(groupMachines)));
            }
            converted.put(grouping,
                    new BrowseDefinition(Collections.unmodifiableList(groups)));
        }
        return Collections.unmodifiableMap(converted);
    }

    private static final class QueryPlan {
        private final List<List<QueryToken>> tokenAlternatives;

        private QueryPlan(final List<List<QueryToken>> tokenAlternatives) {
            this.tokenAlternatives = tokenAlternatives;
        }

        private static QueryPlan from(final String rawText,
                                      final SearchLexicon lexicon) {
            final String normalizedText = normalizeSearchText(rawText);
            if (normalizedText.isEmpty()) {
                throw new IllegalArgumentException("Search text is empty");
            }
            final List<String> rawTokens = splitSearchTokens(normalizedText);
            if (rawTokens.isEmpty()) {
                throw new IllegalArgumentException("Search text is empty");
            }
            final List<PhraseSpan> phrases = findSemanticPhrases(rawTokens, lexicon);
            final List<PhraseSpan> atomicPhrases = new ArrayList<>();
            for (PhraseSpan phrase : phrases) {
                if (phrase.atomic) {
                    atomicPhrases.add(phrase);
                }
            }
            final List<QueryToken> base = bestPhraseSegmentation(rawTokens, atomicPhrases);
            final StringBuilder normalizedWhole = new StringBuilder();
            int wholeSourceTokenCount = 0;
            for (QueryToken token : base) {
                if (normalizedWhole.length() > 0) {
                    normalizedWhole.append(' ');
                }
                normalizedWhole.append(token.normalized);
                wholeSourceTokenCount += token.sourceTokenCount;
            }
            final List<List<QueryToken>> alternatives = new ArrayList<>();
            final Set<String> seenAlternatives = new LinkedHashSet<>();
            addAlternative(alternatives, seenAlternatives, Collections.singletonList(
                    new QueryToken(normalizedWhole.toString(), wholeSourceTokenCount)));
            final List<PhraseSpan> compatiblePhrases = new ArrayList<>(atomicPhrases);
            addAlternative(alternatives, seenAlternatives, base);
            for (PhraseSpan phrase : phrases) {
                if (!phrase.atomic && doesNotOverlap(phrase, atomicPhrases)) {
                    compatiblePhrases.add(phrase);
                    final List<PhraseSpan> selected = new ArrayList<>(atomicPhrases);
                    selected.add(phrase);
                    addAlternative(alternatives, seenAlternatives,
                            segmentWithPhrases(rawTokens, selected));
                }
            }
            addAlternative(alternatives, seenAlternatives,
                    bestPhraseSegmentation(rawTokens, compatiblePhrases));
            addMachineAliasPrefixAlternatives(
                    rawTokens, lexicon, alternatives, seenAlternatives);
            return new QueryPlan(Collections.unmodifiableList(alternatives));
        }

        private static void addMachineAliasPrefixAlternatives(
                final List<String> rawTokens, final SearchLexicon lexicon,
                final List<List<QueryToken>> alternatives, final Set<String> seen) {
            if (rawTokens.size() != 1) {
                return;
            }
            final String query = rawTokens.get(0);
            for (String alias : lexicon.compactNameAliases()) {
                if (query.length() <= alias.length()
                        || !query.startsWith(alias)
                        || digitRunEnd(query, alias.length()) != query.length()) {
                    continue;
                }
                final List<QueryToken> split = new ArrayList<>(2);
                split.add(new QueryToken(alias, 1));
                split.add(new QueryToken(query.substring(alias.length()), 1));
                addAlternative(alternatives, seen, split);
            }
        }

        private static List<QueryToken> deduplicateTokens(
                final List<QueryToken> source) {
            final List<QueryToken> result = new ArrayList<>(source.size());
            final Set<String> seen = new LinkedHashSet<>();
            for (QueryToken token : source) {
                if (seen.add(token.normalized)) {
                    result.add(token);
                }
            }
            return Collections.unmodifiableList(result);
        }

        private static List<PhraseSpan> findSemanticPhrases(
                final List<String> rawTokens, final SearchLexicon lexicon) {
            final List<PhraseSpan> result = new ArrayList<>();
            for (int start = 0; start + 1 < rawTokens.size(); start++) {
                final StringBuilder phrase = new StringBuilder(rawTokens.get(start));
                final Set<String> terms = new LinkedHashSet<>();
                terms.add(rawTokens.get(start));
                for (int end = start + 1; end < rawTokens.size(); end++) {
                    final String term = rawTokens.get(end);
                    if (!terms.add(term)) {
                        break;
                    }
                    phrase.append(' ').append(term);
                    final QueryToken query = new QueryToken(
                            phrase.toString(), end - start + 1);
                    final int phraseKind = lexicon.phraseKind(query.normalized);
                    if (phraseKind != 0) {
                        result.add(new PhraseSpan(
                                start, end + 1, query, phraseKind == 2));
                    }
                }
            }
            return result;
        }

        private static boolean doesNotOverlap(
                final PhraseSpan candidate, final List<PhraseSpan> selected) {
            for (PhraseSpan phrase : selected) {
                if (candidate.start < phrase.end && phrase.start < candidate.end) {
                    return false;
                }
            }
            return true;
        }

        private static List<QueryToken> segmentWithPhrases(
                final List<String> rawTokens, final List<PhraseSpan> selected) {
            final List<QueryToken> result = new ArrayList<>();
            int index = 0;
            while (index < rawTokens.size()) {
                PhraseSpan starting = null;
                for (PhraseSpan phrase : selected) {
                    if (phrase.start == index
                            && (starting == null || phrase.end > starting.end)) {
                        starting = phrase;
                    }
                }
                if (starting == null) {
                    result.add(new QueryToken(rawTokens.get(index++), 1));
                } else {
                    result.add(starting.query);
                    index = starting.end;
                }
            }
            return deduplicateTokens(result);
        }

        private static List<QueryToken> bestPhraseSegmentation(
                final List<String> rawTokens, final List<PhraseSpan> phrases) {
            final List<List<PhraseSpan>> phrasesByStart = new ArrayList<>(rawTokens.size());
            for (int index = 0; index < rawTokens.size(); index++) {
                phrasesByStart.add(new ArrayList<>());
            }
            for (PhraseSpan phrase : phrases) {
                phrasesByStart.get(phrase.start).add(phrase);
            }

            final List<List<QueryToken>> bestFrom = new ArrayList<>(
                    Collections.nCopies(rawTokens.size() + 1, null));
            bestFrom.set(rawTokens.size(), Collections.emptyList());
            for (int start = rawTokens.size() - 1; start >= 0; start--) {
                List<QueryToken> best = prepend(
                        new QueryToken(rawTokens.get(start), 1), bestFrom.get(start + 1));
                for (PhraseSpan phrase : phrasesByStart.get(start)) {
                    final List<QueryToken> candidate = prepend(
                            phrase.query, bestFrom.get(phrase.end));
                    if (candidate.size() < best.size()
                            || candidate.size() == best.size()
                            && candidate.get(0).sourceTokenCount
                                    > best.get(0).sourceTokenCount) {
                        best = candidate;
                    }
                }
                bestFrom.set(start, best);
            }
            return deduplicateTokens(bestFrom.get(0));
        }

        private static List<QueryToken> prepend(
                final QueryToken first, final List<QueryToken> rest) {
            final List<QueryToken> result = new ArrayList<>(rest.size() + 1);
            result.add(first);
            result.addAll(rest);
            return result;
        }

        private static void addAlternative(
                final List<List<QueryToken>> destination,
                final Set<String> seen, final List<QueryToken> candidate) {
            final StringBuilder key = new StringBuilder();
            for (QueryToken token : candidate) {
                key.append(token.normalized).append('\u0001');
            }
            if (seen.add(key.toString())) {
                destination.add(candidate);
            }
        }
    }

    private static final class PhraseSpan {
        private final int start;
        private final int end;
        private final QueryToken query;
        private final boolean atomic;

        private PhraseSpan(final int start, final int end, final QueryToken query,
                           final boolean atomic) {
            this.start = start;
            this.end = end;
            this.query = query;
            this.atomic = atomic;
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
        private final int sourceTokenCount;

        private QueryToken(final String normalized, final int sourceTokenCount) {
            this.normalized = normalized;
            codePointCount = normalized.codePointCount(0, normalized.length());
            this.sourceTokenCount = sourceTokenCount;
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
        private final List<Machine> machines;

        private BrowseGroupDefinition(final String key, final String label,
                                      @Nullable final String sectionKey,
                                      final List<Machine> machines) {
            this.key = key;
            this.label = label;
            this.sectionKey = sectionKey;
            this.machines = machines;
        }
    }

    private static final class SearchLexicon {
        private final List<String> phraseCandidates;
        private final List<String> atomicPhrases;
        private final List<String> compactNameAliases;
        private final List<String> partNumberStems;

        private SearchLexicon(final CatalogSearchLexicon source) {
            phraseCandidates = source.getPhraseCandidatesList();
            atomicPhrases = source.getAtomicPhrasesList();
            compactNameAliases = source.getCompactNameAliasesList();
            partNumberStems = source.getPartNumberStemsList();
        }

        private int phraseKind(final String query) {
            if (contains(atomicPhrases, query)) {
                return 2;
            }
            return containsPrefix(phraseCandidates, query) ? 1 : 0;
        }

        private List<String> compactNameAliases() {
            return compactNameAliases;
        }

        @Nullable
        private String partNumberStem(final String query) {
            for (int length = Math.min(5, query.length()); length >= 4; length--) {
                final String candidate = query.substring(0, length);
                if (contains(partNumberStems, candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private static boolean contains(final List<String> sortedValues,
                                        final String value) {
            return Collections.binarySearch(sortedValues, value) >= 0;
        }

        private static boolean containsPrefix(final List<String> sortedValues,
                                              final String prefix) {
            final int found = Collections.binarySearch(sortedValues, prefix);
            if (found >= 0) {
                return true;
            }
            final int insertion = -found - 1;
            return insertion < sortedValues.size()
                    && sortedValues.get(insertion).startsWith(prefix);
        }
    }
}
