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
        assertEquals(Collections.singletonList(configuredCodename),
                searchMachines("J416s (M2 Pro)"));
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
        assertTrue(MachineCatalog.isBlankSearchText("，；、"));
        assertFalse(MachineCatalog.isBlankSearchText("\u3000Kanga\u3000"));

        assertEquals(searchUids(catalog, "iMac G3"),
                searchUids(catalog, "iMac，G3"));
        assertEquals(searchUids(catalog, "iMac G3"),
                searchUids(catalog, "iMac、G3"));
        assertEquals(searchUids(catalog, "iMac G3"),
                searchUids(catalog, "iMac：G3"));
        assertEquals(searchUids(catalog, "iMac G3"),
                searchUids(catalog, "iMac。G3"));
        assertEquals(searchUids(catalog, "iMac20,1"),
                searchUids(catalog, "iMac20，1"));
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
        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                primaryEvidence(fullWidth).relation());
        assertEquals(SearchHit.Field.CODENAME, primaryEvidence(fullWidth).field());
        assertEquals("Kanga", primaryEvidence(fullWidth).matchedValue());
    }

    @Test
    public void acronymBoundariesDoNotSplitRomanNumeralsOrUnits() {
        final MachineCatalog fixture = catalogOf(
                searchFixture("MI900055", 2000)
                        .addNames(identity("Macintosh IIci"))
                        .build(),
                searchFixture("MI900056", 2000)
                        .addNames(identity("Acronym Fixture"))
                        .addCodenames(identity("DBLite"))
                        .build(),
                searchFixture("MI900057", 2000)
                        .addNames(identity("Machine 1GHz"))
                        .build());

        assertEquals(SearchHit.Relation.UNIT_PREFIX, primaryEvidence(findHit(
                search(fixture, "II"), "II", fixture.requireByUid("MI900055"))).relation());
        assertEquals(SearchHit.Relation.UNIT_INTERNAL, primaryEvidence(findHit(
                search(fixture, "Ici"), "Ici", fixture.requireByUid("MI900055"))).relation());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, primaryEvidence(findHit(
                search(fixture, "Lite"), "Lite", fixture.requireByUid("MI900056"))).relation());
        assertEquals(SearchHit.Relation.UNIT_INTERNAL, primaryEvidence(findHit(
                search(fixture, "Hz"), "Hz", fixture.requireByUid("MI900057"))).relation());
    }

    @Test
    public void searchHitsExposeTheActualRangeInsideTheDisplayedValue() {
        assertMatchRange("J185", "MI000301", 0, 4, "J185");
        assertMatchRange("185", "MI000301", 1, 4, "185");
        assertMatchRange("ｊ１８５", "MI000301", 0, 4, "J185");
        assertMatchRange("kang", "MI000215", 0, 4, "Kang");
        assertMatchRange("ＡＮＧ", "MI000215", 1, 4, "ang");

        final SearchHit compactName = findHit(
                "macbookpro", catalog.requireByUid("MI000413"));
        assertEquals(SearchHit.Field.NAME, primaryEvidence(compactName).field());
        assertEquals("MacBook Pro", matchedSubstring(compactName));

        final CatalogMachine repeated = searchFixture("MI900015", 2000)
                .addNames(identity("Banana"))
                .build();
        final SearchHit hit = search(catalogOf(repeated), "ana").get(0);
        assertEquals(1, primaryEvidence(hit).matchStartInclusive());
        assertEquals(4, primaryEvidence(hit).matchEndExclusive());
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
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, primaryEvidence(hit).relation());
        assertEquals(SearchHit.Field.NAME, primaryEvidence(hit).field());
        assertEquals("Alpha", primaryEvidence(hit).matchedValue());

        hit = search(fixtureCatalog, "alph").get(0);
        assertEquals(SearchHit.Relation.UNIT_PREFIX, primaryEvidence(hit).relation());
        assertEquals("Alpha", primaryEvidence(hit).matchedValue());

        hit = search(fixtureCatalog, "lph").get(0);
        assertEquals(SearchHit.Relation.UNIT_INTERNAL, primaryEvidence(hit).relation());
        assertEquals("Alpha", primaryEvidence(hit).matchedValue());

        hit = search(fixtureCatalog, "beta").get(0);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT, primaryEvidence(hit).relation());
        assertEquals(SearchHit.Field.NAME, primaryEvidence(hit).field());
        assertEquals("Beta Extended", primaryEvidence(hit).matchedValue());

        hit = search(fixtureCatalog, "M0001").get(0);
        assertEquals(SearchHit.Relation.UNIT_PREFIX, primaryEvidence(hit).relation());
        assertEquals("M0001E", primaryEvidence(hit).matchedValue());
    }

    @Test
    public void trueTiesUseHomepageCategoryThenIntroductionThenNaturalName() {
        final CatalogMachine olderServer = tiedCodenameFixture(
                "MI900001", "Aardvark Server", 1970).toBuilder()
                .setProductTypeKey("mac_server").build();
        final CatalogMachine olderDesktop = tiedCodenameFixture(
                "MI900002", "Zulu Desktop", 1980).toBuilder()
                .setProductTypeKey("power_mac_g3_g4_g5").build();
        final CatalogMachine newerDesktop = tiedCodenameFixture(
                "MI900003", "Alpha Desktop", 2025).toBuilder()
                .setProductTypeKey("power_mac_g3_g4_g5").build();
        final List<String> expected = List.of("MI900002", "MI900003", "MI900001");

        assertEquals(expected,
                searchUids(catalogOf(olderServer, newerDesktop, olderDesktop), "needle"));
        assertEquals(expected,
                searchUids(catalogOf(newerDesktop, olderDesktop, olderServer), "needle"));
    }

    @Test
    public void sharedCodenameTiesFollowHomepageCategoryOrderBeforeIntroduction() {
        assertEquals(List.of("MI000151", "MI000152", "MI000197"),
                searchUids(catalog, "Gossamer"));
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
    public void completeVisiblePhrasePrefersTheWholeNameOverAContainingName() {
        final CatalogMachine exact = searchFixture("MI900025", 2000)
                .addNames(identity("Mac Pro")).build();
        final CatalogMachine containing = searchFixture("MI900026", 2000)
                .addNames(identity("iMac Pro")).build();

        assertEquals(List.of("MI900025", "MI900026"),
                searchUids(catalogOf(containing, exact), "mac pro"));
    }

    @Test
    public void semanticUnitRankingHandlesRepresentativeUserQueries() {
        assertEquals("Mac Pro", search("mac pro").get(0).machine().name());
        assertEquals("Mac Pro", search("pro").get(0).machine().name());
        assertEquals("Mac mini", search("mini").get(0).machine().name());
        assertEquals("Mac mini (M1, 2020)",
                search("m1").get(0).machine().name());
        assertEquals("MacBook Air (M2, 2022)",
                search("m2").get(0).machine().name());
        assertEquals("Mac mini (M1, 2020)",
                search("2020").get(0).machine().name());

        assertHit("J185", "MI000301", SearchHit.Relation.COMPLETE_UNIT,
                SearchHit.Field.CODENAME, "J185 (iMac20,1)");
        final SearchHit exactGestalt = search("313").get(0);
        assertEquals("MI000215", exactGestalt.machine().uid());
        assertEquals(SearchHit.Field.GESTALT_ID, primaryEvidence(exactGestalt).field());
        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                primaryEvidence(exactGestalt).relation());
        assertEquals(SearchHit.Relation.UNIT_INTERNAL, primaryEvidence(findHit(
                "313", catalog.requireByUid("MI000427"))).relation());

        assertEquals(List.of(
                        "Macintosh Performa 6400 Series",
                        "Power Macintosh 6400",
                        "Macintosh PowerBook 3400c",
                        "Macintosh PowerBook 2400c",
                        "PowerBook G3"),
                search("PowerStar").stream()
                        .map(hit -> hit.machine().name()).collect(Collectors.toList()));
    }

    @Test
    public void processorAndMachineAbbreviationsComposeWithNamesAndYears() {
        for (String processor : List.of(
                "G3", "G4", "G5", "68K", "PPC", "P4", "Pentium",
                "NHM", "WSM", "SNB", "IVB", "HSW", "BDW", "PNR",
                "SKL", "KBL", "CFL", "AML", "CLX", "CML", "ICL",
                "Core i3", "i5", "i7", "i9", "C2D", "C2E",
                "Lynnfield", "Haswell", "4960HQ", "T7700", "7450",
                "T1", "T2", "A12Z", "A18 Pro", "A18Pro",
                "M1", "M1P", "M1M", "M1U",
                "M2", "M2P", "M2M", "M2U",
                "M3", "M3P", "M3M", "M3U",
                "M4", "M4P", "M4M",
                "M5", "M5P", "M5M")) {
            assertFalse(processor, catalog.search(
                    processor, MachineCatalog.SearchScope.PROCESSOR).hits().isEmpty());
        }
        assertEquals(searchUids(catalog, "M1 Pro"), searchUids(catalog, "M1Pro"));
        assertEquals(searchUids(catalog, "M1 Pro"), searchUids(catalog, "M1P"));
        assertEquals(searchUids(catalog, "Core i7"), searchUids(catalog, "i7"));
        assertEquals(searchUids(catalog, "A18 Pro"), searchUids(catalog, "A18Pro"));

        final List<SearchHit> t2 = search("T2");
        final long t2SecurityChipMachines = t2.stream().filter(hit ->
                hit.machine().processor().contains("Apple T2")).count();
        assertTrue(t2SecurityChipMachines > 0);
        assertTrue(t2.subList(0, (int) t2SecurityChipMachines).stream().allMatch(hit ->
                hit.machine().processor().contains("Apple T2")
                        && primaryEvidence(hit).field() == SearchHit.Field.PROCESSOR));
        final List<SearchHit> t1 = search("T1");
        final long t1SecurityChipMachines = t1.stream().filter(hit ->
                hit.machine().processor().contains("Apple T1")).count();
        assertTrue(t1SecurityChipMachines > 0);
        assertTrue(t1.subList(0, (int) t1SecurityChipMachines).stream().allMatch(hit ->
                hit.machine().processor().contains("Apple T1")
                        && primaryEvidence(hit).field() == SearchHit.Field.PROCESSOR));
        assertTrue(catalog.search("A12Z", MachineCatalog.SearchScope.PROCESSOR)
                .hits().stream().allMatch(hit ->
                        hit.machine().processor().contains("Apple A12Z")));
        assertTrue(catalog.search("A18Pro", MachineCatalog.SearchScope.PROCESSOR)
                .hits().stream().allMatch(hit ->
                        hit.machine().processor().contains("Apple A18 Pro")));
        for (String displayOnlyTerm : List.of(
                "FPU", "Tonga", "Jade Chop", "Donan", "Tahiti")) {
            assertTrue(displayOnlyTerm, catalog.search(
                    displayOnlyTerm, MachineCatalog.SearchScope.PROCESSOR).hits().isEmpty());
        }
        assertTrue(Collections.disjoint(List.of("MI000034", "MI000038", "MI000071"),
                catalog.search("3210", MachineCatalog.SearchScope.PROCESSOR).hits().stream()
                        .map(hit -> hit.machine().uid()).collect(Collectors.toList())));
        assertTrue(Collections.disjoint(List.of("MI000104", "MI000114", "MI000116"),
                catalog.search("5x86", MachineCatalog.SearchScope.PROCESSOR).hits().stream()
                        .map(hit -> hit.machine().uid()).collect(Collectors.toList())));
        assertTrue(Collections.disjoint(List.of("MI000104", "MI000114", "MI000116"),
                catalog.search("6x86", MachineCatalog.SearchScope.PROCESSOR).hits().stream()
                        .map(hit -> hit.machine().uid()).collect(Collectors.toList())));

        final List<SearchHit> m1Pro = search("MacBook Pro M1 Pro");
        assertFalse(m1Pro.isEmpty());
        assertTrue(m1Pro.stream().allMatch(hit ->
                hit.machine().processor().contains("Apple M1 Pro")));

        for (String abbreviation : List.of(
                "MB", "MBN", "MBP", "MBA", "MM", "MP", "PB", "PM", "WGS", "ANS",
                "DTK", "BW", "DA", "QS", "MDD", "WS", "rMB", "nMB", "rMBP", "TB")) {
            assertFalse(abbreviation, catalog.search(
                    abbreviation, MachineCatalog.SearchScope.NAME).hits().isEmpty());
        }
        assertTrue(catalog.search("MBPX", MachineCatalog.SearchScope.NAME).hits().isEmpty());

        final List<SearchHit> imacG3 = search("iMac G3");
        assertFalse(imacG3.isEmpty());
        assertTrue(imacG3.get(0).machine().name().startsWith("iMac"));
        assertTrue(imacG3.get(0).machine().processorFamilyKeys().contains("g3"));

        final List<SearchHit> imacM4 = search("iMac M4");
        assertFalse(imacM4.isEmpty());
        assertTrue(imacM4.get(0).machine().name().startsWith("iMac"));
        assertTrue(imacM4.get(0).machine().processorFamilyKeys().contains("m4"));

        final List<SearchHit> macBookAirM2 = search("MacBook Air M2");
        assertFalse(macBookAirM2.isEmpty());
        assertEquals("macbook_air", macBookAirM2.get(0).machine().productTypeKey());
        assertTrue(macBookAirM2.get(0).machine().processorFamilyKeys().contains("m2"));

        final List<String> compactNames = searchUids(catalog, "macbookpro 2014");
        assertFalse(compactNames.isEmpty());
        assertEquals(compactNames, searchUids(catalog, "MBP 2014"));
        assertEquals(compactNames, searchUids(catalog, "MBP2014"));
        assertTrue(search("MBP 2014").stream().allMatch(hit ->
                hit.machine().productTypeKey().equals("macbook_pro")
                        && introducedIn(hit.machine(), 2014)));

        final List<String> powerMac8100 = searchUids(catalog, "PM 8100");
        assertFalse(powerMac8100.isEmpty());
        assertEquals(powerMac8100, searchUids(catalog, "PM8100"));

        final List<SearchHit> powerMac2005 = search("powermac 2005");
        assertFalse(powerMac2005.isEmpty());
        assertTrue(powerMac2005.get(0).machine().productTypeKey().equals("power_mac")
                || powerMac2005.get(0).machine().productTypeKey()
                        .equals("power_mac_g3_g4_g5"));
        assertTrue(introducedIn(powerMac2005.get(0).machine(), 2005));

        assertEquals(2, catalog.search(
                "DTK", MachineCatalog.SearchScope.NAME).hits().size());
        assertTrue(catalog.search("MP", MachineCatalog.SearchScope.NAME).hits().stream()
                .noneMatch(hit -> hit.machine().uid().equals("MI000253")));
    }

    @Test
    public void responseFacetsUseTheSameFieldScopedResultsAsTheirCounts() {
        final CatalogMachine visibleName = searchFixture("MI900071", 2000)
                .addNames(identity("Alpha 2020"))
                .build();
        final CatalogMachine emcNumber = searchFixture("MI900072", 2000)
                .addNames(identity("Beta"))
                .addEmcNumbers(identity("2020"))
                .build();
        final MachineCatalog fixture = catalogOf(emcNumber, visibleName);
        final MachineCatalog.SearchResponse all =
                fixture.search("2020", MachineCatalog.SearchScope.ALL);

        assertEquals(2, all.hits().size());
        assertEquals(2, all.facets().size());
        assertEquals(1, facetCount(all, SearchHit.Field.NAME));
        assertEquals(1, facetCount(all, SearchHit.Field.EMC_NUMBER));
        assertFacetCountsMatchScopes(fixture, "2020", all);

        final MachineCatalog.SearchResponse emc =
                fixture.search("2020", MachineCatalog.SearchScope.EMC_NUMBER);
        assertEquals(MachineCatalog.SearchScope.EMC_NUMBER, emc.scope());
        assertEquals(1, emc.hits().size());
        assertEquals("MI900072", emc.hits().get(0).machine().uid());
        assertEquals(SearchHit.Field.EMC_NUMBER,
                primaryEvidence(emc.hits().get(0)).field());
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
                    .allMatch(hit -> primaryEvidence(hit).field()
                            == SearchHit.Field.PART_NUMBER));
        }
    }

    @Test
    public void whitespaceTokensMayAndAcrossNamesIdentifiersAndIntroductionYears() {
        final CatalogMachine crossField = searchFixture("MI900031", 2000)
                .addNames(identity("PowerBook G3"))
                .addCodenames(identity("Kanga"))
                .build();
        final CatalogMachine modelAndName = searchFixture("MI900032", 2000)
                .addNames(identity("Notebook 2012"))
                .addModelNumbers(identity("A1278"))
                .build();
        final CatalogMachine introductionOnly = searchFixture("MI900033", 2012)
                .addNames(identity("Neutral Notebook"))
                .addModelNumbers(identity("A1278"))
                .build();
        final MachineCatalog fixture = catalogOf(
                crossField, modelAndName, introductionOnly);

        final List<SearchHit> kanga = search(fixture, "PowerBook G3 Kanga");
        assertEquals(1, kanga.size());
        assertEquals("MI900031", kanga.get(0).machine().uid());
        assertEquals(2, kanga.get(0).evidence().size());
        assertTrue(kanga.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.NAME));
        assertTrue(kanga.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.CODENAME));
        assertTrue(fixture.search("PowerBook G3 Kanga",
                MachineCatalog.SearchScope.NAME).hits().isEmpty());
        assertEquals(kanga.get(0).machine(),
                search(fixture, "PowerBook   \u3000 G3  Kanga").get(0).machine());

        final List<SearchHit> modelAndYear = search(fixture, "A1278 2012");
        assertEquals(List.of("MI900032", "MI900033"), modelAndYear.stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertTrue(modelAndYear.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.NAME));
        assertTrue(modelAndYear.get(0).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.MODEL_NUMBER));
        assertTrue(modelAndYear.get(1).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.INTRODUCTION));
        assertTrue(modelAndYear.get(1).evidence().stream().anyMatch(
                item -> item.field() == SearchHit.Field.MODEL_NUMBER));
        assertTrue(fixture.search("A1278 2012",
                MachineCatalog.SearchScope.MODEL_NUMBER).hits().isEmpty());
        assertTrue(fixture.search("A1278 2012",
                MachineCatalog.SearchScope.NAME).hits().isEmpty());
        assertEquals(1, search(catalogOf(introductionOnly), "A1278 2012").size());

        assertEquals(List.of("MI900033"), fixture.search(
                "2012", MachineCatalog.SearchScope.INTRODUCTION).hits().stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertTrue(fixture.search("201", MachineCatalog.SearchScope.INTRODUCTION)
                .hits().isEmpty());
        assertFalse(search(fixture, "201").isEmpty());
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
        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                primaryEvidence(hits.get(0)).relation());
    }

    @Test
    public void adjacentPhrasesAndEvidenceInOneValueResolveMultiwordAmbiguity() {
        assertEquals("MI000404", search("Mac Pro M2").get(0).machine().uid());
        assertEquals("MI000404", search("Mac Pro 2023").get(0).machine().uid());

        final String imac2008 = search("iMac 2008").get(0).machine().uid();
        assertTrue(imac2008.equals("MI000273") || imac2008.equals("MI000274"));
        assertTrue(List.of("MI000151", "MI000152", "MI000153", "MI000154")
                .contains(search("Power Mac G3").get(0).machine().uid()));

        final CatalogMachine coherent = searchFixture("MI900043", 2000)
                .addNames(identity("Alpha 2008"))
                .build();
        final CatalogMachine distributed = searchFixture("MI900044", 2000)
                .addNames(identity("Alpha"))
                .addEmcNumbers(identity("2008"))
                .build();
        assertEquals("MI900043", search(catalogOf(distributed, coherent), "Alpha 2008")
                .get(0).machine().uid());
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

        assertEquals(SearchHit.Relation.UNIT_INTERNAL,
                primaryEvidence(internalHit).relation());
        assertTrue(distributedHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));
        assertTrue(hits.indexOf(distributedHit) < hits.indexOf(internalHit));

        assertEquals(2, bothFormsHit.evidence().size());
        assertTrue(bothFormsHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));
    }

    @Test
    public void fieldScopeUsesTheSameRankingAsAllAndItsFacetCount() {
        final CatalogMachine bothForms = searchFixture("MI900048", 2000)
                .addNames(identity("Hybrid xalpha betay"))
                .addNames(identity("Alpha"))
                .addNames(identity("Beta"))
                .build();
        final MachineCatalog fixture = catalogOf(bothForms);

        final MachineCatalog.SearchResponse all = fixture.search(
                "alpha beta", MachineCatalog.SearchScope.ALL);
        assertEquals(1, all.hits().size());
        assertEquals(1, facetCount(all, SearchHit.Field.NAME));

        final List<SearchHit> scoped = fixture.search(
                "alpha beta", MachineCatalog.SearchScope.NAME).hits();
        final SearchHit scopedHit = findHit(
                scoped, "alpha beta", fixture.requireByUid("MI900048"));
        assertEquals(2, scopedHit.evidence().size());
        assertTrue(scopedHit.evidence().stream().allMatch(
                evidence -> evidence.relation() == SearchHit.Relation.COMPLETE_UNIT));
    }

    @Test
    public void repeatedQueryTokensAreEquivalentToOneTerm() {
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
        assertEquals("Mac Alpha", primaryEvidence(canonicalHit).matchedValue());
        assertEquals(4, primaryEvidence(canonicalHit).matchStartInclusive());
        assertEquals(9, primaryEvidence(canonicalHit).matchEndExclusive());
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
                primaryEvidence(findHit(search(fixture, "a"), "a",
                        fixture.requireByUid("MI900063"))).relation());
        assertEquals(SearchHit.Relation.UNIT_PREFIX,
                primaryEvidence(findHit(search(fixture, "a"), "a",
                        fixture.requireByUid("MI900064"))).relation());
        assertEquals(SearchHit.Relation.UNIT_INTERNAL,
                primaryEvidence(findHit(search(fixture, "a"), "a",
                        fixture.requireByUid("MI900065"))).relation());
        assertEquals(SearchHit.Relation.UNIT_PREFIX,
                primaryEvidence(findHit(search(fixture, "m1"), "m1",
                        fixture.requireByUid("MI900066"))).relation());
    }

    @Test
    public void eachCandidateUsesItsBestOccurrenceForRankAndHighlight() {
        final CatalogMachine allInOneSource = searchFixture("MI900081", 2000)
                .addNames(identity("Power Macintosh G3 (All In One)"))
                .build();
        final CatalogMachine laterTwentySource = searchFixture("MI900082", 2000)
                .addNames(identity("iMac (Early 2006 20-inch)"))
                .build();
        final MachineCatalog fixture = catalogOf(allInOneSource, laterTwentySource);
        final Machine allInOne = fixture.requireByUid("MI900081");
        final SearchHit wordHit = findHit(search(fixture, "in"), "in", allInOne);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                primaryEvidence(wordHit).relation());
        assertEquals("In", matchedSubstring(wordHit));

        final Machine laterTwenty = fixture.requireByUid("MI900082");
        final SearchHit numberHit = findHit(search(fixture, "20"), "20", laterTwenty);
        assertEquals(SearchHit.Relation.COMPLETE_UNIT,
                primaryEvidence(numberHit).relation());
        assertEquals(laterTwenty.name().lastIndexOf("20"),
                primaryEvidence(numberHit).matchStartInclusive());
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
    public void partNumberSuffixesFollowTheAuthoredRevisionGrammar() {
        final Machine mc700 = catalog.requireByUid("MI000339");
        assertPartNumberEvidence("MC700", mc700, "MC700*/A (2.3 GHz)", 5);
        assertPartNumberEvidence("MC700C", mc700, "MC700*/A (2.3 GHz)", 6);
        assertPartNumberEvidence("MC700CH", mc700, "MC700*/A (2.3 GHz)", 6);
        assertPartNumberEvidence("MC700CH/", mc700, "MC700*/A (2.3 GHz)", 7);
        assertPartNumberEvidence("MC700CH/A", mc700, "MC700*/A (2.3 GHz)", 8);

        final Machine multiRevision = catalog.requireByUid("MI000055");
        assertPartNumberEvidence("M2147LL/B", multiRevision, "M2147*/B", 8);

        for (String query : List.of(
                "MC700J", "MC700AB", "MC700J/", "MC700J/A",
                "MC700AB/A", "MC700XX/A", "MC700CH/A")) {
            final MachineCatalog.SearchResponse concrete =
                    catalog.search(query, MachineCatalog.SearchScope.NAME);
            assertEquals(query, MachineCatalog.SearchScope.ALL, concrete.scope());
            assertEquals(query, Collections.singletonList(mc700), concrete.hits().stream()
                    .map(SearchHit::machine).collect(Collectors.toList()));
            assertEquals(query, "MC700*/A (2.3 GHz)",
                    primaryEvidence(concrete.hits().get(0)).matchedValue());
        }
        for (String query : List.of("MC700LL/B", "MC700ABC", "MC700LL\\A",
                "MC700/A")) {
            assertTrue(query, catalog.search(query, MachineCatalog.SearchScope.ALL)
                    .hits().isEmpty());
        }

        final MachineCatalog.SearchResponse shared =
                catalog.search("MD212ZP/A", MachineCatalog.SearchScope.ALL);
        assertEquals(MachineCatalog.SearchScope.ALL, shared.scope());
        assertEquals(List.of("MI000348", "MI000349"), shared.hits().stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertTrue(shared.hits().stream()
                .allMatch(hit -> primaryEvidence(hit).field()
                        == SearchHit.Field.PART_NUMBER));

        final MachineCatalog.SearchResponse crossIdentifier =
                catalog.search("M9020", MachineCatalog.SearchScope.ALL);
        assertEquals(List.of("MI000034", "MI000166"), crossIdentifier.hits().stream()
                .map(hit -> hit.machine().uid()).collect(Collectors.toList()));
        assertEquals(List.of(SearchHit.Field.MODEL_NUMBER, SearchHit.Field.PART_NUMBER),
                crossIdentifier.hits().stream()
                        .map(hit -> primaryEvidence(hit).field())
                        .collect(Collectors.toList()));

    }

    private static boolean introducedIn(final Machine machine, final int year) {
        return machine.introductions().stream()
                .anyMatch(introduction -> introduction.year() == year);
    }

}
