package com.macindex.macindex;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.resources.LogoAsset;
import com.macindex.macindex.resources.MachineResourceRegistry;
import com.macindex.macindex.userstate.AppStateRepository;
import com.macindex.macindex.userstate.CompareSelection;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommand;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;
import com.macindex.macindex.userstate.UserStateSuccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Displays either the user's selected comparison or a read-only shared comparison. */
public class CompareActivity extends AppCompatActivity {

    private static final String STATE_SHARED_LEFT_UID = "sharedLeftUid";
    private static final String STATE_SHARED_RIGHT_UID = "sharedRightUid";
    private static final String STATE_TRANSIENT_LEFT_UID = "transientLeftUid";
    private static final String STATE_TRANSIENT_RIGHT_UID = "transientRightUid";

    private boolean isAbleToInitialize;
    private boolean isInitialized;
    private boolean isAbleToManage;

    private MenuItem initialMenuItem;
    private MenuItem clearColumnMenuItem;
    private MenuItem exchangeColumnMenuItem;
    private MenuItem copyCompareMenuItem;
    private MenuItem shareLinkCompareMenuItem;
    private MenuItem manageListMenuItem;
    private MenuItem clearListMenuItem;
    private MenuItem highlightDifferencesMenuItem;

    private SpecsHelper specsHelperLeft;
    private SpecsHelper specsHelperRight;
    private MachineCatalog catalog;
    private UserStateLifecycleAdapter stateAdapter;
    private UserState currentState;
    private LifecycleMachineImageLoader imageLoader;
    @Nullable
    private NavigationContract.ComparisonRequest sharedRequest;
    @Nullable
    private NavigationContract.ComparisonRequest transientSelection;
    private CompareRenderKey renderedKey;

    private Machine leftMachine;
    private Machine rightMachine;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);
        ContentInsetsHelper.apply(this);
        specsHelperLeft = new SpecsHelper(this);
        specsHelperRight = new SpecsHelper(this);
        imageLoader = new LifecycleMachineImageLoader(this, getAssets());
        setTitle(getString(R.string.menu_compare));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        restoreComparisonState(savedInstanceState);
        StartupUiGate.bind(this, this::initializeState);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (currentState != null && leftMachine != null && rightMachine != null
                && isInitialized) {
            bindSounds(leftMachine, rightMachine, currentState);
        }
    }

    @Override
    protected void onStop() {
        release();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        if (sharedRequest != null) {
            outState.putString(STATE_SHARED_LEFT_UID, sharedRequest.getLeftUID());
            outState.putString(STATE_SHARED_RIGHT_UID, sharedRequest.getRightUID());
        }
        if (transientSelection != null) {
            outState.putString(STATE_TRANSIENT_LEFT_UID, transientSelection.getLeftUID());
            outState.putString(STATE_TRANSIENT_RIGHT_UID, transientSelection.getRightUID());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_compare, menu);
        initialMenuItem = menu.findItem(R.id.initCompareItem);
        clearColumnMenuItem = menu.findItem(R.id.clearColumnCompareItem);
        exchangeColumnMenuItem = menu.findItem(R.id.switchCompareItem);
        copyCompareMenuItem = menu.findItem(R.id.copyCompareItem);
        shareLinkCompareMenuItem = menu.findItem(R.id.shareLinkCompareItem);
        manageListMenuItem = menu.findItem(R.id.manageCompareItem);
        clearListMenuItem = menu.findItem(R.id.clearCompareItem);
        highlightDifferencesMenuItem = menu.findItem(R.id.highlightDifferencesCompareItem);
        if (currentState != null) {
            highlightDifferencesMenuItem.setChecked(
                    currentState.getPreferences().getHighlightCompareDifferences());
        }
        updateMenuState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.initCompareItem) {
            showSelectionDialog();
        } else if (itemID == R.id.clearColumnCompareItem) {
            clearDisplayedComparison();
        } else if (itemID == R.id.switchCompareItem) {
            swapColumns();
        } else if (itemID == R.id.copyCompareItem) {
            if (isInitialized) {
                specsHelperLeft.copySpecification(
                        new String[]{leftMachine.name(), rightMachine.name()},
                        new String[][]{
                                specsHelperLeft.specification(leftMachine),
                                specsHelperRight.specification(rightMachine)});
            }
        } else if (itemID == R.id.shareLinkCompareItem) {
            if (isInitialized) {
                specsHelperLeft.generateShareLink(leftMachine.uid(), rightMachine.uid());
            }
        } else if (itemID == R.id.manageCompareItem) {
            showManageListDialog();
        } else if (itemID == R.id.clearCompareItem) {
            showClearListDialog();
        } else if (itemID == R.id.highlightDifferencesCompareItem) {
            if (currentState != null) {
                execute(UserStateCommands.setHighlightCompareDifferences(!item.isChecked()),
                        ignored -> { }, R.string.submenu_compare_highlight_differences,
                        R.string.compare_highlight_save_failed);
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initializeState(@NonNull final MachineCatalog readyCatalog,
                                 @NonNull final AppStateRepository repository) {
        if (stateAdapter != null) {
            return;
        }
        catalog = readyCatalog;
        stateAdapter = new UserStateLifecycleAdapter(
                this,
                repository,
                state -> {
                    currentState = state;
                    render(state);
                },
                error -> ExceptionHelper.showUserStateReadFailure(this, error));
    }

    private void render(@NonNull final UserState state) {
        discardInvalidTransientSelection(state);
        final CompareRenderKey nextKey = new CompareRenderKey(
                state, sharedRequest, transientSelection);
        if (nextKey.equals(renderedKey)) {
            final List<String> compareUIDs = state.getLibrary()
                    .getCompare().getMachineUids();
            setMenuState(compareUIDs.size() >= 2, nextKey.hasComparison(),
                    !compareUIDs.isEmpty());
            if (highlightDifferencesMenuItem != null) {
                highlightDifferencesMenuItem.setChecked(
                        state.getPreferences().getHighlightCompareDifferences());
            }
            applyDifferenceHighlight(
                    state.getPreferences().getHighlightCompareDifferences());
            return;
        }
        release();
        leftMachine = null;
        rightMachine = null;
        setMenuState(false, false, false);
        boolean completed = false;
        try {
            final CompareSelection selection = state.getLibrary().getCompare();
            final List<String> compareUIDs = selection.getMachineUids();
            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final LinearLayout initialLayout = findViewById(R.id.initialLayout);
            final TextView emptyText = findViewById(R.id.emptyText);
            final TextView initialText = findViewById(R.id.initialText);
            final ScrollView compareScroll = findViewById(R.id.compareScroll);

            if (highlightDifferencesMenuItem != null) {
                highlightDifferencesMenuItem.setChecked(
                        state.getPreferences().getHighlightCompareDifferences());
            }

            if (sharedRequest != null) {
                showComparison(
                        catalog.requireByUid(sharedRequest.getLeftUID()),
                        catalog.requireByUid(sharedRequest.getRightUID()),
                        state,
                        initialLayout,
                        emptyLayout,
                        compareScroll,
                        compareUIDs);
                completed = true;
                return;
            }

            if (transientSelection != null) {
                showComparison(
                        catalog.requireByUid(transientSelection.getLeftUID()),
                        catalog.requireByUid(transientSelection.getRightUID()),
                        state,
                        initialLayout,
                        emptyLayout,
                        compareScroll,
                        compareUIDs);
                completed = true;
                return;
            }

            if (compareUIDs.size() >= 2) {
                final String left = selection.getLeftUid();
                final String right = selection.getRightUid();
                if (!left.equals(right) && compareUIDs.contains(left)
                        && compareUIDs.contains(right)) {
                    showComparison(catalog.requireByUid(left), catalog.requireByUid(right),
                            state, initialLayout, emptyLayout, compareScroll, compareUIDs);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(initialText,
                            TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                    initialLayout.setVisibility(View.VISIBLE);
                    emptyLayout.setVisibility(View.GONE);
                    compareScroll.setVisibility(View.GONE);
                    setMenuState(true, false, true);
                }
                completed = true;
                return;
            }

            emptyText.setText(getResources().getStringArray(R.array.compare_insufficient_tips)
                    [compareUIDs.isEmpty() ? 0 : 1]);
            TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText,
                    TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            initialLayout.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
            compareScroll.setVisibility(View.GONE);
            setMenuState(false, false, !compareUIDs.isEmpty());
            completed = true;
        } finally {
            renderedKey = completed ? nextKey : null;
        }
    }

    private void showComparison(@NonNull final Machine left,
                                @NonNull final Machine right,
                                @NonNull final UserState state,
                                @NonNull final LinearLayout initialLayout,
                                @NonNull final LinearLayout emptyLayout,
                                @NonNull final ScrollView compareScroll,
                                @NonNull final List<String> compareUIDs) {
        initialLayout.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.GONE);
        compareScroll.setVisibility(View.VISIBLE);
        loadSpecs(left, right, state);
        setMenuState(compareUIDs.size() >= 2, true, !compareUIDs.isEmpty());
    }

    private void loadSpecs(@NonNull final Machine left,
                           @NonNull final Machine right,
                           @NonNull final UserState state) {
        leftMachine = left;
        rightMachine = right;

        setMachineImage(R.id.picLeft, left);
        setMachineImage(R.id.picRight, right);
        bindSounds(left, right, state);
        setProcessorTypeImage(R.id.processorTypeImageLeft,
                R.id.processorTypeImageLayoutLeft, left);
        setProcessorTypeImage(R.id.processorTypeImageRight,
                R.id.processorTypeImageLayoutRight, right);

        final ImageView everymac = findViewById(R.id.everymac);
        specsHelperLeft.initLinks(left, right, everymac);
        ThemeHelper.applyInvertedLogo(this, everymac);

        final TextView nameLeft = findViewById(R.id.nameTextLeft);
        final TextView nameRight = findViewById(R.id.nameTextRight);
        reloadName(nameLeft, left.name());
        reloadName(nameRight, right.name());
        final boolean fixedNavigation = state.getPreferences().getFixedNavigation();
        nameLeft.setOnClickListener(view -> UserLibraryViewAdapter.openMachine(
                this, catalog, fixedNavigation, Collections.singletonList(left), left));
        nameRight.setOnClickListener(view -> UserLibraryViewAdapter.openMachine(
                this, catalog, fixedNavigation, Collections.singletonList(right), right));

        final int[] labels = {R.string.year, R.string.model, R.string.id, R.string.gestalt,
                R.string.order, R.string.codename, R.string.emc, R.string.processor,
                R.string.graphics,
                R.string.display, R.string.maxram, R.string.type, R.string.software,
                R.string.storage, R.string.features, R.string.expansion, R.string.design,
                R.string.support};
        final String[] leftSpecification = specsHelperLeft.specification(left);
        final String[] rightSpecification = specsHelperRight.specification(right);
        final boolean isLeftClassic = isClassic(left);
        final boolean isRightClassic = isClassic(right);

        final LinearLayout specsContainer = findViewById(R.id.compareSpecsContainer);
        specsContainer.removeAllViews();
        for (int index = 0; index < labels.length; index++) {
            if ((isLeftClassic && isRightClassic
                    && (labels[index] == R.string.id || labels[index] == R.string.emc))
                    || (!isLeftClassic && !isRightClassic
                    && labels[index] == R.string.gestalt)) {
                continue;
            }
            final View row = getLayoutInflater().inflate(
                    R.layout.chunk_compare_row, specsContainer, false);
            ((TextView) row.findViewById(R.id.compareTitle)).setText(labels[index]);
            final TextView compareLeft = row.findViewById(R.id.compareLeft);
            final TextView compareRight = row.findViewById(R.id.compareRight);
            final String leftInfo = leftSpecification[index];
            final String rightInfo = rightSpecification[index];
            row.setTag(!Objects.equals(leftInfo, rightInfo));
            if (labels[index] == R.string.processor) {
                compareLeft.setText(specsHelperLeft.formatModels(
                        leftInfo, left.processorModelRanges()));
                compareRight.setText(specsHelperRight.formatModels(
                        rightInfo, right.processorModelRanges()));
            } else if (labels[index] == R.string.graphics) {
                compareLeft.setText(specsHelperLeft.formatModels(
                        leftInfo, left.graphicsModelRanges()));
                compareRight.setText(specsHelperRight.formatModels(
                        rightInfo, right.graphicsModelRanges()));
            } else if (labels[index] == R.string.order) {
                specsHelperLeft.initPartNumbers(compareLeft, leftInfo);
                specsHelperRight.initPartNumbers(compareRight, rightInfo);
            } else {
                compareLeft.setText(specsHelperLeft.getDisplayInfo(leftInfo));
                compareRight.setText(specsHelperRight.getDisplayInfo(rightInfo));
            }
            specsHelperLeft.initCompareCopy(
                    compareLeft, left.name(), leftInfo, right.name(), rightInfo);
            specsHelperLeft.initCompareCopy(
                    compareRight, left.name(), leftInfo, right.name(), rightInfo);
            if (index == labels.length - 1) {
                specsHelperLeft.setSupportColor(compareLeft, left.supportStatus());
                specsHelperLeft.setSupportColor(compareRight, right.supportStatus());
            }
            specsContainer.addView(row);
        }
        applyDifferenceHighlight(state.getPreferences().getHighlightCompareDifferences());
    }

    private void bindSounds(@NonNull final Machine left,
                            @NonNull final Machine right,
                            @NonNull final UserState state) {
        final ImageView imageLeft = findViewById(R.id.picLeft);
        final ImageView imageRight = findViewById(R.id.picRight);
        final VolumeWarningSession volumeWarningSession =
                ((MacIndexApplication) getApplication()).volumeWarningSession();
        specsHelperLeft.initSound(left, imageLeft, null,
                state.getPreferences().getPlayDeathSound(),
                state.getPreferences().getEnableVolumeWarning(),
                volumeWarningSession);
        specsHelperRight.initSound(right, imageRight, null,
                state.getPreferences().getPlayDeathSound(),
                state.getPreferences().getEnableVolumeWarning(),
                volumeWarningSession);
    }

    private static boolean isClassic(@NonNull final Machine machine) {
        return machine.identifiers() == null
                && !"xserve".equals(machine.productTypeKey());
    }

    private void applyDifferenceHighlight(final boolean enabled) {
        final LinearLayout specsContainer = findViewById(R.id.compareSpecsContainer);
        if (specsContainer == null) {
            return;
        }
        final int backgroundColor = ContextCompat.getColor(
                this, R.color.colorCompareDifferent);
        for (int index = 0; index < specsContainer.getChildCount(); index++) {
            final View row = specsContainer.getChildAt(index);
            row.setBackgroundColor(enabled && Boolean.TRUE.equals(row.getTag())
                    ? backgroundColor : Color.TRANSPARENT);
        }
    }

    private void reloadName(@NonNull final TextView name, @NonNull final String machineName) {
        name.setVisibility(View.INVISIBLE);
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        name.setText(machineName);
        name.setTextSize(20);
        name.post(() -> {
            if (name.getLayout() != null
                    && !name.getLayout().getText().toString().equals(machineName)) {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            }
            name.setVisibility(View.VISIBLE);
        });
    }

    private void setMachineImage(final int viewID, @NonNull final Machine machine) {
        final ImageView image = findViewById(viewID);
        imageLoader.load(Integer.toString(viewID), machine,
                getResources().getDisplayMetrics().widthPixels / 2,
                Math.round(150 * getResources().getDisplayMetrics().density),
                picture -> {
                    ThemeHelper.applyMachineImage(this, image);
                    image.setImageBitmap(picture);
                },
                error -> {
                    Log.e("MachineImage", "Unable to load image for " + machine.uid(), error);
                    image.setImageDrawable(null);
                });
        image.setContentDescription(machine.name());
        image.setOnLongClickListener(view -> {
            startActivity(NavigationContract.machineImageIntent(this, machine.uid()));
            return true;
        });
    }

    private void setProcessorTypeImage(final int imageID,
                                       final int layoutID,
                                       @NonNull final Machine machine) {
        final LogoAsset asset = MachineResourceRegistry.processorTypeLogo(machine);
        final View layout = findViewById(layoutID);
        final ImageView image = findViewById(imageID);
        if (asset == null) {
            layout.setVisibility(View.GONE);
            image.setImageDrawable(null);
        } else {
            layout.setVisibility(View.VISIBLE);
            ThemeHelper.setLogo(this, image, asset);
        }
    }

    private void clearDisplayedComparison() {
        if (sharedRequest != null) {
            finish();
            return;
        }
        if (transientSelection != null) {
            transientSelection = null;
            if (currentState != null) {
                render(currentState);
            }
            return;
        }
        execute(UserStateCommands.clearCompareSelection(), ignored -> { },
                R.string.menu_compare, R.string.compare_selection_save_failed);
    }

    private void swapColumns() {
        if (sharedRequest != null) {
            sharedRequest = NavigationContract.ComparisonRequest.create(
                    sharedRequest.getRightUID(), sharedRequest.getLeftUID());
            if (currentState != null) {
                render(currentState);
            }
            return;
        }
        if (transientSelection != null) {
            transientSelection = NavigationContract.ComparisonRequest.create(
                    transientSelection.getRightUID(), transientSelection.getLeftUID());
            if (currentState != null) {
                render(currentState);
            }
            return;
        }
        execute(UserStateCommands.swapCompareSelection(), ignored -> { },
                R.string.menu_compare, R.string.compare_selection_save_failed);
    }

    private void showSelectionDialog() {
        if (currentState == null || catalog == null) {
            return;
        }
        final CompareSelection selection = currentState.getLibrary().getCompare();
        final List<String> compareUIDs = selection.getMachineUids();
        final CharSequence[] choices = new CharSequence[compareUIDs.size()];
        final boolean[] checked = new boolean[compareUIDs.size()];
        final String selectedLeft = transientSelection == null
                ? selection.getLeftUid() : transientSelection.getLeftUID();
        final String selectedRight = transientSelection == null
                ? selection.getRightUid() : transientSelection.getRightUID();
        for (int index = 0; index < compareUIDs.size(); index++) {
            final String uid = compareUIDs.get(index);
            choices[index] = catalog.requireByUid(uid).name();
            checked[index] = uid.equals(selectedLeft) || uid.equals(selectedRight);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.submenu_compare_initialize)
                .setMultiChoiceItems(choices, checked,
                        (ignored, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.link_confirm, null)
                .setNegativeButton(R.string.link_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    final List<String> selected = new ArrayList<>(2);
                    for (int index = 0; index < checked.length; index++) {
                        if (checked[index]) {
                            selected.add(compareUIDs.get(index));
                        }
                    }
                    if (selected.size() != 2) {
                        Toast.makeText(this, R.string.compare_select_exactly_two,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final String chosenLeft = selected.get(0);
                    final String chosenRight = selected.get(1);
                    final boolean selectionIsUnchanged = chosenLeft.equals(
                            selection.getLeftUid()) && chosenRight.equals(
                            selection.getRightUid());
                    if (sharedRequest != null && selectionIsUnchanged) {
                        sharedRequest = null;
                        dialog.dismiss();
                        render(currentState);
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    execute(UserStateCommands.setCompareSelection(
                                    chosenLeft, chosenRight),
                            result -> {
                                transientSelection = result.getPreferences()
                                        .getRememberCompareState()
                                        ? null : NavigationContract.ComparisonRequest.create(
                                                chosenLeft, chosenRight);
                                sharedRequest = null;
                                currentState = result;
                                dialog.dismiss();
                                render(result);
                            },
                            error -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                ExceptionHelper.showUserStateEditFailure(this, error,
                                        R.string.submenu_compare_initialize,
                                        R.string.compare_selection_save_failed);
                            });
                }));
        dialog.show();
    }

    private void restoreComparisonState(final Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            sharedRequest = NavigationContract.ComparisonRequest.from(getIntent());
            return;
        }
        try {
            sharedRequest = restoreSavedRequest(
                    savedInstanceState, STATE_SHARED_LEFT_UID, STATE_SHARED_RIGHT_UID);
            transientSelection = restoreSavedRequest(
                    savedInstanceState, STATE_TRANSIENT_LEFT_UID, STATE_TRANSIENT_RIGHT_UID);
        } catch (IllegalArgumentException error) {
            sharedRequest = null;
            transientSelection = null;
            Log.w("CompareState", "Ignoring invalid saved comparison state.", error);
        }
    }

    @Nullable
    private static NavigationContract.ComparisonRequest restoreSavedRequest(
            @NonNull final Bundle state,
            @NonNull final String leftKey,
            @NonNull final String rightKey) {
        final String left = state.getString(leftKey);
        final String right = state.getString(rightKey);
        if (left == null && right == null) {
            return null;
        }
        if (left == null || right == null) {
            throw new IllegalArgumentException("Incomplete saved comparison request");
        }
        return NavigationContract.ComparisonRequest.create(left, right);
    }

    private void discardInvalidTransientSelection(@NonNull final UserState state) {
        if (transientSelection == null) {
            return;
        }
        final List<String> compareUIDs = state.getLibrary().getCompare().getMachineUids();
        if (!compareUIDs.contains(transientSelection.getLeftUID())
                || !compareUIDs.contains(transientSelection.getRightUID())) {
            transientSelection = null;
        }
    }

    private void showManageListDialog() {
        if (currentState == null || catalog == null) {
            return;
        }
        final List<String> compareUIDs = currentState.getLibrary()
                .getCompare().getMachineUids();
        final View selectChunk = getLayoutInflater().inflate(
                R.layout.chunk_favourites_select, null);
        final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
        final Set<String> deleteSelections = new HashSet<>();
        for (String uid : compareUIDs) {
            final CheckBox checkBox = new CheckBox(this);
            checkBox.setText(catalog.requireByUid(uid).name());
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    deleteSelections.add(uid);
                } else {
                    deleteSelections.remove(uid);
                }
            });
            selectLayout.addView(checkBox);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.submenu_compare_manage)
                .setMessage(R.string.compare_manage)
                .setView(selectChunk)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        execute(UserStateCommands.removeCompareMachines(deleteSelections),
                                ignored -> { }, R.string.submenu_compare_manage,
                                R.string.compare_list_save_failed))
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void showClearListDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.submenu_compare_clear)
                .setMessage(R.string.compare_clear_warning)
                .setPositiveButton(R.string.action_clear, (dialog, which) ->
                        execute(UserStateCommands.clearCompareList(), ignored -> { },
                                R.string.submenu_compare_clear,
                                R.string.compare_list_save_failed))
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private <T> void execute(@NonNull final UserStateCommand<T> command,
                             @NonNull final UserStateSuccess<T> success,
                             final int title,
                             final int message) {
        execute(command, success, error -> ExceptionHelper.showUserStateWriteFailure(
                this, error, title, message));
    }

    private <T> void execute(@NonNull final UserStateCommand<T> command,
                             @NonNull final UserStateSuccess<T> success,
                             @NonNull final com.macindex.macindex.userstate.UserStateFailure failure) {
        if (stateAdapter != null) {
            stateAdapter.execute(command, success, failure);
        }
    }

    private void release() {
        if (specsHelperLeft != null) {
            specsHelperLeft.release();
        }
        if (specsHelperRight != null) {
            specsHelperRight.release();
        }
    }

    /** The smallest user-state slice that requires rebuilding comparison structure or media. */
    private static final class CompareRenderKey {
        private enum Mode {
            SHARED,
            SELECTED,
            NEEDS_SELECTION,
            EMPTY,
            SINGLE
        }

        private final Mode mode;
        private final String displayedLeftUID;
        private final String displayedRightUID;
        private final boolean playDeathSound;
        private final boolean enableVolumeWarning;
        private final boolean fixedNavigation;

        private CompareRenderKey(@NonNull final UserState state,
                                 final NavigationContract.ComparisonRequest sharedRequest,
                                 final NavigationContract.ComparisonRequest transientSelection) {
            final CompareSelection selection = state.getLibrary().getCompare();
            final List<String> compareUIDs = selection.getMachineUids();
            if (sharedRequest != null) {
                mode = Mode.SHARED;
                displayedLeftUID = sharedRequest.getLeftUID();
                displayedRightUID = sharedRequest.getRightUID();
            } else if (transientSelection != null) {
                mode = Mode.SELECTED;
                displayedLeftUID = transientSelection.getLeftUID();
                displayedRightUID = transientSelection.getRightUID();
            } else if (compareUIDs.size() >= 2
                    && !selection.getLeftUid().equals(selection.getRightUid())
                    && compareUIDs.contains(selection.getLeftUid())
                    && compareUIDs.contains(selection.getRightUid())) {
                mode = Mode.SELECTED;
                displayedLeftUID = selection.getLeftUid();
                displayedRightUID = selection.getRightUid();
            } else {
                mode = compareUIDs.isEmpty() ? Mode.EMPTY
                        : (compareUIDs.size() == 1 ? Mode.SINGLE : Mode.NEEDS_SELECTION);
                displayedLeftUID = "";
                displayedRightUID = "";
            }
            playDeathSound = state.getPreferences().getPlayDeathSound();
            enableVolumeWarning = state.getPreferences().getEnableVolumeWarning();
            fixedNavigation = state.getPreferences().getFixedNavigation();
        }

        private boolean hasComparison() {
            return mode == Mode.SHARED || mode == Mode.SELECTED;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) return true;
            if (!(other instanceof CompareRenderKey)) return false;
            final CompareRenderKey that = (CompareRenderKey) other;
            return playDeathSound == that.playDeathSound
                    && enableVolumeWarning == that.enableVolumeWarning
                    && fixedNavigation == that.fixedNavigation
                    && mode == that.mode
                    && displayedLeftUID.equals(that.displayedLeftUID)
                    && displayedRightUID.equals(that.displayedRightUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode, displayedLeftUID, displayedRightUID, playDeathSound,
                    enableVolumeWarning, fixedNavigation);
        }
    }

    private void setMenuState(final boolean ableToInitialize,
                              final boolean initialized,
                              final boolean ableToManage) {
        isAbleToInitialize = ableToInitialize;
        isInitialized = initialized;
        isAbleToManage = ableToManage;
        updateMenuState();
    }

    private void updateMenuState() {
        if (initialMenuItem != null) {
            initialMenuItem.setEnabled(isAbleToInitialize);
        }
        if (clearColumnMenuItem != null) {
            clearColumnMenuItem.setEnabled(isInitialized);
        }
        if (exchangeColumnMenuItem != null) {
            exchangeColumnMenuItem.setEnabled(isInitialized);
        }
        if (copyCompareMenuItem != null) {
            copyCompareMenuItem.setEnabled(isInitialized);
        }
        if (shareLinkCompareMenuItem != null) {
            shareLinkCompareMenuItem.setEnabled(isInitialized);
        }
        if (manageListMenuItem != null) {
            manageListMenuItem.setEnabled(isAbleToManage);
        }
        if (clearListMenuItem != null) {
            clearListMenuItem.setEnabled(isAbleToManage);
        }
        if (highlightDifferencesMenuItem != null) {
            highlightDifferencesMenuItem.setEnabled(isInitialized);
        }
    }
}
