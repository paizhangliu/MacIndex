package com.macindex.macindex;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.macindex.macindex.catalog.BrowseGroup;
import com.macindex.macindex.catalog.BrowseGrouping;
import com.macindex.macindex.catalog.BrowseScope;
import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.userstate.AppStateRepository;
import com.macindex.macindex.userstate.FavouriteFolder;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * MacIndex.
 * University of Illinois, CS125 FA19 Final Project
 * University of Illinois, CS199 Kotlin SP20 Final Project
 * https://macindex.paizhang.info/
 * https://github.com/paizhangliu/MacIndex
 *
 * Basic functionality was finished on 16:12 CST, Dec 2, 2019.
 * 3.0 Update May 12, 2020 at Champaign, Illinois, U.S.A.
 * 4.0 Update June 13, 2020 at Shenyang, Liaoning, China.
 * 4.5 Update January 7, 2021 at Jinzhong, Shanxi, China.
 * 4.9 Update July 22, 2026 at Jinzhong, Shanxi, China.
 */
public class MainActivity extends AppCompatActivity {

    private static final int DRAWER_OPEN_EDGE_DP = 200;
    private static final String STATE_DRAWER_OPEN = "drawerOpen";
    private static final String STATE_SCOPE = "mainBrowseScope";
    private static final String STATE_GROUPING = "mainBrowseGrouping";
    private static final String STATE_EXTERNAL_REQUEST_CONSUMED =
            "externalRequestConsumed";

    private DrawerLayout drawerLayout;
    private BrowseScope browseScope = BrowseScope.ALL;
    private BrowseGrouping browseGrouping = BrowseGrouping.NAMES;
    private List<BrowseGroup> browseGroups = Collections.emptyList();
    private final List<View> loadedMachineRows = new ArrayList<>();
    private Set<String> favouriteUids = Collections.emptySet();
    private MachineCatalog catalog;
    private UserState userState;
    private UserStateLifecycleAdapter userStateAdapter;
    private Bundle restorationState;
    private boolean initialized;
    private boolean startupPending = true;
    private boolean drawerGesture;
    private float drawerGestureDownX;
    private float drawerGestureDownY;
    private int drawerOpenEdgeWidth;
    private int drawerTouchSlop;
    private boolean trackingWideDrawerGesture;
    private boolean consumingWideDrawerGesture;
    private Runnable pendingDrawerAction;
    private boolean pendingNoticePresented;
    private boolean externalRequestConsumed;

    private AutomaticUpdateCoordinator updateCoordinator;
    private UpdateCheckState updateState;
    private AlertDialog crashReportDialog;
    private AlertDialog updateDialog;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> startupPending);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ContentInsetsHelper.apply(this);
        restorationState = savedInstanceState;
        final boolean restoredRequestConsumed = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_EXTERNAL_REQUEST_CONSUMED, false);
        // A new shortcut or app link may revive a killed singleTask Activity together with its
        // old saved state. The new Intent always wins over the old "already consumed" marker.
        externalRequestConsumed = restoreExternalRequestConsumed(
                restoredRequestConsumed, getIntent());
        initMenu();
        initBackHandling();

        final MacIndexApplication application = (MacIndexApplication) getApplication();
        updateCoordinator = application.automaticUpdateCoordinator();
        updateCoordinator.getState().observe(this, state -> {
            updateState = state;
            renderAutomaticUpdate();
        });

        StartupUiGate.bind(this, () -> startupPending = false,
                (readyCatalog, repository) -> {
                    catalog = readyCatalog;
                    observeUserState(repository);
                });
    }

    private void initBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackStarted(@NonNull final BackEventCompat backEvent) {
                final int drawerEdge = ViewCompat.getLayoutDirection(drawerLayout)
                        == ViewCompat.LAYOUT_DIRECTION_RTL
                        ? BackEventCompat.EDGE_RIGHT : BackEventCompat.EDGE_LEFT;
                drawerGesture = drawerLayout != null
                        && !drawerLayout.isDrawerVisible(GravityCompat.START)
                        && backEvent.getSwipeEdge() == drawerEdge;
            }

            @Override
            public void handleOnBackPressed() {
                if (drawerGesture) {
                    drawerGesture = false;
                    drawerLayout.openDrawer(GravityCompat.START);
                } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }

            @Override
            public void handleOnBackCancelled() {
                drawerGesture = false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        // Keep the app's forgiving legacy swipe region without touching DrawerLayout internals.
        final int action = event.getActionMasked();
        if (consumingWideDrawerGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                consumingWideDrawerGesture = false;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            drawerGestureDownX = event.getX();
            drawerGestureDownY = event.getY();
            trackingWideDrawerGesture = drawerLayout != null
                    && !drawerLayout.isDrawerVisible(GravityCompat.START)
                    && drawerLayout.getDrawerLockMode(GravityCompat.START)
                    == DrawerLayout.LOCK_MODE_UNLOCKED
                    && isInDrawerOpenEdge(drawerGestureDownX);
        } else if ((action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) && trackingWideDrawerGesture) {
            trackingWideDrawerGesture = false;
        } else if (action == MotionEvent.ACTION_MOVE && trackingWideDrawerGesture) {
            final float horizontal = drawerOpeningDistance(event.getX());
            final float vertical = Math.abs(event.getY() - drawerGestureDownY);
            if (vertical > drawerTouchSlop && vertical > Math.abs(horizontal)) {
                trackingWideDrawerGesture = false;
            } else if (horizontal > drawerTouchSlop && horizontal > vertical) {
                trackingWideDrawerGesture = false;
                consumingWideDrawerGesture = true;
                final MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            trackingWideDrawerGesture = false;
        }

        return super.dispatchTouchEvent(event);
    }

    private boolean isInDrawerOpenEdge(final float x) {
        if (ViewCompat.getLayoutDirection(drawerLayout) == ViewCompat.LAYOUT_DIRECTION_RTL) {
            return x >= drawerLayout.getWidth() - drawerOpenEdgeWidth;
        }
        return x <= drawerOpenEdgeWidth;
    }

    private float drawerOpeningDistance(final float x) {
        if (ViewCompat.getLayoutDirection(drawerLayout) == ViewCompat.LAYOUT_DIRECTION_RTL) {
            return drawerGestureDownX - x;
        }
        return x - drawerGestureDownX;
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
        userState = state;
        favouriteUids = collectFavouriteUids(state);
        refreshFavouriteIndicators();
        resetDrawerSelection();

        if (!initialized) {
            if (restorationState != null) {
                browseScope = restoreEnum(restorationState, STATE_SCOPE,
                        BrowseScope.class, state.getUiMemory().getMainScope());
                browseGrouping = restoreEnum(restorationState, STATE_GROUPING,
                        BrowseGrouping.class, state.getUiMemory().getMainGrouping());
            } else if (state.getPreferences().getRememberMainState()) {
                browseScope = state.getUiMemory().getMainScope();
                browseGrouping = state.getUiMemory().getMainGrouping();
            } else {
                browseScope = BrowseScope.ALL;
                browseGrouping = BrowseGrouping.NAMES;
            }
            initInterface();
            initialized = true;
            if (restorationState != null
                    && restorationState.getBoolean(STATE_DRAWER_OPEN, false)) {
                resetDrawerTitle();
                resetDrawerSelection();
            }
            restorationState = null;
            continueStartupPresentation();
            return;
        }

        if (state.getPendingNotice() == null) {
            pendingNoticePresented = false;
        }
        continueStartupPresentation();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        // Only the latest request survives startup. Once READY, consuming clears it exactly once.
        setIntent(intent);
        externalRequestConsumed = false;
        if (initialized) {
            continueStartupPresentation();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (initialized) {
            continueStartupPresentation();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAWER_OPEN,
                drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START));
        outState.putString(STATE_SCOPE, browseScope.name());
        outState.putString(STATE_GROUPING, browseGrouping.name());
        outState.putBoolean(STATE_EXTERNAL_REQUEST_CONSUMED, externalRequestConsumed);
    }

    @Override
    protected void onDestroy() {
        pendingDrawerAction = null;
        if (crashReportDialog != null) {
            crashReportDialog.dismiss();
            crashReportDialog = null;
        }
        if (updateDialog != null) {
            updateDialog.setOnCancelListener(null);
            updateDialog.dismiss();
            updateDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        } else if (itemId == R.id.mainExpandAllItem) {
            setAllCategoriesVisibility(true);
        } else if (itemId == R.id.mainCollapseAllItem) {
            setAllCategoriesVisibility(false);
        } else if (itemId == R.id.mainResetItem) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            setBrowseState(BrowseScope.ALL, BrowseGrouping.NAMES);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void initMenu() {
        drawerLayout = findViewById(R.id.mainContainer);
        drawerOpenEdgeWidth = Math.round(DRAWER_OPEN_EDGE_DP
                * getResources().getDisplayMetrics().density);
        drawerTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        bindBrowseItem(R.id.group0MenuItem, BrowseScope.ALL, null);
        bindBrowseItem(R.id.group1MenuItem, BrowseScope.APPLE_68K, null);
        bindBrowseItem(R.id.group2MenuItem, BrowseScope.POWERPC, null);
        bindBrowseItem(R.id.group3MenuItem, BrowseScope.INTEL, null);
        bindBrowseItem(R.id.group4MenuItem, BrowseScope.APPLE_SILICON, null);
        bindBrowseItem(R.id.view1MenuItem, null, BrowseGrouping.NAMES);
        bindBrowseItem(R.id.view2MenuItem, null, BrowseGrouping.PROCESSORS);
        bindBrowseItem(R.id.view3MenuItem, null, BrowseGrouping.YEARS);

        findViewById(R.id.searchMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, SearchActivity.class))));
        findViewById(R.id.randomMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(this::openRandom));
        findViewById(R.id.favouriteMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, FavouriteActivity.class))));
        findViewById(R.id.compareMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, CompareActivity.class))));
        findViewById(R.id.commentMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, CommentActivity.class))));
        findViewById(R.id.aboutMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, SettingsAboutActivity.class))));
        findViewById(R.id.newAboutMenuItem).setOnClickListener(view ->
                closeDrawerWithAction(() -> startActivity(
                        new Intent(this, NewAboutActivity.class))));

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull final View drawerView, final float slideOffset) {
                // No state change.
            }

            @Override
            public void onDrawerOpened(@NonNull final View drawerView) {
                resetDrawerTitle();
            }

            @Override
            public void onDrawerClosed(@NonNull final View drawerView) {
                runPendingDrawerAction();
                setTitle(translateTitleRes());
            }

            @Override
            public void onDrawerStateChanged(final int newState) {
                resetDrawerSelection();
            }
        });

        final Toolbar toolbar = findViewById(R.id.mainToolbar);
        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, 0, 0);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        setSupportActionBar(toolbar);
    }

    private void bindBrowseItem(final int viewId,
                                final BrowseScope scope,
                                final BrowseGrouping grouping) {
        findViewById(viewId).setOnClickListener(view -> closeDrawerWithAction(() ->
                setBrowseState(scope == null ? browseScope : scope,
                        grouping == null ? browseGrouping : grouping)));
    }

    private void setBrowseState(final BrowseScope scope, final BrowseGrouping grouping) {
        if (!initialized || (browseScope == scope && browseGrouping == grouping)) {
            return;
        }
        browseScope = scope;
        browseGrouping = grouping;
        initInterface();
        userStateAdapter.execute(UserStateCommands.setMainBrowseState(
                        scope, grouping),
                ignored -> { },
                error -> ExceptionHelper.showUserStateWriteFailure(this, error,
                        R.string.menu_view, R.string.main_browse_save_failed));
    }

    private void closeDrawerWithAction(final Runnable action) {
        pendingDrawerAction = action;
        if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            runPendingDrawerAction();
        }
    }

    private void runPendingDrawerAction() {
        final Runnable action = pendingDrawerAction;
        pendingDrawerAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void resetDrawerTitle() {
        setTitle(R.string.app_name);
    }

    private void resetDrawerSelection() {
        if (drawerLayout == null) {
            return;
        }
        updateDrawerSelection(findViewById(R.id.groupLayout), translateScopeMenuRes());
        updateDrawerSelection(findViewById(R.id.viewLayout), translateGroupingMenuRes());
        final TextView random = findViewById(R.id.randomMenuItem);
        final boolean limited = userState != null
                && userState.getPreferences().getLimitRandomToCurrentBrowse();
        random.setText(limited ? R.string.menu_random_limited_label : R.string.menu_random);
    }

    private void updateDrawerSelection(final LinearLayout layout, final int selectedId) {
        for (int i = 1; i < layout.getChildCount(); i++) {
            if (!(layout.getChildAt(i) instanceof TextView)) {
                continue;
            }
            final TextView item = (TextView) layout.getChildAt(i);
            final boolean selected = item.getId() == selectedId;
            item.setEnabled(!selected);
            item.setTextColor(ContextCompat.getColor(this,
                    selected ? android.R.color.white : R.color.colorDefaultText));
            item.setBackgroundColor(ContextCompat.getColor(this,
                    selected ? R.color.colorSelectedBackground : R.color.colorBackground));
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, selected ? R.drawable.ic_baseline_check_24_white : 0, 0);
        }
    }

    private void initInterface() {
        setTitle(translateTitleRes());
        browseGroups = catalog.browseGroups(browseScope, browseGrouping);
        loadedMachineRows.clear();
        final LinearLayout container = findViewById(R.id.categoryContainer);
        final LayoutTransition transition = container.getLayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        container.setLayoutTransition(null);
        try {
            container.removeAllViews();
            for (BrowseGroup group : browseGroups) {
                if (group.sectionKey() != null) {
                    final View section = getLayoutInflater().inflate(
                            R.layout.chunk_main_section, container, false);
                    ((TextView) section.findViewById(R.id.mainSection)).setText(
                            translateSectionRes(group.sectionKey()));
                    container.addView(section);
                }
                final View category = getLayoutInflater().inflate(
                        R.layout.chunk_category, container, false);
                final LinearLayout categoryLayout =
                        category.findViewById(R.id.categoryInfoLayout);
                final TextView categoryName = category.findViewById(R.id.category);
                TextViewCompat.setAutoSizeTextTypeWithDefaults(categoryName,
                        TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                categoryName.setText(group.label());
                categoryName.setOnClickListener(new View.OnClickListener() {
                    private boolean loaded;

                    @Override
                    public void onClick(final View view) {
                        if (!loaded) {
                            populateCategory(categoryLayout, group);
                            loaded = true;
                        }
                        setCategoryVisibility(categoryLayout, categoryName,
                                !categoryName.isActivated());
                    }
                });
                container.addView(category);
            }
            removeLastCategoryDivider(container);
        } finally {
            container.setLayoutTransition(transition);
        }
        resetDrawerSelection();
    }

    private void populateCategory(final LinearLayout layout, final BrowseGroup group) {
        for (Machine machine : group.machines()) {
            final View row = MachineRowBinder.inflate(getLayoutInflater(), layout);
            MachineRowBinder.bindCatalogMachine(
                    row,
                    machine,
                    favouriteUids.contains(machine.uid()),
                    ignored -> openMachine(machine, group.machines(), false));
            row.setVisibility(View.GONE);
            layout.addView(row);
            loadedMachineRows.add(row);
        }
    }

    private static void setCategoryVisibility(final LinearLayout layout,
                                              final TextView categoryName,
                                              final boolean visible) {
        if (layout.getChildCount() < 2) {
            categoryName.setActivated(visible);
            return;
        }
        final boolean hasDivider = !(layout.getChildAt(1) instanceof LinearLayout);
        final int firstMachine = hasDivider ? 2 : 1;
        for (int i = firstMachine; i < layout.getChildCount(); i++) {
            layout.getChildAt(i).setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (hasDivider) {
            layout.getChildAt(1).setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        categoryName.setActivated(visible);
    }

    private static void removeLastCategoryDivider(final LinearLayout container) {
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            final LinearLayout category = container.getChildAt(i)
                    .findViewById(R.id.categoryInfoLayout);
            if (category != null && category.getChildCount() > 1) {
                category.removeViewAt(1);
                return;
            }
        }
    }

    private void setAllCategoriesVisibility(final boolean visible) {
        final LinearLayout container = findViewById(R.id.categoryContainer);
        final LayoutTransition outerTransition = container.getLayoutTransition();
        container.setLayoutTransition(null);
        try {
            for (int i = 0; i < container.getChildCount(); i++) {
                final LinearLayout category = container.getChildAt(i)
                        .findViewById(R.id.categoryInfoLayout);
                if (category == null) {
                    continue;
                }
                final TextView name = category.findViewById(R.id.category);
                if (name.isActivated() != visible) {
                    final LayoutTransition transition = category.getLayoutTransition();
                    category.setLayoutTransition(null);
                    name.performClick();
                    category.setLayoutTransition(transition);
                }
            }
        } finally {
            container.setLayoutTransition(outerTransition);
        }
    }

    private void refreshFavouriteIndicators() {
        for (View row : loadedMachineRows) {
            MachineRowBinder.refreshFavourite(row, favouriteUids);
        }
    }

    private void openRandom() {
        if (!initialized || catalog.machines().isEmpty()) {
            return;
        }
        final boolean limited = userState.getPreferences().getLimitRandomToCurrentBrowse();
        final List<Machine> candidates;
        if (limited) {
            candidates = catalog.scopeMachines(browseScope);
        } else {
            candidates = catalog.machines();
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Random machine range is empty");
        }
        final Machine machine = candidates.get(new Random().nextInt(candidates.size()));
        openMachine(machine, Collections.singletonList(machine), false);
    }

    private void openMachine(final Machine machine,
                             final List<Machine> normalNavigation,
                             final boolean forceNavigationButtons) {
        final List<Machine> navigation;
        if (!forceNavigationButtons && userState.getPreferences().getFixedNavigation()) {
            navigation = catalog.sequenceForProductType(machine.productTypeKey());
        } else {
            navigation = normalNavigation;
        }
        startActivity(NavigationContract.machineSpecsIntent(this,
                NavigationContract.MachineRequest.create(
                        machine, navigation, forceNavigationButtons)));
    }

    private boolean consumeExternalLaunch(final Intent intent) {
        if (intent == null || !initialized) {
            return false;
        }
        final Uri deepLink = intent.getData();
        if (deepLink != null) {
            externalRequestConsumed = true;
            intent.setData(null);
            intent.setAction(null);
            decodeDeepLink(deepLink.toString());
            return true;
        }

        final NavigationContract.ShortcutDestination destination =
                NavigationContract.getShortcutDestination(intent);
        if (destination == null) {
            return false;
        }
        externalRequestConsumed = true;
        intent.setAction(null);
        switch (destination) {
            case RANDOM:
                openRandom();
                break;
            case SEARCH:
                startActivity(new Intent(this, SearchActivity.class));
                break;
            case FAVOURITES:
                startActivity(new Intent(this, FavouriteActivity.class));
                break;
            case COMMENTS:
                startActivity(new Intent(this, CommentActivity.class));
                break;
            default:
                throw new IllegalStateException("Unhandled shortcut " + destination);
        }
        return true;
    }

    static boolean hasExternalRequest(final Intent intent) {
        return intent != null && (intent.getData() != null
                || NavigationContract.getShortcutDestination(intent) != null);
    }

    static boolean restoreExternalRequestConsumed(final boolean restoredConsumed,
                                                   final Intent currentIntent) {
        return restoredConsumed && !hasExternalRequest(currentIntent);
    }

    private void decodeDeepLink(final String deepLink) {
        try {
            if (ShareLinkHelper.isComparison(deepLink)) {
                final String[] uids = resolveSharedMachines(
                        ShareLinkHelper.decodeComparison(deepLink), catalog);
                startActivity(NavigationContract.comparisonIntent(this,
                        NavigationContract.ComparisonRequest.create(uids[0], uids[1])));
                return;
            }
            final String uid = resolveSharedMachines(
                    new String[]{ShareLinkHelper.decode(deepLink)}, catalog)[0];
            final Machine machine = catalog.requireByUid(uid);
            openMachine(machine, Collections.singletonList(machine), false);
        } catch (IllegalArgumentException e) {
            Log.w("DeepLink", "Unable to process an invalid share link.", e);
            ExceptionHelper.showMessageDialog(this,
                    R.string.share_main_decode_failed_title,
                    R.string.share_main_decode_failed,
                    this::continueStartupPresentation);
        }
    }

    static String[] resolveSharedMachines(final String[] identities,
                                          final MachineCatalog catalog) {
        final String[] result = new String[identities.length];
        for (int i = 0; i < identities.length; i++) {
            final String identity = identities[i] == null ? "" : identities[i].trim();
            final Machine machine = catalog.findByUid(identity);
            if (machine != null) {
                result[i] = machine.uid();
            } else {
                final Machine legacyMachine = catalog.resolveLegacyName(identity);
                if (legacyMachine == null) {
                    throw new IllegalArgumentException("Unknown shared machine");
                }
                result[i] = legacyMachine.uid();
            }
        }
        return result;
    }

    private void continueStartupPresentation() {
        if (!initialized || userStateAdapter == null || userState == null
                || crashReportDialog != null || isFinishing() || isDestroyed()) {
            return;
        }
        final MacIndexApplication application = (MacIndexApplication) getApplication();
        crashReportDialog = application.presentLastCrashReport(
                this, this::onCrashReportAcknowledged);
        if (crashReportDialog != null) {
            return;
        }
        if (!externalRequestConsumed && consumeExternalLaunch(getIntent())) {
            return;
        }
        presentNoticeOrCheckForUpdates();
    }

    private void onCrashReportAcknowledged() {
        crashReportDialog = null;
        if (!isFinishing() && !isDestroyed()) {
            getWindow().getDecorView().post(this::continueStartupPresentation);
        }
    }

    private void presentNoticeOrCheckForUpdates() {
        if (!initialized || userStateAdapter == null || userState == null) {
            return;
        }
        if (userState.getPendingNotice() != null) {
            if (!pendingNoticePresented) {
                PendingNoticePresenter.show(
                        this, userState.getPendingNotice(), userStateAdapter);
                pendingNoticePresented = true;
            }
            return;
        }
        pendingNoticePresented = false;
        updateCoordinator.checkIfEnabled(
                userState.getPreferences().getAutomaticallyCheckUpdates(),
                BuildConfig.VERSION_NAME,
                userState.getPreferences().getSkippedUpdateVersion());
        renderAutomaticUpdate();
    }

    private void renderAutomaticUpdate() {
        if (!initialized || userState == null || crashReportDialog != null
                || userState.getPendingNotice() != null
                || updateState == null
                || !userState.getPreferences().getAutomaticallyCheckUpdates()
                || updateDialog != null || isFinishing() || isDestroyed()) {
            return;
        }
        if (updateState.getStatus() != UpdateCheckState.Status.AVAILABLE
                || updateState.getResult().getLatest().getVersion().equals(
                userState.getPreferences().getSkippedUpdateVersion())) {
            return;
        }
        final UpdateCheckState rendered = updateState;
        updateDialog = UpdateDialogPresenter.show(this, rendered, true,
                new UpdateDialogPresenter.Listener() {
                    @Override
                    public void onOpen(final UpdateChecker.Information information) {
                        LinkLoadingHelper.startBrowser(
                                information.getReleasePage(), MainActivity.this);
                        acknowledgeAutomaticUpdate(rendered);
                    }

                    @Override
                    public void onSkip(final UpdateChecker.Information information) {
                        acknowledgeAutomaticUpdate(rendered);
                        userStateAdapter.execute(
                                UserStateCommands.setSkippedUpdateVersion(
                                        information.getVersion()),
                                ignored -> { },
                                error -> ExceptionHelper.showUserStateWriteFailure(
                                        MainActivity.this, error,
                                        R.string.update_skip, R.string.setting_save_failed));
                    }

                    @Override
                    public void onAcknowledge() {
                        acknowledgeAutomaticUpdate(rendered);
                    }
                });
    }

    private void acknowledgeAutomaticUpdate(final UpdateCheckState rendered) {
        updateDialog = null;
        updateCoordinator.acknowledge(rendered);
    }

    private int translateTitleRes() {
        switch (browseScope) {
            case ALL:
                return R.string.menu_group0;
            case APPLE_68K:
                return R.string.menu_group1;
            case POWERPC:
                return R.string.menu_group2;
            case INTEL:
                return R.string.menu_group3;
            case APPLE_SILICON:
                return R.string.menu_group4;
            default:
                throw new IllegalStateException("Unhandled browse scope " + browseScope);
        }
    }

    private int translateScopeMenuRes() {
        switch (browseScope) {
            case ALL:
                return R.id.group0MenuItem;
            case APPLE_68K:
                return R.id.group1MenuItem;
            case POWERPC:
                return R.id.group2MenuItem;
            case INTEL:
                return R.id.group3MenuItem;
            case APPLE_SILICON:
                return R.id.group4MenuItem;
            default:
                throw new IllegalStateException("Unhandled browse scope " + browseScope);
        }
    }

    private int translateGroupingMenuRes() {
        switch (browseGrouping) {
            case NAMES:
                return R.id.view1MenuItem;
            case PROCESSORS:
                return R.id.view2MenuItem;
            case YEARS:
                return R.id.view3MenuItem;
            default:
                throw new IllegalStateException("Unhandled browse grouping " + browseGrouping);
        }
    }

    private int translateSectionRes(final String section) {
        switch (section) {
            case "desktop":
                return R.string.main_section_desktop;
            case "laptop":
                return R.string.main_section_laptop;
            case "server":
                return R.string.main_section_server;
            default:
                throw new IllegalArgumentException("Unknown main section " + section);
        }
    }

    private static Set<String> collectFavouriteUids(final UserState state) {
        final Set<String> result = new HashSet<>();
        for (FavouriteFolder folder : state.getLibrary().getFavouriteFolders()) {
            result.addAll(folder.getMachineUids());
        }
        return result;
    }

    private static <T extends Enum<T>> T restoreEnum(final Bundle state,
                                                      final String key,
                                                      final Class<T> type,
                                                      final T fallback) {
        final String raw = state.getString(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ignored) {
            Log.w("MainState", "Ignoring an invalid restored value for " + key + '.');
            return fallback;
        }
    }
}
