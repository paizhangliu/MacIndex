package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.macindex.macindex.catalog.CatalogLoader;
import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.catalog.MachineCatalog.Facet;
import com.macindex.macindex.catalog.MachineCatalog.SearchResponse;
import com.macindex.macindex.catalog.MachineCatalog.SearchScope;
import com.macindex.macindex.catalog.SearchHit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Device-level search interaction, row rendering, and text-layout contracts. */
@RunWith(AndroidJUnit4.class)
public final class SearchUiInstrumentationTest {

    @Test
    public void unifiedSearchCopyNamesAllSupportedInputs() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final Configuration englishConfiguration = new Configuration(
                context.getResources().getConfiguration());
        englishConfiguration.setLocale(Locale.US);
        final Context english = context.createConfigurationContext(englishConfiguration);
        assertEquals("Machine name, codename, or identifiers",
                english.getString(R.string.search_hint));
        assertEquals("No results for “Kanga”",
                english.getString(R.string.search_noResult, "Kanga"));
        assertEquals("Tap the Search key to search",
                english.getString(R.string.search_prompt));
        assertEquals("Loading…", english.getString(R.string.search_loading));
        assertEquals("Codename: ", english.getString(
                R.string.search_result_field_prefix,
                english.getString(R.string.search_field_codename)));
        assertEquals("Alias: ", english.getString(
                R.string.search_result_field_prefix,
                english.getString(R.string.search_field_alias)));
        assertEquals("All (9)", english.getString(
                R.string.search_facet_with_count,
                english.getString(R.string.search_facet_all), 9));

        final Configuration chineseConfiguration = new Configuration(
                context.getResources().getConfiguration());
        chineseConfiguration.setLocale(Locale.SIMPLIFIED_CHINESE);
        final Context chinese = context.createConfigurationContext(chineseConfiguration);
        assertEquals("机型名称、开发代号或标识符",
                chinese.getString(R.string.search_hint));
        assertEquals("没有找到“Kanga”",
                chinese.getString(R.string.search_noResult, "Kanga"));
        assertEquals("开发代号：", chinese.getString(
                R.string.search_result_field_prefix,
                chinese.getString(R.string.search_field_codename)));
        assertEquals("别名：", chinese.getString(
                R.string.search_result_field_prefix,
                chinese.getString(R.string.search_field_alias)));
        assertEquals("点击键盘上的搜索键开始搜索",
                chinese.getString(R.string.search_prompt));
        assertEquals("正在加载…", chinese.getString(R.string.search_loading));
        assertEquals("全部（9）", chinese.getString(
                R.string.search_facet_with_count,
                chinese.getString(R.string.search_facet_all), 9));
    }

    @Test
    public void searchResultRowsHighlightVisibleEvidenceAndRebindRecycledContent()
            throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Configuration englishConfiguration = new Configuration(
                context.getResources().getConfiguration());
        englishConfiguration.setLocale(Locale.US);
        final Context themedContext = new ContextThemeWrapper(
                context.createConfigurationContext(englishConfiguration), R.style.AppTheme);
        final MachineCatalog catalog = CatalogLoader.load(context.getAssets());

        final SearchHit titleHit = findHitByName(
                allSearch(catalog, "Macintosh 145B"),
                "Macintosh PowerBook 145, 145B");
        assertTrue(countEvidence(titleHit, SearchHit.Field.NAME,
                titleHit.machine().name()) >= 2);
        final Set<String> favourites = Collections.singleton(titleHit.machine().uid());
        final AtomicReference<Machine> selected = new AtomicReference<>();
        final SearchResultAdapter titleAdapter = new SearchResultAdapter(
                Collections.singletonList(titleHit), favourites, themedContext, selected::set);
        assertEquals(1, titleAdapter.getCount());
        assertEquals(titleHit.machine(), titleAdapter.navigationMachines().get(0));
        assertEquals(1, titleAdapter.getViewTypeCount());
        assertTrue(titleAdapter.areAllItemsEnabled());
        final ListView parent = new ListView(themedContext);
        final View titleRow = titleAdapter.getView(0, null, parent);
        final TextView name = titleRow.findViewById(R.id.machineRowName);
        final TextView detail = titleRow.findViewById(R.id.machineRowSecondary);
        assertEquals(titleHit.machine().name(), name.getText().toString());
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertNotNull(name.getCompoundDrawablesRelative()[2]);
        assertNoBoldSpans(name);
        final String namePrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_name));
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(namePrefix + titleHit.machine().name(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, namePrefix.length(), titleHit.evidence(),
                SearchHit.Field.NAME, titleHit.machine().name());
        assertTrue(titleRow.getContentDescription().toString().contains("Bookmarked"));

        final SearchHit codenameHit = allSearch(catalog, "Kanga").get(0);
        final SearchResultAdapter codenameAdapter = new SearchResultAdapter(
                Collections.singletonList(codenameHit), Collections.emptySet(),
                themedContext, selected::set);
        final View reusedResult = codenameAdapter.getView(0, titleRow, parent);
        assertTrue(reusedResult == titleRow);
        assertEquals(codenameHit.machine().name(), name.getText().toString());
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertNoBoldSpans(name);
        assertNull(name.getCompoundDrawablesRelative()[2]);
        final String codenamePrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_codename));
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(codenamePrefix + codenameHit.matchedValue(),
                detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, codenamePrefix.length(),
                codenameHit.evidence(), codenameHit.field(), codenameHit.matchedValue());
        assertFalse(detail.getText().toString().contains(
                codenameHit.machine().introductionDisplayText()));
        assertFalse(reusedResult.getContentDescription().toString().contains("Bookmarked"));
        reusedResult.performClick();
        assertEquals(codenameHit.machine(), selected.get());

        final SearchHit crossFieldHit = allSearch(catalog, "PowerBook G3 Kanga").get(0);
        assertEquals(3, crossFieldHit.evidence().size());
        final SearchResultAdapter crossFieldAdapter = new SearchResultAdapter(
                Collections.singletonList(crossFieldHit), Collections.emptySet(),
                themedContext, unused -> { });
        crossFieldAdapter.getView(0, reusedResult, parent);
        assertEquals("PowerBook G3", name.getText().toString());
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertNoBoldSpans(name);
        final String crossFieldNameLine = namePrefix + "PowerBook G3";
        final String crossFieldExplanation = crossFieldNameLine + "\n"
                + codenamePrefix + "Kanga";
        assertEquals(crossFieldExplanation, detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, crossFieldHit.evidence(),
                new EvidenceExpectation(namePrefix.length(),
                        SearchHit.Field.NAME, "PowerBook G3"),
                new EvidenceExpectation(crossFieldNameLine.length() + 1
                        + codenamePrefix.length(),
                        SearchHit.Field.CODENAME, "Kanga"));

        final SearchHit aliasHit = allSearch(catalog, "Macintosh PowerBook 5300c").get(0);
        assertEquals(SearchHit.Field.NAME, aliasHit.field());
        assertFalse(aliasHit.machine().name().equals(aliasHit.matchedValue()));
        final SearchResultAdapter aliasAdapter = new SearchResultAdapter(
                Collections.singletonList(aliasHit), Collections.emptySet(),
                themedContext, unused -> { });
        aliasAdapter.getView(0, reusedResult, parent);
        final String aliasPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_alias));
        assertNoBoldSpans(name);
        assertEquals(aliasPrefix + aliasHit.matchedValue(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, aliasPrefix.length(), aliasHit.evidence(),
                SearchHit.Field.NAME, aliasHit.matchedValue());

        final SearchHit emcHit = catalog.search(
                "2020", SearchScope.EMC_NUMBER).hits().get(0);
        final SearchResultAdapter emcAdapter = new SearchResultAdapter(
                Collections.singletonList(emcHit), Collections.emptySet(),
                themedContext, unused -> { });
        emcAdapter.getView(0, reusedResult, parent);
        final String emcPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_emc_number));
        assertEquals(emcPrefix + emcHit.matchedValue(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, emcPrefix.length(), emcHit.evidence(),
                SearchHit.Field.EMC_NUMBER, emcHit.matchedValue());

        final SearchResponse partNumberStem = catalog.search("M5994", SearchScope.ALL);
        assertEquals(SearchScope.ALL, partNumberStem.scope());
        assertEquals(1, partNumberStem.hits().size());
        final SearchHit partNumberHit = partNumberStem.hits().get(0);
        assertEquals(SearchHit.Field.PART_NUMBER, partNumberHit.field());
        assertEquals("M5994*/A", partNumberHit.matchedValue());
        final SearchResponse completePartNumber =
                catalog.search("M5994LL/A", SearchScope.NAME);
        assertEquals(SearchScope.ALL, completePartNumber.scope());
        assertEquals(1, completePartNumber.hits().size());
        assertEquals(SearchHit.Field.PART_NUMBER, completePartNumber.hits().get(0).field());
        assertEquals(partNumberHit.machine(), completePartNumber.hits().get(0).machine());
        final SearchResultAdapter partNumberAdapter = new SearchResultAdapter(
                Collections.singletonList(partNumberHit), Collections.emptySet(),
                themedContext, unused -> { });
        partNumberAdapter.getView(0, reusedResult, parent);
        final String partNumberPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_part_number));
        assertEquals(partNumberPrefix + "M5994*/A", detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, partNumberPrefix.length(),
                partNumberHit.evidence(), SearchHit.Field.PART_NUMBER,
                partNumberHit.matchedValue());

        final SearchHit aliasAndPartNumber = allSearch(catalog, "TAM M3459").get(0);
        final SearchResultAdapter aliasAndPartAdapter = new SearchResultAdapter(
                Collections.singletonList(aliasAndPartNumber), Collections.emptySet(),
                themedContext, unused -> { });
        aliasAndPartAdapter.getView(0, reusedResult, parent);
        final String aliasLine = aliasPrefix + "TAM";
        final String aliasAndPartExplanation = aliasLine + "\n"
                + partNumberPrefix + "M3459*/A";
        assertNoBoldSpans(name);
        assertEquals(aliasAndPartExplanation, detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, aliasAndPartNumber.evidence(),
                new EvidenceExpectation(aliasPrefix.length(),
                        SearchHit.Field.NAME, "TAM"),
                new EvidenceExpectation(aliasLine.length() + 1
                        + partNumberPrefix.length(),
                        SearchHit.Field.PART_NUMBER, "M3459*/A"));

        final SearchHit configuredIdentity = allSearch(catalog, "J185 iMac20,1").get(0);
        assertEquals(2, configuredIdentity.evidence().size());
        final SearchResultAdapter configuredIdentityAdapter = new SearchResultAdapter(
                Collections.singletonList(configuredIdentity), Collections.emptySet(),
                themedContext, unused -> { });
        configuredIdentityAdapter.getView(0, reusedResult, parent);
        final String configuredCodenameValue = "J185 (iMac20,1)";
        final String modelIdentifierValue = "iMac20,1";
        final String modelIdentifierPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_model_identifier));
        final String configuredFirstLine = codenamePrefix + configuredCodenameValue;
        final String configuredExplanation = configuredFirstLine + "\n"
                + modelIdentifierPrefix + modelIdentifierValue;
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(Integer.MAX_VALUE, detail.getMaxLines());
        assertNoBoldSpans(name);
        assertEquals(configuredExplanation, detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, configuredIdentity.evidence(),
                new EvidenceExpectation(codenamePrefix.length(),
                        SearchHit.Field.CODENAME, configuredCodenameValue),
                new EvidenceExpectation(configuredFirstLine.length() + 1
                        + modelIdentifierPrefix.length(),
                        SearchHit.Field.MODEL_IDENTIFIER, modelIdentifierValue));
        assertEquals(themedContext.getString(R.string.machine_row_accessibility,
                        configuredIdentity.machine().name(), configuredExplanation).trim(),
                reusedResult.getContentDescription().toString());
        assertFalse(reusedResult.getContentDescription().toString().contains(
                partNumberPrefix));

        titleAdapter.getView(0, reusedResult, parent);
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertNoBoldSpans(name);
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(namePrefix + titleHit.machine().name(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, namePrefix.length(), titleHit.evidence(),
                SearchHit.Field.NAME, titleHit.machine().name());
        assertFalse(reusedResult.getContentDescription().toString().contains("J185"));
        assertFalse(reusedResult.getContentDescription().toString().contains("iMac20,1"));
    }

    private static SearchHit findHitByName(final List<SearchHit> hits, final String name) {
        for (SearchHit hit : hits) {
            if (name.equals(hit.machine().name())) {
                return hit;
            }
        }
        throw new AssertionError("Missing search hit " + name);
    }

    private static int countEvidence(final SearchHit hit,
                                     final SearchHit.Field field,
                                     final String matchedValue) {
        int count = 0;
        for (SearchHit.Evidence evidence : hit.evidence()) {
            if (evidence.field() == field && matchedValue.equals(evidence.matchedValue())) {
                count++;
            }
        }
        return count;
    }

    private static void assertOnlyEvidenceRangesBold(
            final TextView textView,
            final int valueStart,
            final List<SearchHit.Evidence> evidenceList,
            final SearchHit.Field field,
            final String matchedValue) {
        assertOnlyEvidenceRangesBold(textView, evidenceList,
                new EvidenceExpectation(valueStart, field, matchedValue));
    }

    private static void assertOnlyEvidenceRangesBold(
            final TextView textView,
            final List<SearchHit.Evidence> evidenceList,
            final EvidenceExpectation... expectations) {
        assertTrue(textView.getText() instanceof Spanned);
        final Spanned styled = (Spanned) textView.getText();
        final boolean[] expectedBold = new boolean[styled.length()];
        for (EvidenceExpectation expectation : expectations) {
            boolean foundEvidence = false;
            for (SearchHit.Evidence evidence : evidenceList) {
                if (evidence.field() == expectation.field
                        && expectation.matchedValue.equals(evidence.matchedValue())) {
                    foundEvidence = true;
                    for (int index = expectation.valueStart
                            + evidence.matchStartInclusive();
                         index < expectation.valueStart
                                 + evidence.matchEndExclusive(); index++) {
                        expectedBold[index] = true;
                    }
                }
            }
            assertTrue("Missing displayed evidence " + expectation.field
                    + ": " + expectation.matchedValue, foundEvidence);
        }
        final boolean[] actualBold = new boolean[styled.length()];
        final StyleSpan[] spans = styled.getSpans(0, styled.length(), StyleSpan.class);
        assertTrue(spans.length > 0);
        for (StyleSpan span : spans) {
            assertEquals(Typeface.BOLD, span.getStyle());
            for (int index = styled.getSpanStart(span);
                 index < styled.getSpanEnd(span); index++) {
                actualBold[index] = true;
            }
        }
        for (int index = 0; index < styled.length(); index++) {
            assertEquals("Unexpected bold state at " + index,
                    expectedBold[index], actualBold[index]);
        }
    }

    private static final class EvidenceExpectation {
        private final int valueStart;
        private final SearchHit.Field field;
        private final String matchedValue;

        private EvidenceExpectation(final int sourceValueStart,
                                    final SearchHit.Field sourceField,
                                    final String sourceMatchedValue) {
            valueStart = sourceValueStart;
            field = sourceField;
            matchedValue = sourceMatchedValue;
        }
    }

    private static void assertNoBoldSpans(final TextView textView) {
        if (!(textView.getText() instanceof Spanned)) {
            return;
        }
        final Spanned styled = (Spanned) textView.getText();
        assertEquals(0, styled.getSpans(0, styled.length(), StyleSpan.class).length);
    }

    @Test
    public void searchFacetsRefineOnlyTheCurrentQueryAndNeverAutoOpen() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final MachineCatalog catalog = CatalogLoader.load(context.getAssets());

        final SearchResponse yearResults = catalog.search("2020", SearchScope.ALL);
        assertEquals(SearchScope.ALL, yearResults.scope());
        assertEquals(9, yearResults.allCount());
        assertEquals(2, yearResults.facets().size());
        final Facet yearName = requireFacet(yearResults, SearchHit.Field.NAME);
        final Facet yearEmc = requireFacet(yearResults, SearchHit.Field.EMC_NUMBER);
        assertEquals(8, yearName.count());
        assertEquals(1, yearEmc.count());
        final SearchResponse m2Results = catalog.search("M2", SearchScope.ALL);
        assertTrue(m2Results.facets().size() > 2);
        final Facet m2Name = requireFacet(m2Results, SearchHit.Field.NAME);
        final Facet m2Codename = requireFacet(m2Results, SearchHit.Field.CODENAME);
        final Facet m2PartNumber = requireFacet(
                m2Results, SearchHit.Field.PART_NUMBER);

        final Instrumentation.ActivityMonitor specsMonitor = instrumentation.addMonitor(
                SpecsActivity.class.getName(), null, true);
        SearchActivity activity = null;
        try {
            final Intent intent = new Intent(context, SearchActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (SearchActivity) instrumentation.startActivitySync(intent);
            final SearchActivity launchedActivity = activity;
            waitForUiCondition(instrumentation, "Search screen did not become ready", () ->
                    launchedActivity.findViewById(R.id.searchInput).isEnabled());

            submitQuery(instrumentation, activity, "2020", yearResults.allCount());
            assertFacetUi(instrumentation, activity, yearResults, true);
            assertFacetChip(instrumentation, activity, R.id.searchFacetName,
                    R.string.search_field_name, yearName.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetEmcNumber,
                    R.string.search_field_emc_number, yearEmc.count());

            clickFacet(instrumentation, activity, R.id.searchFacetName, yearName.count());
            assertCheckedFacet(instrumentation, activity, R.id.searchFacetName);
            clickFacet(instrumentation, activity, R.id.searchFacetEmcNumber, yearEmc.count());
            assertCheckedFacet(instrumentation, activity, R.id.searchFacetEmcNumber);

            setQuery(instrumentation, activity, "M2", false);
            assertSearchIsCleared(instrumentation, activity);
            submitQuery(instrumentation, activity, "M2", m2Results.allCount());
            assertFacetUi(instrumentation, activity, m2Results, true);
            assertFacetChip(instrumentation, activity, R.id.searchFacetName,
                    R.string.search_field_name, m2Name.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetCodename,
                    R.string.search_field_codename, m2Codename.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetPartNumber,
                    R.string.search_field_part_number, m2PartNumber.count());
            assertViewIsHidden(instrumentation, activity, R.id.searchFacetEmcNumber);

            clickFacet(instrumentation, activity, R.id.searchFacetCodename,
                    m2Codename.count());
            assertCheckedFacet(instrumentation, activity, R.id.searchFacetCodename);

            final AtomicReference<SearchActivity> recreatedReference = new AtomicReference<>();
            instrumentation.runOnMainSync(launchedActivity::recreate);
            waitForUiCondition(instrumentation, "Search screen did not recreate", () -> {
                for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (candidate instanceof SearchActivity && candidate != launchedActivity) {
                        recreatedReference.set((SearchActivity) candidate);
                        return true;
                    }
                }
                return false;
            });
            final SearchActivity recreatedActivity = recreatedReference.get();
            assertNotNull("Search screen did not recreate", recreatedActivity);
            activity = recreatedActivity;
            final SearchActivity activeSearch = recreatedActivity;
            waitForUiCondition(instrumentation,
                    "Recreation did not preserve the temporary Codename facet", () -> {
                final RadioButton codename = activeSearch.findViewById(
                        R.id.searchFacetCodename);
                final ListView results = activeSearch.findViewById(R.id.resultList);
                return codename != null && codename.isChecked()
                        && results.getAdapter() != null
                        && results.getAdapter().getCount() == m2Codename.count();
            });
            assertCheckedFacet(instrumentation, activeSearch, R.id.searchFacetCodename);
            assertFacetChip(instrumentation, activeSearch, R.id.searchFacetAll,
                    R.string.search_facet_all, m2Results.allCount());
            assertFacetChip(instrumentation, activeSearch, R.id.searchFacetName,
                    R.string.search_field_name, m2Name.count());
            assertFacetChip(instrumentation, activeSearch, R.id.searchFacetCodename,
                    R.string.search_field_codename, m2Codename.count());

            final Instrumentation.ActivityMonitor aboutMonitor = instrumentation.addMonitor(
                    NewAboutActivity.class.getName(), null, false);
            NewAboutActivity aboutActivity = null;
            try {
                instrumentation.runOnMainSync(() -> activeSearch.startActivity(
                        new Intent(activeSearch, NewAboutActivity.class)));
                aboutActivity = (NewAboutActivity) aboutMonitor.waitForActivityWithTimeout(5000);
                assertNotNull("About screen did not open", aboutActivity);
                final NewAboutActivity openedAbout = aboutActivity;
                waitForUiCondition(instrumentation,
                        "About screen did not become resumed", () ->
                                openedAbout.getLifecycle().getCurrentState()
                                        .isAtLeast(Lifecycle.State.RESUMED));
                instrumentation.runOnMainSync(openedAbout::finish);
                waitForUiCondition(instrumentation,
                        "Unrelated navigation changed the temporary facet", () -> {
                    final RadioButton codename = activeSearch.findViewById(
                            R.id.searchFacetCodename);
                    final ListView results = activeSearch.findViewById(R.id.resultList);
                    return codename != null && codename.isChecked()
                            && results.getAdapter() != null
                            && results.getAdapter().getCount() == m2Codename.count();
                });
            } finally {
                if (aboutActivity != null && !aboutActivity.isFinishing()) {
                    final NewAboutActivity openedAbout = aboutActivity;
                    instrumentation.runOnMainSync(openedAbout::finish);
                }
                instrumentation.removeMonitor(aboutMonitor);
            }

            instrumentation.runOnMainSync(() -> {
                final ListView results = activeSearch.findViewById(R.id.resultList);
                final View firstResult = results.getAdapter().getView(0, null, results);
                assertNotNull("Filtered search did not render its first row", firstResult);
                firstResult.performClick();
            });
            waitForUiCondition(instrumentation,
                    "Opening a machine changed the temporary facet", () -> {
                final RadioButton codename = activeSearch.findViewById(
                        R.id.searchFacetCodename);
                final ListView results = activeSearch.findViewById(R.id.resultList);
                return codename != null && codename.isChecked()
                        && results.getAdapter() != null
                        && results.getAdapter().getCount() == m2Codename.count();
            });
            assertEquals("Machine row did not open Specs", 1, specsMonitor.getHits());
            assertCheckedFacet(instrumentation, activeSearch, R.id.searchFacetCodename);

            // Editing a filtered query immediately returns the next submission to All.
            submitQuery(instrumentation, activeSearch, "2020", yearResults.allCount());
            assertFacetUi(instrumentation, activeSearch, yearResults, true);

            setQuery(instrumentation, activeSearch, "", false);
            assertSearchIsCleared(instrumentation, activeSearch);

            final List<SearchHit> kangaResults = allSearch(catalog, "Kanga");
            assertEquals(1, kangaResults.size());
            submitQuery(instrumentation, activeSearch, "Kanga", 1);
            instrumentation.waitForIdleSync();
            SystemClock.sleep(100);
            instrumentation.waitForIdleSync();
            assertEquals("A unique result must remain on the search screen",
                    1, specsMonitor.getHits());
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, activeSearch.findViewById(
                        R.id.searchFacetContainer).getVisibility());
                assertEquals(1, ((ListView) activeSearch.findViewById(
                        R.id.resultList)).getAdapter().getCount());
            });
        } finally {
            instrumentation.removeMonitor(specsMonitor);
            if (activity != null) {
                final SearchActivity launchedActivity = activity;
                instrumentation.runOnMainSync(launchedActivity::finish);
                instrumentation.waitForIdleSync();
            }
        }
    }

    private static Facet requireFacet(final SearchResponse response,
                                      final SearchHit.Field field) {
        for (Facet facet : response.facets()) {
            if (facet.field() == field) {
                return facet;
            }
        }
        throw new AssertionError("Missing search facet " + field);
    }

    private static void setQuery(final Instrumentation instrumentation,
                                 final SearchActivity activity,
                                 final String query,
                                 final boolean submit) {
        instrumentation.runOnMainSync(() -> ((SearchView) activity.findViewById(
                R.id.searchInput)).setQuery(query, submit));
        instrumentation.waitForIdleSync();
    }

    private static void submitQuery(final Instrumentation instrumentation,
                                    final SearchActivity activity,
                                    final String query,
                                    final int expectedCount) {
        setQuery(instrumentation, activity, query, true);
        waitForUiCondition(instrumentation, "Search did not return " + expectedCount
                + " results for " + query, () -> {
            final ListView results = activity.findViewById(R.id.resultList);
            return results.getAdapter() != null
                    && results.getAdapter().getCount() == expectedCount;
        });
    }

    private static void clickFacet(final Instrumentation instrumentation,
                                   final SearchActivity activity,
                                   final int facetId,
                                   final int expectedCount) {
        instrumentation.runOnMainSync(() -> {
            final RadioButton facet = activity.findViewById(facetId);
            assertNotNull(facet);
            // CompoundButton toggles even when View.performClick() reports no explicit
            // OnClickListener. The observable contract is the checked state and result count.
            facet.performClick();
        });
        waitForUiCondition(instrumentation, "Facet did not return " + expectedCount
                + " results", () -> {
            final ListView results = activity.findViewById(R.id.resultList);
            return results.getAdapter() != null
                    && results.getAdapter().getCount() == expectedCount
                    && ((RadioButton) activity.findViewById(facetId)).isChecked();
        });
    }

    private static void assertFacetUi(final Instrumentation instrumentation,
                                      final SearchActivity activity,
                                      final SearchResponse response,
                                      final boolean allChecked) {
        instrumentation.runOnMainSync(() -> {
            final HorizontalScrollView container = activity.findViewById(
                    R.id.searchFacetContainer);
            final RadioGroup group = activity.findViewById(R.id.searchFacetGroup);
            assertEquals(View.VISIBLE, container.getVisibility());
            int visibleFacetCount = 0;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (group.getChildAt(index).getVisibility() == View.VISIBLE) {
                    visibleFacetCount++;
                }
            }
            assertEquals(response.facets().size() + 1, visibleFacetCount);
            final RadioButton all = activity.findViewById(R.id.searchFacetAll);
            assertEquals(activity.getString(R.string.search_facet_with_count,
                            activity.getString(R.string.search_facet_all),
                            response.allCount()),
                    all.getText().toString());
            assertEquals(allChecked, all.isChecked());
            assertEquals(response.allCount(),
                    ((ListView) activity.findViewById(R.id.resultList))
                            .getAdapter().getCount());
        });
    }

    private static void assertFacetChip(final Instrumentation instrumentation,
                                        final SearchActivity activity,
                                        final int facetId,
                                        final int fieldLabel,
                                        final int count) {
        instrumentation.runOnMainSync(() -> {
            final RadioButton chip = activity.findViewById(facetId);
            assertNotNull(chip);
            assertEquals(View.VISIBLE, chip.getVisibility());
            assertEquals(activity.getString(R.string.search_facet_with_count,
                            activity.getString(fieldLabel), count),
                    chip.getText().toString());
        });
    }

    private static void assertCheckedFacet(final Instrumentation instrumentation,
                                           final SearchActivity activity,
                                           final int facetId) {
        instrumentation.runOnMainSync(() -> {
            final RadioGroup group = activity.findViewById(R.id.searchFacetGroup);
            assertEquals(facetId, group.getCheckedRadioButtonId());
            assertTrue(((RadioButton) activity.findViewById(facetId)).isChecked());
        });
    }

    private static void assertViewIsHidden(final Instrumentation instrumentation,
                                           final SearchActivity activity,
                                           final int viewId) {
        instrumentation.runOnMainSync(() -> assertEquals(View.GONE,
                activity.findViewById(viewId).getVisibility()));
    }

    private static void assertSearchIsCleared(final Instrumentation instrumentation,
                                              final SearchActivity activity) {
        instrumentation.runOnMainSync(() -> {
            assertEquals(View.GONE, activity.findViewById(
                    R.id.searchFacetContainer).getVisibility());
            assertNull(((ListView) activity.findViewById(R.id.resultList)).getAdapter());
        });
    }

    private static void waitForUiCondition(final Instrumentation instrumentation,
                                           final String failureMessage,
                                           final UiCondition condition) {
        final long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10);
        final AtomicReference<Boolean> satisfied = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> satisfied.set(condition.isSatisfied()));
            if (satisfied.get()) {
                return;
            }
            SystemClock.sleep(25);
        }
        assertTrue(failureMessage, false);
    }

    @FunctionalInterface
    private interface UiCondition {
        boolean isSatisfied();
    }

    @Test
    public void specificationLayoutsPreserveFullFontMetrics() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Configuration largeFont = new Configuration(
                context.getResources().getConfiguration());
        largeFont.fontScale = 1.3f;
        final Context largeFontContext = context.createConfigurationContext(largeFont);
        final Context themedContext = new ContextThemeWrapper(
                largeFontContext, R.style.AppTheme);
        final View specs = LayoutInflater.from(themedContext)
                .inflate(R.layout.activity_specs, null, false);

        final TextView name = specs.findViewById(R.id.nameText);
        assertTrue(name.getIncludeFontPadding());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, name.getLayoutParams().height);
        assertEquals(Math.round(30 * largeFontContext.getResources()
                        .getDisplayMetrics().density),
                name.getMinHeight());

        final int[] rows = {R.id.basicInfoLayout, R.id.processorTypeImageLayout,
                R.id.idLayout, R.id.gestaltLayout, R.id.codenameLayout,
                R.id.graphicsLayout, R.id.typeLayout, R.id.expansionLayout,
                R.id.designLayout, R.id.supportLayout, R.id.commentLayout};
        for (int row : rows) {
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
                    specs.findViewById(row).getLayoutParams().height);
        }

        final TextView graphicsTitle = specs.findViewById(R.id.graphicsTitle);
        final TextView graphicsText = specs.findViewById(R.id.graphicsText);
        final TextView codenameTitle = specs.findViewById(R.id.codenameTitle);
        final TextView codenameText = specs.findViewById(R.id.codenameText);
        assertTrue(graphicsTitle.getIncludeFontPadding());
        assertTrue(graphicsText.getIncludeFontPadding());
        assertTrue(codenameTitle.getIncludeFontPadding());
        assertTrue(codenameText.getIncludeFontPadding());
        assertTextFits(name, "Desktop gyp", 320, largeFontContext);
        assertTextFits(codenameTitle, "Codename", 130, largeFontContext);
        assertTextFits(codenameText,
                "PowerStar\nHacksaw\nInstaTower\nAlchemy gyp",
                220, largeFontContext);
        assertTextFits(graphicsTitle, "Graphics", 130, largeFontContext);
        assertTextFits(graphicsText, "Graphics gyp", 220, largeFontContext);

        final View compareRow = LayoutInflater.from(themedContext)
                .inflate(R.layout.chunk_compare_row, null, false);
        final TextView compareTitle = compareRow.findViewById(R.id.compareTitle);
        final TextView compareLeft = compareRow.findViewById(R.id.compareLeft);
        final TextView compareRight = compareRow.findViewById(R.id.compareRight);
        assertTrue(compareTitle.getIncludeFontPadding());
        assertTrue(compareLeft.getIncludeFontPadding());
        assertTrue(compareRight.getIncludeFontPadding());
        assertTextFits(compareTitle, "Graphics", 320, largeFontContext);
        assertTextFits(compareLeft, "Graphics gyp", 150, largeFontContext);
        assertTextFits(compareRight, "Design gyp", 150, largeFontContext);

        final ListView machineRowParent = new ListView(themedContext);
        final View machineRow = MachineRowBinder.inflate(
                LayoutInflater.from(themedContext), machineRowParent);
        final MachineCatalog catalog;
        try {
            catalog = CatalogLoader.load(context.getAssets());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        MachineRowBinder.bindCatalogMachine(
                machineRow,
                catalog.requireByUid("MI000424"),
                true,
                unused -> { });
        final TextView machineName = machineRow.findViewById(R.id.machineRowName);
        final TextView machineSecondary = machineRow.findViewById(
                R.id.machineRowSecondary);
        assertTrue(machineName.getIncludeFontPadding());
        assertTrue(machineSecondary.getIncludeFontPadding());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
                machineName.getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
                machineSecondary.getLayoutParams().height);
        assertEquals(1, machineName.getMaxLines());
        assertEquals(1, machineSecondary.getMaxLines());
        assertEquals(Typeface.BOLD, machineName.getTypeface().getStyle());
        assertEquals(TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM,
                TextViewCompat.getAutoSizeTextType(machineName));
        assertTextFits(machineName,
                "MacBook Pro (14-inch, M3 Pro or M3 Max, Nov 2023)",
                340, largeFontContext);
        assertEquals(1, machineName.getLineCount());
        assertTrue(machineName.getTextSize()
                < 18 * largeFontContext.getResources().getDisplayMetrics().scaledDensity);
        assertTextFits(machineSecondary,
                "Model Identifier: MacBookPro99,99 gyp",
                288, largeFontContext);

        final SearchHit configuredCodename = allSearch(catalog, "J185 iMac20,1").get(0);
        MachineRowBinder.bindSearchHit(
                machineRow, configuredCodename, false, unused -> { });
        final String codenamePrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_codename));
        final String modelIdentifierPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_model_identifier));
        final String codenameLine = codenamePrefix + "J185 (iMac20,1)";
        final String explanation = codenameLine + "\n"
                + modelIdentifierPrefix + "iMac20,1";
        assertEquals(1, machineName.getMaxLines());
        assertEquals(Integer.MAX_VALUE, machineSecondary.getMaxLines());
        assertEquals(Typeface.BOLD, machineName.getTypeface().getStyle());
        assertNull(machineName.getCompoundDrawablesRelative()[2]);
        assertEquals(explanation, machineSecondary.getText().toString());
        assertOnlyEvidenceRangesBold(machineSecondary, configuredCodename.evidence(),
                new EvidenceExpectation(codenamePrefix.length(),
                        SearchHit.Field.CODENAME, "J185 (iMac20,1)"),
                new EvidenceExpectation(codenameLine.length() + 1
                        + modelIdentifierPrefix.length(),
                        SearchHit.Field.MODEL_IDENTIFIER, "iMac20,1"));
        assertEquals(themedContext.getString(R.string.machine_row_accessibility,
                        configuredCodename.machine().name(), explanation).trim(),
                machineRow.getContentDescription().toString());
    }

    private static List<SearchHit> allSearch(final MachineCatalog catalog,
                                             final String text) {
        return catalog.search(text, SearchScope.ALL).hits();
    }

    private static void assertTextFits(final TextView textView,
                                       final String text,
                                       final int widthDp,
                                       final Context context) {
        textView.setText(text);
        final int width = Math.round(widthDp
                * context.getResources().getDisplayMetrics().density);
        final int height = textView.getLayoutParams().height;
        final int heightSpec = height >= 0
                ? View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int widthSpec = View.MeasureSpec.makeMeasureSpec(
                width, View.MeasureSpec.AT_MOST);
        // AppCompat's pre-O auto-size fallback can choose a smaller text size during the
        // first layout and invalidate TextView's Layout. An attached hierarchy receives
        // the requested follow-up pass from ViewRootImpl. This detached test view has no
        // parent to propagate requestLayout(), so explicitly reproduce those settling passes.
        for (int pass = 0; pass < 3; pass++) {
            textView.forceLayout();
            textView.measure(widthSpec, heightSpec);
            textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
            if (textView.getLayout() != null && !textView.isLayoutRequested()) {
                break;
            }
        }
        assertNotNull(textView.getLayout());
        final int availableHeight = textView.getHeight()
                - textView.getCompoundPaddingTop()
                - textView.getCompoundPaddingBottom();
        assertTrue(textView.getLayout().getHeight() <= availableHeight);
    }

}
