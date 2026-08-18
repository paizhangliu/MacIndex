package com.macindex.macindex.catalog;

import static com.macindex.macindex.catalog.CatalogTestSupport.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.macindex.macindex.catalog.proto.CatalogMachine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Release-catalog integrity and browse contracts, separate from search algorithm fixtures. */
public final class CatalogContractTest {

    @BeforeClass
    public static void loadReleaseCatalog() throws Exception {
        CatalogTestSupport.loadCatalog();
    }

    @Test
    public void exposesStableUidObjectsOnly() {
        assertFalse(catalog.machines().isEmpty());
        assertEquals(payload.getMachinesCount(), catalog.machines().size());
        final Machine first = catalog.machines().get(0);
        assertEquals(first, catalog.findByUid(first.uid().toLowerCase()));
        assertEquals(first, catalog.requireByUid(first.uid()));
        assertNull(catalog.findByUid("MI999999"));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.requireByUid("MI999999"));
    }

    @Test
    public void resolvesEveryBuildValidatedLegacyName() throws Exception {
        final String json = Files.readString(
                Path.of(System.getProperty("macindex.catalog.legacyNames.path")),
                StandardCharsets.UTF_8);
        final JSONObject document = new JSONObject(json);
        assertEquals(1, document.getInt("schema"));
        final JSONArray names = document.getJSONArray("names");
        assertTrue(names.length() > 0);
        for (int index = 0; index < names.length(); index++) {
            final JSONObject identity = names.getJSONObject(index);
            final Machine resolved = catalog.resolveLegacyName(identity.getString("name"));
            assertNotNull(identity.getString("name"), resolved);
            assertEquals(identity.getString("uid"), resolved.uid());
        }
        assertEquals("MI000001", catalog.resolveLegacyName("  Macintosh 128K  ").uid());
        assertNull(catalog.resolveLegacyName("Macintosh"));
        assertNull(catalog.resolveLegacyName("not a legacy machine"));
    }

    @Test
    public void everyAuthoredSearchValueFindsItsMachineWithoutDuplicates() {
        for (CatalogMachine source : payload.getMachinesList()) {
            final Machine expected = catalog.requireByUid(source.getUid());
            assertSearchValuesFind(source.getNamesList(), expected);
            assertSearchValuesFind(source.getCodenamesList(), expected);
            assertSearchValuesFind(source.getModelNumbersList(), expected);
            assertSearchValuesFind(source.getIdentifiersList(), expected);
            assertSearchValuesFind(source.getGestaltIdsList(), expected);
            assertSearchValuesFind(source.getOrderNumbersList(), expected);
            assertSearchValuesFind(source.getEmcNumbersList(), expected);
        }
    }

    @Test
    public void browseSectionsFollowTheFirstVisibleGroupInEachScope() {
        final List<BrowseGroup> allProducts = catalog.browseGroups(
                BrowseScope.ALL, BrowseGrouping.NAMES);
        assertEquals("desktop", allProducts.get(0).sectionKey());
        assertEquals(3, allProducts.stream()
                .filter(group -> group.sectionKey() != null).count());

        final List<BrowseGroup> appleSiliconProducts = catalog.browseGroups(
                BrowseScope.APPLE_SILICON, BrowseGrouping.NAMES);
        assertFalse(appleSiliconProducts.isEmpty());
        assertEquals("desktop", appleSiliconProducts.get(0).sectionKey());
        assertEquals(2, appleSiliconProducts.stream()
                .filter(group -> group.sectionKey() != null).count());
        assertTrue(appleSiliconProducts.stream()
                .anyMatch(group -> "laptop".equals(group.sectionKey())));

        for (BrowseScope scope : BrowseScope.values()) {
            final List<BrowseGroup> processors = catalog.browseGroups(
                    scope, BrowseGrouping.PROCESSORS);
            assertTrue(processors.stream().noneMatch(group -> group.sectionKey() != null));
        }
    }

    @Test
    public void scopeCandidatesAreUniqueAndIndependentOfPresentationGrouping() {
        for (BrowseScope scope : BrowseScope.values()) {
            final List<Machine> candidates = catalog.scopeMachines(scope);
            final Set<String> candidateUids = new LinkedHashSet<>();
            candidates.forEach(machine -> candidateUids.add(machine.uid()));
            assertEquals(candidates.size(), candidateUids.size());
            if (scope != BrowseScope.ALL) {
                assertTrue(candidates.stream().allMatch(machine ->
                        machine.manufacturerKey().equals(expectedManufacturer(scope))));
            }
            final List<Machine> authoredOrder = catalog.machines().stream()
                    .filter(machine -> candidateUids.contains(machine.uid()))
                    .collect(Collectors.toList());
            assertEquals(authoredOrder, candidates);
            for (BrowseGrouping grouping : BrowseGrouping.values()) {
                final Set<String> groupedUids = new LinkedHashSet<>();
                catalog.browseGroups(scope, grouping).forEach(group ->
                        group.machines().forEach(machine -> groupedUids.add(machine.uid())));
                assertTrue(scope + "/" + grouping, candidateUids.containsAll(groupedUids));
            }
        }
        assertEquals(catalog.machines().size(), catalog.scopeMachines(BrowseScope.ALL).size());
    }

    private static String expectedManufacturer(final BrowseScope scope) {
        switch (scope) {
            case APPLE_68K:
                return "apple68k";
            case POWERPC:
                return "appleppc";
            case INTEL:
                return "appleintel";
            case APPLE_SILICON:
                return "applearm";
            default:
                throw new IllegalArgumentException("No single manufacturer for " + scope);
        }
    }

    @Test
    public void fixedNavigationSequenceUsesTheAuthoredProductBrowseOrder() {
        assertProductSequence("imac_normal");
        assertProductSequence("macbook_pro");
        assertProductSequence("mac_performa");
        assertProductSequence("powerbook_duo");
        assertProductSequence("workgroup_server");
        assertThrows(IllegalArgumentException.class,
                () -> catalog.sequenceForProductType("not_a_product"));
    }

    @Test
    public void rejectsDuplicateUidsWhileBuildingTheUidIndex() {
        assertThrows(CatalogFormatException.class, () -> CatalogLoader.load(
                payload.toBuilder().addMachines(payload.getMachines(0))
                        .build().toByteArray()));
    }

    @Test
    public void resultsAndCatalogAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> catalog.machines().clear());
        final List<SearchHit> results = search("mac");
        assertThrows(UnsupportedOperationException.class, results::clear);
        final MachineCatalog.SearchResponse response =
                catalog.search("2020", MachineCatalog.SearchScope.ALL);
        assertThrows(UnsupportedOperationException.class, response.hits()::clear);
        assertThrows(UnsupportedOperationException.class, response.facets()::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> response.hits().get(0).evidence().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.machines().get(0).processorFamilyKeys().clear());
    }
}
