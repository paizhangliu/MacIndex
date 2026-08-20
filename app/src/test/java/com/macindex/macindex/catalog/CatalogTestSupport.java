package com.macindex.macindex.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.macindex.macindex.catalog.proto.CatalogIdentity;
import com.macindex.macindex.catalog.proto.CatalogIntroduction;
import com.macindex.macindex.catalog.proto.CatalogBrowseDefinition;
import com.macindex.macindex.catalog.proto.CatalogBrowseGroup;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogPayload;
import com.macindex.macindex.catalog.proto.CatalogSearchDisplayMapping;
import com.macindex.macindex.catalog.proto.CatalogSearchField;
import com.macindex.macindex.catalog.proto.CatalogSearchValue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class CatalogTestSupport {

    static MachineCatalog catalog;
    static CatalogPayload payload;

    private CatalogTestSupport() {
    }

    static void loadCatalog() throws Exception {
        try (InputStream input = Files.newInputStream(
                Path.of(System.getProperty("macindex.catalog.path")))) {
            payload = CatalogPayload.parseFrom(input);
        }
        catalog = new MachineCatalog(payload);
    }

    static void assertSearchValuesFind(
            final List<CatalogIdentity> searchValues,
            final Machine expected) {
        for (CatalogIdentity entry : searchValues) {
            final String value = entry.getValue();
            final List<SearchHit> results = search(value);
            assertValidMatchRange(findHit(results, value, expected));
            assertEquals(results.size(), results.stream()
                    .map(result -> result.machine().uid()).distinct().count());
        }
    }

    static void assertDerivedSearchValuesFind(
            final List<CatalogSearchValue> searchValues,
            final Machine expected) {
        for (CatalogSearchValue entry : searchValues) {
            final String value = entry.getValue();
            final SearchHit.Field field;
            switch (entry.getField()) {
                case CATALOG_SEARCH_FIELD_NAME:
                    field = SearchHit.Field.NAME;
                    break;
                case CATALOG_SEARCH_FIELD_INTRODUCTION:
                    field = SearchHit.Field.INTRODUCTION;
                    break;
                case CATALOG_SEARCH_FIELD_PROCESSOR:
                    field = SearchHit.Field.PROCESSOR;
                    break;
                default:
                    throw new AssertionError("Unexpected derived search field");
            }
            final List<SearchHit> results = catalog.search(
                    value, MachineCatalog.SearchScope.forField(field)).hits();
            assertValidMatchRange(findHit(results, value, expected));
            assertEquals(results.size(), results.stream()
                    .map(result -> result.machine().uid()).distinct().count());
        }
    }

    static List<SearchHit> search(final String query) {
        return search(catalog, query);
    }

    static void assertPartNumberEvidence(
            final String query, final Machine machine, final String display,
            final int expectedMatchEnd) {
        final SearchHit hit = findHit(query, machine);
        final SearchHit.Evidence evidence = primaryEvidence(hit);
        assertEquals(SearchHit.Field.PART_NUMBER, evidence.field());
        assertEquals(display, evidence.matchedValue());
        assertEquals(0, evidence.matchStartInclusive());
        assertEquals(expectedMatchEnd, evidence.matchEndExclusive());
        assertValidMatchRange(hit);
    }

    static List<SearchHit> search(
            final MachineCatalog source, final String query) {
        return source.search(query, MachineCatalog.SearchScope.ALL).hits();
    }

    static List<Machine> searchMachines(final String query) {
        return search(query).stream().map(SearchHit::machine).collect(Collectors.toList());
    }

    static void assertHit(final String query, final String uid,
                                    final SearchHit.Relation relation,
                                    final SearchHit.Field field,
                                    final String matchedValue) {
        final SearchHit hit = findHit(query, catalog.requireByUid(uid));
        final SearchHit.Evidence evidence = primaryEvidence(hit);
        assertEquals(relation, evidence.relation());
        assertEquals(field, evidence.field());
        assertEquals(matchedValue, evidence.matchedValue());
        assertValidMatchRange(hit);
    }

    static void assertMatchRange(final String query, final String uid,
                                           final int start, final int end,
                                           final String expectedSubstring) {
        final SearchHit hit = findHit(query, catalog.requireByUid(uid));
        final SearchHit.Evidence evidence = primaryEvidence(hit);
        assertEquals(start, evidence.matchStartInclusive());
        assertEquals(end, evidence.matchEndExclusive());
        assertEquals(expectedSubstring, matchedSubstring(hit));
        assertValidMatchRange(hit);
    }

    static String matchedSubstring(final SearchHit hit) {
        final SearchHit.Evidence evidence = primaryEvidence(hit);
        return evidence.matchedValue().substring(
                evidence.matchStartInclusive(), evidence.matchEndExclusive());
    }

    static void assertValidMatchRange(final SearchHit hit) {
        final SearchHit.Evidence evidence = primaryEvidence(hit);
        assertTrue(evidence.matchStartInclusive() >= 0);
        assertTrue(evidence.matchStartInclusive() < evidence.matchEndExclusive());
        assertTrue(evidence.matchEndExclusive() <= evidence.matchedValue().length());
        assertFalse(Character.isLowSurrogate(
                evidence.matchedValue().charAt(evidence.matchStartInclusive())));
        assertFalse(evidence.matchEndExclusive() < evidence.matchedValue().length()
                && Character.isLowSurrogate(
                evidence.matchedValue().charAt(evidence.matchEndExclusive())));
    }

    static SearchHit.Evidence primaryEvidence(final SearchHit hit) {
        return hit.evidence().get(0);
    }

    static SearchHit findHit(final String query, final Machine machine) {
        return findHit(search(query), query, machine);
    }

    static SearchHit findHit(final List<SearchHit> hits, final String query,
                                       final Machine machine) {
        return hits.stream()
                .filter(hit -> hit.machine() == machine)
                .findFirst()
                .orElseThrow(() -> new AssertionError(machine.uid() + ": " + query));
    }

    static List<String> searchUids(
            final MachineCatalog source, final String query) {
        return search(source, query).stream().map(hit -> hit.machine().uid())
                .collect(Collectors.toList());
    }

    static int facetCount(final MachineCatalog.SearchResponse response,
                                    final SearchHit.Field field) {
        return response.facets().stream()
                .filter(facet -> facet.field() == field)
                .mapToInt(MachineCatalog.Facet::count)
                .findFirst().orElse(0);
    }

    static void assertFacetCountsMatchScopes(
            final MachineCatalog source, final String query,
            final MachineCatalog.SearchResponse response) {
        for (MachineCatalog.Facet facet : response.facets()) {
            assertEquals(facet.field().name(), facet.count(), source.search(
                    query, MachineCatalog.SearchScope.forField(facet.field())).hits().size());
        }
    }

    static CatalogMachine tiedCodenameFixture(
            final String uid, final String name, final int year) {
        return searchFixture(uid, year)
                .addNames(identity(name))
                .addCodenames(identity("Needle"))
                .build();
    }

    static CatalogMachine.Builder searchFixture(
            final String uid, final int year) {
        return payload.getMachines(0).toBuilder()
                .setUid(uid)
                .clearIntroductions()
                .addIntroductions(CatalogIntroduction.newBuilder()
                        .setYear(year)
                        .setMonth(1))
                .clearDerivedSearchValues()
                .addDerivedSearchValues(CatalogSearchValue.newBuilder()
                        .setValue(Integer.toString(year))
                        .setField(CatalogSearchField.CATALOG_SEARCH_FIELD_INTRODUCTION)
                        .setExactTokenOnly(true)
                        .setDisplayMapping(CatalogSearchDisplayMapping
                                .CATALOG_SEARCH_DISPLAY_MAPPING_DIRECT))
                .clearNames()
                .clearCodenames()
                .clearModelNumbers()
                .clearIdentifiers()
                .clearGestaltIds()
                .clearOrderNumbers()
                .clearEmcNumbers();
    }

    static CatalogIdentity identity(final String value) {
        return CatalogIdentity.newBuilder().setValue(value).build();
    }

    static MachineCatalog catalogOf(final CatalogMachine... machines) {
        final CatalogPayload.Builder fixture = payload.toBuilder()
                .clearMachines()
                .clearRetiredMachines()
                .clearBrowseDefinitions();
        for (CatalogBrowseDefinition definition : payload.getBrowseDefinitionsList()) {
            final CatalogBrowseDefinition.Builder cleanDefinition = definition.toBuilder()
                    .clearGroups();
            for (CatalogBrowseGroup group : definition.getGroupsList()) {
                cleanDefinition.addGroups(group.toBuilder().clearMachineUids());
            }
            fixture.addBrowseDefinitions(cleanDefinition);
        }
        for (CatalogMachine machine : machines) {
            fixture.addMachines(machine);
        }
        return new MachineCatalog(fixture.build());
    }

    static void assertProductSequence(final String productTypeKey) {
        final List<Machine> sequence = catalog.sequenceForProductType(productTypeKey);
        final BrowseGroup group = catalog.browseGroups(BrowseScope.ALL, BrowseGrouping.NAMES)
                .stream()
                .filter(candidate -> candidate.key().equals(productTypeKey))
                .findFirst().orElseThrow();
        assertEquals(group.machines(), sequence);
        assertFalse(sequence.isEmpty());
        for (int index = 1; index < sequence.size(); index++) {
            assertTrue(sequence.get(index - 1).introductionSortKey()
                    <= sequence.get(index).introductionSortKey());
        }
    }
}
