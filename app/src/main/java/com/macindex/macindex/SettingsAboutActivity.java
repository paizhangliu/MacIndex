package com.macindex.macindex;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.macindex.macindex.userstate.Appearance;
import com.macindex.macindex.userstate.InvalidUserDataException;
import com.macindex.macindex.userstate.PreparedUserDataImport;
import com.macindex.macindex.userstate.ThemeBootstrapStore;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommand;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;
import com.macindex.macindex.userstate.UserStateUnavailableException;

import java.io.IOException;

public class SettingsAboutActivity extends AppCompatActivity {

    private SettingsAboutViewModel userDataViewModel;

    private UserStateLifecycleAdapter userStateAdapter;

    private boolean settingsInitialized;

    private boolean renderingSettings;

    private UserState renderedSettingsState;

    private ProgressDialog userDataProgressDialog;

    private AlertDialog importConfirmationDialog;

    private AlertDialog userDataMessageDialog;

    private View userDataButton;

    private boolean activityResumed;

    private final ActivityResultLauncher<String> exportUserDataLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                        if (uri != null) {
                            exportUserData(uri);
                        }
                    });

    private final ActivityResultLauncher<String[]> importUserDataLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importUserData(uri);
                }
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_about);
        ContentInsetsHelper.apply(this);
        this.setTitle(getResources().getString(R.string.menu_about_settings));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userDataViewModel = new ViewModelProvider(this).get(SettingsAboutViewModel.class);
        userDataViewModel.getState().observe(this, this::renderUserDataState);
        StartupUiGate.bind(this, (catalog, userState) -> {
                        userDataViewModel.initialize(userState, catalog);
                        if (userStateAdapter == null) {
                            userStateAdapter = new UserStateLifecycleAdapter(
                                    SettingsAboutActivity.this, userState,
                                    SettingsAboutActivity.this::renderSettings,
                                    error -> ExceptionHelper.showUserStateReadFailure(
                                            SettingsAboutActivity.this, error));
                        }
                    });
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (userDataViewModel != null) {
            renderUserDataState(userDataViewModel.getState().getValue());
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dismissUserDataProgress();
        dismissImportConfirmation();
        dismissUserDataMessage();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initSettings(final UserState state) {
        final SwitchMaterial swSortComment = findViewById(R.id.switchSortComment);
        final SwitchMaterial swDeathSound = findViewById(R.id.switchDeathSound);
        final SwitchMaterial swNavButtons = findViewById(R.id.switchNavButtons);
        final SwitchMaterial swQuickNav = findViewById(R.id.switchQuickNav);
        final SwitchMaterial swRandomAll = findViewById(R.id.switchRandomAll);
        final SwitchMaterial swSaveMainUsage = findViewById(R.id.switchSaveMainUsage);
        final SwitchMaterial swSaveCompareUsage = findViewById(R.id.switchSaveCompareUsage);
        final SwitchMaterial swAutoCheckUpdate = findViewById(R.id.switchAutoCheckUpdate);
        final SwitchMaterial swVolWarning = findViewById(R.id.switchVolWarning);
        final Spinner appearanceSpinner = findViewById(R.id.appearanceSpinner);

        userDataButton = findViewById(R.id.userDataButton);
        userDataButton.setOnClickListener(view -> showUserDataOptions());
        final String[] appearanceOptions = getAppearanceOptions();
        final ArrayAdapter<String> appearanceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, appearanceOptions);
        appearanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appearanceSpinner.setAdapter(appearanceAdapter);
        final ThemeBootstrapStore themeStore =
                ((MacIndexApplication) getApplication()).themeBootstrapStore();
        final Appearance[] appearances = Appearance.values();
        appearanceSpinner.setSelection(themeStore.read().ordinal());
        appearanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int selection, final long id) {
                final Appearance appearance = appearances[selection];
                if (appearance != themeStore.read()) {
                    try {
                        themeStore.write(appearance);
                        ThemeHelper.apply(appearance);
                    } catch (UserStateUnavailableException failure) {
                        Log.e("Appearance", "Unable to save appearance.", failure);
                        appearanceSpinner.setSelection(themeStore.read().ordinal());
                        ExceptionHelper.showMessageDialog(SettingsAboutActivity.this,
                                R.string.setting_appearance,
                                R.string.setting_appearance_save_failed);
                    }
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
            }
        });
        findViewById(R.id.appearanceButton).setOnClickListener(
                view -> appearanceSpinner.performClick());

        settingsInitialized = true;
        renderSettings(state);

        swSortComment.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setSortComments(isChecked));
        });
        swDeathSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setPlayDeathSound(isChecked));
        });
        swNavButtons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(
                    UserStateCommands.setUseNavigationButtons(isChecked));
        });
        swQuickNav.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setFixedNavigation(isChecked));
        });
        swRandomAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(
                    UserStateCommands.setLimitRandomToCurrentBrowse(isChecked));
        });
        swSaveMainUsage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setRememberMainState(isChecked));
        });
        swSaveCompareUsage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setRememberCompareState(isChecked));
        });
        swAutoCheckUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setAutomaticUpdateChecks(isChecked));
        });
        swVolWarning.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!renderingSettings) executeSetting(UserStateCommands.setVolumeWarning(isChecked));
        });
    }

    private void renderSettings(final UserState state) {
        renderedSettingsState = state;
        if (!settingsInitialized) {
            initSettings(state);
            return;
        }
        renderingSettings = true;
        try {
            final com.macindex.macindex.userstate.UserPreferences preferences =
                    state.getPreferences();
            ((SwitchMaterial) findViewById(R.id.switchSortComment))
                    .setChecked(preferences.getSortComments());
            ((SwitchMaterial) findViewById(R.id.switchDeathSound))
                    .setChecked(preferences.getPlayDeathSound());
            final SwitchMaterial navigation = findViewById(R.id.switchNavButtons);
            navigation.setChecked(preferences.getUseNavigationButtons());
            final SwitchMaterial fixedNavigation = findViewById(R.id.switchQuickNav);
            fixedNavigation.setChecked(preferences.getFixedNavigation());
            fixedNavigation.setEnabled(preferences.getUseNavigationButtons());
            ((SwitchMaterial) findViewById(R.id.switchRandomAll))
                    .setChecked(preferences.getLimitRandomToCurrentBrowse());
            ((SwitchMaterial) findViewById(R.id.switchSaveMainUsage))
                    .setChecked(preferences.getRememberMainState());
            ((SwitchMaterial) findViewById(R.id.switchSaveCompareUsage))
                    .setChecked(preferences.getRememberCompareState());
            ((SwitchMaterial) findViewById(R.id.switchAutoCheckUpdate))
                    .setChecked(preferences.getAutomaticallyCheckUpdates());
            ((SwitchMaterial) findViewById(R.id.switchVolWarning))
                    .setChecked(preferences.getEnableVolumeWarning());
        } finally {
            renderingSettings = false;
        }
    }

    private void executeSetting(final UserStateCommand<UserState> command) {
        if (userStateAdapter == null) {
            return;
        }
        userStateAdapter.execute(command, ignored -> { },
                error -> {
                    if (renderedSettingsState != null) {
                        renderSettings(renderedSettingsState);
                    }
                    ExceptionHelper.showUserStateWriteFailure(this, error,
                            R.string.menu_about_settings, R.string.setting_save_failed);
                });
    }

    private String[] getAppearanceOptions() {
        return new String[]{
                getString(R.string.setting_appearance_system),
                getString(R.string.setting_appearance_light),
                getString(R.string.setting_appearance_dark)
        };
    }

    private void showUserDataOptions() {
        if (userDataViewModel == null || !userDataViewModel.isIdle()) {
            return;
        }
        final String[] options = new String[]{
                getString(R.string.user_data_export),
                getString(R.string.user_data_import)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_user_data)
                .setItems(options, (dialogInterface, selection) -> {
                    if (selection == 0) {
                        launchExportDocumentPicker();
                    } else {
                        launchImportDocumentPicker();
                    }
                })
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void launchExportDocumentPicker() {
        try {
            exportUserDataLauncher.launch(SettingsAboutViewModel.DEFAULT_FILE_NAME);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w("ExportUserData", "Unable to open the export document picker.", e);
            ExceptionHelper.showMessageDialog(this, R.string.user_data_export,
                    R.string.user_data_export_picker_failed);
        }
    }

    private void launchImportDocumentPicker() {
        try {
            importUserDataLauncher.launch(new String[]{
                    "application/json", "text/json", "text/plain"
            });
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w("ImportUserData", "Unable to open the import document picker.", e);
            ExceptionHelper.showMessageDialog(this, R.string.user_data_import,
                    R.string.user_data_import_picker_failed);
        }
    }

    private void exportUserData(final Uri uri) {
        userDataViewModel.exportUserData(uri);
    }

    private void importUserData(final Uri uri) {
        userDataViewModel.readImport(uri);
    }

    private void renderUserDataState(final SettingsAboutViewModel.State state) {
        if (state == null || !activityResumed) {
            return;
        }
        setUserDataButtonEnabled(state.status == SettingsAboutViewModel.Status.IDLE);
        if (state.isRunning()) {
            dismissImportConfirmation();
            dismissUserDataMessage();
            final int message;
            if (state.status == SettingsAboutViewModel.Status.EXPORTING) {
                message = R.string.loading_user_data_export;
            } else if (state.status == SettingsAboutViewModel.Status.APPLYING_IMPORT) {
                message = R.string.loading_user_data_apply;
            } else {
                message = R.string.loading_user_data_import;
            }
            showUserDataProgress(message);
            return;
        }

        dismissUserDataProgress();
        if (state.status == SettingsAboutViewModel.Status.CONFIRMING_IMPORT) {
            dismissUserDataMessage();
            showImportConfirmation(state.imported);
        } else {
            dismissImportConfirmation();
        }

        if (state.status == SettingsAboutViewModel.Status.EXPORT_SUCCEEDED) {
            userDataViewModel.acknowledge(state);
            Toast.makeText(this, R.string.user_data_export_success,
                    Toast.LENGTH_SHORT).show();
        } else if (state.status == SettingsAboutViewModel.Status.IMPORT_SUCCEEDED) {
            userDataViewModel.acknowledge(state);
            Toast.makeText(this, R.string.user_data_import_success,
                    Toast.LENGTH_SHORT).show();
        } else if (state.status == SettingsAboutViewModel.Status.EXPORT_FAILED
                || state.status == SettingsAboutViewModel.Status.IMPORT_READ_FAILED
                || state.status == SettingsAboutViewModel.Status.IMPORT_APPLY_FAILED) {
            showUserDataError(state);
        }
    }

    private void showUserDataProgress(final int message) {
        dismissUserDataProgress();
        userDataProgressDialog = new ProgressDialog(this);
        userDataProgressDialog.setMessage(getString(message));
        userDataProgressDialog.setIndeterminate(true);
        userDataProgressDialog.setCancelable(false);
        userDataProgressDialog.show();
    }

    private void dismissUserDataProgress() {
        if (userDataProgressDialog != null) {
            userDataProgressDialog.dismiss();
            userDataProgressDialog = null;
        }
    }

    private void setUserDataButtonEnabled(final boolean enabled) {
        if (userDataButton != null) {
            userDataButton.setEnabled(enabled);
        }
    }

    private void showImportConfirmation(final PreparedUserDataImport imported) {
        if (imported == null || importConfirmationDialog != null) {
            return;
        }
        String information = getString(R.string.user_data_import_information,
                imported.getCommentCount(), imported.getFavouriteCount(),
                imported.getFolderCount(), imported.getCompareCount());
        if (imported.getRemovedCount() != 0) {
            information += "\n\n" + getString(R.string.user_data_import_removed,
                    imported.getRemovedCount());
        }
        importConfirmationDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.user_data_import)
                .setMessage(information)
                .setPositiveButton(R.string.link_confirm,
                        (dialogInterface, selection) -> userDataViewModel.confirmImport())
                .setNegativeButton(R.string.link_cancel,
                        (dialogInterface, selection) -> userDataViewModel.cancelImport())
                .setOnCancelListener(dialogInterface -> userDataViewModel.cancelImport())
                .create();
        importConfirmationDialog.setOnDismissListener(dialog -> importConfirmationDialog = null);
        importConfirmationDialog.show();
    }

    private void dismissImportConfirmation() {
        if (importConfirmationDialog != null) {
            importConfirmationDialog.dismiss();
            importConfirmationDialog = null;
        }
    }

    private void showUserDataError(final SettingsAboutViewModel.State state) {
        if (state.status == SettingsAboutViewModel.Status.EXPORT_FAILED
                && state.error instanceof IOException) {
            Log.w("ExportUserData", "Unable to write the export file.", state.error);
            showUserDataChoice(state, R.string.user_data_export,
                    R.string.user_data_export_failed, R.string.user_data_choose_location,
                    this::launchExportDocumentPicker);
        } else if (state.status == SettingsAboutViewModel.Status.EXPORT_FAILED
                && state.error instanceof UserStateUnavailableException) {
            Log.e("ExportUserData", "Unable to prepare user data for export.", state.error);
            showUserDataChoice(state, R.string.user_data_export,
                    R.string.user_data_export_prepare_failed,
                    R.string.action_try_again, this::launchExportDocumentPicker);
        } else if (state.status == SettingsAboutViewModel.Status.IMPORT_READ_FAILED
                && state.error instanceof InvalidUserDataException) {
            Log.w("ImportUserData", "The selected file is not a valid export.", state.error);
            showUserDataChoice(state, R.string.user_data_import,
                    R.string.user_data_import_invalid, R.string.user_data_choose_file,
                    this::launchImportDocumentPicker);
        } else if (state.status == SettingsAboutViewModel.Status.IMPORT_READ_FAILED
                && state.error instanceof IOException) {
            Log.w("ImportUserData", "Unable to read the selected import file.", state.error);
            showUserDataChoice(state, R.string.user_data_import,
                    R.string.user_data_import_failed, R.string.user_data_choose_file,
                    this::launchImportDocumentPicker);
        } else if (state.status == SettingsAboutViewModel.Status.IMPORT_APPLY_FAILED) {
            if (!(state.error instanceof UserStateUnavailableException)) {
                throw ExceptionHelper.unexpected(state.error);
            }
            showUserDataChoice(state, R.string.user_data_import,
                    R.string.user_data_import_apply_failed, R.string.action_try_again,
                    () -> userDataViewModel.retryImport(state.imported));
        } else {
            throw ExceptionHelper.unexpected(state.error);
        }
    }

    private void showUserDataChoice(final SettingsAboutViewModel.State state,
                                    final int title, final int message,
                                    final int positiveLabel, final Runnable positiveAction) {
        if (userDataMessageDialog != null) {
            return;
        }
        userDataMessageDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(positiveLabel, (dialogInterface, selection) -> {
                    userDataViewModel.acknowledge(state);
                    positiveAction.run();
                })
                .setNegativeButton(R.string.link_cancel,
                        (dialogInterface, selection) -> userDataViewModel.acknowledge(state))
                .create();
        userDataMessageDialog.setOnDismissListener(dialog -> userDataMessageDialog = null);
        userDataMessageDialog.show();
    }

    private void dismissUserDataMessage() {
        if (userDataMessageDialog != null) {
            userDataMessageDialog.dismiss();
            userDataMessageDialog = null;
        }
    }
}
