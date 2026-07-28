package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
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

import java.util.ArrayList;
import java.util.List;

/**
 * MacIndex Compare Activity
 * Jan. 18, 2021
 * Mar. 29, 2022
 * July 22, 2026
 */
public class CompareActivity extends AppCompatActivity {

    private boolean isAbleToInitialize = false;

    private boolean isInitialized = false;

    private boolean isAbleToManage = true;

    private MenuItem initialMenuItem = null;

    private MenuItem clearColumnMenuItem = null;

    private MenuItem exchangeColumnMenuItem = null;

    private MenuItem copyCompareMenuItem = null;

    private MenuItem shareLinkCompareMenuItem = null;

    private MenuItem manageListMenuItem = null;

    private MenuItem clearListMenuItem = null;

    private SpecsHelper specsHelperLeft = null;

    private SpecsHelper specsHelperRight = null;

    private String leftName = null;

    private String rightName = null;

    private String[] leftSpecification = null;

    private String[] rightSpecification = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);
        WindowInsetsHelper.apply(this);
        specsHelperLeft = new SpecsHelper(this);
        specsHelperRight = new SpecsHelper(this);
        this.setTitle(getResources().getString(R.string.menu_compare));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        if (getSharedComparison() == null
                && !PrefsHelper.getBooleanPrefs("isSaveCompareUsage", this)) {
            clearComparing(this);
        }
        initCompare();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!MainActivity.validateOperation(this)) {
            return;
        }
        if (getSharedComparison() == null
                && !PrefsHelper.getBooleanPrefs("isSaveCompareUsage", this)) {
            clearComparing(this);
        }
        initCompare();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (isInitialized || PrefsHelper.getBooleanPrefs("isCompareReloadNeeded", this)) {
            initCompare();
        }
    }

    @Override
    protected void onStop() {
        release();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_compare, menu);
        initialMenuItem = menu.findItem(R.id.initCompareItem);
        clearColumnMenuItem = menu.findItem(R.id.clearColumnCompareItem);
        exchangeColumnMenuItem = menu.findItem(R.id.switchCompareItem);
        copyCompareMenuItem = menu.findItem(R.id.copyCompareItem);
        shareLinkCompareMenuItem = menu.findItem(R.id.shareLinkCompareItem);
        manageListMenuItem = menu.findItem(R.id.manageCompareItem);
        clearListMenuItem = menu.findItem(R.id.clearCompareItem);
        updateMenuState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.initCompareItem) {
            initCompareItem();
        } else if (itemID == R.id.clearColumnCompareItem) {
            if (getSharedComparison() == null) {
                clearComparing(this);
                initCompare();
            } else {
                finish();
            }
        } else if (itemID == R.id.switchCompareItem) {
            final String[] sharedComparison = getSharedComparison();
            if (sharedComparison == null) {
                final String oldLeft = PrefsHelper.getStringPrefs("userComparesLeft", this);
                PrefsHelper.editPrefs("userComparesLeft", PrefsHelper.getStringPrefs("userComparesRight", this), this);
                PrefsHelper.editPrefs("userComparesRight", oldLeft, this);
            } else {
                getIntent().putExtra("compareLeft", sharedComparison[1]);
                getIntent().putExtra("compareRight", sharedComparison[0]);
            }
            initCompare();
        } else if (itemID == R.id.copyCompareItem) {
            specsHelperLeft.copySpecification(new String[]{leftName, rightName},
                    new String[][]{leftSpecification, rightSpecification});
        } else if (itemID == R.id.shareLinkCompareItem) {
            specsHelperLeft.generateShareLink(leftName, rightName);
        } else if (itemID == R.id.manageCompareItem) {
            manageList();
        } else if (itemID == R.id.clearCompareItem) {
            final AlertDialog.Builder clearWarningDialog = new AlertDialog.Builder(this);
            clearWarningDialog.setTitle(R.string.submenu_compare_clear);
            clearWarningDialog.setMessage(R.string.compare_clear_warning);
            clearWarningDialog.setPositiveButton(R.string.action_clear, (dialog, which) -> {
                saveCompareList(new ArrayList<>(), this);
                clearComparing(this);
                initCompare();
            });
            clearWarningDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
                // Cancelled, nothing to do.
            });
            clearWarningDialog.show();
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

    private void initCompare() {
        release();
        try {
            final String[] sharedComparison = getSharedComparison();
            if (sharedComparison == null) {
                PrefsHelper.editPrefs("isCompareReloadNeeded", false, this);
            }
            final List<String> compareNames = getCompareList(this);
            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final LinearLayout initialLayout = findViewById(R.id.initialLayout);
            final TextView emptyText = findViewById(R.id.emptyText);
            final TextView initialText = findViewById(R.id.initialText);
            final ScrollView compareScroll = findViewById(R.id.compareScroll);

            if (sharedComparison != null || compareNames.size() >= 2) {
                final String compareLeft = sharedComparison == null
                        ? PrefsHelper.getStringPrefs("userComparesLeft", this) : sharedComparison[0];
                final String compareRight = sharedComparison == null
                        ? PrefsHelper.getStringPrefs("userComparesRight", this) : sharedComparison[1];
                if (sharedComparison != null || (!compareLeft.equals(compareRight)
                        && compareNames.contains(compareLeft) && compareNames.contains(compareRight))) {
                    int[] leftID = MainActivity.getMachineHelper().searchHelper("name", compareLeft,
                            "all", true, false);
                    int[] rightID = MainActivity.getMachineHelper().searchHelper("name", compareRight,
                            "all", true, false);
                    if (leftID.length != 1 || rightID.length != 1) {
                        if (sharedComparison == null) {
                            clearComparing(this);
                        }
                        throw new IllegalArgumentException("Invalid machine selection");
                    }
                    initialLayout.setVisibility(View.GONE);
                    emptyLayout.setVisibility(View.GONE);
                    compareScroll.setVisibility(View.VISIBLE);
                    setAbleToInitialize(compareNames.size() >= 2);
                    setInitialized(true);
                    setAbleToManage(!compareNames.isEmpty());
                    loadSpecs(leftID[0], rightID[0]);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(initialText,
                            TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                    initialLayout.setVisibility(View.VISIBLE);
                    emptyLayout.setVisibility(View.GONE);
                    compareScroll.setVisibility(View.GONE);
                    setAbleToInitialize(true);
                    setInitialized(false);
                    setAbleToManage(true);
                }
            } else {
                emptyText.setText(getResources().getStringArray(R.array.compare_insufficient_tips)
                        [compareNames.isEmpty() ? 0 : 1]);
                TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText,
                        TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                initialLayout.setVisibility(View.GONE);
                emptyLayout.setVisibility(View.VISIBLE);
                compareScroll.setVisibility(View.GONE);
                setAbleToInitialize(false);
                setInitialized(false);
                setAbleToManage(!compareNames.isEmpty());
                clearComparing(this);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initCompare",
                    "Unable to initialize comparison.");
        }
    }

    private void initCompareItem() {
        try {
            final List<String> compareNames = getCompareList(this);
            final String currentLeft = PrefsHelper.getStringPrefs("userComparesLeft", this);
            final String currentRight = PrefsHelper.getStringPrefs("userComparesRight", this);
            final CharSequence[] choices = compareNames.toArray(new CharSequence[0]);
            final boolean[] checked = new boolean[choices.length];
            for (int i = 0; i < compareNames.size(); i++) {
                checked[i] = compareNames.get(i).equals(currentLeft)
                        || compareNames.get(i).equals(currentRight);
            }

            final AlertDialog.Builder selectDialog = new AlertDialog.Builder(this);
            selectDialog.setTitle(R.string.submenu_compare_initialize);
            selectDialog.setMultiChoiceItems(choices, checked,
                    (dialog, which, isChecked) -> checked[which] = isChecked);
            selectDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
                // To be overwritten...
            });
            selectDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
                // Cancelled, nothing to do.
            });
            final AlertDialog selectDialogCreated = selectDialog.create();
            selectDialogCreated.show();

            // Overwrite the positive button
            selectDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    final List<String> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selected.add(compareNames.get(i));
                        }
                    }
                    if (selected.size() != 2) {
                        Toast.makeText(this, R.string.compare_select_exactly_two, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PrefsHelper.editPrefs("userComparesLeft", selected.get(0), this);
                    PrefsHelper.editPrefs("userComparesRight", selected.get(1), this);
                    // Continue with user's compare list after manual selection.
                    clearSharedComparison();
                    selectDialogCreated.dismiss();
                    initCompare();
                } catch (Exception e) {
                    ExceptionHelper.handleException(this, e, "initCompareItem",
                            "Unable to set comparing machines.");
                }
            });
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initCompareItem",
                    "Unable to initialize the selection dialog.");
        }
    }

    private void loadSpecs(final int leftID, final int rightID) {
        final MachineHelper helper = MainActivity.getMachineHelper();
        leftName = helper.getName(leftID);
        rightName = helper.getName(rightID);

        setMachineImage(R.id.picLeft, leftID, leftName);
        setMachineImage(R.id.picRight, rightID, rightName);
        final ImageView imageLeft = findViewById(R.id.picLeft);
        final ImageView imageRight = findViewById(R.id.picRight);
        specsHelperLeft.initSound(leftID, imageLeft, null);
        specsHelperRight.initSound(rightID, imageRight, null);
        setProcessorTypeImage(R.id.processorTypeImageLeft, R.id.processorTypeImageLayoutLeft, leftID);
        setProcessorTypeImage(R.id.processorTypeImageRight, R.id.processorTypeImageLayoutRight, rightID);

        final ImageView everymac = findViewById(R.id.everymac);
        specsHelperLeft.initLinks(new int[]{leftID, rightID},
                new String[]{leftName, rightName}, everymac);

        final TextView nameLeft = findViewById(R.id.nameTextLeft);
        final TextView nameRight = findViewById(R.id.nameTextRight);
        reloadName(nameLeft, leftName);
        reloadName(nameRight, rightName);
        nameLeft.setOnClickListener(view -> SpecsIntentHelper.sendIntent(new int[]{leftID}, leftID, this));
        nameRight.setOnClickListener(view -> SpecsIntentHelper.sendIntent(new int[]{rightID}, rightID, this));

        final int[] labels = {R.string.year, R.string.model, R.string.id, R.string.gestalt,
                R.string.order, R.string.emc, R.string.processor, R.string.graphics,
                R.string.display, R.string.maxram, R.string.type, R.string.software,
                R.string.storage, R.string.features, R.string.expansion, R.string.design,
                R.string.support};
        leftSpecification = helper.getSpecs(leftID);
        rightSpecification = helper.getSpecs(rightID);
        final boolean isLeftClassic = helper.isClassicMachine(leftID);
        final boolean isRightClassic = helper.isClassicMachine(rightID);

        final LinearLayout specsContainer = findViewById(R.id.compareSpecsContainer);
        specsContainer.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            if ((isLeftClassic && isRightClassic
                    && (labels[i] == R.string.id || labels[i] == R.string.emc))
                    || (!isLeftClassic && !isRightClassic
                    && labels[i] == R.string.gestalt)) {
                continue;
            }
            final View row = getLayoutInflater().inflate(R.layout.chunk_compare_row, specsContainer, false);
            ((TextView) row.findViewById(R.id.compareTitle)).setText(labels[i]);
            final TextView compareLeft = row.findViewById(R.id.compareLeft);
            final TextView compareRight = row.findViewById(R.id.compareRight);
            final String leftInfo = leftSpecification[i];
            final String rightInfo = rightSpecification[i];
            if (labels[i] == R.string.processor) {
                compareLeft.setText(specsHelperLeft.formatModels(leftInfo,
                        helper.getProcessorModelRanges(leftID)));
                compareRight.setText(specsHelperRight.formatModels(rightInfo,
                        helper.getProcessorModelRanges(rightID)));
            } else if (labels[i] == R.string.graphics) {
                compareLeft.setText(specsHelperLeft.formatModels(leftInfo,
                        helper.getGraphicsModelRanges(leftID)));
                compareRight.setText(specsHelperRight.formatModels(rightInfo,
                        helper.getGraphicsModelRanges(rightID)));
            } else if (labels[i] == R.string.order) {
                specsHelperLeft.initPartNumbers(compareLeft, leftInfo);
                specsHelperRight.initPartNumbers(compareRight, rightInfo);
            } else {
                compareLeft.setText(leftInfo);
                compareRight.setText(rightInfo);
            }
            specsHelperLeft.initCompareCopy(compareLeft, leftName, leftInfo, rightName, rightInfo);
            specsHelperLeft.initCompareCopy(compareRight, leftName, leftInfo, rightName, rightInfo);
            if (i == labels.length - 1) {
                specsHelperLeft.setSupportColor(compareLeft, leftInfo);
                specsHelperLeft.setSupportColor(compareRight, rightInfo);
            }
            specsContainer.addView(row);
        }
    }

    private void reloadName(final TextView name, final String thisName) {
        name.setVisibility(View.INVISIBLE);

        // Reset the auto-sizing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            name.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        } else {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(name,
                    TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        }

        // Reset the Machine Name.
        name.setText(thisName);
        name.setTextSize(20);

        // Auto-sizing only if two lines are insufficient.
        name.post(() -> {
            if (!name.getLayout().getText().toString().equals(thisName)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    name.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(name,
                            TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
            }
            name.setVisibility(View.VISIBLE);
        });
    }

    private void setMachineImage(final int viewID, final int machineID, final String name) {
        final ImageView image = findViewById(viewID);
        final Bitmap picture = MainActivity.getMachineHelper().getPicture(machineID);
        if (picture != null) {
            image.setImageBitmap(picture);
        } else {
            image.setImageDrawable(null);
        }
        image.setContentDescription(name);
        // Set a long click listener
        image.setOnLongClickListener(v -> {
            Intent viewImageIntent = new Intent(CompareActivity.this, ViewImageActivity.class);
            viewImageIntent.putExtra("machineID", machineID);
            startActivity(viewImageIntent);
            return true;
        });
    }

    private void setProcessorTypeImage(final int imageID, final int layoutID, final int machineID) {
        final int drawableID = MainActivity.getMachineHelper().getProcessorTypeImage(machineID, this);
        final View layout = findViewById(layoutID);
        final ImageView image = findViewById(imageID);
        if (drawableID == 0) {
            layout.setVisibility(View.GONE);
        } else {
            layout.setVisibility(View.VISIBLE);
            image.setImageResource(drawableID);
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

    private void manageList() {
        try {
            final List<String> compareNames = getCompareList(this);
            final View selectChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_select, null);
            final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
            final boolean[] deleteSelections = new boolean[compareNames.size()];
            for (int i = 0; i < compareNames.size(); i++) {
                CheckBox thisCheckBox = new CheckBox(this);
                thisCheckBox.setText(compareNames.get(i));
                final int finalI = i;
                thisCheckBox.setOnCheckedChangeListener((compoundButton, b) ->
                        deleteSelections[finalI] = thisCheckBox.isChecked());
                selectLayout.addView(thisCheckBox);
            }

            final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
            deleteDialog.setTitle(R.string.submenu_compare_manage);
            deleteDialog.setMessage(R.string.compare_manage);
            deleteDialog.setView(selectChunk);
            deleteDialog.setPositiveButton(R.string.action_delete, (dialog, which) -> {
                try {
                    final List<String> remaining = new ArrayList<>();
                    for (int i = 0; i < compareNames.size(); i++) {
                        if (!deleteSelections[i]) {
                            remaining.add(compareNames.get(i));
                        }
                    }
                    saveCompareList(remaining, this);
                    ensureSelectionValid(this);
                    initCompare();
                } catch (Exception e) {
                    ExceptionHelper.handleException(this, e, "manageListConfirm",
                            "Unable to edit compare list.");
                }
            });
            deleteDialog.setNegativeButton(R.string.link_cancel, ((dialog, which) -> {
                // Cancelled, do nothing
            }));
            deleteDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "manageList",
                    "Unable to manage compare list.");
        }
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
    }

    private void setAbleToInitialize(final boolean newStatus) {
        DebugHelper.log("CompareActivity", "isAbleToInitialize set to " + newStatus);
        isAbleToInitialize = newStatus;
        updateMenuState();
    }

    private void setInitialized(final boolean newStatus) {
        DebugHelper.log("CompareActivity", "isInitialized set to " + newStatus);
        isInitialized = newStatus;
        updateMenuState();
    }

    private void setAbleToManage(final boolean newStatus) {
        DebugHelper.log("CompareActivity", "isAbleToManage set to " + newStatus);
        isAbleToManage = newStatus;
        updateMenuState();
    }

    private String[] getSharedComparison() {
        final Intent intent = getIntent();
        if (intent == null) {
            return null;
        }
        final String compareLeft = intent.getStringExtra("compareLeft");
        final String compareRight = intent.getStringExtra("compareRight");
        if (compareLeft == null || compareRight == null || compareLeft.equals(compareRight)) {
            return null;
        }
        return new String[]{compareLeft, compareRight};
    }

    private void clearSharedComparison() {
        getIntent().removeExtra("compareLeft");
        getIntent().removeExtra("compareRight");
    }

    static List<String> getCompareList(final Context thisContext) {
        return CompareListHelper.parse(PrefsHelper.getStringPrefs("userCompares", thisContext));
    }

    static void saveCompareList(final List<String> compareNames, final Context thisContext) {
        PrefsHelper.editPrefs("userCompares", CompareListHelper.serialize(compareNames), thisContext);
        PrefsHelper.editPrefs("isCompareReloadNeeded", true, thisContext);
    }

    static void toggleCompare(final String machineName, final Context thisContext) {
        final List<String> compareNames = getCompareList(thisContext);
        if (compareNames.contains(machineName)) {
            compareNames.remove(machineName);
            saveCompareList(compareNames, thisContext);
            ensureSelectionValid(thisContext);
            return;
        }
        if (compareNames.size() >= 10) {
            return;
        }
        compareNames.add(machineName);
        saveCompareList(compareNames, thisContext);
    }

    public static void checkIsComparing(final String machineName, final Context thisContext) {
        final String normalizedName = machineName.startsWith("[") && machineName.endsWith("]")
                ? machineName.substring(1, machineName.length() - 1) : machineName;
        DebugHelper.log("CompareActivity", "Checking for deletion");
        if (normalizedName.equals(PrefsHelper.getStringPrefs("userComparesLeft", thisContext))
                || normalizedName.equals(PrefsHelper.getStringPrefs("userComparesRight", thisContext))) {
            clearComparing(thisContext);
        } else {
            ensureSelectionValid(thisContext);
        }
    }

    private static void ensureSelectionValid(final Context thisContext) {
        final List<String> compareNames = getCompareList(thisContext);
        final String left = PrefsHelper.getStringPrefs("userComparesLeft", thisContext);
        final String right = PrefsHelper.getStringPrefs("userComparesRight", thisContext);
        if (compareNames.size() < 2 || left.equals(right)
                || !compareNames.contains(left) || !compareNames.contains(right)) {
            clearComparing(thisContext);
        }
    }

    private static void clearComparing(final Context thisContext) {
        Log.w("CompareActivity", "Clearing left/right parameters");
        PrefsHelper.clearPrefs("userComparesLeft", thisContext);
        PrefsHelper.clearPrefs("userComparesRight", thisContext);
    }
}
