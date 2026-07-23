package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * MacIndex Comment Activity
 * Jan. 13, 2021
 */
public class CommentActivity extends AppCompatActivity {

    private int[] machineIDs;

    private ProgressDialog waitDialog = null;

    private Thread commentsThread = null;

    private volatile int commentsRequestID = 0;

    private boolean isAbleToManage = false;

    private MenuItem manageCommentsItem = null;

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
        this.setTitle(getResources().getString(R.string.menu_comment));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        MainActivity.validateOperation(this);

        waitDialog = new ProgressDialog(this);
        waitDialog.setMessage(getString(R.string.loading_comments));
        waitDialog.setCancelable(false);

        if (savedInstanceState != null && savedInstanceState.getBoolean("loadComplete")) {
            machineIDs = savedInstanceState.getIntArray("machineIDs");
            initComments(false);
        } else {
            // Check whether if the string is empty on creation.
            if (PrefsHelper.getStringPrefs("userComments", this).isEmpty()) {
                final AlertDialog.Builder nullWarningDialog = new AlertDialog.Builder(this);
                nullWarningDialog.setTitle(R.string.menu_comment);
                nullWarningDialog.setMessage(R.string.comments_not_available);
                nullWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                    // Confirmed.
                });
                nullWarningDialog.show();
            }
            initComments(true);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // If reload is needed..
        if (PrefsHelper.getBooleanPrefs("isCommentsReloadNeeded", this)) {
            initComments(true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (waitDialog != null && !waitDialog.isShowing() && machineIDs != null) {
            outState.putBoolean("loadComplete", true);
            outState.putIntArray("machineIDs", machineIDs);
        } else {
            outState.putBoolean("loadComplete", false);
        }
    }

    @Override
    protected void onDestroy() {
        commentsRequestID++;
        if (commentsThread != null) {
            commentsThread.interrupt();
            commentsThread = null;
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_comment, menu);
        manageCommentsItem = menu.findItem(R.id.deleteCommentsItem);
        manageCommentsItem.setEnabled(isAbleToManage);
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
            clearWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                PrefsHelper.clearPrefs("userComments", this);
                initComments(true);
            });
            clearWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
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

    private void initComments(final boolean reloadPositions) {
        try {
            final int requestID = ++commentsRequestID;
            if (commentsThread != null) {
                commentsThread.interrupt();
            }
            if (waitDialog != null && waitDialog.isShowing()) {
                waitDialog.dismiss();
            }
            // Reset reload parameter
            PrefsHelper.editPrefs("isCommentsReloadNeeded", false, this);

            // Init Container...
            final LinearLayout commentContainer = findViewById(R.id.commentContainer);
            LayoutTransition layoutTransition = commentContainer.getLayoutTransition();
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            commentContainer.removeAllViews();

            final LinearLayout emptyLayout = findViewById(R.id.emptyLayout);
            final TextView emptyText = findViewById(R.id.emptyText);

            if (PrefsHelper.getStringPrefs("userComments", this).length() != 0) {
                // Adapt new behaviour
                setAbleToManage(true);
                emptyLayout.setVisibility(View.GONE);
                String[] thisCommentsStrings = PrefsHelper.getStringPrefs("userComments", this).split("││");
                if (reloadPositions) {
                    waitDialog.show();
                }
                commentsThread = new Thread() {
                    @Override
                    public void run() {
                        final int[] machineIDsForRequest;
                        try {
                            if (reloadPositions) {
                                // Run searches on the separate thread.
                                final List<Integer> validMachineIDs = new ArrayList<>();
                                for (int i = 0; i < thisCommentsStrings.length; i++) {
                                    String[] splitedThisString = thisCommentsStrings[i].split("│");
                                    int[] thisID = MainActivity.getMachineHelper().searchHelper("name", splitedThisString[0],
                                            "all", true, false);
                                    if (thisID.length != 1) {
                                        Log.e("CommentSearchThread", "Error occurred on search string " + splitedThisString[0]);
                                        continue;
                                    }
                                    validMachineIDs.add(thisID[0]);
                                }
                                machineIDsForRequest = new int[validMachineIDs.size()];
                                for (int i = 0; i < validMachineIDs.size(); i++) {
                                    machineIDsForRequest[i] = validMachineIDs.get(i);
                                }
                            } else {
                                machineIDsForRequest = machineIDs == null ? new int[0] : machineIDs;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                        if (Thread.currentThread().isInterrupted() || requestID != commentsRequestID) {
                            return;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestID != commentsRequestID || isFinishing()
                                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                    return;
                                }
                                try {
                                    machineIDs = machineIDsForRequest;
                                    if (reloadPositions) {
                                        waitDialog.dismiss();
                                    }
                                    // Update the UI after the thread done.
                                    for (int i = 0; i < machineIDs.length; i++) {
                                        final View commentsChunk = getLayoutInflater()
                                                .inflate(R.layout.chunk_comments, commentContainer, false);
                                        final TextView machineName = commentsChunk.findViewById(R.id.machineName);
                                        final TextView machineComment = commentsChunk.findViewById(R.id.machineComment);
                                        final LinearLayout commentChunk = commentsChunk.findViewById(R.id.comment_chunk);

                                        // Set Machine Info Accordingly
                                        final String thisName = MainActivity.getMachineHelper().getName(machineIDs[i]);
                                        machineName.setText(thisName);
                                        for (String thisString : thisCommentsStrings) {
                                            String[] commentParts = thisString.split("│", 2);
                                            if (commentParts.length == 2 && commentParts[0].equals(thisName)) {
                                                machineComment.setText(commentParts[1]);
                                                break;
                                            }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            machineName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                        } else {
                                            TextViewCompat.setAutoSizeTextTypeWithDefaults(machineName, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                                        }

                                        int finalI = i;
                                        commentChunk.setOnClickListener(view -> {
                                            SpecsIntentHelper.sendIntent(machineIDs, machineIDs[finalI], CommentActivity.this, false);
                                        });
                                        commentChunk.setOnLongClickListener(view -> {
                                            ClipboardManager clipboard = (ClipboardManager) CommentActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                                            ClipData clip = ClipData.newPlainText("userComment", machineComment.getText());
                                            clipboard.setPrimaryClip(clip);
                                            Toast.makeText(CommentActivity.this, MainActivity.getRes().getString(R.string.error_copy_information), Toast.LENGTH_LONG).show();
                                            return true;
                                        });

                                        commentContainer.addView(commentsChunk);
                                    }
                                    Log.i("CommentSearchThread", thisCommentsStrings.length + " Machines loaded in the container.");
                                } catch (final Exception e) {
                                    ExceptionHelper.handleException(CommentActivity.this, e, "CommentSearchThread", "Cannot add children to container. Likely illegal comment prefs string. Please reset the application. String is: "
                                            + PrefsHelper.getStringPrefs("userComments", CommentActivity.this));
                                }
                            }
                        });
                    }
                };
                commentsThread.start();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    emptyText.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(emptyText, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
                setAbleToManage(false);
                emptyLayout.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }

    // Deleted previous empty detection since ver. 4.9

    // Adapted from FavouriteActivity
    private void deleteComments() {
        try {
            if (PrefsHelper.getStringPrefs("userComments", this).isEmpty()) {
                // Under the new behaviour, this branch should not be taken.
                throw new IllegalAccessException("Should not enter this MenuItem");
            } else {
                final View selectChunk = this.getLayoutInflater().inflate(R.layout.chunk_favourites_select, null);
                final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
                final String[] thisCommentsStrings = PrefsHelper.getStringPrefs("userComments", this).split("││");
                final int[] currentSelections = new int[thisCommentsStrings.length];
                for (int i = 0; i < thisCommentsStrings.length; i++) {
                    CheckBox thisCheckBox = new CheckBox(this);
                    thisCheckBox.setText(thisCommentsStrings[i].split("│")[0]);
                    thisCheckBox.setChecked(false);
                    int finalI = i;
                    thisCheckBox.setOnCheckedChangeListener((compoundButton, b) -> {
                        currentSelections[finalI] = thisCheckBox.isChecked() ? 1 : 0;
                    });
                    selectLayout.addView(thisCheckBox);
                }

                // Create the dialog.
                final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
                deleteDialog.setTitle(R.string.submenu_comments_delete);
                deleteDialog.setMessage(R.string.comments_delete);
                deleteDialog.setView(selectChunk);
                deleteDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
                    try {
                        // Delete the folders.
                        String newString = "";
                        for (int j = 0; j < thisCommentsStrings.length; j++) {
                            if (currentSelections[j] == 0) {
                                newString = newString.concat("││" + thisCommentsStrings[j]);
                            }
                        }
                        if (!newString.isEmpty()) {
                            newString = newString.substring(2);
                        }
                        PrefsHelper.editPrefs("userComments", newString, this);
                        initComments(true);
                    } catch (Exception e) {
                        ExceptionHelper.handleException(this, e, "deleteCommentsConfirm", "Illegal comment prefs string. Please reset the application. String is: "
                                + PrefsHelper.getStringPrefs("userComments", this));
                    }
                });
                deleteDialog.setNegativeButton(R.string.link_cancel, ((dialog, which) -> {
                    // Cancelled, do nothing
                }));
                deleteDialog.show();
            }
        } catch (final Exception e) {
            ExceptionHelper.handleException(CommentActivity.this, e, "deleteComments", "Illegal comment prefs string. Please reset the application. String is: "
                    + PrefsHelper.getStringPrefs("userComments", CommentActivity.this));
        }
    }

    private void setAbleToManage(final boolean newStatus) {
        Log.i("CommentActivity", "isAbleToManage set to " + newStatus);
        isAbleToManage = newStatus;
        // Avoid null pointers
        if (manageCommentsItem != null) {
            manageCommentsItem.setEnabled(newStatus);
        }
    }
}
