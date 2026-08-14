package com.macindex.macindex;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * MacIndex Favourite Activity
 * Jan. 15, 2021
 */
public class FavouriteActivity extends AppCompatActivity {

    private int[][] loadPositions = {};

    private ProgressDialog waitDialog = null;

    private Thread favouritesThread = null;

    private volatile int favouritesRequestID = 0;

    private boolean isAbleToManage = false;

    private MenuItem renameFolderItem = null;

    private MenuItem manageFolderItem = null;

    private MenuItem clearFolderItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favourite);
        WindowInsetsHelper.apply(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateUp();
            }
        });
        this.setTitle(getResources().getString(R.string.menu_favourite));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        findViewById(R.id.emptyText).setOnClickListener(view -> createFolder());

        waitDialog = new ProgressDialog(this);
        waitDialog.setMessage(getString(R.string.loading_favourites));
        waitDialog.setCancelable(false);

        initFavourites(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // If reload is needed..
        if (PrefsHelper.getBooleanPrefs("isFavouritesReloadNeeded", this)) {
            initFavourites(true);
        }
    }

    // Adapted from MainActivity
    @Override
    protected void onDestroy() {
        favouritesRequestID++;
        if (favouritesThread != null) {
            favouritesThread.interrupt();
            favouritesThread = null;
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_favourite, menu);
        renameFolderItem = menu.findItem(R.id.renameFolderItem);
        manageFolderItem = menu.findItem(R.id.deleteFolderItem);
        clearFolderItem = menu.findItem(R.id.clearFolderItem);
        renameFolderItem.setEnabled(isAbleToManage);
        manageFolderItem.setEnabled(isAbleToManage);
        clearFolderItem.setEnabled(isAbleToManage);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.addFolderItem) {
            createFolder();
        } else if (itemID == R.id.deleteFolderItem) {
            deleteFolder();
        } else if (itemID == R.id.renameFolderItem) {
            renameFolder();
        } else if (itemID == R.id.clearFolderItem) {
            final AlertDialog.Builder clearFoldersDialog = new AlertDialog.Builder(this);
            clearFoldersDialog.setTitle(R.string.submenu_favourite_clear);
            clearFoldersDialog.setMessage(R.string.favourites_clear_warning);
            clearFoldersDialog.setPositiveButton(R.string.action_clear, (dialogInterface, i) -> {
                UserFavouriteHelper.clear(this);
                initFavourites(true);
            });
            clearFoldersDialog.setNegativeButton(R.string.link_cancel, ((dialogInterface, i) -> {
                // Cancelled.
            }));
            clearFoldersDialog.show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
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

    private void initFavourites(final boolean reloadPositions) {
        final int requestID = ++favouritesRequestID;
        if (favouritesThread != null) {
            favouritesThread.interrupt();
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        // Reset reload parameter
        PrefsHelper.editPrefs("isFavouritesReloadNeeded", false, this);
        DebugHelper.log("initFavouritesReload", String.valueOf(reloadPositions));

        // Adapt initInterface from MainActivity
        try {
            // Parent layout of all categories.
            final LinearLayout categoryContainer = findViewById(R.id.categoryContainer);
            // Fix an animation bug here
            final LayoutTransition layoutTransition = categoryContainer.getLayoutTransition();
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            // Get Folder Names
            final List<UserFavouriteHelper.Folder> folders = UserFavouriteHelper.read(this);
            final String[] allFolders = getFolders(folders, false);

            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final TextView emptyText = findViewById(R.id.emptyText);

            if (allFolders.length == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    emptyText.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
                // Adapt new behaviour
                setAbleToManage(false);
                emptyLayout.setVisibility(View.VISIBLE);
            } else {
                setAbleToManage(true);
                emptyLayout.setVisibility(View.GONE);
            }

            if (reloadPositions) {
                waitDialog.show();
            }
            favouritesThread = new Thread() {
                @Override
                public void run() {
                    try {
                        final int[][] positionsForRequest;
                        if (reloadPositions) {
                            // Get Load Positions
                            positionsForRequest = new int[allFolders.length][];
                            for (int i = 0; i < allFolders.length; i++) {
                                final List<Integer> validMachineIDs = new ArrayList<>();
                                for (String machineUID : folders.get(i).machineUIDs) {
                                    validMachineIDs.add(MainActivity.getMachineHelper()
                                            .getMachineID(machineUID));
                                }
                                positionsForRequest[i] = new int[validMachineIDs.size()];
                                for (int j = 0; j < validMachineIDs.size(); j++) {
                                    positionsForRequest[i][j] = validMachineIDs.get(j);
                                }
                                // Is sorting needed?
                                if (PrefsHelper.getBooleanPrefsSafe("isSortComment", FavouriteActivity.this)) {
                                    positionsForRequest[i] = MainActivity.getMachineHelper().directSortByYear(positionsForRequest[i]);
                                }
                            }
                        } else {
                            positionsForRequest = loadPositions;
                        }
                        if (Thread.currentThread().isInterrupted() || requestID != favouritesRequestID) {
                            return;
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestID != favouritesRequestID || isFinishing()
                                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                    return;
                                }
                                try {
                                    loadPositions = positionsForRequest;
                                    if (reloadPositions) {
                                        waitDialog.dismiss();
                                    }
                                    // Set up each category.
                                    categoryContainer.setLayoutTransition(null);
                                    try {
                                        categoryContainer.removeAllViews();
                                        for (int i = 0; i < loadPositions.length; i++) {
                                            final int[] thisCategoryPositions = loadPositions[i];
                                            final String thisFolderName = allFolders[i];
                                            final View categoryChunk = getLayoutInflater()
                                                    .inflate(R.layout.chunk_category, categoryContainer, false);
                                            final LinearLayout categoryChunkLayout = categoryChunk.findViewById(R.id.categoryInfoLayout);
                                            final TextView categoryName = categoryChunk.findViewById(R.id.category);

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                categoryName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                            } else {
                                                TextViewCompat.setAutoSizeTextTypeWithDefaults(categoryName, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                            }

                                            if (thisCategoryPositions.length != 0) {
                                                categoryName.setText(thisFolderName);

                                                /* Remake my teammate's code */
                                                categoryName.setOnClickListener(new View.OnClickListener() {
                                                    private boolean thisVisibility = false;
                                                    private boolean isCategoryLoaded = false;

                                                    @Override
                                                    public void onClick(final View view) {
                                                        try {
                                                            if (!isCategoryLoaded) {
                                                                final TextView[] thisMachines = SpecsIntentHelper
                                                                        .initCategory(categoryChunkLayout,
                                                                                thisCategoryPositions, false,
                                                                                FavouriteActivity.this);
                                                                SpecsIntentHelper.refreshFavourites(
                                                                        new TextView[][]{thisMachines},
                                                                        FavouriteActivity.this);
                                                                isCategoryLoaded = true;
                                                            }

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
                                                            ExceptionHelper.handleException(FavouriteActivity.this, e,
                                                                    "initFavourites", "Unable to display favourites.");
                                                        }
                                                    }
                                                });
                                                categoryContainer.addView(categoryChunk);
                                            } else {
                                                // Empty folder
                                                categoryName.setText(getString(
                                                        R.string.favourites_empty_folder,
                                                        thisFolderName));
                                                categoryContainer.addView(categoryChunk);
                                            }
                                        }
                                        // Remove the last divider.
                                        if (categoryContainer.getChildCount() != 0) {
                                            ((LinearLayout) categoryContainer.getChildAt(
                                                    categoryContainer.getChildCount() - 1)).removeViewAt(1);
                                        }
                                    } finally {
                                        categoryContainer.setLayoutTransition(layoutTransition);
                                    }
                                } catch (Exception e) {
                                    ExceptionHelper.handleException(FavouriteActivity.this, e,
                                            "initFavourites", "Unable to display favourites.");
                                }
                            }
                        });
                    } catch (CancellationException ignored) {
                        // Replaced by a newer refresh or cancelled by the user.
                    } catch (final Exception e) {
                        runOnUiThread(() -> {
                            if (requestID != favouritesRequestID || isFinishing()
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                    && isDestroyed())) {
                                return;
                            }
                            if (waitDialog != null && waitDialog.isShowing()) {
                                waitDialog.dismiss();
                            }
                            ExceptionHelper.handleException(FavouriteActivity.this, e,
                                    "FavouritesSearchThread", "Illegal favourite preference string.");
                        });
                    }
                }
            };
            favouritesThread.start();
        } catch (final Exception e) {
            ExceptionHelper.handleException(FavouriteActivity.this, e,
                    "initFavourites", "Unable to read favourites.");
        }
    }

    public static String[] getFolders(final Context thisContext, final Boolean isTailing) {
        try {
            return getFolders(UserFavouriteHelper.read(thisContext), isTailing);
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "getFolders",
                    "Unable to read favourite folders.");
            return new String[0];
        }
    }

    private static String[] getFolders(final List<UserFavouriteHelper.Folder> folders,
                                       final boolean isTailing) {
        final String[] folderNames = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            final UserFavouriteHelper.Folder folder = folders.get(i);
            folderNames[i] = folder.name + (isTailing
                    ? " (" + folder.machineUIDs.size() + ")" : "");
        }
        return folderNames;
    }

    public static boolean validateFolderName(final String inputtedName, final String[] currentStrings, final Context thisContext) {
        if (inputtedName.isEmpty()) {
            Log.w("validateFolderName", "Empty input.");
            Toast.makeText(thisContext, R.string.favourites_error_empty, Toast.LENGTH_LONG).show();
            return false;
        } else if (inputtedName.length() > 30 || inputtedName.contains("\n")) {
            Log.w("validateFolderName", "Input is too long.");
            Toast.makeText(thisContext, R.string.favourites_error_length, Toast.LENGTH_LONG).show();
            return false;
        } else {
            // Check if specified
            for (String toCheck : currentStrings) {
                if (toCheck.equals(inputtedName)) {
                    Log.w("validateFolderName", "Conflict - Specified.");
                    Toast.makeText(thisContext, R.string.favourites_error_conflict, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        return true;
    }

    private void createFolder() {
        // Check for folder count
        final String[] currentStrings = getFolders(this, false);
        if (currentStrings.length >= 15) {
            final AlertDialog.Builder folderLimitDialog = new AlertDialog.Builder(this);
            folderLimitDialog.setTitle(R.string.submenu_favourite_add);
            folderLimitDialog.setMessage(R.string.favourites_error_limit);
            folderLimitDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                // Confirmed
            });
            folderLimitDialog.show();
        } else {
            final View newFolderChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_new, null);
            final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
            final AlertDialog.Builder newFolderDialog = new AlertDialog.Builder(this);
            newFolderDialog.setTitle(R.string.submenu_favourite_add);
            newFolderDialog.setMessage(R.string.favourites_new_folder);
            newFolderDialog.setView(newFolderChunk);
            newFolderDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                // To be overwritten...
            });
            newFolderDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                // Do nothing
            });

            final AlertDialog newFolderDialogCreated = newFolderDialog.create();
            newFolderDialogCreated.show();
            // Overwrite the positive button
            newFolderDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    final String inputtedName = folderName.getText().toString().trim();
                    // Check if the input is legal
                    if (validateFolderName(inputtedName, currentStrings, this)) {
                        final List<UserFavouriteHelper.Folder> folders =
                                UserFavouriteHelper.read(this);
                        folders.add(0, new UserFavouriteHelper.Folder(
                                inputtedName, new ArrayList<>()));
                        UserFavouriteHelper.write(folders, this);
                        newFolderDialogCreated.dismiss();
                        initFavourites(true);
                    }
                } catch (Exception e) {
                    ExceptionHelper.handleException(FavouriteActivity.this, e,
                            "newFolderDialog", "Unable to save favourite folder.");
                }
            });
        }
    }

    private void deleteFolder() {
        try {
            final View selectChunk = this.getLayoutInflater().inflate(R.layout.chunk_favourites_select, null);
            final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
            final String[] currentStrings = getFolders(this, true);
            final int[] currentSelections = new int[currentStrings.length];
            for (int i = 0; i < currentStrings.length; i++) {
                CheckBox thisCheckBox = new CheckBox(this);
                thisCheckBox.setText(currentStrings[i]);
                thisCheckBox.setChecked(false);
                int finalI = i;
                thisCheckBox.setOnCheckedChangeListener((compoundButton, b) -> {
                    currentSelections[finalI] = thisCheckBox.isChecked() ? 1 : 0;
                });
                selectLayout.addView(thisCheckBox);
            }

            // Create the dialog.
            final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
            deleteDialog.setTitle(R.string.submenu_favourite_delete);
            deleteDialog.setMessage(R.string.favourites_delete);
            deleteDialog.setView(selectChunk);
            deleteDialog.setPositiveButton(R.string.action_delete, (dialog, which) -> {
                try {
                    final List<UserFavouriteHelper.Folder> folders =
                            UserFavouriteHelper.read(this);
                    for (int j = currentSelections.length - 1; j >= 0; j--) {
                        if (currentSelections[j] != 0) {
                            folders.remove(j);
                        }
                    }
                    UserFavouriteHelper.write(folders, this);
                    initFavourites(true);
                } catch (Exception e) {
                    ExceptionHelper.handleException(this, e, "deleteFolderConfirm",
                            "Unable to delete favourite folder.");
                }
            });
            deleteDialog.setNegativeButton(R.string.link_cancel, ((dialog, which) -> {
                // Cancelled, do nothing
            }));
            deleteDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "deleteFolder",
                    "Unable to read favourite folders.");
        }
    }

    private void renameFolder() {
        try {
            final AlertDialog.Builder renameDialog = new AlertDialog.Builder(this);
            renameDialog.setTitle(R.string.submenu_favourite_rename);
            renameDialog.setMessage(R.string.favourites_rename);
            // Setup each option in dialog.
            final View folderChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_list, null);
            final RadioGroup folderOptions = folderChunk.findViewById(R.id.option);
            final String[] allFolders = getFolders(this, false);
            for (int i = 0; i < allFolders.length; i++) {
                final RadioButton folderOption = new RadioButton(this);
                folderOption.setText(allFolders[i]);
                folderOption.setId(i);
                if (i == 0) {
                    folderOption.setChecked(true);
                }
                folderOptions.addView(folderOption);
            }
            renameDialog.setView(folderChunk);

            // When user tapped confirm or cancel...
            renameDialog.setPositiveButton(MainActivity.getRes().getString(R.string.link_confirm),
                    (dialog, which) -> {
                        try {
                            // Adapt New Folder Dialog
                            final View newFolderChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_new, null);
                            final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
                            folderName.setText(allFolders[folderOptions.getCheckedRadioButtonId()]);
                            final AlertDialog.Builder newFolderDialog = new AlertDialog.Builder(this);
                            newFolderDialog.setTitle(R.string.submenu_favourite_rename);
                            newFolderDialog.setMessage(R.string.favourites_new_folder);
                            newFolderDialog.setView(newFolderChunk);
                            newFolderDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                                // To be overwritten...
                            });
                            newFolderDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                                // Do nothing
                            });

                            final AlertDialog newFolderDialogCreated = newFolderDialog.create();
                            newFolderDialogCreated.show();
                            // Overwrite the positive button
                            newFolderDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                                try {
                                    final String inputtedName = folderName.getText().toString().trim();
                                    // Check if the input is legal
                                    if (validateFolderName(inputtedName, allFolders, this)) {
                                        final List<UserFavouriteHelper.Folder> folders =
                                                UserFavouriteHelper.read(this);
                                        folders.get(folderOptions.getCheckedRadioButtonId()).name =
                                                inputtedName;
                                        UserFavouriteHelper.write(folders, this);
                                        initFavourites(true);
                                        newFolderDialogCreated.dismiss();
                                    }
                                } catch (Exception e) {
                                    ExceptionHelper.handleException(FavouriteActivity.this, e,
                                            "newFolderDialog_Rename", "Unable to rename favourite folder.");
                                }
                            });
                        } catch (Exception e) {
                            ExceptionHelper.handleException(this, e, null, null);
                        }
                    });
            renameDialog.setNegativeButton(MainActivity.getRes().getString(R.string.link_cancel),
                    (dialog, which) -> {
                        // Cancelled.
                    });
            renameDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "renameFolder",
                    "Unable to read favourite folders.");
        }
    }

    private void setAbleToManage(final boolean newStatus) {
        DebugHelper.log("FavouriteActivity", "isAbleToManage set to " + newStatus);
        isAbleToManage = newStatus;
        // Avoid null pointers
        if (renameFolderItem != null && manageFolderItem != null && clearFolderItem != null) {
            renameFolderItem.setEnabled(newStatus);
            manageFolderItem.setEnabled(newStatus);
            clearFolderItem.setEnabled(newStatus);
        }
    }
}
