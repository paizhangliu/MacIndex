package com.macindex.macindex;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.catalog.MachineCatalog.Facet;
import com.macindex.macindex.catalog.MachineCatalog.SearchResponse;
import com.macindex.macindex.catalog.MachineCatalog.SearchScope;
import com.macindex.macindex.catalog.SearchHit;
import com.macindex.macindex.userstate.AppStateRepository;
import com.macindex.macindex.userstate.FavouriteFolder;
import com.macindex.macindex.userstate.UserPreferences;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    private static final String STATE_QUERY = "searchQuery";
    private static final String STATE_SCOPE = "searchScope";

    private SearchView searchText;
    private TextView textResult;
    private HorizontalScrollView searchFacetContainer;
    private RadioGroup searchFacetGroup;
    private ListView resultList;
    private SearchResultAdapter resultListAdapter;

    private MachineCatalog catalog;
    private UserStateLifecycleAdapter userStateAdapter;
    private UserPreferences preferences;
    private Set<String> favouriteUids = Collections.emptySet();
    private boolean initialized;
    private boolean settingQuery;
    private SearchScope selectedScope = SearchScope.ALL;
    private Bundle restorationState;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ContentInsetsHelper.apply(this);
        restorationState = savedInstanceState;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.menu_search);

        searchText = findViewById(R.id.searchInput);
        textResult = findViewById(R.id.textResult);
        searchFacetContainer = findViewById(R.id.searchFacetContainer);
        searchFacetGroup = findViewById(R.id.searchFacetGroup);
        resultList = findViewById(R.id.resultList);
        initSearchBox();
        initFacetControls();
        showSearchLoading();

        StartupUiGate.bind(this, (readyCatalog, repository) -> {
            catalog = readyCatalog;
            observeUserState(repository);
        });
    }

    private void observeUserState(final AppStateRepository repository) {
        if (userStateAdapter != null) {
            return;
        }
        userStateAdapter = new UserStateLifecycleAdapter(this, repository,
                this::onUserStateChanged,
                error -> ExceptionHelper.showUserStateReadFailure(this, error));
    }

    private void onUserStateChanged(final UserState state) {
        preferences = state.getPreferences();
        favouriteUids = collectFavouriteUids(state);
        if (resultListAdapter != null) {
            resultListAdapter.setFavouriteUids(favouriteUids);
        }
        if (initialized) {
            return;
        }

        initialized = true;
        if (restorationState != null) {
            restoreQuery();
            return;
        }
        final String query = searchText.getQuery().toString();
        if (MachineCatalog.isBlankSearchText(query)) {
            resetSearchPrompt();
        } else {
            performSearch(query.trim());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.searchHelpItem) {
            showSearchHelp();
        } else if (itemId == R.id.searchAppleSNItem) {
            LinkLoadingHelper.startBrowser("https://checkcoverage.apple.com/", this);
        } else if (itemId == R.id.searchEveryMacItem) {
            LinkLoadingHelper.startBrowser("https://everymac.com/ultimate-mac-lookup/", this);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showSearchHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_help_title)
                .setMessage(R.string.search_help_content)
                .setPositiveButton(R.string.help_confirm, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY,
                searchText == null ? "" : searchText.getQuery().toString());
        outState.putString(STATE_SCOPE, selectedScope.name());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initSearchBox() {
        searchText.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                searchText.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                if (settingQuery) {
                    return true;
                }
                final String input = newText.trim();
                if (MachineCatalog.isBlankSearchText(input)) {
                    selectedScope = SearchScope.ALL;
                    clearResults();
                    if (initialized) {
                        resetSearchPrompt();
                    } else {
                        showSearchLoading();
                    }
                } else {
                    performSearch(input);
                }
                return true;
            }
        });
    }

    private void restoreQuery() {
        if (restorationState == null) {
            return;
        }
        final String query = restorationState.getString(STATE_QUERY, "");
        final String scopeName = restorationState.getString(
                STATE_SCOPE, SearchScope.ALL.name());
        restorationState = null;
        try {
            selectedScope = SearchScope.valueOf(scopeName);
        } catch (IllegalArgumentException ignored) {
            Log.w("SearchState", "Ignoring an invalid restored search scope.");
            selectedScope = SearchScope.ALL;
        }
        setQuery(query);
        if (MachineCatalog.isBlankSearchText(query)) {
            selectedScope = SearchScope.ALL;
            clearResults();
            resetSearchPrompt();
        } else {
            performSearch(query.trim());
        }
    }

    private void setQuery(final String query) {
        settingQuery = true;
        try {
            searchText.setQuery(query, false);
        } finally {
            settingQuery = false;
        }
    }

    private void performSearch(final String input) {
        if (catalog == null || preferences == null) {
            showSearchLoading();
            return;
        }
        final SearchScope requestedScope = selectedScope;
        SearchResponse response = catalog.search(input, requestedScope);
        if (requestedScope != SearchScope.ALL
                && !responseContainsScope(response, requestedScope)) {
            response = catalog.search(input, SearchScope.ALL);
        }
        selectedScope = response.scope();
        renderResults(input, response);
    }

    private static boolean responseContainsScope(final SearchResponse response,
                                                 final SearchScope scope) {
        final SearchHit.Field field = scope.field();
        if (field == null) {
            return true;
        }
        // A refinement is useful only while the query still has a visible choice between
        // fields. Never restore a hidden one-field filter after the indexed catalog changes.
        if (response.facets().size() < 2) {
            return false;
        }
        for (Facet facet : response.facets()) {
            if (facet.field() == field) {
                return true;
            }
        }
        return false;
    }

    private void renderResults(final String input, final SearchResponse response) {
        resultListAdapter = new SearchResultAdapter(
                response.hits(), favouriteUids, this, this::openMachine);
        final int resultCount = resultListAdapter.getCount();
        if (resultCount == 0) {
            textResult.setText(getString(R.string.search_noResult, input));
        } else {
            textResult.setText(getResources().getQuantityString(
                    R.plurals.search_results, resultCount, resultCount));
        }
        textResult.setTextColor(ContextCompat.getColor(this, R.color.colorDefaultText));
        renderFacetChips(response);
        resultList.setAdapter(resultListAdapter);
    }

    private void clearResults() {
        resultListAdapter = null;
        resultList.setAdapter(null);
        hideFacetChips();
    }

    private void resetSearchPrompt() {
        textResult.setText(R.string.search_prompt);
        textResult.setTextColor(ContextCompat.getColor(this, R.color.colorDefaultText));
    }

    private void showSearchLoading() {
        textResult.setText(R.string.search_loading);
        textResult.setTextColor(ContextCompat.getColor(this, R.color.colorDefaultText));
    }

    private void openMachine(final Machine machine) {
        final List<Machine> navigation;
        if (preferences.getFixedNavigation()) {
            navigation = catalog.sequenceForProductType(machine.productTypeKey());
        } else {
            navigation = resultListAdapter.navigationMachines();
        }
        startActivity(NavigationContract.machineSpecsIntent(this,
                NavigationContract.MachineRequest.create(
                        machine, navigation, false)));
    }

    private void initFacetControls() {
        bindFacetButton(R.id.searchFacetAll, SearchScope.ALL);
        bindFacetButton(R.id.searchFacetName, SearchScope.NAME);
        bindFacetButton(R.id.searchFacetCodename, SearchScope.CODENAME);
        bindFacetButton(R.id.searchFacetModelNumber, SearchScope.MODEL_NUMBER);
        bindFacetButton(R.id.searchFacetModelIdentifier, SearchScope.MODEL_IDENTIFIER);
        bindFacetButton(R.id.searchFacetGestaltId, SearchScope.GESTALT_ID);
        bindFacetButton(R.id.searchFacetPartNumber, SearchScope.PART_NUMBER);
        bindFacetButton(R.id.searchFacetEmcNumber, SearchScope.EMC_NUMBER);
        bindFacetButton(R.id.searchFacetProcessor, SearchScope.PROCESSOR);
        bindFacetButton(R.id.searchFacetIntroduction, SearchScope.INTRODUCTION);
    }

    private void bindFacetButton(final int viewId, final SearchScope scope) {
        searchFacetGroup.findViewById(viewId).setOnClickListener(unused -> {
            if (scope == selectedScope) {
                return;
            }
            selectedScope = scope;
            final String input = searchText.getQuery().toString().trim();
            if (!MachineCatalog.isBlankSearchText(input)) {
                performSearch(input);
            }
        });
    }

    private void renderFacetChips(final SearchResponse response) {
        final List<Facet> facets = response.facets();
        if (facets.size() < 2) {
            hideFacetChips();
            return;
        }
        hideFacetButtons();
        showFacetChip(R.id.searchFacetAll,
                R.string.search_facet_all, response.allCount());
        for (Facet facet : facets) {
            final SearchScope scope = SearchScope.forField(facet.field());
            showFacetChip(facetViewId(scope),
                    MachineRowBinder.fieldLabel(facet.field()), facet.count());
        }
        final int selectedId = facetViewId(selectedScope);
        final View selectedChip = searchFacetGroup.findViewById(selectedId);
        if (selectedChip == null || selectedChip.getVisibility() != View.VISIBLE) {
            selectedScope = SearchScope.ALL;
            searchFacetGroup.check(R.id.searchFacetAll);
        } else {
            searchFacetGroup.check(selectedId);
        }
        searchFacetContainer.setVisibility(View.VISIBLE);
    }

    private void showFacetChip(final int viewId,
                               final int labelResource,
                               final int count) {
        final RadioButton chip = searchFacetGroup.findViewById(viewId);
        chip.setText(getString(R.string.search_facet_with_count,
                getString(labelResource), count));
        chip.setVisibility(View.VISIBLE);
    }

    private void hideFacetChips() {
        searchFacetGroup.clearCheck();
        hideFacetButtons();
        searchFacetContainer.setVisibility(View.GONE);
    }

    private void hideFacetButtons() {
        for (int index = 0; index < searchFacetGroup.getChildCount(); index++) {
            searchFacetGroup.getChildAt(index).setVisibility(View.GONE);
        }
    }

    private static int facetViewId(final SearchScope scope) {
        switch (scope) {
            case ALL:
                return R.id.searchFacetAll;
            case NAME:
                return R.id.searchFacetName;
            case CODENAME:
                return R.id.searchFacetCodename;
            case MODEL_NUMBER:
                return R.id.searchFacetModelNumber;
            case MODEL_IDENTIFIER:
                return R.id.searchFacetModelIdentifier;
            case GESTALT_ID:
                return R.id.searchFacetGestaltId;
            case PART_NUMBER:
                return R.id.searchFacetPartNumber;
            case EMC_NUMBER:
                return R.id.searchFacetEmcNumber;
            case PROCESSOR:
                return R.id.searchFacetProcessor;
            case INTRODUCTION:
                return R.id.searchFacetIntroduction;
            default:
                throw new IllegalStateException("Unknown search scope " + scope);
        }
    }

    private static Set<String> collectFavouriteUids(final UserState state) {
        final Set<String> result = new HashSet<>();
        for (FavouriteFolder folder : state.getLibrary().getFavouriteFolders()) {
            result.addAll(folder.getMachineUids());
        }
        return result;
    }

}
