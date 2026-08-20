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
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.widget.TextViewCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

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
    public void searchResultRowsHighlightVisibleEvidenceAndRebindRecycledContent()
            throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Configuration englishConfiguration = new Configuration(
                context.getResources().getConfiguration());
        englishConfiguration.setLocale(Locale.US);
        final Context themedContext = new ContextThemeWrapper(
                context.createConfigurationContext(englishConfiguration), R.style.AppTheme);
        final MachineCatalog catalog = StartupTestCatalog.get(context);

        final SearchHit titleHit = findHitByName(
                allSearch(catalog, "Macintosh 145B"),
                "Macintosh PowerBook 145, 145B");
        final Set<String> favourites = Collections.singleton(titleHit.machine().uid());
        final AtomicReference<Machine> selected = new AtomicReference<>();
        final SearchResultAdapter titleAdapter = new SearchResultAdapter(
                Collections.singletonList(titleHit), favourites, themedContext, selected::set);
        assertEquals(1, titleAdapter.getCount());
        assertEquals(titleHit.machine(), titleAdapter.navigationMachines().get(0));
        final ListView parent = new ListView(themedContext);
        final View titleRow = titleAdapter.getView(0, null, parent);
        final TextView name = titleRow.findViewById(R.id.machineRowName);
        final TextView detail = titleRow.findViewById(R.id.machineRowSecondary);
        assertEquals(titleHit.machine().name(), name.getText().toString());
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertEquals(1, name.getMaxLines());
        assertEquals(TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM,
                TextViewCompat.getAutoSizeTextType(name));
        assertNotNull(name.getCompoundDrawablesRelative()[2]);
        assertNoBoldSpans(name);
        final String namePrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_name));
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(namePrefix + titleHit.machine().name(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, namePrefix.length(), titleHit.evidence(),
                SearchHit.Field.NAME, titleHit.machine().name());

        final SearchHit codenameHit = allSearch(catalog, "Kanga").get(0);
        final SearchHit.Evidence codenameEvidence = primaryEvidence(codenameHit);
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
        assertEquals(codenamePrefix + codenameEvidence.matchedValue(),
                detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, codenamePrefix.length(),
                codenameHit.evidence(), codenameEvidence.field(),
                codenameEvidence.matchedValue());
        assertFalse(detail.getText().toString().contains(
                codenameHit.machine().introductionDisplayText()));
        reusedResult.performClick();
        assertEquals(codenameHit.machine(), selected.get());

        final SearchHit crossFieldHit = allSearch(catalog, "PowerBook G3 Kanga").get(0);
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
        final SearchHit.Evidence aliasEvidence = primaryEvidence(aliasHit);
        final SearchResultAdapter aliasAdapter = new SearchResultAdapter(
                Collections.singletonList(aliasHit), Collections.emptySet(),
                themedContext, unused -> { });
        aliasAdapter.getView(0, reusedResult, parent);
        final String aliasPrefix = themedContext.getString(
                R.string.search_result_field_prefix,
                themedContext.getString(R.string.search_field_alias));
        assertNoBoldSpans(name);
        assertEquals(aliasPrefix + aliasEvidence.matchedValue(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, aliasPrefix.length(), aliasHit.evidence(),
                SearchHit.Field.NAME, aliasEvidence.matchedValue());

        final SearchHit partNumberHit = allSearch(catalog, "M5994").get(0);
        final SearchHit.Evidence partNumberEvidence = primaryEvidence(partNumberHit);
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
                partNumberEvidence.matchedValue());

        final SearchHit configuredIdentity = allSearch(catalog, "J185 iMac20,1").get(0);
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
        assertNoBoldSpans(name);
        assertEquals(configuredExplanation, detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, configuredIdentity.evidence(),
                new EvidenceExpectation(codenamePrefix.length(),
                        SearchHit.Field.CODENAME, configuredCodenameValue),
                new EvidenceExpectation(configuredFirstLine.length() + 1
                        + modelIdentifierPrefix.length(),
                        SearchHit.Field.MODEL_IDENTIFIER, modelIdentifierValue));
        titleAdapter.getView(0, reusedResult, parent);
        assertEquals(Typeface.BOLD, name.getTypeface().getStyle());
        assertNoBoldSpans(name);
        assertEquals(View.VISIBLE, detail.getVisibility());
        assertEquals(namePrefix + titleHit.machine().name(), detail.getText().toString());
        assertOnlyEvidenceRangesBold(detail, namePrefix.length(), titleHit.evidence(),
                SearchHit.Field.NAME, titleHit.machine().name());
    }

    private static SearchHit findHitByName(final List<SearchHit> hits, final String name) {
        for (SearchHit hit : hits) {
            if (name.equals(hit.machine().name())) {
                return hit;
            }
        }
        throw new AssertionError("Missing search hit " + name);
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
        final MachineCatalog catalog = StartupTestCatalog.get(context);

        final SearchResponse yearResults = catalog.search("2020", SearchScope.ALL);
        final Facet yearName = requireFacet(yearResults, SearchHit.Field.NAME);
        final Facet yearEmc = requireFacet(yearResults, SearchHit.Field.EMC_NUMBER);
        final Facet yearIntroduction = requireFacet(
                yearResults, SearchHit.Field.INTRODUCTION);
        final SearchResponse m2Results = catalog.search("M2", SearchScope.ALL);
        final Facet m2Name = requireFacet(m2Results, SearchHit.Field.NAME);
        final Facet m2Codename = requireFacet(m2Results, SearchHit.Field.CODENAME);
        final Facet m2PartNumber = requireFacet(
                m2Results, SearchHit.Field.PART_NUMBER);
        final Facet m2Processor = requireFacet(m2Results, SearchHit.Field.PROCESSOR);

        final Instrumentation.ActivityMonitor specsMonitor = instrumentation.addMonitor(
                SpecsActivity.class.getName(), null, true);
        SearchActivity activity = null;
        try {
            final Intent intent = new Intent(context, SearchActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (SearchActivity) instrumentation.startActivitySync(intent);
            final SearchActivity launchedActivity = activity;
            waitForUiCondition(instrumentation, "Search screen did not become ready", () ->
                    launchedActivity.getString(R.string.search_prompt).contentEquals(
                            ((TextView) launchedActivity.findViewById(
                                    R.id.textResult)).getText()));
            instrumentation.runOnMainSync(() -> {
                final SearchView search = launchedActivity.findViewById(R.id.searchInput);
                final TextView status = launchedActivity.findViewById(R.id.textResult);
                assertEquals(launchedActivity.getString(R.string.search_hint),
                        search.getQueryHint());
                assertEquals(launchedActivity.getString(R.string.search_prompt),
                        status.getText().toString());
            });

            enterQuery(instrumentation, activity, "2020", yearResults.allCount());
            assertFacetUi(instrumentation, activity, yearResults, true);
            assertFacetChip(instrumentation, activity, R.id.searchFacetName,
                    R.string.search_field_name, yearName.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetEmcNumber,
                    R.string.search_field_emc_number, yearEmc.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetIntroduction,
                    R.string.search_field_introduction, yearIntroduction.count());

            clickFacet(instrumentation, activity, R.id.searchFacetName, yearName.count());
            assertCheckedFacet(instrumentation, activity, R.id.searchFacetName);
            clickFacet(instrumentation, activity, R.id.searchFacetEmcNumber, yearEmc.count());
            assertCheckedFacet(instrumentation, activity, R.id.searchFacetEmcNumber);

            enterQuery(instrumentation, activity, "M2", m2Results.allCount());
            assertFacetUi(instrumentation, activity, m2Results, true);
            assertFacetChip(instrumentation, activity, R.id.searchFacetName,
                    R.string.search_field_name, m2Name.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetCodename,
                    R.string.search_field_codename, m2Codename.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetPartNumber,
                    R.string.search_field_part_number, m2PartNumber.count());
            assertFacetChip(instrumentation, activity, R.id.searchFacetProcessor,
                    R.string.search_field_processor, m2Processor.count());
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

            // The selected facet is retained while it remains relevant; otherwise the
            // live query naturally returns to All.
            enterQuery(instrumentation, activeSearch, "2020", yearResults.allCount());
            assertFacetUi(instrumentation, activeSearch, yearResults, true);

            setQuery(instrumentation, activeSearch, "");
            assertSearchIsCleared(instrumentation, activeSearch);

            final List<SearchHit> kangaResults = allSearch(catalog, "Kanga");
            assertEquals(1, kangaResults.size());
            enterQuery(instrumentation, activeSearch, "Kanga", 1);
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
                                 final String query) {
        instrumentation.runOnMainSync(() -> ((SearchView) activity.findViewById(
                R.id.searchInput)).setQuery(query, false));
        instrumentation.waitForIdleSync();
    }

    private static void enterQuery(final Instrumentation instrumentation,
                                   final SearchActivity activity,
                                   final String query,
                                   final int expectedCount) {
        setQuery(instrumentation, activity, query);
        waitForUiCondition(instrumentation, "Live search did not return " + expectedCount
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

    private static List<SearchHit> allSearch(final MachineCatalog catalog,
                                             final String text) {
        return catalog.search(text, SearchScope.ALL).hits();
    }

    private static SearchHit.Evidence primaryEvidence(final SearchHit hit) {
        return hit.evidence().get(0);
    }

}
