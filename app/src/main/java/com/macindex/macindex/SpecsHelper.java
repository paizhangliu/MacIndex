package com.macindex.macindex;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MacIndex Specs Helper
 * Extracted from SpecsActivity on July 22, 2026.
 */
class SpecsHelper {

    private final Context thisContext;

    private boolean startup = true;

    private MediaPlayer startupSound = null;

    private MediaPlayer deathSound = null;

    private final Vibrator vibrator;

    SpecsHelper(final Context thisContext) {
        this.thisContext = thisContext;
        vibrator = (Vibrator) thisContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    void initSound(final int machineID, final ImageView image, final TextView informationLabel) {
        try {
            release();
            startup = true;

            // Init startup and death sound
            final int[] sound = MainActivity.getMachineHelper().getSound(machineID, thisContext);
            final int startupID = sound[0];
            final int deathID = sound[1];

            if (startupID != 0 || deathID != 0) {
                // Set Sound accordingly
                if (startupID != 0 && deathID != 0
                        && PrefsHelper.getBooleanPrefs("isPlayDeathSound", thisContext)) {
                    // Startup sound exists, death sound exists, and user prefers both
                    if (informationLabel != null) {
                        informationLabel.setText(thisContext.getResources().getString(R.string.information_specs_full));
                    }
                    startupSound = MediaPlayer.create(thisContext, startupID);
                    deathSound = MediaPlayer.create(thisContext, deathID);
                    DebugHelper.log("InitSound", "Startup and death sound loaded");
                } else {
                    // Startup sound exists, death sound not exist
                    // Fix IllegalStateException
                    if (informationLabel != null) {
                        informationLabel.setText(thisContext.getResources().getString(R.string.information_specs_no_death));
                    }
                    startupSound = MediaPlayer.create(thisContext, startupID);
                    deathSound = null;
                    DebugHelper.log("InitSound", "Startup sound loaded");
                }
                // Should set a listener
                image.setOnClickListener(unused -> {
                    // Initialize Sound.
                    try {
                        vibrate();
                        if (!startupSound.isPlaying() && (deathSound == null || !deathSound.isPlaying())) {
                            // Not playing any sound
                            if (PrefsHelper.getBooleanPrefs("isEnableVolWarningThisTime", thisContext)
                                    && PrefsHelper.getBooleanPrefs("isEnableVolWarning", thisContext)) {
                                // High Volume Warning Enabled
                                boolean currentOutputDevice = false;
                                AudioManager audioManager = (AudioManager) thisContext.getSystemService(Context.AUDIO_SERVICE);
                                if (audioManager != null) {
                                    for (AudioDeviceInfo deviceInfo : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                                        final int thisType = deviceInfo.getType();
                                        DebugHelper.log("VolWarning", "Get type " + thisType);
                                        if (thisType == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                                || thisType == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                                || thisType == AudioDeviceInfo.TYPE_USB_HEADSET
                                                || thisType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                                || thisType == AudioDeviceInfo.TYPE_HEARING_AID) {
                                            DebugHelper.log("VolWarning", "Earphone detected");
                                            currentOutputDevice = true;
                                            break;
                                        }
                                    }
                                    int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                    int currentVolumePercentage = 100 * currentVolume / maxVolume;
                                    DebugHelper.log("VolWarning", "Enabled, current percentage " + currentVolumePercentage
                                            + " current output device " + currentOutputDevice);
                                    if (currentVolumePercentage >= 60 && currentOutputDevice) {
                                        DebugHelper.log("VolWarning", "Armed");
                                        final AlertDialog.Builder volWarningDialog = new AlertDialog.Builder(thisContext);
                                        volWarningDialog.setMessage(R.string.information_specs_high_vol_warning);
                                        volWarningDialog.setPositiveButton(R.string.action_play_anyway, (dialogInterface, i) -> {
                                            // Enabled, and popup a warning
                                            PrefsHelper.editPrefs("isEnableVolWarningThisTime", false, thisContext);
                                            playSound();
                                        });
                                        volWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                                            // Do nothing
                                        });
                                        volWarningDialog.show();
                                    } else {
                                        // Enabled, but should not popup a warning
                                        DebugHelper.log("VolWarning", "Unarmed");
                                        playSound();
                                    }
                                } else {
                                    // Enabled, but audio service not available
                                    ExceptionHelper.handleException(thisContext, null,
                                            "VolWarning",
                                            "Audio Service Not Available.");
                                    playSound();
                                }
                            } else {
                                // High Volume Warning Disabled
                                DebugHelper.log("VolWarning", "Disabled");
                                playSound();
                            }
                        }
                    } catch (Exception e) {
                        ExceptionHelper.handleException(thisContext, e,
                                "initImage", "Unable to initialize sounds.");
                    }
                });
            } else {
                // Exception for PowerBook DuoDock...
                // Fix IllegalStateException
                startupSound = null;
                deathSound = null;
                DebugHelper.log("InitSound", "Startup and death sound do not exist");
                image.setOnClickListener(v -> {
                    vibrate();
                    Toast.makeText(thisContext, R.string.information_specs_no_sound,
                            Toast.LENGTH_SHORT).show();
                });
                if (informationLabel != null) {
                    informationLabel.setText(R.string.information_specs_no_sound);
                }
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "initSound", "Failed, Machine ID " + machineID);
        }
    }

    void initLinks(final int machineID, final String thisName, final ImageView link) {
        link.setOnClickListener(v -> loadLinks(machineID, thisName));
    }

    void initLinks(final int[] machineIDs, final String[] machineNames, final ImageView link) {
        final String[] machineLinks = new String[machineIDs.length];
        for (int i = 0; i < machineIDs.length; i++) {
            machineLinks[i] = MainActivity.getMachineHelper().getConfig(machineIDs[i]);
        }
        link.setOnClickListener(v -> LinkLoadingHelper.loadLinks(machineNames,
                machineLinks, thisContext));
    }

    void initCopy(final TextView entry, final String thisInfo, final String clipLabel) {
        entry.setOnLongClickListener(view -> {
            if (!isAvailable(thisInfo)) {
                Toast.makeText(thisContext,
                        thisContext.getString(R.string.error_copy_not_available), Toast.LENGTH_LONG).show();
            } else {
                copy(clipLabel, thisInfo, R.string.copy_information_success);
            }
            return true;
        });
    }

    void initCompareCopy(final View entry, final String leftName, final String leftInfo,
                         final String rightName, final String rightInfo) {
        entry.setOnLongClickListener(view -> {
            if (!isAvailable(leftInfo) && !isAvailable(rightInfo)) {
                Toast.makeText(thisContext,
                        thisContext.getString(R.string.error_copy_not_available), Toast.LENGTH_LONG).show();
            } else {
                copy("compareInfo", generateCompareInfo(leftName, leftInfo, rightName, rightInfo),
                        R.string.copy_information_success);
            }
            return true;
        });
    }

    void setSupportColor(final TextView support, final String thisSupport) {
        // Set Support Box Text Color.
        if (thisSupport.equals("Obsolete")) {
            support.setTextColor(Color.RED);
        } else if (thisSupport.equals("Vintage")) {
            support.setTextColor(Color.MAGENTA);
        } else if (thisSupport.equals("Supported")) {
            support.setTextColor(Color.GREEN);
        }
    }

    void copySpecification(final String[] machineNames, final String[][] machineSpecs) {
        try {
            if (machineNames == null || machineSpecs == null
                    || machineNames.length == 0 || machineNames.length > 2
                    || machineNames.length != machineSpecs.length) {
                throw new IllegalArgumentException();
            }
            final int entryCount = machineSpecs[0].length;
            for (String[] specs : machineSpecs) {
                if (specs == null || specs.length != entryCount) {
                    throw new IllegalArgumentException();
                }
            }

            // 2021.11.13 at Jinzhong, Shanxi, China
            final AlertDialog.Builder shareDialog = new AlertDialog.Builder(thisContext);
            shareDialog.setTitle(thisContext.getString(R.string.submenu_specs_share));
            final String[] shareEntries = thisContext.getResources().getStringArray(R.array.share_menu);
            final String[] shareDescription = thisContext.getResources().getStringArray(R.array.share_description);
            shareDialog.setItems(shareEntries, (dialog, which) -> {
                try {
                    if (which == 0 || which == 1) {
                        // Model no. or all info
                        List<Integer> currentEntries = new ArrayList<>(5);
                        for (int i = 1; i < (which == 0 ? Math.min(6, entryCount) : entryCount); i++) {
                            currentEntries.add(i);
                        }
                        final String modelInfo = generateShareInfo(machineNames, machineSpecs, currentEntries);
                        copy("MacIndexModelInfo", modelInfo, 0);
                        Toast.makeText(thisContext, shareDescription[which], Toast.LENGTH_LONG).show();
                    } else if (which == 2) {
                        // User choose info
                        final View selectChunk = ((LayoutInflater) thisContext
                                .getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                                .inflate(R.layout.chunk_favourites_select, null);
                        final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
                        final String[] selectableSpecs = getSpecificationLabels(entryCount);
                        final boolean[] currentSelections = new boolean[selectableSpecs.length];
                        for (int i = 0; i < selectableSpecs.length; i++) {
                            CheckBox thisCheckBox = new CheckBox(thisContext);
                            thisCheckBox.setText(selectableSpecs[i]);
                            thisCheckBox.setChecked(false);
                            int finalI = i;
                            thisCheckBox.setOnCheckedChangeListener((compoundButton, b) ->
                                    currentSelections[finalI] = thisCheckBox.isChecked());
                            if (!isAvailable(machineSpecs, i)) {
                                // This is not supposed...
                                thisCheckBox.setEnabled(false);
                                thisCheckBox.setText(thisContext.getString(
                                        R.string.share_menu_unavailable, selectableSpecs[i]));
                            }
                            selectLayout.addView(thisCheckBox);
                        }

                        final AlertDialog.Builder selectDialog = new AlertDialog.Builder(thisContext);
                        selectDialog.setTitle(shareEntries[which]);
                        selectDialog.setView(selectChunk);
                        selectDialog.setPositiveButton(R.string.link_confirm, (dialog2, which2) -> {
                            // To be overwritten...
                        });
                        selectDialog.setNegativeButton(R.string.link_cancel, ((dialog2, which2) -> {
                            // Cancelled, do nothing
                        }));
                        final AlertDialog selectDialogCreated = selectDialog.create();
                        selectDialogCreated.show();

                        selectDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                            // Overwrite the positive button
                            try {
                                List<Integer> currentEntries = new ArrayList<>(0);
                                for (int i = 0; i < currentSelections.length; i++) {
                                    if (currentSelections[i]) {
                                        currentEntries.add(i);
                                    }
                                }
                                if (currentEntries.size() > 0) {
                                    final String modelInfo = generateShareInfo(machineNames, machineSpecs, currentEntries);
                                    copy("MacIndexModelInfo", modelInfo, 0);
                                    Toast.makeText(thisContext, shareDescription[which], Toast.LENGTH_LONG).show();
                                    selectDialogCreated.dismiss();
                                } else {
                                    Toast.makeText(thisContext, R.string.share_menu_null, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                ExceptionHelper.handleException(thisContext, e,
                                        "selectDialog", "Error when copying currentSelections.");
                            }
                        });
                    }
                } catch (Exception e) {
                    ExceptionHelper.handleException(thisContext, e,
                            "shareDialog", "Unable to create the share dialog.");
                }
            });
            shareDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "shareDialog", "Unable to create the share dialog.");
        }
    }

    void generateShareLink(final String machineName) {
        copy("MacIndexShareLink", ShareLinkHelper.create(machineName), R.string.share_link_generated);
    }

    void generateShareLink(final String leftName, final String rightName) {
        copy("MacIndexCompareShareLink", ShareLinkHelper.createComparison(leftName, rightName),
                R.string.share_link_generated);
    }

    void release() {
        try {
            if (startupSound != null && startupSound.isPlaying()) {
                startupSound.stop();
                DebugHelper.log("releaseSound", "Startup sound stopped");
            }
            if (deathSound != null && deathSound.isPlaying()) {
                deathSound.stop();
                DebugHelper.log("releaseSound", "Death sound stopped");
            }
            if (startupSound != null) {
                startupSound.release();
                DebugHelper.log("releaseSound", "Startup sound released");
            }
            if (deathSound != null) {
                deathSound.release();
                DebugHelper.log("releaseSound", "Death sound released");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.w("SpecsHelper", "Unable to release all sounds.");
        } finally {
            startupSound = null;
            deathSound = null;
        }
    }

    private void playSound() {
        try {
            if (startupSound == null) {
                throw new IllegalStateException();
            }
            if (deathSound != null) {
                if (startup) {
                    startupSound.start();
                    startup = false;
                } else {
                    deathSound.start();
                    startup = true;
                }
            } else {
                startupSound.start();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "playSound", "Unable to play sound.");
        }
    }

    private String generateShareInfo(final String[] machineNames, final String[][] machineSpecs,
                                     final List<Integer> entries) {
        String modelInfo = "";
        try {
            for (int i = 0; i < machineNames.length; i++) {
                if (!modelInfo.isEmpty()) {
                    modelInfo = modelInfo.concat("\n\n");
                }
                modelInfo = modelInfo.concat(generateShareInfo(machineNames[i], machineSpecs[i],
                        entries, machineNames.length > 1));
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "generateShareInfo", "Illegal Argument. Received arguments:" + entries.toString());
        }
        return modelInfo.trim();
    }

    private String generateShareInfo(final String machineName, final String[] machineSpecs,
                                     final List<Integer> entries, final boolean isCompare) {
        String modelInfo = machineName + (isCompare ? ":\n" : "\n");
        final String[] labels = getSpecificationLabels(machineSpecs.length);
        for (int entry : entries) {
            if (entry < 0 || entry >= labels.length) {
                throw new IllegalArgumentException();
            }
            if (isAvailable(machineSpecs[entry])) {
                modelInfo = modelInfo.concat(labels[entry]
                        + (Locale.getDefault().getDisplayLanguage().equals("中文") ? "：" : ": ")
                        + machineSpecs[entry] + "\n");
            }
        }
        return modelInfo.trim();
    }

    private String[] getSpecificationLabels(final int entryCount) {
        final int[] labelIDs = {R.string.year, R.string.model, R.string.id, R.string.gestalt,
                R.string.order, R.string.emc, R.string.processor, R.string.graphics,
                R.string.type, R.string.maxram, R.string.software, R.string.storage,
                R.string.bus_expansion, R.string.design, R.string.support, R.string.comment};
        if (entryCount <= 0 || entryCount > labelIDs.length) {
            throw new IllegalArgumentException();
        }
        final String[] labels = new String[entryCount];
        for (int i = 0; i < entryCount; i++) {
            labels[i] = thisContext.getString(labelIDs[i]);
        }
        return labels;
    }

    private String generateCompareInfo(final String leftName, final String leftInfo,
                                       final String rightName, final String rightInfo) {
        return leftName + ": " + leftInfo + "; " + rightName + ": " + rightInfo;
    }

    private boolean isAvailable(final String thisInfo) {
        return thisInfo != null
                && !thisInfo.equals(thisContext.getString(R.string.not_applicable))
                && !thisInfo.equals(thisContext.getString(R.string.comment_null));
    }

    private boolean isAvailable(final String[][] machineSpecs, final int entry) {
        for (String[] specs : machineSpecs) {
            if (isAvailable(specs[entry])) {
                return true;
            }
        }
        return false;
    }

    private void loadLinks(final int machineID, final String thisName) {
        LinkLoadingHelper.loadLinks(thisName,
                MainActivity.getMachineHelper().getConfig(machineID), thisContext);
    }

    private void copy(final String clipLabel, final String thisInfo, final int toastMessage) {
        final ClipboardManager clipboard = (ClipboardManager)
                thisContext.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(clipLabel, thisInfo);
        clipboard.setPrimaryClip(clip);
        if (toastMessage != 0) {
            Toast.makeText(thisContext, toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void vibrate() {
        if (vibrator == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(50);
        }
    }
}
