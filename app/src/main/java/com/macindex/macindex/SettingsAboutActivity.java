package com.macindex.macindex;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.os.Bundle;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsAboutActivity extends AppCompatActivity {

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
        final SwitchMaterial swVolWarning = findViewById(R.id.switchVolWarning);
        final SwitchMaterial swOpenDirectly = findViewById(R.id.switchOpenDirectly);

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
        swVolWarning.setChecked(PrefsHelper.getBooleanPrefs("isEnableVolWarning", this));
        swOpenDirectly.setChecked(PrefsHelper.getBooleanPrefs("isOpenDirectly", this));

        swSortComment.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSortComment", isChecked, this));
        swDeathSound.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isPlayDeathSound", isChecked, this));
        swNavButtons.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isUseNavButtons", isChecked, this));
        swQuickNav.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isFixedNav", isChecked, this));
        swRandomAll.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isRandomAll", isChecked, this));
        swSaveMainUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveMainUsage", isChecked, this));
        swSaveSearchUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveSearchUsage", isChecked, this));
        swSaveCompareUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveCompareUsage", isChecked, this));
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
            swQuickNav.setEnabled(true);
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
}
