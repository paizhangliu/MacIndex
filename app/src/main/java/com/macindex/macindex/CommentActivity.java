package com.macindex.macindex;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * MacIndex Comment Activity
 * Jan. 13, 2021
 */
public class CommentActivity extends AppCompatActivity {

    private boolean isAbleToManage = false;

    private MenuItem manageCommentsItem = null;

    private MenuItem clearCommentsItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        WindowInsetsHelper.apply(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateUp();
            }
        });
        setTitle(getResources().getString(R.string.menu_comment));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }
        initComments();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (PrefsHelper.getBooleanPrefs("isCommentsReloadNeeded", this)) {
            initComments();
        }
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
                    (dialogInterface, i) -> {
                        UserCommentHelper.clear(this);
                        initComments();
                    });
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
        navigateUp();
        return true;
    }

    private void navigateUp() {
        if (!MainActivity.getMainState()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    private void initComments() {
        try {
            PrefsHelper.editPrefs("isCommentsReloadNeeded", false, this);
            final List<UserCommentHelper.Comment> comments = UserCommentHelper.read(this);
            final ListView commentList = findViewById(R.id.commentList);
            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final TextView emptyText = findViewById(R.id.emptyText);
            if (comments.isEmpty()) {
                commentList.setAdapter(null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    emptyText.setAutoSizeTextTypeWithDefaults(
                            TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText,
                            TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
                setAbleToManage(false);
                emptyLayout.setVisibility(View.VISIBLE);
                return;
            }

            final int[] machineIDs = new int[comments.size()];
            for (int i = 0; i < comments.size(); i++) {
                machineIDs[i] = MainActivity.getMachineHelper()
                        .getMachineID(comments.get(i).machineUID);
            }
            final int[] displayedIDs = PrefsHelper.getBooleanPrefs("isSortComment", this)
                    ? MainActivity.getMachineHelper().directSortByYear(machineIDs) : machineIDs;
            commentList.setAdapter(new CommentListAdapter(displayedIDs, comments, this));
            setAbleToManage(true);
            emptyLayout.setVisibility(View.GONE);
            DebugHelper.log("CommentActivity", comments.size()
                    + " Machines loaded in the container.");
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initComments",
                    "Illegal comment preference string.");
        }
    }

    // Adapted from FavouriteActivity
    private void deleteComments() {
        try {
            final List<UserCommentHelper.Comment> comments = UserCommentHelper.read(this);
            final View selectChunk = getLayoutInflater().inflate(
                    R.layout.chunk_favourites_select, null);
            final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
            final boolean[] currentSelections = new boolean[comments.size()];
            for (int i = 0; i < comments.size(); i++) {
                final CheckBox thisCheckBox = new CheckBox(this);
                thisCheckBox.setText(MainActivity.getMachineHelper()
                        .getIdentityName(comments.get(i).machineUID));
                final int finalI = i;
                thisCheckBox.setOnCheckedChangeListener((compoundButton, checked) ->
                        currentSelections[finalI] = checked);
                selectLayout.addView(thisCheckBox);
            }

            final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
            deleteDialog.setTitle(R.string.submenu_comments_delete);
            deleteDialog.setMessage(R.string.comments_delete);
            deleteDialog.setView(selectChunk);
            deleteDialog.setPositiveButton(R.string.action_delete, (dialog, which) -> {
                try {
                    for (int i = currentSelections.length - 1; i >= 0; i--) {
                        if (currentSelections[i]) {
                            comments.remove(i);
                        }
                    }
                    UserCommentHelper.write(comments, this);
                    initComments();
                } catch (Exception e) {
                    ExceptionHelper.handleException(this, e, "deleteCommentsConfirm",
                            "Illegal comment preference string.");
                }
            });
            deleteDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
                // Cancelled, do nothing.
            });
            deleteDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "deleteComments",
                    "Illegal comment preference string.");
        }
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
