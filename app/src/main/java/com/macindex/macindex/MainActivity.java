package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.widget.TextViewCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * MacIndex.
 * University of Illinois, CS125 FA19 Final Project
 * University of Illinois, CS199 Kotlin SP20 Final Project
 * https://MacIndex.paizhang.info/
 * https://github.com/paizhangliu/MacIndex
 *
 * Basic functionality was finished on 16:12 CST, Dec 2, 2019.
 * 3.0 Update May 12, 2020 at Champaign, Illinois, U.S.A.
 * 4.0 Update June 13, 2020 at Shenyang, Liaoning, China.
 * 4.5 Update January 7, 2021 at Jinzhong, Shanxi, China.
 * 4.9 Update July 22, 2026 at Jinzhong, Shanxi, China.
 */
public class MainActivity extends AppCompatActivity {

    private static SQLiteDatabase database = null;

    private static MachineHelper machineHelper = null;

    private static Resources resources = null;

    private DrawerLayout mDrawerLayout = null;

    private String thisManufacturer = null;

    private String thisFilter = null;

    private int[][] loadPositions = {};

    private TextView[][] machineLoadedCount = null;

    private ProgressDialog waitDialog = null;

    private Thread interfaceThread = null;

    private volatile int interfaceRequestID = 0;

    private boolean isDrawerGesture = false;

    private static boolean isMainRunning = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowInsetsHelper.apply(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackStarted(@NonNull final BackEventCompat backEvent) {
                isDrawerGesture = mDrawerLayout != null
                        && !mDrawerLayout.isDrawerOpen(GravityCompat.START)
                        && backEvent.getSwipeEdge() == BackEventCompat.EDGE_LEFT;
            }

            @Override
            public void handleOnBackPressed() {
                if (isDrawerGesture) {
                    isDrawerGesture = false;
                    mDrawerLayout.openDrawer(GravityCompat.START);
                } else {
                    handleBackPressed();
                }
            }

            @Override
            public void handleOnBackCancelled() {
                isDrawerGesture = false;
            }
        });

        try {
            isMainRunning = true;
            thisManufacturer = PrefsHelper.getStringPrefs("lastMainManufacturer", this);
            thisFilter = PrefsHelper.getStringPrefs("lastMainFilter", this);
            initMenu();
            waitDialog = new ProgressDialog(MainActivity.this);
            waitDialog.setMessage(getString(R.string.loading_category));
            waitDialog.setCancelable(false);

            resources = getResources();
            final File databaseFile = getDatabasePath("specs.db");
            final boolean isNewVersion = PrefsHelper.registerNewVersion(this);
            if ((!databaseFile.exists() || isNewVersion)
                    && (database == null || !database.isOpen())) {
                // The bundled database is large. Copy it off the UI thread on install and update.
                waitDialog.show();
                final Context applicationContext = getApplicationContext();
                new Thread(() -> {
                    Exception initializationError = null;
                    try {
                        initDatabase(applicationContext, isNewVersion);
                    } catch (Exception e) {
                        initializationError = e;
                    }
                    final Exception finalInitializationError = initializationError;
                    runOnUiThread(() -> {
                        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                && isDestroyed())) {
                            return;
                        }
                        waitDialog.dismiss();
                        if (finalInitializationError == null) {
                            completeCreation(savedInstanceState, isNewVersion);
                        } else {
                            ExceptionHelper.handleException(this, finalInitializationError,
                                    "MainCreation", "Unable to initialize the database.");
                        }
                    });
                }, "MacIndex-DatabaseInit").start();
            } else {
                completeCreation(savedInstanceState, isNewVersion);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "MainCreation", "Unable to create the main activity.");
        }
    }

    private void completeCreation(final Bundle savedInstanceState, final boolean isNewVersion) {
        try {
            if (savedInstanceState == null) {
                // Creating activity due to user
                Log.i("MacIndex", "Welcome to MacIndex.");

                // If MainActivity Usage is set to not be saved
                if (!(PrefsHelper.getBooleanPrefs("isSaveMainUsage", this))) {
                    PrefsHelper.clearPrefs("lastMainManufacturer", this);
                    PrefsHelper.clearPrefs("lastMainFilter", this);
                }

                // Reset Volume Warning
                PrefsHelper.clearPrefs("isEnableVolWarningThisTime", this);

                if (machineHelper == null || database == null || resources == null || !database.isOpen()) {
                    Log.i("MacIndex", "Initializing database.");
                    initDatabase(this, false);
                } else {
                    Log.w("MacIndex", "Database already initialized.");
                }

                // Cache clear if new version is registered
                if (isNewVersion) {
                    clearCache();
                }

                initInterface(true);

                // Deep Link Support, Activity Not Present
                Uri deepLink = getIntent().getData();
                if (deepLink != null) {
                    decodeDeepLink(deepLink.toString());
                } else {
                    Log.w("onCreateDeepLinkEntry", "Got null data");
                }
            } else {
                // Creating activity due to system
                Log.i("MacIndex", "Reloading the main activity.");

                validateOperation(this);
                if (savedInstanceState.getBoolean("loadComplete")) {
                    // Restore the saved ID list
                    final int loadPositionsCount = savedInstanceState.getInt("loadPositionsCount");
                    loadPositions = new int[loadPositionsCount][];
                    for (int i = 0; i < loadPositionsCount; i++) {
                        loadPositions[i] = savedInstanceState.getIntArray("loadPositions" + i);
                    }
                    initInterface(false);
                } else {
                    initInterface(true);
                }

                // Finally, restore drawer.
                if (savedInstanceState.getBoolean("drawerOpen")) {
                    resetDrawerTitle();
                    resetDrawerSelection();
                }
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "MainCreation", "Unable to create the main activity.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Deep Link Support, Activity Present
        // Override this function due to the special lunch mode
        Uri deepLink = intent.getData();
        if (deepLink != null) {
            decodeDeepLink(deepLink.toString());
        } else {
            Log.w("onNewIntentDeepLinkEntry", "Got null data");
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        try {
            // If reload is needed..
            if (PrefsHelper.getBooleanPrefs("isReloadNeeded", this)) {
                setTitle(getString(translateTitleRes()));
                initInterface(true);
                PrefsHelper.editPrefs("isReloadNeeded", false, this);
            }

            // Reload favourites
            SpecsIntentHelper.refreshFavourites(machineLoadedCount, this);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "MainOnRestart", "Unable to resume normal activity.");
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Is still loading?
        if (waitDialog != null && !waitDialog.isShowing() && machineHelper != null) {
            // Save the currently received ID list
            outState.putBoolean("loadComplete", true);
            outState.putInt("loadPositionsCount", loadPositions.length);
            for (int i = 0; i < loadPositions.length; i++) {
                outState.putIntArray("loadPositions" + i, loadPositions[i]);
            }
        } else {
            outState.putBoolean("loadComplete", false);
        }

        // Is drawer opened?
        outState.putBoolean("drawerOpen", mDrawerLayout != null
                && mDrawerLayout.isDrawerOpen(GravityCompat.START));
    }

    @Override
    protected void onDestroy() {
        isMainRunning = false;
        interfaceRequestID++;
        if (interfaceThread != null) {
            interfaceThread.interrupt();
            interfaceThread = null;
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_main, menu);
        // Debug items visibility
        if (!BuildConfig.DEBUG) {
            Log.i("DebugMode", "Disabling debug menu items.");
            menu.findItem(R.id.mainDebugReloadItem).setVisible(false);
            menu.findItem(R.id.mainDebugTriggerErrorItem).setVisible(false);
            menu.findItem(R.id.mainDebugRunnerItem).setVisible(false);
            menu.findItem(R.id.mainDebugClearCacheItem).setVisible(false);
            menu.findItem(R.id.mainDebugVersionRegistration).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == android.R.id.home) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            } else {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }
        } else if (itemID == R.id.mainDebugReloadItem) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            reloadDatabase(this);
            initInterface(true);
        } else if (itemID == R.id.mainDebugTriggerErrorItem) {
            ExceptionHelper.handleException(this, null, "Debug", "User triggered.");
        } else if (itemID == R.id.mainDebugClearCacheItem) {
            clearCache();
        } else if (itemID == R.id.mainDebugVersionRegistration) {
            PrefsHelper.editPrefs("lastKnownVersion", BuildConfig.VERSION_CODE - 1, this);
            PrefsHelper.triggerRebirth(this);
        } else if (itemID == R.id.mainDebugRunnerItem) {
            Toast.makeText(this, "Complete", Toast.LENGTH_SHORT).show();
        } else if (itemID == R.id.mainResetItem) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            }
            if (!(thisManufacturer.equals("all") && thisFilter.equals("names"))) {
                thisManufacturer = "all";
                thisFilter = "names";
                PrefsHelper.editPrefs("lastMainManufacturer", "all", this);
                PrefsHelper.editPrefs("lastMainFilter", "names", this);
                initInterface(true);
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void handleBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            finish();
        }
    }

    private static synchronized void initDatabase(final Context context, final boolean isNewVersion) {
        try {
            Log.w("Database", "Initializing.");
            if (database != null && database.isOpen() && machineHelper != null) {
                return;
            }

            final Context applicationContext = context.getApplicationContext();
            final File dbFilePath = applicationContext.getDatabasePath("specs.db");
            final File dbFolder = dbFilePath.getParentFile();
            if (dbFolder == null || (!dbFolder.exists() && !dbFolder.mkdirs())) {
                throw new IllegalStateException("Unable to create database directory");
            }
            if (!dbFilePath.exists() || isNewVersion) {
                final File temporaryDatabase = new File(dbFolder, "specs.db.tmp");
                if (temporaryDatabase.exists() && !temporaryDatabase.delete()) {
                    throw new IllegalStateException("Unable to remove temporary database file");
                }
                try (InputStream inputStream = applicationContext.getAssets().open("specs.db");
                     OutputStream outputStream = new FileOutputStream(temporaryDatabase)) {
                    byte[] buffer = new byte[64 * 1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();
                } catch (Exception copyException) {
                    if (!temporaryDatabase.delete()) {
                        Log.w("Database", "Unable to remove partial database file.");
                    }
                    throw copyException;
                }
                if (dbFilePath.exists() && !applicationContext.deleteDatabase("specs.db")) {
                    throw new IllegalStateException("Unable to remove outdated database");
                }
                if (!temporaryDatabase.renameTo(dbFilePath)) {
                    if (!temporaryDatabase.delete()) {
                        Log.w("Database", "Unable to remove temporary database file.");
                    }
                    throw new IllegalStateException("Unable to install bundled database");
                }
            }

            DatabaseOpenHelper dbHelper = new DatabaseOpenHelper(applicationContext);
            database = dbHelper.getReadableDatabase();

            // Open MachineHelper
            machineHelper = new MachineHelper(database);

        } catch (Exception e) {
            if (isNewVersion) {
                PrefsHelper.editPrefs("lastKnownVersion", BuildConfig.VERSION_CODE - 1,
                        context.getApplicationContext());
            }
            Log.e("initDatabaseSafe", "Initialize failed.", e);
            throw new IllegalStateException("Unable to initialize bundled database", e);
        }
    }

    private static void closeDatabase() {
        if (machineHelper != null) {
            machineHelper.setStopQuery();
        }
        if (database != null) {
            Log.w("Database", "Current database close.");
            database.close();
        }
        database = null;
        machineHelper = null;
    }

    private void initMenu() {
        try {
            Log.i("initMenu", "Initializing");
            // Set the slide menu.
            // Set the edge size of drawer.
            mDrawerLayout = findViewById(R.id.mainContainer);
            mDrawerLayout.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                     oldLeft, oldTop, oldRight, oldBottom) ->
                    enlargeDrawerEdge());

            // Initialize the navigation bar

            // Manufacturer Menu
            // Manufacturer 0: all (Default)
            findViewById(R.id.group0MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisManufacturer = "all";
                PrefsHelper.editPrefs("lastMainManufacturer", "all", this);
                initInterface(true);
            });
            // Manufacturer 1: apple68k
            findViewById(R.id.group1MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisManufacturer = "apple68k";
                PrefsHelper.editPrefs("lastMainManufacturer", "apple68k", this);
                initInterface(true);
            });
            // Manufacturer 2: appleppc
            findViewById(R.id.group2MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisManufacturer = "appleppc";
                PrefsHelper.editPrefs("lastMainManufacturer", "appleppc", this);
                initInterface(true);
            });
            // Manufacturer 3: appleintel
            findViewById(R.id.group3MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisManufacturer = "appleintel";
                PrefsHelper.editPrefs("lastMainManufacturer", "appleintel", this);
                initInterface(true);
            });
            // Manufacturer 4: applearm
            findViewById(R.id.group4MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisManufacturer = "applearm";
                PrefsHelper.editPrefs("lastMainManufacturer", "applearm", this);
                initInterface(true);
            });

            // Filter Menu
            // Filter 1: names (Default)
            findViewById(R.id.view1MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisFilter = "names";
                PrefsHelper.editPrefs("lastMainFilter", "names", this);
                initInterface(true);
            });
            // Filter 2: processors
            findViewById(R.id.view2MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisFilter = "processors";
                PrefsHelper.editPrefs("lastMainFilter", "processors", this);
                initInterface(true);
            });
            // Filter 3: years
            findViewById(R.id.view3MenuItem).setOnClickListener(view -> {
                mDrawerLayout.closeDrawers();
                thisFilter = "years";
                PrefsHelper.editPrefs("lastMainFilter", "years", this);
                initInterface(true);
            });

            // Main Menu
            // SearchActivity Entrance
            findViewById(R.id.searchMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
                mDrawerLayout.closeDrawers();
            });
            // Random Access
            findViewById(R.id.randomMenuItem).setOnClickListener(view -> {
                openRandom();
                mDrawerLayout.closeDrawers();
            });
            // FavouriteActivity Entrance
            findViewById(R.id.favouriteMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, FavouriteActivity.class));
                mDrawerLayout.closeDrawers();
            });
            // CompareActivity Entrance
            findViewById(R.id.compareMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, CompareActivity.class));
                mDrawerLayout.closeDrawers();
            });
            // CommentActivity Entrance
            findViewById(R.id.commentMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, CommentActivity.class));
                mDrawerLayout.closeDrawers();
            });
            // SettingsAboutActivity Entrance
            findViewById(R.id.aboutMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, SettingsAboutActivity.class));
                mDrawerLayout.closeDrawers();
            });
            // AboutActivity Entrance
            findViewById(R.id.newAboutMenuItem).setOnClickListener(view -> {
                startActivity(new Intent(MainActivity.this, NewAboutActivity.class));
                mDrawerLayout.closeDrawers();
            });

            // Set a drawer listener to change title and color.
            mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
                @Override
                public void onDrawerSlide(@NonNull final View drawerView, final float slideOffset) {
                    // No action
                }

                @Override
                public void onDrawerOpened(@NonNull final View drawerView) {
                    resetDrawerTitle();
                }

                @Override
                public void onDrawerClosed(@NonNull final View drawerView) {
                    setTitle(getString(translateTitleRes()));
                }

                @Override
                public void onDrawerStateChanged(final int newState) {
                    resetDrawerSelection();
                }
            });

            // Set the toolbar.
            final Toolbar mainToolbar = findViewById(R.id.mainToolbar);
            final ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mainToolbar, 0, 0);
            mDrawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();
            setSupportActionBar(mainToolbar);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "initMenu", "Initialize failed!!");
        }
    }

    private void enlargeDrawerEdge() {
        try {
            final Field mDragger = mDrawerLayout.getClass().getDeclaredField(
                    "mLeftDragger");
            mDragger.setAccessible(true);
            final ViewDragHelper draggerObj = (ViewDragHelper) mDragger
                    .get(mDrawerLayout);
            draggerObj.setEdgeSize(draggerObj.getDefaultEdgeSize() * 10);
        } catch (Exception e) {
            // A changed AndroidX field must not take the whole main menu down.
            Log.w("initMenu", "Unable to enlarge drawer edge.", e);
        }
    }

    private void resetDrawerTitle() {
        // Set if it is in EveryMac mode.
        if (PrefsHelper.getBooleanPrefs("isOpenEveryMac", MainActivity.this)) {
            setTitle(getString(R.string.app_name_everymac));
        } else {
            setTitle(R.string.app_name);
        }
    }

    private void resetDrawerSelection() {
        // Manufacturer Menu
        final LinearLayout manufacturerLayout = findViewById(R.id.groupLayout);
        for (int i = 1; i < manufacturerLayout.getChildCount(); i++) {
            if (manufacturerLayout.getChildAt(i) instanceof TextView) {
                final TextView currentChild = (TextView) manufacturerLayout.getChildAt(i);
                if (currentChild == findViewById(translateManufacturerMenuRes())) {
                    currentChild.setEnabled(false);
                    currentChild.setTextColor(Color.WHITE);
                    currentChild.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    currentChild.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_check_24_white, 0);

                } else {
                    currentChild.setEnabled(true);
                    currentChild.setTextColor(getResources().getColor(R.color.colorDefaultText));
                    currentChild.setBackgroundColor(Color.WHITE);
                    currentChild.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                }
            }
        }

        // Filter Menu
        final LinearLayout filterLayout = findViewById(R.id.viewLayout);
        for (int i = 1; i < filterLayout.getChildCount(); i++) {
            if (filterLayout.getChildAt(i) instanceof TextView) {
                final TextView currentChild = (TextView) filterLayout.getChildAt(i);
                if (currentChild == findViewById(translateFilterMenuRes())) {
                    currentChild.setEnabled(false);
                    currentChild.setTextColor(Color.WHITE);
                    currentChild.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    currentChild.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_check_24_white, 0);
                } else {
                    currentChild.setEnabled(true);
                    currentChild.setTextColor(getResources().getColor(R.color.colorDefaultText));
                    currentChild.setBackgroundColor(Color.WHITE);
                    currentChild.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                }
            }
        }

        // If EveryMac enabled, random should be disabled
        if (PrefsHelper.getBooleanPrefs("isOpenEveryMac", MainActivity.this)) {
            findViewById(R.id.randomMenuItem).setEnabled(false);
            findViewById(R.id.favouriteMenuItem).setEnabled(false);
            findViewById(R.id.compareMenuItem).setEnabled(false);
            findViewById(R.id.commentMenuItem).setEnabled(false);
        } else {
            findViewById(R.id.randomMenuItem).setEnabled(true);
            findViewById(R.id.favouriteMenuItem).setEnabled(true);
            findViewById(R.id.compareMenuItem).setEnabled(true);
            findViewById(R.id.commentMenuItem).setEnabled(true);
        }

        // If limit range enabled, a message should append
        if (PrefsHelper.getBooleanPrefs("isRandomAll", MainActivity.this)) {
            ((TextView) findViewById(R.id.randomMenuItem))
                    .setText(getString(R.string.menu_random) + getString(R.string.menu_random_limited));
        } else {
            ((TextView) findViewById(R.id.randomMenuItem))
                    .setText(getString(R.string.menu_random));
        }
    }

    private void initInterface(final boolean reloadPositions) {
        try {
            final int requestID = ++interfaceRequestID;
            if (interfaceThread != null) {
                interfaceThread.interrupt();
            }
            if (waitDialog != null && waitDialog.isShowing()) {
                waitDialog.dismiss();
            }
            boolean internalReloadFlag = reloadPositions;
            // Set Activity title.
            setTitle(getString(translateTitleRes()));
            // Parent layout of all categories.
            final LinearLayout categoryContainer = findViewById(R.id.categoryContainer);
            // Fix an animation bug here
            LayoutTransition layoutTransition = categoryContainer.getLayoutTransition();
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            categoryContainer.removeAllViews();
            // Get filter string and positions.
            final String[][] thisFilterString = machineHelper.getFilterString(thisFilter);

            // Query cache.
            if (internalReloadFlag) {
                internalReloadFlag = !(operateCache(false));
            }

            if (internalReloadFlag) {
                waitDialog.show();
            }
            final boolean finalInternalReloadFlag = internalReloadFlag;
            final String manufacturerForRequest = thisManufacturer;
            interfaceThread = new Thread() {
                @Override
                public void run() {
                    final int[][] positionsForRequest;
                    if (finalInternalReloadFlag) {
                        positionsForRequest = machineHelper.filterSearchHelper(thisFilterString, manufacturerForRequest,
                                PrefsHelper.getBooleanPrefsSafe("isSortAgain", MainActivity.this));
                    } else {
                        positionsForRequest = loadPositions;
                    }
                    if (Thread.currentThread().isInterrupted() || requestID != interfaceRequestID) {
                        return;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestID != interfaceRequestID || isFinishing()
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                return;
                            }
                            try {
                                loadPositions = positionsForRequest;
                                if (finalInternalReloadFlag) {
                                    waitDialog.dismiss();
                                    // Cache only the request that is still current.
                                    operateCache(true);
                                }
                                // Set up each category.
                                machineLoadedCount = new TextView[loadPositions.length][];
                                for (int i = 0; i < loadPositions.length; i++) {
                                    final View categoryChunk = getLayoutInflater()
                                            .inflate(R.layout.chunk_category, categoryContainer, false);
                                    final LinearLayout categoryChunkLayout = categoryChunk.findViewById(R.id.categoryInfoLayout);
                                    final TextView categoryName = categoryChunk.findViewById(R.id.category);

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        categoryName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                    } else {
                                        TextViewCompat.setAutoSizeTextTypeWithDefaults(categoryName, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                    }

                                    if (loadPositions[i].length != 0) {
                                        categoryName.setText(thisFilterString[2][i]);

                                        /* Remake my teammate's code */
                                        categoryName.setOnClickListener(new View.OnClickListener() {
                                            private boolean thisVisibility = false;

                                            @Override
                                            public void onClick(final View view) {
                                                try {
                                                    final View firstChild = categoryChunkLayout.getChildAt(1);
                                                    if (thisVisibility) {
                                                        // Make machines invisible.
                                                        if (!(firstChild instanceof LinearLayout)) {
                                                            // Have the divider
                                                            for (int j = 2; j < categoryChunkLayout.getChildCount(); j++) {
                                                                categoryChunkLayout.getChildAt(j).setVisibility(View.GONE);
                                                                thisVisibility = false;
                                                            }
                                                            firstChild.setVisibility(View.VISIBLE);
                                                        } else {
                                                            // Does not have the divider
                                                            for (int j = 1; j < categoryChunkLayout.getChildCount(); j++) {
                                                                categoryChunkLayout.getChildAt(j).setVisibility(View.GONE);
                                                                thisVisibility = false;
                                                            }
                                                        }
                                                    } else {
                                                        // Make machines visible.
                                                        if (!(firstChild instanceof LinearLayout)) {
                                                            // Have the divider
                                                            for (int j = 2; j < categoryChunkLayout.getChildCount(); j++) {
                                                                categoryChunkLayout.getChildAt(j).setVisibility(View.VISIBLE);
                                                                thisVisibility = true;
                                                            }
                                                            firstChild.setVisibility(View.GONE);
                                                        } else {
                                                            // Does not have the divider
                                                            for (int j = 1; j < categoryChunkLayout.getChildCount(); j++) {
                                                                categoryChunkLayout.getChildAt(j).setVisibility(View.VISIBLE);
                                                                thisVisibility = true;
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    ExceptionHelper.handleException(MainActivity.this, e, null, null);
                                                }
                                            }
                                        });
                                        Log.i("initCategory", "Loading category " + i);
                                        machineLoadedCount[i] = SpecsIntentHelper
                                                .initCategory(categoryChunkLayout, loadPositions[i], false, MainActivity.this);
                                        categoryContainer.addView(categoryChunk);
                                    }
                                }
                                // Remove the last divider.
                                if (categoryContainer.getChildCount() != 0) {
                                    ((LinearLayout) categoryContainer.getChildAt(categoryContainer.getChildCount() - 1)).removeViewAt(1);
                                }

                                // Load the favourites star.
                                SpecsIntentHelper.refreshFavourites(machineLoadedCount, MainActivity.this);
                            } catch (Exception e) {
                                ExceptionHelper.handleException(MainActivity.this, e, null, null);
                            }

                            // If user lunched MacIndex for the first time, a message should show.
                            if (PrefsHelper.getBooleanPrefs("isFirstLunch", MainActivity.this)) {
                                final AlertDialog.Builder firstLunchGreet = new AlertDialog.Builder(MainActivity.this);
                                firstLunchGreet.setTitle(R.string.information_first_lunch_title);
                                firstLunchGreet.setMessage(R.string.information_first_lunch);
                                firstLunchGreet.setPositiveButton(R.string.get_started, (dialogInterface, i) -> mDrawerLayout.openDrawer(GravityCompat.START));
                                firstLunchGreet.show();
                                PrefsHelper.editPrefs("isFirstLunch", false, MainActivity.this);
                            }

                        }
                    });
                }
            };
            interfaceThread.start();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "initInterface", "Initialize failed!!");
        }
    }

    private void openRandom() {
        try {
            if (machineHelper.getMachineCount() == 0) {
                throw new IllegalStateException();
            }
            if (PrefsHelper.getBooleanPrefs("isOpenEveryMac", this)) {
                // This should not happen.
                throw new IllegalStateException();
            } else {
                int machineID = 0;
                if (!PrefsHelper.getBooleanPrefs("isRandomAll", this)) {
                    // Random All mode.
                    machineID = new Random().nextInt(machineHelper.getMachineCount());
                    Log.i("RandomAccess", "Random All mode, get total " + machineHelper.getMachineCount() + " , ID " + machineID);
                } else {
                    // Limited Random mode.
                    int totalLoadad = 0;
                    for (int[] i : loadPositions) {
                        totalLoadad += i.length;
                    }
                    if (totalLoadad == 0) {
                        throw new IllegalStateException();
                    }
                    int randomCode = new Random().nextInt(totalLoadad);
                    Log.i("RandomAccess", "Limit Random mode, get total " + totalLoadad + " , ID " + randomCode);
                    for (int[] loadPosition : loadPositions) {
                        if (randomCode >= loadPosition.length) {
                            randomCode -= loadPosition.length;
                        } else {
                            machineID = loadPosition[randomCode];
                            break;
                        }
                    }
                }
                Log.i("RandomAccess", "Machine ID " + machineID);
                SpecsIntentHelper.sendIntent(new int[]{machineID}, machineID, this, true);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }

    private int translateTitleRes() {
        switch (thisManufacturer) {
            case "all":
                return R.string.menu_group0;
            case "apple68k":
                return R.string.menu_group1;
            case "appleppc":
                return R.string.menu_group2;
            case "appleintel":
                return R.string.menu_group3;
            case "applearm":
                return R.string.menu_group4;
            default:
                ExceptionHelper.handleException(this, null,
                        "translateTitleRes",
                        "Not a Valid Manufacturer Selection, This should NOT happen!!");
                return R.string.menu_group0;
        }
    }

    private int translateManufacturerMenuRes() {
        switch (thisManufacturer) {
            case "all":
                return R.id.group0MenuItem;
            case "apple68k":
                return R.id.group1MenuItem;
            case "appleppc":
                return R.id.group2MenuItem;
            case "appleintel":
                return R.id.group3MenuItem;
            case "applearm":
                return R.id.group4MenuItem;
            default:
                ExceptionHelper.handleException(this, null,
                        "translateManufacturerMenuRes",
                        "Not a Valid Manufacturer Selection, This should NOT happen!!");
                return R.id.group0MenuItem;
        }
    }

    private int translateFilterMenuRes() {
        switch (thisFilter) {
            case "names":
                return R.id.view1MenuItem;
            case "processors":
                return R.id.view2MenuItem;
            case "years":
                return R.id.view3MenuItem;
            default:
                ExceptionHelper.handleException(this, null,
                        "translateFilterMenuRes",
                        "Not a Valid Search Column Selection, This should NOT happen!!");
                return R.id.view1MenuItem;
        }
    }

    private boolean operateCache(final boolean isWrite) {
        try {
            String toWrite = "";
            if (isWrite) {
                for (int i = 0; i < loadPositions.length; i++) {
                    for (int j = 0; j < loadPositions[i].length; j++) {
                        toWrite = toWrite.concat(String.valueOf(loadPositions[i][j]));
                        if (!(j + 1 == loadPositions[i].length)) {
                            toWrite = toWrite.concat(",");
                        }
                    }
                    if (!(i + 1 == loadPositions.length)) {
                        toWrite = toWrite.concat(";");
                    }
                }
                Log.w("operateCache", "String to write: " + toWrite);
            }
            switch (thisManufacturer) {
                case "all":
                    switch (thisFilter) {
                        case "names":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM0F0", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM0F0", this);
                                break;
                            }
                        case "processors":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM0F1", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM0F1", this);
                                break;
                            }
                        case "years":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM0F2", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM0F2", this);
                                break;
                            }
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                case "apple68k":
                    switch (thisFilter) {
                        case "names":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM1F0", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM1F0", this);
                                break;
                            }
                        case "processors":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM1F1", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM1F1", this);
                                break;
                            }
                        case "years":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM1F2", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM1F2", this);
                                break;
                            }
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                case "appleppc":
                    switch (thisFilter) {
                        case "names":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM2F0", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM2F0", this);
                                break;
                            }
                        case "processors":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM2F1", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM2F1", this);
                                break;
                            }
                        case "years":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM2F2", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM2F2", this);
                                break;
                            }
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                case "appleintel":
                    switch (thisFilter) {
                        case "names":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM3F0", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM3F0", this);
                                break;
                            }
                        case "processors":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM3F1", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM3F1", this);
                                break;
                            }
                        case "years":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM3F2", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM3F2", this);
                                break;
                            }
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                case "applearm":
                    switch (thisFilter) {
                        case "names":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM4F0", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM4F0", this);
                                break;
                            }
                        case "processors":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM4F1", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM4F1", this);
                                break;
                            }
                        case "years":
                            if (isWrite) {
                                PrefsHelper.editPrefs("lastCachedM4F2", toWrite, this);
                                return true;
                            } else {
                                toWrite = PrefsHelper.getStringPrefs("lastCachedM4F2", this);
                                break;
                            }
                        default:
                            throw new IllegalArgumentException();
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            if (!isWrite) {
                if (toWrite.isEmpty()) {
                    Log.i("MainCache", "Cache is empty.");
                    return false;
                } else {
                    Log.i("MainCache", "Hit cache string: " + toWrite);
                    String[] splitedCategories = toWrite.split(";");
                    loadPositions = new int[splitedCategories.length][];
                    for (int i = 0; i < splitedCategories.length; i++) {
                        // Check if empty:
                        if (splitedCategories[i].isEmpty()) {
                            loadPositions[i] = new int[0];
                            continue;
                        }
                        String[] splitedMachineIDs = splitedCategories[i].split(",");
                        loadPositions[i] = new int[splitedMachineIDs.length];
                        for (int j = 0; j < splitedMachineIDs.length; j++) {
                            loadPositions[i][j] = Integer.parseInt(splitedMachineIDs[j]);
                        }
                    }
                    return true;
                }
            } else {
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "MainCache",
                    "Unable to operate the cache.");
            return false;
        }
    }

    private void clearCache() {
        Log.w("MainCache", "Clearing cache.");
        if (BuildConfig.DEBUG) {
            Toast.makeText(this, "Cache clear requested.", Toast.LENGTH_SHORT).show();
        }
        PrefsHelper.clearPrefs("lastCachedM0F0", this);
        PrefsHelper.clearPrefs("lastCachedM0F1", this);
        PrefsHelper.clearPrefs("lastCachedM0F2", this);
        PrefsHelper.clearPrefs("lastCachedM1F0", this);
        PrefsHelper.clearPrefs("lastCachedM1F1", this);
        PrefsHelper.clearPrefs("lastCachedM1F2", this);
        PrefsHelper.clearPrefs("lastCachedM2F0", this);
        PrefsHelper.clearPrefs("lastCachedM2F1", this);
        PrefsHelper.clearPrefs("lastCachedM2F2", this);
        PrefsHelper.clearPrefs("lastCachedM3F0", this);
        PrefsHelper.clearPrefs("lastCachedM3F1", this);
        PrefsHelper.clearPrefs("lastCachedM3F2", this);
        PrefsHelper.clearPrefs("lastCachedM4F0", this);
        PrefsHelper.clearPrefs("lastCachedM4F1", this);
        PrefsHelper.clearPrefs("lastCachedM4F2", this);
    }

    private void decodeDeepLink(final String deepLink) {
        try {
            if (ShareLinkHelper.isComparison(deepLink)) {
                openComparison(ShareLinkHelper.decodeComparison(deepLink));
                return;
            }

            final String machineName = ShareLinkHelper.decode(deepLink);
            Log.i("DeepLinkDecode", "Got machine " + machineName);
            int[] decodedID = decodeStartedParam(machineName);
            if (decodedID.length != 1) {
                Log.w("DeepLinkDecode", "Unable to decode the requested link.");
                Toast.makeText(this, R.string.share_main_decode_failed, Toast.LENGTH_LONG).show();
            } else {
                // Decoded successfully, call intent parser
                SpecsIntentHelper.sendIntent(decodedID, decodedID[0], this, false);
            }
        } catch (Exception e) {
            Log.w("DeepLinkDecode", "Unable to process the link due to illegal parameter.");
            Toast.makeText(this, R.string.share_main_decode_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openComparison(final String[] machineNames) {
        if (machineNames == null || machineNames.length != 2
                || machineNames[0].equals(machineNames[1])
                || decodeStartedParam(machineNames[0]).length != 1
                || decodeStartedParam(machineNames[1]).length != 1) {
            Log.w("DeepLinkDecode", "Unable to decode the requested comparison.");
            Toast.makeText(this, R.string.share_main_decode_failed, Toast.LENGTH_LONG).show();
            return;
        }

        final List<String> compareNames = new ArrayList<>(CompareActivity.getCompareList(this));
        // Keep both shared machines in the original 10-machine compare list limit.
        compareNames.remove(machineNames[0]);
        compareNames.remove(machineNames[1]);
        while (compareNames.size() > 8) {
            compareNames.remove(0);
        }
        compareNames.add(machineNames[0]);
        compareNames.add(machineNames[1]);
        CompareActivity.saveCompareList(compareNames, this);
        PrefsHelper.editPrefs("userComparesLeft", machineNames[0], this);
        PrefsHelper.editPrefs("userComparesRight", machineNames[1], this);

        startActivity(new Intent(this, CompareActivity.class));
    }

    // Return an ID array with matched name. Input: suspected machine name.
    private int[] decodeStartedParam(final String param) {
        try {
            // Param must not be an empty string.
            if (param == null || param.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return MainActivity.getMachineHelper().searchHelper("name", param.trim(),
                    "all", true, false);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "MachineParamDecoder",
                    "Unable to decode the parameter: " + String.valueOf(param));
            return new int[0];
        }
    }

    public static MachineHelper getMachineHelper() {
        return machineHelper;
    }

    public static Resources getRes() {
        return resources;
    }

    // Verify if the application was killed due to system's process termination.
    public static void validateOperation(final Context context) {
        if (machineHelper == null || database == null || resources == null || !database.isOpen()) {
            Log.w("MainValidate", "Process was killed. Reloading resources.");
            resources = context.getResources();
            initDatabase(context, false);
        }
    }

    // When there is an incomplete database query, reload the database.
    public static void reloadDatabase(final Context context) {
        Log.w("Database", "Reload requested.");
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, "Database reload requested", Toast.LENGTH_SHORT).show();
        }
        closeDatabase();
        initDatabase(context, false);
    }

    public static boolean getMainState() {
        return isMainRunning;
    }
}
