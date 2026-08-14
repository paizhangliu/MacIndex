package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;

public class SearchActivity extends AppCompatActivity {

    private SearchView searchText = null;

    private TextView textResult = null;

    private ListView resultList = null;

    private Spinner filtersSpinner = null;

    private Spinner optionsSpinner = null;

    private MachineListAdapter resultListAdapter = null;

    private int[] positions = null;

    private ProgressDialog waitDialog = null;

    private boolean userStopped = true;

    private Thread searchThread = null;

    private volatile int searchRequestID = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        WindowInsetsHelper.apply(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateUp();
            }
        });

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        // Set the dialog AT HERE; its structure is different from other activities
        waitDialog = new ProgressDialog(SearchActivity.this);
        waitDialog.setMessage(getString(R.string.loading_search));
        waitDialog.setCancelable(false);

        waitDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.link_cancel), (dialog, which) -> {
            // To be rewritten
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        this.setTitle(R.string.menu_search);

        final LinearLayout mainLayout = findViewById(R.id.mainLayout);
        LayoutTransition layoutTransition = mainLayout.getLayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);

        filtersSpinner = findViewById(R.id.filtersSpinner);
        optionsSpinner = findViewById(R.id.optionsSpinner);

        // If SearchActivity Usage is set to not be saved
        if (!(PrefsHelper.getBooleanPrefs("isSaveSearchUsage", this))) {
            PrefsHelper.clearPrefs("lastSearchFiltersSpinner", this);
            PrefsHelper.clearPrefs("lastSearchOptionsSpinner", this);
        }

        initSearch();
        initSpinners();

        // Init Search Prompt at Here!!
        resetIllegal();

        if (savedInstanceState != null) {
            searchText.setQuery(savedInstanceState.getCharSequence("searchInput"), false);
            final String[] savedUIDs = savedInstanceState.getStringArray("machineUIDs");
            if (savedUIDs != null) {
                try {
                    positions = new int[savedUIDs.length];
                    for (int i = 0; i < savedUIDs.length; i++) {
                        positions[i] = MainActivity.getMachineHelper().getMachineID(savedUIDs[i]);
                    }
                } catch (IllegalArgumentException ignored) {
                    // The saved search may belong to an older database revision.
                    positions = null;
                }
            }
            final boolean reloadPositions = !savedInstanceState.getBoolean("loadComplete")
                    || positions == null;
            performSearch(reloadPositions
                    ? savedInstanceState.getCharSequence("searchInput").toString() : null,
                    reloadPositions);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.searchClearItem) {
            resetIllegal();
            cancelSearch();
        } else if (itemID == R.id.searchResetItem) {
            PrefsHelper.editPrefs("lastSearchFiltersSpinner", 0, SearchActivity.this);
            PrefsHelper.editPrefs("lastSearchOptionsSpinner", 0, SearchActivity.this);
            filtersSpinner.setSelection(0);
            optionsSpinner.setSelection(0);
            searchText.setQuery("", true);
            searchText.clearFocus();
            cancelSearch();
            changeTips();
        } else if (itemID == R.id.searchAppleSNItem) {
            LinkLoadingHelper.startBrowser("https://checkcoverage.apple.com/", this);
        } else if (itemID == R.id.searchEveryMacItem) {
            LinkLoadingHelper.startBrowser("https://everymac.com/ultimate-mac-lookup/", this);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (resultListAdapter != null) {
            resultListAdapter.refreshFavourites();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (positions != null) {
            final String[] machineUIDs = new String[positions.length];
            for (int i = 0; i < positions.length; i++) {
                machineUIDs[i] = MainActivity.getMachineHelper().getUID(positions[i]);
            }
            outState.putStringArray("machineUIDs", machineUIDs);
        }
        outState.putCharSequence("searchInput", searchText.getQuery());
        if (waitDialog != null && !waitDialog.isShowing()) {
            outState.putBoolean("loadComplete", true);
        } else {
            outState.putBoolean("loadComplete", false);
        }
    }

    @Override
    protected void onDestroy() {
        userStopped = true;
        searchRequestID++;
        if (searchThread != null) {
            searchThread.interrupt();
            searchThread = null;
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        navigateUp();
        return true;
    }

    private void navigateUp() {
        if (!MainActivity.getMainState()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    private void initSpinners() {
        try {
            ArrayAdapter<CharSequence> filtersAdapter = ArrayAdapter.createFromResource(this,
                    R.array.search_Filters, android.R.layout.simple_spinner_item);
            ArrayAdapter<String> optionsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                    Arrays.asList(getResources().getStringArray(R.array.search_Options)));

            filtersAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            optionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            filtersSpinner.setAdapter(filtersAdapter);
            optionsSpinner.setAdapter(optionsAdapter);

            filtersSpinner.setSelection(PrefsHelper.getIntPrefs("lastSearchFiltersSpinner", this));
            optionsSpinner.setSelection(PrefsHelper.getIntPrefs("lastSearchOptionsSpinner", this));

            filtersSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    updateSearchParameter("lastSearchFiltersSpinner", i, false);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Nothing to do.
                }
            });

            optionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    updateSearchParameter("lastSearchOptionsSpinner", i, true);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Nothing to do.
                }
            });
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initSpinners", "Unable to initialize spinners.");
        }
    }

    private void updateSearchParameter(final String preference, final int selection,
                                       final boolean updateTips) {
        try {
            if (PrefsHelper.getIntPrefs(preference, this) == selection) {
                if (updateTips && searchText.getQuery().length() == 0) {
                    changeTips();
                }
                return;
            }

            PrefsHelper.editPrefs(preference, selection, this);
            if (updateTips) {
                changeTips();
            }

            final String currentSearch = searchText.getQuery().toString().trim();
            if (currentSearch.isEmpty()) {
                resetIllegal();
                cancelSearch();
            } else if (!startSearch(currentSearch)) {
                cancelSearch();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "updateSearchParameter", "Unable to update search parameters.");
        }
    }

    private String translateFiltersParam() {
        int thisSelection = PrefsHelper.getIntPrefs("lastSearchFiltersSpinner", this);
        switch (thisSelection) {
            case 0:
                return "all";
            case 1:
                return "apple68k";
            case 2:
                return "appleppc";
            case 3:
                return "appleintel";
            case 4:
                return "applearm";
            default:
                ExceptionHelper.handleException(this, null,
                        "translateFilterParam",
                        "Not a Valid Manufacturer Selection, This should NOT happen!!");
                return "all";
        }
    }

    private String[] translateOptionsParam() {
        int thisSelection = PrefsHelper.getIntPrefs("lastSearchOptionsSpinner", this);
        switch (thisSelection) {
            case 0:
                return new String[]{"sname"};
            case 1:
                return new String[]{"smodel", "sident", "sgestalt", "sorder", "semc"};
            default:
                ExceptionHelper.handleException(this, null,
                        "translateOptionsParam",
                        "Not a Valid Search Column Selection, This should NOT happen!!");
                return new String[]{"sname"};
        }
    }

    private boolean translateMatchParam() {
        int thisSelection = PrefsHelper.getIntPrefs("lastSearchOptionsSpinner", this);
        switch (thisSelection) {
            case 0:
                return false;
            case 1:
                return true;
            default:
                ExceptionHelper.handleException(this, null,
                        "translateMatchParam",
                        "Not a Valid Search Column Selection, This should NOT happen!!");
                return false;
        }
    }

    /* Logic was improved since 4.8.2. disableCheck, lengthCheck, and strictCheck were removed. 8/30/2021 */

    private void initSearch() {
        searchText = findViewById(R.id.searchInput);
        textResult = findViewById(R.id.textResult);
        resultList = findViewById(R.id.resultList);

        searchText.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                searchText.clearFocus();
                return startSearch(query);
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                // TRIM to get the correct validation result.
                String searchInput = newText.trim();
                // Initialize on-the-fly validation.
                resetIllegal();
                if (!searchInput.equals("")) {
                    characterCheck(searchInput, translateMatchParam());
                } else {
                    // No input
                    resetIllegal();
                }
                return false;
            }
        });

        // Set auto-sizing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textResult.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        } else {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(textResult, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        }
    }

    private void clearSearch() {
        resultListAdapter = null;
        resultList.setAdapter(null);
    }

    private void cancelSearch() {
        userStopped = true;
        searchRequestID++;
        if (searchThread != null) {
            searchThread.interrupt();
            searchThread = null;
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        clearSearch();
    }

    private void resetIllegal() {
        textResult.setText(R.string.search_prompt);
        textResult.setTextColor(getColor(R.color.colorDefaultText));
    }

    private void changeTips() {
        try {
            int thisSelection = PrefsHelper.getIntPrefs("lastSearchOptionsSpinner", this);
            String[] searchTips = getResources().getStringArray(R.array.search_Tips);
            if (thisSelection >= searchTips.length) {
                throw new IllegalStateException();
            }
            searchText.setQueryHint(searchTips[thisSelection]);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "changeTips", "Invalid Search Tips Configuration.");
        }
    }

    private boolean startSearch(final String s) {
        try {
            String searchInput = s.trim();
            if (!searchInput.equals("")) {
                if (characterCheck(searchInput, translateMatchParam())) {
                    // Remove Results only before actual search starts.
                    performSearch(searchInput, true);
                    return true;
                } else {
                    return false;
                }
            } else {
                // No input
                resetIllegal();
                return false;
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
            return false;
        }
    }

    private boolean characterCheck(final String validateInput, final boolean method) {
        // Check the length first
        if ((method && validateInput.length() > 20) || (!method && validateInput.length() > 60)) {
            DebugHelper.log("validate", "Overlength Detected!");
            // Set the overlength prompt here..
            textResult.setText(R.string.search_overlength);
            textResult.setTextColor(Color.RED);
            return false;
        }

        for (int i = 0; i < validateInput.length(); i++) {
            if (Character.isISOControl(validateInput.charAt(i))) {
                DebugHelper.log("validate", "Illegal Char Detected!");
                textResult.setText(R.string.search_illegal);
                textResult.setTextColor(Color.RED);
                return false;
            }
        }
        return true;
    }

    private void performSearch(final String searchInput, final boolean reloadPositions) {
        try {
            final int requestID = ++searchRequestID;
            if (searchThread != null) {
                searchThread.interrupt();
            }
            if (waitDialog != null && waitDialog.isShowing()) {
                waitDialog.dismiss();
            }
            DebugHelper.log("performSearch", "Reload Flag: " + reloadPositions);
            userStopped = false;
            if (reloadPositions) {
                waitDialog.show();

                // Rewrite negative button
                waitDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                    Log.e("Search", "Terminated due to the user.");
                    userStopped = true;
                    searchRequestID++;
                    if (searchThread != null) {
                        searchThread.interrupt();
                        searchThread = null;
                    }
                    waitDialog.dismiss();
                });
            }
            final String[] searchColumnsForRequest = translateOptionsParam();
            final String manufacturerForRequest = translateFiltersParam();
            final boolean exactMatchForRequest = translateMatchParam();
            searchThread = new Thread() {
                @Override
                public void run() {
                    int[] positionsForRequest = positions;
                    if (reloadPositions) {
                        final String[] searchColumns = searchColumnsForRequest;
                        int[][] subPositions = new int[searchColumns.length][];
                        String rawSearchInput;
                        boolean rawMatchParam;
                        int resultCount = 0;

                        // Search by translated columns
                        for (int i = 0; i < searchColumns.length; i++) {
                            // For order number: use the part before the regional suffix.
                            if (searchColumns[i].equals("sorder")) {
                                if (searchInput.length() < 4) {
                                    // omit this
                                    subPositions[i] = new int[0];
                                    continue;
                                }
                                // Overwrite input
                                rawSearchInput = searchInput.substring(0,
                                        Math.min(5, searchInput.length()));
                                // Overwrite match param.
                                rawMatchParam = false;
                            } else {
                                rawSearchInput = searchInput;
                                rawMatchParam = exactMatchForRequest;
                            }
                            subPositions[i] = MainActivity.getMachineHelper().searchHelper(searchColumns[i], rawSearchInput,
                                    manufacturerForRequest, rawMatchParam, true);
                            // A few current part numbers only have four characters before xx.
                            if (searchColumns[i].equals("sorder")
                                    && subPositions[i].length == 0
                                    && rawSearchInput.length() > 4) {
                                subPositions[i] = MainActivity.getMachineHelper().searchHelper(
                                        searchColumns[i], rawSearchInput.substring(0, 4),
                                        manufacturerForRequest, false, true);
                            }
                            resultCount += subPositions[i].length;
                        }

                        // Add raw results
                        int[] newPositions = new int[resultCount];
                        int previousCount = 0;
                        for (int i = 0; i < searchColumns.length; i++) {
                            for (int j = 0; j < subPositions[i].length; j++) {
                                newPositions[previousCount] = subPositions[i][j];
                                previousCount++;
                            }
                        }
                        // A model-number search can match the same machine in multiple columns.
                        newPositions = MainActivity.getMachineHelper().checkDuplicate(newPositions);

                        positionsForRequest = newPositions;
                    }
                    if (Thread.currentThread().isInterrupted() || requestID != searchRequestID) {
                        return;
                    }
                    final int[] finalPositionsForRequest = positionsForRequest;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestID != searchRequestID || isFinishing()
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                return;
                            }
                            try {
                                positions = finalPositionsForRequest;
                                if (reloadPositions) {
                                        waitDialog.dismiss();
                                }
                                if (!userStopped) {
                                    DebugHelper.log("Search", "Terminated normally.");
                                    userStopped = true;
                                    clearSearch();
                                    // NullSafe
                                    if (positions != null) {
                                        DebugHelper.log("performSearchLoad", "Position Length: "
                                                + positions.length + ", Reload Flag: " + reloadPositions);
                                        if (positions.length == 0) {
                                            textResult.setText(R.string.search_noResult);
                                            textResult.setTextColor(getColor(R.color.colorDefaultText));
                                        } else {
                                            textResult.setText(getResources().getQuantityString(
                                                    R.plurals.search_results, positions.length,
                                                    positions.length));
                                            textResult.setTextColor(getColor(R.color.colorDefaultText));
                                        }
                                        resultListAdapter = new MachineListAdapter(positions, SearchActivity.this);
                                        resultList.setAdapter(resultListAdapter);

                                        // Open directly?
                                        if (reloadPositions && positions.length == 1
                                                && PrefsHelper.getBooleanPrefs("isOpenDirectly", SearchActivity.this)) {
                                            SpecsIntentHelper.openMachine(positions, positions[0],
                                                    SearchActivity.this);
                                        }
                                    }
                                } else {
                                    Log.w("Search", "Terminated Abnormally.");
                                }
                            } catch (final Exception e) {
                                ExceptionHelper.handleException(SearchActivity.this, e, null, null);
                            }
                        }
                    });
                }
            };
            searchThread.start();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }
}
