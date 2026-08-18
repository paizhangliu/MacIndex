package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.userstate.UserComment;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MacIndex Comment Activity
 * Jan. 13, 2021
 */
public class CommentActivity extends AppCompatActivity {

    private boolean isAbleToManage = false;
    private MenuItem manageCommentsItem = null;
    private MenuItem clearCommentsItem = null;
    private MachineCatalog catalog;
    private UserStateLifecycleAdapter stateAdapter;
    private UserState currentState;
    private List<UserComment> renderedComments;
    private boolean renderedSortByIntroduction;
    private boolean renderedFixedNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        ContentInsetsHelper.apply(this);
        setTitle(getString(R.string.menu_comment));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        StartupUiGate.bind(this, (readyCatalog, repository) -> {
                        if (stateAdapter != null) return;
                        catalog = readyCatalog;
                        stateAdapter = new UserStateLifecycleAdapter(
                                CommentActivity.this,
                                repository,
                                state -> {
                                    currentState = state;
                                    final List<UserComment> comments =
                                            state.getLibrary().getComments();
                                    final boolean sortByIntroduction =
                                            state.getPreferences().getSortComments();
                                    final boolean fixedNavigation =
                                            state.getPreferences().getFixedNavigation();
                                    if (renderedComments == null
                                            || !renderedComments.equals(comments)
                                            || renderedSortByIntroduction != sortByIntroduction
                                            || renderedFixedNavigation != fixedNavigation) {
                                        initComments(comments, sortByIntroduction, fixedNavigation);
                                        renderedComments = comments;
                                        renderedSortByIntroduction = sortByIntroduction;
                                        renderedFixedNavigation = fixedNavigation;
                                    }
                                },
                                error -> ExceptionHelper.showUserStateReadFailure(
                                        CommentActivity.this, error));
                    });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_comment, menu);
        manageCommentsItem = menu.findItem(R.id.deleteCommentsItem);
        clearCommentsItem = menu.findItem(R.id.clearCommentsItem);
        manageCommentsItem.setEnabled(isAbleToManage);
        clearCommentsItem.setEnabled(isAbleToManage);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.deleteCommentsItem) {
            deleteComments();
        } else if (itemID == R.id.clearCommentsItem) {
            final AlertDialog.Builder clearWarningDialog = new AlertDialog.Builder(this);
            clearWarningDialog.setTitle(R.string.submenu_comments_clear);
            clearWarningDialog.setMessage(R.string.comments_clear_warning);
            clearWarningDialog.setPositiveButton(R.string.action_clear,
                    (dialogInterface, i) -> stateAdapter.execute(
                            UserStateCommands.clearComments(),
                            ignored -> { },
                            error -> ExceptionHelper.showUserStateWriteFailure(this, error,
                                    R.string.submenu_comments_clear,
                                    R.string.comments_clear_failed)));
            clearWarningDialog.setNegativeButton(R.string.link_cancel,
                    (dialogInterface, i) -> {
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

    private void initComments(final List<UserComment> comments,
                              final boolean sortByIntroduction,
                              final boolean fixedNavigation) {
        final ListView commentList = findViewById(R.id.commentList);
        final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
        final TextView emptyText = findViewById(R.id.emptyText);
        if (comments.isEmpty()) {
            commentList.setAdapter(null);
            TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText,
                    TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            setAbleToManage(false);
            emptyLayout.setVisibility(View.VISIBLE);
            return;
        }

        commentList.setAdapter(new CommentListAdapter(
                comments, catalog, sortByIntroduction, fixedNavigation, this));
        setAbleToManage(true);
        emptyLayout.setVisibility(View.GONE);
        DebugHelper.log("CommentActivity", comments.size()
                + " Machines loaded in the container.");
    }

    private void deleteComments() {
        if (currentState == null) return;
            final List<UserComment> comments = currentState.getLibrary().getComments();
            final View selectChunk = getLayoutInflater().inflate(
                    R.layout.chunk_favourites_select, null);
            final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
            final Set<String> selectedUids = new HashSet<>();
            for (UserComment comment : comments) {
                final CheckBox checkBox = new CheckBox(this);
                checkBox.setText(catalog.requireByUid(comment.getMachineUid()).name());
                checkBox.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) {
                        selectedUids.add(comment.getMachineUid());
                    } else {
                        selectedUids.remove(comment.getMachineUid());
                    }
                });
                selectLayout.addView(checkBox);
            }

            final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
            deleteDialog.setTitle(R.string.submenu_comments_delete);
            deleteDialog.setMessage(R.string.comments_delete);
            deleteDialog.setView(selectChunk);
            deleteDialog.setPositiveButton(R.string.action_delete, (dialog, which) ->
                    stateAdapter.execute(
                            UserStateCommands.removeComments(selectedUids),
                            ignored -> { },
                            error -> ExceptionHelper.showUserStateWriteFailure(this, error,
                                    R.string.submenu_comments_delete,
                                    R.string.comments_delete_failed)));
            deleteDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
                // Cancelled, do nothing.
            });
        deleteDialog.show();
    }

    private void setAbleToManage(final boolean newStatus) {
        DebugHelper.log("CommentActivity", "isAbleToManage set to " + newStatus);
        isAbleToManage = newStatus;
        if (manageCommentsItem != null && clearCommentsItem != null) {
            manageCommentsItem.setEnabled(newStatus);
            clearCommentsItem.setEnabled(newStatus);
        }
    }

}
