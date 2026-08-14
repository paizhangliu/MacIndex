package com.macindex.macindex;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;

public class SettingsAboutActivity extends AppCompatActivity {

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
        WindowInsetsHelper.apply(this);
        this.setTitle(getResources().getString(R.string.menu_about_settings));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }
        initSettings();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initSettings() {
        final SwitchMaterial swSortComment = findViewById(R.id.switchSortComment);
        final SwitchMaterial swEveryMac = findViewById(R.id.switchEveryMac);
        final SwitchMaterial swDeathSound = findViewById(R.id.switchDeathSound);
        final SwitchMaterial swNavButtons = findViewById(R.id.switchNavButtons);
        final SwitchMaterial swQuickNav = findViewById(R.id.switchQuickNav);
        final SwitchMaterial swRandomAll = findViewById(R.id.switchRandomAll);
        final SwitchMaterial swSaveMainUsage = findViewById(R.id.switchSaveMainUsage);
        final SwitchMaterial swSaveSearchUsage = findViewById(R.id.switchSaveSearchUsage);
        final SwitchMaterial swSaveCompareUsage = findViewById(R.id.switchSaveCompareUsage);
        final SwitchMaterial swAutoCheckUpdate = findViewById(R.id.switchAutoCheckUpdate);
        final SwitchMaterial swVolWarning = findViewById(R.id.switchVolWarning);
        final SwitchMaterial swOpenDirectly = findViewById(R.id.switchOpenDirectly);
        final Spinner appearanceSpinner = findViewById(R.id.appearanceSpinner);

        findViewById(R.id.userDataButton).setOnClickListener(view -> showUserDataOptions());
        final String[] appearanceOptions = getAppearanceOptions();
        final ArrayAdapter<String> appearanceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, appearanceOptions);
        appearanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appearanceSpinner.setAdapter(appearanceAdapter);
        appearanceSpinner.setSelection(ThemeHelper.getAppearance(this));
        appearanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int selection, final long id) {
                if (selection != ThemeHelper.getAppearance(SettingsAboutActivity.this)) {
                    PrefsHelper.editPrefs("appearanceMode", selection,
                            SettingsAboutActivity.this);
                    ThemeHelper.apply(selection);
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
            }
        });
        findViewById(R.id.appearanceButton).setOnClickListener(
                view -> appearanceSpinner.performClick());

        swSortComment.setChecked(PrefsHelper.getBooleanPrefs("isSortComment", this));
        final Boolean everyMacSelection = PrefsHelper.getBooleanPrefs("isOpenEveryMac", this);
        swEveryMac.setChecked(everyMacSelection);
        swDeathSound.setChecked(PrefsHelper.getBooleanPrefs("isPlayDeathSound", this));
        swNavButtons.setChecked(PrefsHelper.getBooleanPrefs("isUseNavButtons", this));
        swQuickNav.setChecked(PrefsHelper.getBooleanPrefs("isFixedNav", this));
        swRandomAll.setChecked(PrefsHelper.getBooleanPrefs("isRandomAll", this));
        swSaveMainUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveMainUsage", this));
        swSaveSearchUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveSearchUsage", this));
        swSaveCompareUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveCompareUsage", this));
        swAutoCheckUpdate.setChecked(PrefsHelper.getBooleanPrefs("isAutoCheckUpdate", this));
        swVolWarning.setChecked(PrefsHelper.getBooleanPrefs("isEnableVolWarning", this));
        swOpenDirectly.setChecked(PrefsHelper.getBooleanPrefs("isOpenDirectly", this));

        swSortComment.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSortComment", isChecked, this));
        swDeathSound.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isPlayDeathSound", isChecked, this));
        swNavButtons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefsHelper.editPrefs("isUseNavButtons", isChecked, this);
            swQuickNav.setEnabled(isChecked && !swEveryMac.isChecked());
        });
        swQuickNav.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isFixedNav", isChecked, this));
        swRandomAll.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isRandomAll", isChecked, this));
        swSaveMainUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveMainUsage", isChecked, this));
        swSaveSearchUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveSearchUsage", isChecked, this));
        swSaveCompareUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveCompareUsage", isChecked, this));
        swAutoCheckUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isAutoCheckUpdate", isChecked, this));
        swVolWarning.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isEnableVolWarning", isChecked, this));
        swOpenDirectly.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isOpenDirectly", isChecked, this));

        // If EveryMac is checked, disable following settings.
        if (everyMacSelection) {
            swSortComment.setEnabled(false);
            swDeathSound.setEnabled(false);
            swNavButtons.setEnabled(false);
            swQuickNav.setEnabled(false);
            swRandomAll.setEnabled(false);
            swVolWarning.setEnabled(false);
            swSaveCompareUsage.setEnabled(false);
        } else {
            swSortComment.setEnabled(true);
            swDeathSound.setEnabled(true);
            swNavButtons.setEnabled(true);
            swQuickNav.setEnabled(swNavButtons.isChecked());
            swRandomAll.setEnabled(true);
            swVolWarning.setEnabled(true);
            swSaveCompareUsage.setEnabled(true);
        }

        swEveryMac.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                final AlertDialog.Builder everyMacWarningDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
                everyMacWarningDialog.setTitle(R.string.setting_everymac);
                everyMacWarningDialog.setMessage(R.string.setting_everymac_warning_content);
                everyMacWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                    PrefsHelper.editPrefs("isOpenEveryMac", true, this);
                    initSettings();
                });
                everyMacWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> swEveryMac.setChecked(false));
                everyMacWarningDialog.show();
            } else {
                PrefsHelper.editPrefs("isOpenEveryMac", false, this);
                initSettings();
            }
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
        final String[] options = new String[]{
                getString(R.string.user_data_export),
                getString(R.string.user_data_import)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_user_data)
                .setItems(options, (dialogInterface, selection) -> {
                    if (selection == 0) {
                        exportUserDataLauncher.launch(UserDataTransferHelper.DEFAULT_FILE_NAME);
                    } else {
                        importUserDataLauncher.launch(new String[]{
                                "application/json", "text/json", "text/plain"
                        });
                    }
                })
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void exportUserData(final Uri uri) {
        final String userData;
        try {
            userData = UserDataTransferHelper.create(this);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "exportUserData", "Unable to prepare user data for export.");
            return;
        }
        try {
            UserDataTransferHelper.write(this, uri, userData);
            Toast.makeText(this, R.string.user_data_export_success,
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.user_data_export_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void importUserData(final Uri uri) {
        try {
            final UserDataTransferHelper.ImportResult imported =
                    UserDataTransferHelper.prepareImport(
                            UserDataTransferHelper.read(this, uri),
                            MainActivity.getMachineHelper());
            showImportConfirmation(imported);
        } catch (UserDataTransferHelper.InvalidTransferException e) {
            Toast.makeText(this, R.string.user_data_import_invalid,
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.user_data_import_failed,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "importUserData", "Unable to prepare imported user data.");
        }
    }

    private void showImportConfirmation(final UserDataTransferHelper.ImportResult imported) {
        String information = getString(R.string.user_data_import_information,
                imported.commentCount, imported.favouriteCount,
                imported.folderCount, imported.compareCount);
        if (imported.getRemovedCount() != 0) {
            information += "\n\n" + getString(R.string.user_data_import_removed,
                    imported.getRemovedCount());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.user_data_import)
                .setMessage(information)
                .setPositiveButton(R.string.link_confirm, (dialogInterface, selection) -> {
                    try {
                        UserDataTransferHelper.applyImport(imported, this);
                        if (imported.getRemovedCount() != 0) {
                            PrefsHelper.showUpgradeReport(this);
                        } else {
                            Toast.makeText(this, R.string.user_data_import_success,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        ExceptionHelper.handleException(this, e,
                                "importUserData", "Unable to import user data.");
                    }
                })
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }
}
