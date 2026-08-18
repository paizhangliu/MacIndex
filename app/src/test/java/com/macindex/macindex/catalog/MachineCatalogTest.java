package com.macindex.macindex.catalog;

import static com.macindex.macindex.catalog.CatalogTestSupport.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.macindex.macindex.catalog.proto.CatalogPayload;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogIntroduction;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MachineCatalogTest {

    @BeforeClass
    public static void loadReleaseCatalog() throws Exception {
        CatalogTestSupport.loadCatalog();
    }

    @Test
    public void unifiedSearchUsesContainsSemanticsAcrossNamesCodenamesAndIdentifiers() {
        final Machine kanga = catalog.requireByUid("MI000215");
        assertEquals(Collections.singletonList(kanga),
                searchMachines("Kanga"));
        assertEquals(Collections.singletonList(kanga),
                searchMachines("kang"));
        assertEquals(Collections.singletonList(kanga),
                searchMachines("ＫＡＮＧＡ"));

        assertEquals(Collections.singletonList(catalog.requireByUid("MI000421")),
                searchMachines("J604"));
        final Machine configuredCodename = catalog.requireByUid("MI000417");
        assertEquals(Collections.singletonList(configuredCodename),
                searchMachines("J416s"));
        assertTrue(search("J416s (M2 Pro)").isEmpty());
        assertTrue(search("M2 Pro configuration").isEmpty());

        assertEquals(List.of(
                        catalog.requireByUid("MI000089"),
                        catalog.requireByUid("MI000090")),
                searchMachines("DBLite"));
        final List<Machine> powerStarResults = searchMachines("PowerStar");
        assertTrue(powerStarResults.contains(catalog.requireByUid("MI000113")));
        assertTrue(powerStarResults.contains(catalog.requireByUid("MI000134")));
        assertTrue(powerStarResults.contains(catalog.requireByUid("MI000148")));
        assertTrue(powerStarResults.contains(catalog.requireByUid("MI000149")));
        assertTrue(powerStarResults.contains(catalog.requireByUid("MI000215")));

        final Machine macBookPro = catalog.requireByUid("MI000413");
        assertEquals(Collections.singletonList(macBookPro),
                searchMachines("BookPro18,3"));
        assertTrue(macBookPro.identifiers().contains("MacBookPro18,3 (M1 Pro)"));

        assertThrows(NullPointerException.class, () -> search(null));
        assertThrows(IllegalArgumentException.class, () -> search("  "));
        assertTrue(MachineCatalog.isBlankSearchText(null));
        assertTrue(MachineCatalog.isBlankSearchText("\u3000"));
        assertTrue(MachineCatalog.isBlankSearchText("\u00A0"));
        assertFalse(MachineCatalog.isBlankSearchText("\u3000Kanga\u3000"));
    }

    @Test
    public void searchHitsExplainSemanticRelationFieldAndAuthoredValue() {
        assertHit("PowerBook G3", "MI000215", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.NAME, "PowerBook G3");
        assertHit("Kanga", "MI000215", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.CODENAME, "Kanga");
        assertHit("J185", "MI000301", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.CODENAME, "J185 (iMac20,1)");
        assertHit("kang", "MI000215", SearchHit.Relation.UNIT_PREFIX,
                SearchHit.Field.CODENAME, "Kanga");
        assertHit("anga", "MI000215", SearchHit.Relation.UNIT_INTERNAL,
                SearchHit.Field.CODENAME, "Kanga");
        assertHit("M3553", "MI000215", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.MODEL_NUMBER, "M3553");
        assertHit("iMac20,1", "MI000301", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.MODEL_IDENTIFIER, "iMac20,1");
        assertHit("313", "MI000215", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.GESTALT_ID, "313");
        assertHit("M5994LL/A", "MI000215", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.PART_NUMBER, "M5994*/A");
        assertHit("3442", "MI000301", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.EMC_NUMBER, "3442");

        final SearchHit fullWidth = findHit("ＫＡＮＧＡ", catalog.requireByUid("MI000215"));
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, fullWidth.relation());
        assertEquals(SearchHit.Field.CODENAME, fullWidth.field());
        assertEquals("Kanga", fullWidth.matchedValue());
    }

    @Test
    public void searchHitsExposeTheActualRangeInsideTheDisplayedValue() {
        assertMatchRange("J185", "MI000301", 0, 4, "J185");
        assertMatchRange("185", "MI000301", 1, 4, "185");
        assertMatchRange("ｊ１８５", "MI000301", 0, 4, "J185");
        assertMatchRange("kang", "MI000215", 0, 4, "Kang");
        assertMatchRange("ＡＮＧ", "MI000215", 1, 4, "ang");

        final CatalogMachine repeated = searchFixture("MI900015", 2000)
                .addNames(identity("Banana"))
                .build();
        final SearchHit hit = search(catalogOf(repeated), "ana").get(0);
        assertEquals(1, hit.matchStartInclusive());
        assertEquals(4, hit.matchEndExclusive());
        assertEquals("ana", matchedSubstring(hit));

    }

    @Test
    public void oneMachineKeepsOnlyItsStrongestMostUsefulMatch() {
        final CatalogMachine fixture = searchFixture("MI900001", 2000)
                .addNames(identity("Fixture"))
                .addNames(identity("Alpha Extended"))
                .addNames(identity("Alpha"))
                .addNames(identity("M0001W"))
                .addNames(identity("M0001E"))
                .addNames(identity("Beta Extended"))
                .addCodenames(identity("Alpha"))
                .addCodenames(identity("Beta"))
                .addModelNumbers(identity("Alpha"))
                .build();
        final MachineCatalog fixtureCatalog = catalogOf(fixture);

        SearchHit hit = search(fixtureCatalog, "alpha").get(0);
        assertEquals(1, search(fixtureCatalog, "alpha").size());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, hit.relation());
        assertEquals(SearchHit.Field.NAME, hit.field());
        assertEquals("Alpha", hit.matchedValue());

        hit = search(fixtureCatalog, "alph").get(0);
        assertEquals(SearchHit.Relation.UNIT_PREFIX, hit.relation());
        assertEquals("Alpha", hit.matchedValue());

        hit = search(fixtureCatalog, "lph").get(0);
        assertEquals(SearchHit.Relation.UNIT_INTERNAL, hit.relation());
        assertEquals("Alpha", hit.matchedValue());

        hit = search(fixtureCatalog, "beta").get(0);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, hit.relation());
        assertEquals(SearchHit.Field.NAME, hit.field());
        assertEquals("Beta Extended", hit.matchedValue());

        hit = search(fixtureCatalog, "M0001").get(0);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, hit.relation());
        assertEquals("M0001E", hit.matchedValue());
    }

    @Test
    public void trueTiesUseNaturalVisibleNamesNotDatesOrAuthoredOrder() {
        final CatalogMachine alpha10 = tiedCodenameFixture(
                "MI900001", "Alpha 10", 1980);
        final CatalogMachine zulu = tiedCodenameFixture(
                "MI900002", "Zulu", 1970);
        final CatalogMachine alpha2 = tiedCodenameFixture(
                "MI900003", "Alpha 2", 2025);
        final List<String> expected = List.of("MI900003", "MI900001", "MI900002");

        assertEquals(expected, searchUids(catalogOf(alpha10, zulu, alpha2), "needle"));
        assertEquals(expected, searchUids(catalogOf(alpha2, alpha10, zulu), "needle"));
        assertEquals(expected, searchUids(catalogOf(zulu, alpha2, alpha10), "needle"));
    }

    @Test
    public void equallyStrongVisibleNamesExplainResultsBeforeHiddenFields() {
        final CatalogMachine name = searchFixture("MI900011", 2000)
                .addNames(identity("Zulu Name"))
                .addNames(identity("Needle"))
                .build();
        final CatalogMachine codename = tiedCodenameFixture(
                "MI900012", "Alpha Codename", 2000);
        final CatalogMachine identifier = searchFixture("MI900013", 2000)
                .addNames(identity("Aardvark Identifier"))
                .addModelNumbers(identity("Needle"))
                .build();
        assertEquals(List.of("MI900011", "MI900012", "MI900013"),
                searchUids(catalogOf(identifier, codename, name), "needle"));
    }

    @Test
    public void visibleNameTiePreferencePrecedesNaturalMachineOrder() {
        final CatalogMachine codename = tiedCodenameFixture(
                "MI900014", "Alpha Codename", 2000);
        final CatalogMachine name = searchFixture("MI900015", 2000)
                .addNames(identity("Zulu Needle"))
                .build();

        assertEquals(List.of("MI900015", "MI900014"),
                searchUids(catalogOf(name, codename), "needle"));
    }

    @Test
    public void relationThenCoverageDefineMatchQualityWithoutWeights() {
        final CatalogMachine complete = searchFixture("MI900021", 2000)
                .addNames(identity("Mac Pro")).build();
        final CatalogMachine shorterPrefix = searchFixture("MI900022", 2000)
                .addNames(identity("Project")).build();
        final CatalogMachine longerPrefix = searchFixture("MI900023", 2000)
                .addNames(identity("Professional")).build();
        final CatalogMachine internal = searchFixture("MI900024", 2000)
                .addNames(identity("Apropos")).build();

        assertEquals(List.of("MI900021", "MI900022", "MI900023", "MI900024"),
                searchUids(catalogOf(internal, longerPrefix, complete, shorterPrefix), "pro"));
    }

    @Test
    public void semanticUnitRankingHandlesRepresentativeUserQueries() {
        assertEquals("iMac Pro (2017)", search("pro").get(0).machine().name());
        assertEquals("Mac mini", search("mini").get(0).machine().name());
        assertEquals("iMac (24-inch, M1, 2021)",
                search("m1").get(0).machine().name());
        assertEquals("MacBook Air (15-inch, M2, 2023)",
                search("m2").get(0).machine().name());
        assertEquals("Developer Transition Kit (2020)",
                search("2020").get(0).machine().name());

        assertHit("J185", "MI000301", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.CODENAME, "J185 (iMac20,1)");
        final SearchHit numericCodename = search("185").get(0);
        assertEquals("MI000301", numericCodename.machine().uid());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, numericCodename.relation());
        assertEquals("185", matchedSubstring(numericCodename));

        assertEquals(List.of(
                        "Macintosh Performa 6400 Series",
                        "Macintosh PowerBook 2400c",
                        "Macintosh PowerBook 3400c",
                        "Power Macintosh 6400",
                        "PowerBook G3"),
                search("PowerStar").stream()
                        .map(hit -> hit.machine().name()).collect(Collectors.toList()));
    }

    @Test
    public void responseFacetsUseTheSameFieldScopedResultsAsTheirCounts() {
        final MachineCatalog.SearchResponse years =
                catalog.search("2020", MachineCatalog.SearchScope.ALL);
        assertEquals(9, years.hits().size());
        assertEquals("Developer Transition Kit (2020)",
                years.hits().get(0).machine().name());
        assertEquals(SearchHit.Field.NAME, years.hits().get(0).field());
        assertEquals(2, years.facets().size());
        assertEquals(8, facetCount(years, SearchHit.Field.NAME));
        assertEquals(1, facetCount(years, SearchHit.Field.EMC_NUMBER));
        assertEquals(8, catalog.search(
                "2020", MachineCatalog.SearchScope.NAME).hits().size());
        assertEquals(9, catalog.search(
                "2020", MachineCatalog.SearchScope.NAME).allCount());
        assertEquals(1, catalog.search(
                "2020", MachineCatalog.SearchScope.EMC_NUMBER).hits().size());
        assertFacetCountsMatchScopes("2020", years);

        final MachineCatalog.SearchResponse emc =
                catalog.search("2020", MachineCatalog.SearchScope.EMC_NUMBER);
        assertEquals(MachineCatalog.SearchScope.EMC_NUMBER, emc.scope());
        assertEquals(1, emc.hits().size());
        assertEquals("MI000168", emc.hits().get(0).machine().uid());
        assertEquals(SearchHit.Field.EMC_NUMBER, emc.hits().get(0).field());
    }

    @Test
    public void shortQueriesRankCompleteHumanUnitsAheadOfPartialIdentifiers() {
        final MachineCatalog.SearchResponse m2 =
                catalog.search("M2", MachineCatalog.SearchScope.ALL);
        assertTrue(m2.hits().size() > 4);
        assertTrue(m2.facets().size() > 2);
        assertEquals(3, facetCount(m2, SearchHit.Field.NAME));
        assertTrue(facetCount(m2, SearchHit.Field.CODENAME) > 0);
        assertTrue(facetCount(m2, SearchHit.Field.MODEL_NUMBER) > 0);
        assertTrue(facetCount(m2, SearchHit.Field.PART_NUMBER) > 0);
        assertFalse(catalog.search(
                "M2", MachineCatalog.SearchScope.MODEL_NUMBER).hits().isEmpty());
        assertFalse(catalog.search(
                "M2", MachineCatalog.SearchScope.PART_NUMBER).hits().isEmpty());
        assertFacetCountsMatchScopes("M2", m2);
        for (SearchHit hit : m2.hits().subList(0, 4)) {
            assertEquals(SearchHit.Relation.COMPLETE_UNIT, hit.relation());
        }
        for (String query : List.of("G3", "LC", "SE", "II")) {
            final List<SearchHit> hits = search(query);
            assertFalse(query, hits.isEmpty());
            assertEquals(query, SearchHit.Relation.COMPLETE_UNIT,
                    hits.get(0).relation());
        }
    }

    @Test
    public void partNumberPrefixesRemainVisibleAndRefinable() {
        for (String prefix : List.of("M1", "M2", "M3", "M4", "M5")) {
            final MachineCatalog.SearchResponse all =
                    catalog.search(prefix, MachineCatalog.SearchScope.ALL);
            final MachineCatalog.SearchResponse partNumbers =
                    catalog.search(prefix, MachineCatalog.SearchScope.PART_NUMBER);
            assertFalse(prefix, partNumbers.hits().isEmpty());
            assertEquals(prefix, partNumbers.hits().size(),
                    facetCount(all, SearchHit.Field.PART_NUMBER));
            assertTrue(prefix, partNumbers.hits().stream()
                    .allMatch(hit -> hit.field() == SearchHit.Field.PART_NUMBER));
        }
    }

    @Test
    public void whitespaceTokensMayAndAcrossFieldsButNeverIntroductionDates() {
        final List<SearchHit> kanga = search("PowerBook G3 Kanga");
        assertEquals(1, kanga.size());
        assertEquals("MI000215", kanga.get(0).machine().uid());
        assertEquals(3, kanga.get(0).evidence().size());
        assertTrue(kanga.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.NAME));
        assertTrue(kanga.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.CODENAME));
        assertTrue(catalog.search("PowerBook G3 Kanga",
                MachineCatalog.SearchScope.NAME).hits().isEmpty());
        assertEquals(kanga.get(0).machine(),
                search("PowerBook   \u3000 G3  Kanga").get(0).machine());

        final MachineCatalog.SearchResponse a1278Response =
                catalog.search("A1278", MachineCatalog.SearchScope.ALL);
        final List<SearchHit> a1278 = a1278Response.hits();
        assertEquals(6, a1278.size());
        assertEquals(1, a1278Response.facets().size());
        assertEquals(6, facetCount(a1278Response, SearchHit.Field.MODEL_NUMBER));
        assertTrue(a1278.stream().allMatch(
                hit -> hit.field() == SearchHit.Field.MODEL_NUMBER));
        final List<SearchHit> modelAndVisibleYear = search("A1278 2012");
        assertEquals(1, modelAndVisibleYear.size());
        assertEquals("MI000345", modelAndVisibleYear.get(0).machine().uid());
        assertTrue(modelAndVisibleYear.get(0).machine().name().contains("2012"));
        assertTrue(modelAndVisibleYear.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.NAME));
        assertTrue(modelAndVisibleYear.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.MODEL_NUMBER));
        assertTrue(catalog.search("A1278 2012",
                MachineCatalog.SearchScope.MODEL_NUMBER).hits().isEmpty());
        assertTrue(catalog.search("A1278 2012",
                MachineCatalog.SearchScope.NAME).hits().isEmpty());
        final CatalogMachine introductionOnly = searchFixture("MI900031", 2012)
                .addNames(identity("Neutral Notebook"))
                .addModelNumbers(identity("A1278"))
                .build();
        assertTrue(search(catalogOf(introductionOnly), "A1278 2012").isEmpty());

        final List<String> wallStreet = searchUids(catalog, "Wall Street");
        assertEquals(List.of("MI000216", "MI000217"), wallStreet);
    }

    @Test
    public void contiguousWholePhraseRanksBeforeDistributedAndEvidence() {
        final CatalogMachine phrase = searchFixture("MI900041", 2000)
                .addNames(identity("Zulu Alpha Beta"))
                .build();
        final CatalogMachine distributed = searchFixture("MI900042", 2000)
                .addNames(identity("Alpha"))
                .addCodenames(identity("Beta"))
                .build();
        final List<SearchHit> hits = search(
                catalogOf(distributed, phrase), "Alpha Beta");
        assertEquals(List.of("MI900041", "MI900042"), hits.stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertEquals(1, hits.get(0).evidence().size());
        assertEquals(2, hits.get(1).evidence().size());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, hits.get(0).relation());
        assertTrue(hits.get(0).isWholeQueryMatch());
        assertFalse(hits.get(1).isWholeQueryMatch());
    }

    @Test
    public void completeTokensOutrankAContiguousInternalPhraseAcrossAndWithinMachines() {
        final CatalogMachine internalPhrase = searchFixture("MI900045", 2000)
                .addNames(identity("xalpha betay"))
                .build();
        final CatalogMachine distributed = searchFixture("MI900046", 2000)
                .addNames(identity("Zulu Alpha"))
                .addCodenames(identity("Beta"))
                .build();
        final CatalogMachine bothForms = searchFixture("MI900047", 2000)
                .addNames(identity("Hybrid xalpha betay"))
                .addNames(identity("Alpha"))
                .addCodenames(identity("Beta"))
                .build();
        final MachineCatalog fixture = catalogOf(internalPhrase, distributed, bothForms);

        final List<SearchHit> hits = search(fixture, "alpha beta");
        final SearchHit internalHit = findHit(
                hits, "alpha beta", fixture.requireByUid("MI900045"));
        final SearchHit distributedHit = findHit(
                hits, "alpha beta", fixture.requireByUid("MI900046"));
        final SearchHit bothFormsHit = findHit(
                hits, "alpha beta", fixture.requireByUid("MI900047"));

        assertEquals(SearchHit.Relation.UNIT_INTERNAL, internalHit.relation());
        assertTrue(internalHit.isWholeQueryMatch());
        assertTrue(distributedHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));
        assertFalse(distributedHit.isWholeQueryMatch());
        assertTrue(MachineCatalog.compareSearchHits(distributedHit, internalHit) < 0);

        assertEquals(2, bothFormsHit.evidence().size());
        assertTrue(bothFormsHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));
        assertFalse(bothFormsHit.isWholeQueryMatch());
    }

    @Test
    public void scopedFieldUsesTheSameWinningPlanForHitsAndFacetEligibility() {
        final CatalogMachine tokenWinner = searchFixture("MI900048", 2000)
                .addNames(identity("Hybrid xalpha betay"))
                .addNames(identity("Alpha"))
                .addNames(identity("Beta"))
                .build();
        final CatalogMachine phraseWinner = searchFixture("MI900049", 2000)
                .addNames(identity("Alpha Beta"))
                .addNames(identity("Alpha"))
                .addNames(identity("Beta"))
                .build();
        final MachineCatalog fixture = catalogOf(tokenWinner, phraseWinner);

        final MachineCatalog.SearchResponse all = fixture.search(
                "alpha beta", MachineCatalog.SearchScope.ALL);
        assertEquals(2, all.hits().size());
        assertEquals(2, facetCount(all, SearchHit.Field.NAME));

        final List<SearchHit> scoped = fixture.search(
                "alpha beta", MachineCatalog.SearchScope.NAME).hits();
        final SearchHit tokenHit = findHit(
                scoped, "alpha beta", fixture.requireByUid("MI900048"));
        assertEquals(2, tokenHit.evidence().size());
        assertFalse(tokenHit.isWholeQueryMatch());
        assertTrue(tokenHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));

        final SearchHit phraseHit = findHit(
                scoped, "alpha beta", fixture.requireByUid("MI900049"));
        assertEquals(1, phraseHit.evidence().size());
        assertTrue(phraseHit.isWholeQueryMatch());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, phraseHit.relation());
    }

    @Test
    public void repeatedQueryTokensFallBackAsOneAndTerm() {
        for (String term : List.of("Mac", "2020")) {
            final String repeated = term + " " + term;
            for (MachineCatalog.SearchScope scope : MachineCatalog.SearchScope.values()) {
                final MachineCatalog.SearchResponse singleResponse = catalog.search(term, scope);
                final MachineCatalog.SearchResponse repeatedResponse =
                        catalog.search(repeated, scope);
                assertEquals(term + " / " + scope,
                        singleResponse.hits().stream().map(hit -> hit.machine().uid())
                                .collect(Collectors.toList()),
                        repeatedResponse.hits().stream().map(hit -> hit.machine().uid())
                                .collect(Collectors.toList()));
            }
            assertTrue(repeated, search(repeated).stream()
                    .allMatch(hit -> hit.evidence().size() == 1));
        }

        final CatalogMachine repeatedPhrase = searchFixture("MI900043", 2000)
                .addNames(identity("Zulu Mac Mac"))
                .build();
        final CatalogMachine singleTerm = searchFixture("MI900044", 2000)
                .addNames(identity("Alpha Mac"))
                .build();
        final MachineCatalog fixture = catalogOf(repeatedPhrase, singleTerm);
        assertEquals(searchUids(fixture, "Mac"), searchUids(fixture, "Mac Mac"));
    }

    @Test
    public void modelIdentifierFragmentsUseTheSameContainsMatchingAsOtherFields() {
        final CatalogMachine valid = searchFixture("MI900051", 2000)
                .addNames(identity("Valid Identifier"))
                .addIdentifiers(identity("MacBookPro8,1"))
                .build();
        final CatalogMachine wrongLeft = searchFixture("MI900052", 2000)
                .addNames(identity("Wrong Left Boundary"))
                .addIdentifiers(identity("MacBookPro18,1"))
                .build();
        final CatalogMachine wrongRight = searchFixture("MI900053", 2000)
                .addNames(identity("Wrong Right Boundary"))
                .addIdentifiers(identity("MacBookPro8,10"))
                .build();
        final CatalogMachine ordinaryContains = searchFixture("MI900054", 2000)
                .addNames(identity("Ordinary Contains"))
                .addIdentifiers(identity("MacBookPro18,3"))
                .build();
        final MachineCatalog identifiers = catalogOf(
                wrongRight, wrongLeft, ordinaryContains, valid);

        assertEquals(List.of("MI900051", "MI900053", "MI900052"),
                searchUids(identifiers, "8,1"));
        assertEquals(List.of("MI900052"),
                searchUids(identifiers, "18,1"));
        assertEquals(List.of("MI900053"),
                searchUids(identifiers, "8,10"));
        assertEquals(List.of("MI900054"),
                searchUids(identifiers, "BookPro18,3"));
    }

    @Test
    public void canonicalTitleEvidenceExplainsSameQualityAliasMatches() {
        final CatalogMachine canonical = searchFixture("MI900061", 2000)
                .addNames(identity("Mac Alpha"))
                .addNames(identity("Alpha"))
                .build();
        final CatalogMachine exactAlias = searchFixture("MI900062", 2000)
                .addNames(identity("Aardvark"))
                .addNames(identity("Alpha"))
                .build();
        final MachineCatalog fixture = catalogOf(canonical, exactAlias);

        final List<SearchHit> hits = search(fixture, "Alpha");
        assertEquals(List.of("MI900062", "MI900061"), hits.stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        final SearchHit canonicalHit = findHit(
                hits, "Alpha", fixture.requireByUid("MI900061"));
        assertEquals("Mac Alpha", canonicalHit.matchedValue());
        assertEquals(4, canonicalHit.matchStartInclusive());
        assertEquals(9, canonicalHit.matchEndExclusive());
        assertEquals("Alpha", matchedSubstring(canonicalHit));
    }

    @Test
    public void shortQueriesUseTheSameContainsMatchingAsLongQueries() {
        final CatalogMachine complete = searchFixture("MI900063", 2000)
                .addNames(identity("A"))
                .build();
        final CatalogMachine prefix = searchFixture("MI900064", 2000)
                .addNames(identity("Alpha"))
                .build();
        final CatalogMachine internal = searchFixture("MI900065", 2000)
                .addNames(identity("Beta"))
                .build();
        final CatalogMachine identifierPrefix = searchFixture("MI900066", 2000)
                .addNames(identity("Identifier Prefix"))
                .addModelNumbers(identity("M1234"))
                .build();
        final MachineCatalog fixture = catalogOf(
                identifierPrefix, internal, prefix, complete);

        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                findHit(search(fixture, "a"), "a", fixture.requireByUid("MI900063")).relation());
        assertEquals(SearchHit.Relation.UNIT_PREFIX,
                findHit(search(fixture, "a"), "a", fixture.requireByUid("MI900064")).relation());
        assertEquals(SearchHit.Relation.UNIT_INTERNAL,
                findHit(search(fixture, "a"), "a", fixture.requireByUid("MI900065")).relation());
        assertEquals(SearchHit.Relation.UNIT_PREFIX,
                findHit(search(fixture, "m1"), "m1",
                        fixture.requireByUid("MI900066")).relation());
    }

    @Test
    public void eachCandidateUsesItsBestOccurrenceForRankAndHighlight() {
        final Machine allInOne = machineNamed("Power Macintosh G3 (All In One)");
        final SearchHit wordHit = findHit("in", allInOne);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, wordHit.relation());
        assertEquals("In", matchedSubstring(wordHit));

        final Machine laterTwenty = machineNamed("iMac (Early 2006 20-inch)");
        final SearchHit numberHit = findHit("20", laterTwenty);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, numberHit.relation());
        assertEquals(laterTwenty.name().lastIndexOf("20"), numberHit.matchStartInclusive());
        assertEquals("20", matchedSubstring(numberHit));
    }

    @Test
    public void normalizesUnicodeAtRuntime() {
        final List<Machine> ascii = searchMachines("Macintosh");
        final List<Machine> fullWidth = searchMachines("Ｍａｃｉｎｔｏｓｈ");
        assertEquals(ascii, fullWidth);
        assertFalse(ascii.isEmpty());
        assertEquals(searchMachines("iMac20,1"), searchMachines("iMac20，1"));
    }

    @Test
    public void normalizationMatchesSharedGoldenCases() throws Exception {
        final String json = Files.readString(
                Path.of(System.getProperty("macindex.catalog.normalization.path")),
                StandardCharsets.UTF_8);
        final JSONObject document = new JSONObject(json);
        assertEquals(1, document.getInt("schema"));
        final JSONArray cases = document.getJSONArray("cases");
        assertFalse(cases.length() == 0);
        for (int index = 0; index < cases.length(); index++) {
            final JSONObject testCase = cases.getJSONObject(index);
            assertEquals(
                    "Normalization case " + index,
                    testCase.getString("normalized"),
                    Machine.normalize(testCase.getString("raw")));
        }
    }

    @Test
    public void generatedTextRangesUseJavaUtf16Offsets() throws Exception {
        final String json = Files.readString(
                Path.of(System.getProperty("macindex.catalog.textRange.path")),
                StandardCharsets.UTF_8);
        final JSONObject document = new JSONObject(json);
        assertEquals(2, document.getInt("schema"));
        final JSONArray cases = document.getJSONArray("cases");
        assertFalse(cases.length() == 0);
        for (int caseIndex = 0; caseIndex < cases.length(); caseIndex++) {
            final JSONObject testCase = cases.getJSONObject(caseIndex);
            final String value = testCase.getString("text");
            assertTrue("Golden case must exercise a supplementary code point",
                    value.codePoints().count() < value.length());
            final JSONArray ranges = testCase.getJSONArray("ranges");
            for (int rangeIndex = 0; rangeIndex < ranges.length(); rangeIndex++) {
                final JSONObject range = ranges.getJSONObject(rangeIndex);
                final int start = range.getInt("start");
                final int end = range.getInt("end");
                assertEquals(range.getString("substring"), value.substring(start, end));
                assertFalse(Character.isLowSurrogate(value.charAt(start)));
                assertFalse(end < value.length()
                        && Character.isLowSurrogate(value.charAt(end)));
            }
        }
    }

    @Test
    public void partNumberSuffixesFollowTheAuthoredRevisionGrammar() {
        final Machine target = catalog.requireByUid("MI000215");
        final MachineCatalog.SearchResponse exact =
                catalog.search("M5994", MachineCatalog.SearchScope.ALL);
        assertEquals(MachineCatalog.SearchScope.ALL, exact.scope());
        assertEquals(Collections.singletonList(target), exact.hits().stream()
                .map(SearchHit::machine).collect(Collectors.toList()));
        assertTrue(exact.hits().stream()
                .allMatch(hit -> hit.field() == SearchHit.Field.PART_NUMBER));
        assertEquals("M5994*/A", exact.hits().get(0).matchedValue());

        final Machine mc700 = catalog.requireByUid("MI000339");
        assertPartNumberEvidence("MC700", mc700, "MC700*/A (2.3 GHz)", 5);
        assertPartNumberEvidence("MC700C", mc700, "MC700*/A (2.3 GHz)", 6);
        assertPartNumberEvidence("MC700CH", mc700, "MC700*/A (2.3 GHz)", 6);
        assertPartNumberEvidence("MC700CH/", mc700, "MC700*/A (2.3 GHz)", 7);
        assertPartNumberEvidence("MC700CH/A", mc700, "MC700*/A (2.3 GHz)", 8);

        final Machine multiRevision = catalog.requireByUid("MI000055");
        assertPartNumberEvidence("M2147LL/B", multiRevision, "M2147*/B", 8);

        for (String query : List.of(
                "M5994J", "M5994AB", "M5994J/", "M5994J/A",
                "M5994AB/A", "M5994XX/A", "M5994CH/A")) {
            final MachineCatalog.SearchResponse concrete =
                    catalog.search(query, MachineCatalog.SearchScope.NAME);
            assertEquals(query, MachineCatalog.SearchScope.ALL, concrete.scope());
            assertEquals(query, Collections.singletonList(target), concrete.hits().stream()
                    .map(SearchHit::machine).collect(Collectors.toList()));
            assertEquals(query, "M5994*/A", concrete.hits().get(0).matchedValue());
        }
        for (String query : List.of("M5994LL/B", "M5994ABC", "M5994LL\\A",
                "M5994/A")) {
            assertTrue(query, catalog.search(query, MachineCatalog.SearchScope.ALL)
                    .hits().isEmpty());
        }

        final MachineCatalog.SearchResponse shared =
                catalog.search("MD212ZP/A", MachineCatalog.SearchScope.ALL);
        assertEquals(MachineCatalog.SearchScope.ALL, shared.scope());
        assertEquals(List.of("MI000349", "MI000348"), shared.hits().stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertTrue(shared.hits().stream()
                .allMatch(hit -> hit.field() == SearchHit.Field.PART_NUMBER));

        final MachineCatalog.SearchResponse crossIdentifier =
                catalog.search("M9020", MachineCatalog.SearchScope.ALL);
        assertEquals(List.of("MI000034", "MI000166"), crossIdentifier.hits().stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertEquals(List.of(SearchHit.Field.MODEL_NUMBER, SearchHit.Field.PART_NUMBER),
                crossIdentifier.hits().stream().map(SearchHit::field)
                        .collect(Collectors.toList()));

        assertEquals(MachineCatalog.SearchScope.ALL,
                catalog.search("M599", MachineCatalog.SearchScope.ALL).scope());
        assertTrue(searchOrder("M599").contains(target));
    }

    @Test
    public void unifiedSearchIsMonotonicUnderItsSingleComparator() {
        final List<SearchHit> sorted = search("mac");
        for (int index = 1; index < sorted.size(); index++) {
            final SearchHit previous = sorted.get(index - 1);
            final SearchHit current = sorted.get(index);
            assertTrue(MachineCatalog.compareSearchHits(previous, current) <= 0);
        }
    }

    @Test
    public void searchComparatorIsAntisymmetricAndTransitive() {
        final List<SearchHit> hits = search("pro").subList(0, 16);
        for (SearchHit left : hits) {
            for (SearchHit right : hits) {
                assertEquals(Integer.signum(MachineCatalog.compareSearchHits(left, right)),
                        -Integer.signum(MachineCatalog.compareSearchHits(right, left)));
            }
        }
        for (SearchHit first : hits) {
            for (SearchHit second : hits) {
                if (MachineCatalog.compareSearchHits(first, second) > 0) {
                    continue;
                }
                for (SearchHit third : hits) {
                    if (MachineCatalog.compareSearchHits(second, third) <= 0) {
                        assertTrue(MachineCatalog.compareSearchHits(first, third) <= 0);
                    }
                }
            }
        }
    }

}
