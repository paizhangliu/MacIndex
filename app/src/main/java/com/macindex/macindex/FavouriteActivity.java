package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.os.Bundle;
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

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.userstate.FavouriteFolder;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLimits;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MacIndex Favourite Activity
 * Jan. 15, 2021
 */
public class FavouriteActivity extends AppCompatActivity {

    private boolean isAbleToManage = false;
    private MenuItem renameFolderItem = null;
    private MenuItem manageFolderItem = null;
    private MenuItem clearFolderItem = null;
    private MenuItem addFolderItem = null;
    private MachineCatalog catalog;
    private UserStateLifecycleAdapter stateAdapter;
    private UserState currentState;
    private boolean contentReady;
    private List<FavouriteFolder> renderedFolders;
    private boolean renderedSortByIntroduction;
    private boolean renderedFixedNavigation;
    private final Set<Long> expandedFolderIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favourite);
        ContentInsetsHelper.apply(this);
        setTitle(getString(R.string.menu_favourite));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final View emptyLayout = findViewById(R.id.emptyLayout);
        emptyLayout.setEnabled(false);
        emptyLayout.setOnClickListener(view -> createFolder());
        StartupUiGate.bind(this, (readyCatalog, repository) -> {
                        if (stateAdapter != null) return;
                        catalog = readyCatalog;
                        stateAdapter = new UserStateLifecycleAdapter(
                                FavouriteActivity.this,
                                repository,
                                state -> {
                                    currentState = state;
                                    setContentReady(true);
                                    final List<FavouriteFolder> folders =
                                            state.getLibrary().getFavouriteFolders();
                                    final boolean sortByIntroduction =
                                            state.getPreferences().getSortComments();
                                    final boolean fixedNavigation =
                                            state.getPreferences().getFixedNavigation();
                                    if (renderedFolders == null
                                            || !renderedFolders.equals(folders)
                                            || renderedSortByIntroduction != sortByIntroduction
                                            || renderedFixedNavigation != fixedNavigation) {
                                        initFavourites(folders, sortByIntroduction, fixedNavigation);
                                        renderedFolders = folders;
                                        renderedSortByIntroduction = sortByIntroduction;
                                        renderedFixedNavigation = fixedNavigation;
                                    }
                                },
                                error -> ExceptionHelper.showUserStateReadFailure(
                                        FavouriteActivity.this, error));
                    });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_favourite, menu);
        renameFolderItem = menu.findItem(R.id.renameFolderItem);
        manageFolderItem = menu.findItem(R.id.deleteFolderItem);
        clearFolderItem = menu.findItem(R.id.clearFolderItem);
        addFolderItem = menu.findItem(R.id.addFolderItem);
        addFolderItem.setEnabled(contentReady);
        renameFolderItem.setEnabled(isAbleToManage);
        manageFolderItem.setEnabled(isAbleToManage);
        clearFolderItem.setEnabled(isAbleToManage);
        return true;
    }

    private void setContentReady(final boolean ready) {
        contentReady = ready;
        findViewById(R.id.emptyLayout).setEnabled(ready);
        if (addFolderItem != null) {
            addFolderItem.setEnabled(ready);
        }
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
            clearFoldersDialog.setPositiveButton(R.string.action_clear, (dialog, which) ->
                    stateAdapter.execute(
                            UserStateCommands.clearFavouriteFolders(),
                            ignored -> { },
                            error -> ExceptionHelper.showUserStateWriteFailure(this, error,
                                    R.string.submenu_favourite_clear,
                                    R.string.favourites_clear_failed)));
            clearFoldersDialog.setNegativeButton(R.string.link_cancel, null);
            clearFoldersDialog.show();
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

    private void initFavourites(final List<FavouriteFolder> folders,
                                final boolean sortByIntroduction,
                                final boolean fixedNavigation) {
        final LinearLayout categoryContainer = findViewById(R.id.categoryContainer);
            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final TextView emptyText = findViewById(R.id.emptyText);
            final LayoutTransition transition = categoryContainer.getLayoutTransition();
            if (transition != null) {
                transition.enableTransitionType(LayoutTransition.CHANGING);
            }

            if (folders.isEmpty()) {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        emptyText, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                setAbleToManage(false);
                emptyLayout.setVisibility(View.VISIBLE);
            } else {
                setAbleToManage(true);
                emptyLayout.setVisibility(View.GONE);
            }

        categoryContainer.setLayoutTransition(null);
        try {
            categoryContainer.removeAllViews();
            final Set<Long> existingFolderIds = new HashSet<>();
            for (FavouriteFolder folder : folders) {
                existingFolderIds.add(folder.getId());
                addFolderView(categoryContainer, folder, sortByIntroduction, fixedNavigation);
            }
            expandedFolderIds.retainAll(existingFolderIds);
            removeLastFolderDivider(categoryContainer);
        } finally {
            categoryContainer.setLayoutTransition(transition);
        }
    }

    private void addFolderView(final LinearLayout categoryContainer,
                               final FavouriteFolder folder,
                               final boolean sortByIntroduction,
                               final boolean fixedNavigation) {
        final View categoryChunk = getLayoutInflater().inflate(
                R.layout.chunk_category, categoryContainer, false);
        final LinearLayout categoryLayout = categoryChunk.findViewById(
                R.id.categoryInfoLayout);
        final TextView categoryName = categoryChunk.findViewById(R.id.category);
        setUniformAutoSize(categoryName);

        if (folder.getMachineUids().isEmpty()) {
            categoryName.setText(getString(R.string.favourites_empty_folder, folder.getName()));
            categoryContainer.addView(categoryChunk);
            return;
        }

        categoryName.setText(folder.getName());
        final List<Machine> machines = UserLibraryViewAdapter.resolveMachines(
                catalog, folder.getMachineUids(), sortByIntroduction);
        final boolean initiallyExpanded = expandedFolderIds.contains(folder.getId());
        if (initiallyExpanded) {
            UserLibraryViewAdapter.addFavouriteMachineRows(
                    categoryLayout, machines, catalog, fixedNavigation, this);
            setFolderExpanded(categoryLayout, true);
        }
        categoryName.setOnClickListener(new View.OnClickListener() {
            private boolean expanded = initiallyExpanded;
            private boolean loaded = initiallyExpanded;

            @Override
            public void onClick(View view) {
                if (!loaded) {
                    UserLibraryViewAdapter.addFavouriteMachineRows(
                            categoryLayout, machines, catalog, fixedNavigation,
                            FavouriteActivity.this);
                    loaded = true;
                }
                expanded = !expanded;
                if (expanded) {
                    expandedFolderIds.add(folder.getId());
                } else {
                    expandedFolderIds.remove(folder.getId());
                }
                setFolderExpanded(categoryLayout, expanded);
            }
        });
        categoryContainer.addView(categoryChunk);
    }

    private static void setFolderExpanded(final LinearLayout categoryLayout,
                                          final boolean expanded) {
        int machineStart = 1;
        final View firstAfterTitle = categoryLayout.getChildAt(1);
        if (firstAfterTitle != null && !(firstAfterTitle instanceof LinearLayout)) {
            firstAfterTitle.setVisibility(expanded ? View.GONE : View.VISIBLE);
            machineStart = 2;
        }
        for (int i = machineStart; i < categoryLayout.getChildCount(); i++) {
            categoryLayout.getChildAt(i).setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
    }

    private static void removeLastFolderDivider(final LinearLayout categoryContainer) {
        if (categoryContainer.getChildCount() == 0) return;
        final View last = categoryContainer.getChildAt(categoryContainer.getChildCount() - 1);
        if (last instanceof LinearLayout) {
            final LinearLayout layout = (LinearLayout) last;
            if (layout.getChildCount() > 1 && !(layout.getChildAt(1) instanceof LinearLayout)) {
                layout.removeViewAt(1);
            }
        }
    }

    private static String[] getFolderLabels(final List<FavouriteFolder> folders,
                                            final boolean includeCount) {
        final String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            final FavouriteFolder folder = folders.get(i);
            labels[i] = folder.getName() + (includeCount
                    ? " (" + folder.getMachineUids().size() + ")" : "");
        }
        return labels;
    }

    private void createFolder() {
        if (currentState == null) return;
            final List<FavouriteFolder> folders = currentState.getLibrary().getFavouriteFolders();
            if (folders.size() >= UserStateLimits.MAX_FOLDERS) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.submenu_favourite_add)
                        .setMessage(getString(R.string.favourites_error_limit,
                                UserStateLimits.MAX_FOLDERS))
                        .setPositiveButton(R.string.link_confirm, null)
                        .show();
                return;
            }

            final View newFolderChunk = getLayoutInflater().inflate(
                    R.layout.chunk_favourites_new, null);
            final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.submenu_favourite_add)
                    .setMessage(R.string.favourites_new_folder)
                    .setView(newFolderChunk)
                    .setPositiveButton(R.string.link_confirm, null)
                    .setNegativeButton(R.string.link_cancel, null)
                    .create();
            dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        final String inputtedName = folderName.getText().toString().trim();
                        if (FavouriteFolderNameValidator.validate(
                                folderName, inputtedName, folders)) {
                            stateAdapter.execute(
                                    UserStateCommands.createFavouriteFolder(inputtedName),
                                    ignored -> dialog.dismiss(),
                                    error -> ExceptionHelper.showUserStateEditFailure(
                                            this, error, R.string.submenu_favourite_add,
                                            R.string.favourites_save_failed));
                        }
                    }));
            dialog.show();
    }

    private void deleteFolder() {
        if (currentState == null) return;
            final List<FavouriteFolder> folders = currentState.getLibrary().getFavouriteFolders();
            final String[] labels = getFolderLabels(folders, true);
            final Set<Long> selectedIds = new HashSet<>();
            final View selectChunk = getLayoutInflater().inflate(
                    R.layout.chunk_favourites_select, null);
            final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
            for (int i = 0; i < folders.size(); i++) {
                final FavouriteFolder folder = folders.get(i);
                final CheckBox checkBox = new CheckBox(this);
                checkBox.setText(labels[i]);
                checkBox.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) {
                        selectedIds.add(folder.getId());
                    } else {
                        selectedIds.remove(folder.getId());
                    }
                });
                selectLayout.addView(checkBox);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.submenu_favourite_delete)
                    .setMessage(R.string.favourites_delete)
                    .setView(selectChunk)
                    .setPositiveButton(R.string.action_delete, (dialog, which) ->
                            stateAdapter.execute(
                                    UserStateCommands.deleteFavouriteFolders(selectedIds),
                                    ignored -> { },
                                    error -> ExceptionHelper.showUserStateEditFailure(this, error,
                                            R.string.submenu_favourite_delete,
                                            R.string.favourites_delete_failed)))
                    .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void renameFolder() {
        if (currentState == null) return;
            final List<FavouriteFolder> folders = currentState.getLibrary().getFavouriteFolders();
            final String[] allNames = getFolderLabels(folders, false);
            final View folderChunk = getLayoutInflater().inflate(
                    R.layout.chunk_favourites_list, null);
            final RadioGroup folderOptions = folderChunk.findViewById(R.id.option);
            for (int i = 0; i < allNames.length; i++) {
                final RadioButton option = new RadioButton(this);
                option.setText(allNames[i]);
                option.setId(i);
                option.setChecked(i == 0);
                folderOptions.addView(option);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.submenu_favourite_rename)
                    .setMessage(R.string.favourites_rename)
                    .setView(folderChunk)
                    .setPositiveButton(R.string.link_confirm, (dialog, which) -> {
                        final int folderIndex = folderOptions.getCheckedRadioButtonId();
                        if (folderIndex < 0 || folderIndex >= folders.size()) return;
                        showRenameInput(folders.get(folderIndex), folders);
                    })
                    .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void showRenameInput(final FavouriteFolder folder,
                                 final List<FavouriteFolder> folders) {
        final View newFolderChunk = getLayoutInflater().inflate(
                R.layout.chunk_favourites_new, null);
        final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
        folderName.setText(folder.getName());
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.submenu_favourite_rename)
                .setMessage(R.string.favourites_new_folder)
                .setView(newFolderChunk)
                .setPositiveButton(R.string.link_confirm, null)
                .setNegativeButton(R.string.link_cancel, null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    final String inputtedName = folderName.getText().toString().trim();
                    if (inputtedName.equals(folder.getName())) {
                        dialog.dismiss();
                    } else if (FavouriteFolderNameValidator.validate(
                            folderName, inputtedName, folders)) {
                        stateAdapter.execute(
                                UserStateCommands.renameFavouriteFolder(
                                        folder.getId(), inputtedName),
                                ignored -> dialog.dismiss(),
                                error -> ExceptionHelper.showUserStateEditFailure(
                                        this, error, R.string.submenu_favourite_rename,
                                        R.string.favourites_rename_failed));
                    }
                }));
        dialog.show();
    }

    private static void setUniformAutoSize(final TextView view) {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                view, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
    }

    private void setAbleToManage(final boolean newStatus) {
        isAbleToManage = newStatus;
        if (renameFolderItem != null && manageFolderItem != null && clearFolderItem != null) {
            renameFolderItem.setEnabled(newStatus);
            manageFolderItem.setEnabled(newStatus);
            clearFolderItem.setEnabled(newStatus);
        }
    }

}
