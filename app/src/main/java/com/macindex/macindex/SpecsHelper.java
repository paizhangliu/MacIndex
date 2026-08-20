package com.macindex.macindex;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.SupportStatus;
import com.macindex.macindex.catalog.TextRange;
import com.macindex.macindex.resources.MachineResourceLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * MacIndex Specs Helper
 * Extracted from SpecsActivity on July 22, 2026.
 */
class SpecsHelper {
    private static final int[] SPECIFICATION_LABEL_IDS = {
            R.string.year, R.string.model, R.string.id, R.string.gestalt,
            R.string.order, R.string.codename, R.string.emc, R.string.processor,
            R.string.graphics, R.string.display, R.string.maxram, R.string.type,
            R.string.software, R.string.storage, R.string.features, R.string.expansion,
            R.string.design, R.string.support, R.string.comment
    };

    private final Context thisContext;

    private boolean startup = true;

    private MediaPlayer startupSound = null;

    private MediaPlayer deathSound = null;

    SpecsHelper(final Context thisContext) {
        this.thisContext = thisContext;
    }

    void initSound(final Machine machine, final ImageView image,
                   final boolean playDeathSound,
                   final boolean enableVolumeWarning,
                   final VolumeWarningSession volumeWarningSession) {
        release();
        startup = true;

        // Init startup and death sound
        final String startupFile = MachineResourceLoader.startupSoundAsset(machine);
        final String deathFile = MachineResourceLoader.deathSoundAsset(machine);

        if (startupFile != null || deathFile != null) {
            // Set Sound accordingly
            if (startupFile != null && deathFile != null
                    && playDeathSound) {
                // Startup sound exists, death sound exists, and user prefers both
                startupSound = createSoundPlayer(startupFile);
                deathSound = createSoundPlayer(deathFile);
            } else {
                // Startup sound exists, death sound not exist
                // Fix IllegalStateException
                startupSound = createSoundPlayer(startupFile);
                deathSound = null;
            }
            if (startupSound == null
                    || (deathFile != null && playDeathSound && deathSound == null)) {
                Log.w("SpecsHelper", "Android could not create the machine sound player.");
                release();
                configureUnavailableSound(image);
                return;
            }

            // Should set a listener
            image.setOnClickListener(unused -> {
                performHapticFeedback(image);
                if (soundIsPlayingOrUnavailable()) {
                    return;
                }
                if (enableVolumeWarning && volumeWarningSession.isArmed()) {
                    boolean currentOutputDevice = false;
                    final AudioManager audioManager = (AudioManager) thisContext.getSystemService(
                            Context.AUDIO_SERVICE);
                    if (audioManager != null) {
                        for (AudioDeviceInfo deviceInfo : audioManager.getDevices(
                                AudioManager.GET_DEVICES_OUTPUTS)) {
                            final int thisType = deviceInfo.getType();
                            if (thisType == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                    || thisType == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                    || thisType == AudioDeviceInfo.TYPE_USB_HEADSET
                                    || thisType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                    || thisType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                    || thisType == AudioDeviceInfo.TYPE_HEARING_AID
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                    && thisType == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                                currentOutputDevice = true;
                                break;
                            }
                        }
                        final int currentVolume = audioManager.getStreamVolume(
                                AudioManager.STREAM_MUSIC);
                        final int maxVolume = audioManager.getStreamMaxVolume(
                                AudioManager.STREAM_MUSIC);
                        if (maxVolume <= 0) {
                            Log.w("SpecsHelper", "Invalid music volume range; skipping warning.");
                            playSoundWithFeedback();
                            return;
                        }
                        final int currentVolumePercentage = 100 * currentVolume / maxVolume;
                        if (currentVolumePercentage >= 60 && currentOutputDevice) {
                            new AlertDialog.Builder(thisContext)
                                    .setMessage(R.string.information_specs_high_vol_warning)
                                    .setPositiveButton(R.string.action_play_anyway,
                                            (dialogInterface, i) -> {
                                                volumeWarningSession.disarm();
                                                playSoundWithFeedback();
                                            })
                                    .setNegativeButton(R.string.link_cancel, null)
                                    .show();
                        } else {
                            playSoundWithFeedback();
                        }
                    } else {
                        Log.w("SpecsHelper",
                                "AudioManager is unavailable; skipping volume warning.");
                        playSoundWithFeedback();
                    }
                } else {
                    playSoundWithFeedback();
                }
            });
        } else {
            configureUnavailableSound(image);
        }
    }

    private MediaPlayer createSoundPlayer(final String assetPath) {
        if (assetPath == null) {
            return null;
        }
        final MediaPlayer player = new MediaPlayer();
        try (AssetFileDescriptor asset = thisContext.getAssets().openFd(assetPath)) {
            player.setDataSource(asset.getFileDescriptor(),
                    asset.getStartOffset(), asset.getLength());
            player.prepare();
            return player;
        } catch (IOException | RuntimeException error) {
            Log.w("SpecsHelper", "Unable to prepare Catalog sound " + assetPath, error);
            releaseSound(player, assetPath);
            return null;
        }
    }

    void initLinks(final Machine machine, final ImageView link) {
        link.setOnClickListener(v -> LinkLoadingHelper.loadLinks(machine, thisContext));
    }

    void initLinks(final Machine leftMachine, final Machine rightMachine,
                   final ImageView link) {
        link.setOnClickListener(v -> LinkLoadingHelper.loadLinks(
                leftMachine, rightMachine, thisContext));
    }

    void initCopy(final TextView entry, final String thisInfo, final String clipLabel) {
        entry.setOnLongClickListener(view -> {
            if (!isAvailable(thisInfo)) {
                Toast.makeText(thisContext,
                        thisContext.getString(R.string.copy_not_available), Toast.LENGTH_LONG).show();
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
                        thisContext.getString(R.string.copy_not_available), Toast.LENGTH_LONG).show();
            } else {
                copy("compareInfo", generateCompareInfo(leftName, leftInfo, rightName, rightInfo),
                        R.string.copy_information_success);
            }
            return true;
        });
    }

    void initPartNumbers(final TextView entry, final String thisInfo) {
        entry.setOnClickListener(null);
        entry.setText(getDisplayInfo(thisInfo));
        if (thisInfo == null) {
            return;
        }

        final int previewSize = 3;
        final String[] partNumbers = thisInfo.split("\n");
        if (partNumbers.length <= previewSize) {
            return;
        }

        final StringBuilder collapsedInfo = new StringBuilder();
        for (int i = 0; i < previewSize; i++) {
            if (i != 0) {
                collapsedInfo.append("\n");
            }
            collapsedInfo.append(partNumbers[i]);
        }
        final int remainingSize = partNumbers.length - previewSize;
        collapsedInfo.append("\n").append(thisContext.getResources().getQuantityString(
                R.plurals.part_numbers_more, remainingSize, remainingSize));
        final String thisCollapsedInfo = collapsedInfo.toString();
        entry.setText(thisCollapsedInfo);
        entry.setOnClickListener(new View.OnClickListener() {
            private boolean isExpanded = false;

            @Override
            public void onClick(final View view) {
                isExpanded = !isExpanded;
                entry.setText(isExpanded ? thisInfo : thisCollapsedInfo);
            }
        });
    }

    CharSequence formatModels(final String thisInfo, final List<TextRange> modelRanges) {
        if (thisInfo == null) {
            return getDisplayInfo(null);
        }
        if (modelRanges.isEmpty()) {
            return thisInfo;
        }
        final SpannableString formattedInfo = new SpannableString(thisInfo);
        for (TextRange modelRange : modelRanges) {
            formattedInfo.setSpan(new StyleSpan(Typeface.BOLD),
                    modelRange.startInclusive(), modelRange.endExclusive(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return formattedInfo;
    }

    String getSupportText(final SupportStatus supportStatus) {
        switch (supportStatus) {
            case SUPPORTED:
                return "Supported";
            case VINTAGE:
                return "Vintage";
            case OBSOLETE:
                return "Obsolete";
            case NOT_APPLICABLE:
                return thisContext.getString(R.string.not_applicable);
            default:
                throw new IllegalStateException("Unknown support status " + supportStatus);
        }
    }

    void setSupportColor(final TextView support, final SupportStatus supportStatus) {
        // Set Support Box Text Color.
        switch (supportStatus) {
            case OBSOLETE:
                support.setTextColor(ContextCompat.getColor(thisContext,
                        R.color.colorSupportObsolete));
                break;
            case VINTAGE:
                support.setTextColor(ContextCompat.getColor(thisContext,
                        R.color.colorSupportVintage));
                break;
            case SUPPORTED:
                support.setTextColor(ContextCompat.getColor(thisContext,
                        R.color.colorSupportSupported));
                break;
            case NOT_APPLICABLE:
                break;
            default:
                throw new IllegalStateException("Unknown support status " + supportStatus);
        }
    }

    void copySpecification(final String[] machineNames, final String[][] machineSpecs) {
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
            if (which == 0 || which == 1) {
                        // Model no. or all info
                        final List<Integer> currentEntries;
                        if (which == 0) {
                            currentEntries = modelIdentifierEntries(entryCount);
                        } else {
                            currentEntries = allSpecificationEntries(entryCount);
                        }
                        final String modelInfo = generateShareInfo(machineNames, machineSpecs, currentEntries);
                        if (copy("MacIndexModelInfo", modelInfo, 0)) {
                            Toast.makeText(thisContext, shareDescription[which],
                                    Toast.LENGTH_LONG).show();
                        }
            } else if (which == 2) {
                        // User choose info
                        final View selectChunk = LayoutInflater.from(thisContext)
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
                        selectDialog.setPositiveButton(R.string.link_confirm, null);
                        selectDialog.setNegativeButton(R.string.link_cancel, null);
                        final AlertDialog selectDialogCreated = selectDialog.create();
                        selectDialogCreated.show();

                selectDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    // Overwrite the positive button
                    List<Integer> currentEntries = new ArrayList<>(0);
                    for (int i = 0; i < currentSelections.length; i++) {
                        if (currentSelections[i]) {
                            currentEntries.add(i);
                        }
                    }
                    if (currentEntries.size() > 0) {
                        final String modelInfo = generateShareInfo(machineNames, machineSpecs, currentEntries);
                        if (copy("MacIndexModelInfo", modelInfo, 0)) {
                            Toast.makeText(thisContext, shareDescription[which],
                                    Toast.LENGTH_LONG).show();
                            selectDialogCreated.dismiss();
                        }
                    } else {
                        Toast.makeText(thisContext, R.string.share_menu_null, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        shareDialog.show();
    }

    private void configureUnavailableSound(final ImageView image) {
        startupSound = null;
        deathSound = null;
        image.setOnClickListener(v -> {
            performHapticFeedback(image);
            Toast.makeText(thisContext, R.string.information_specs_no_sound,
                    Toast.LENGTH_SHORT).show();
        });
    }

    void generateShareLink(final String machineUID) {
        copy("MacIndexShareLink", ShareLinkHelper.create(machineUID), R.string.share_link_generated);
    }

    void generateShareLink(final String leftUID, final String rightUID) {
        copy("MacIndexCompareShareLink", ShareLinkHelper.createComparison(leftUID, rightUID),
                R.string.share_link_generated);
    }

    void release() {
        final MediaPlayer oldStartupSound = startupSound;
        final MediaPlayer oldDeathSound = deathSound;
        startupSound = null;
        deathSound = null;
        releaseSound(oldStartupSound, "startup");
        releaseSound(oldDeathSound, "death");
    }

    private static void releaseSound(final MediaPlayer player, final String label) {
        if (player == null) {
            return;
        }
        try {
            // release() relinquishes the native resources from every MediaPlayer state; an
            // isPlaying()/stop() preflight only adds IllegalStateException failure points.
            player.release();
        } catch (RuntimeException error) {
            Log.w("SpecsHelper", "Unable to release " + label + " sound.", error);
            return;
        }
    }

    private void playSound() {
        if (startupSound == null) {
            throw new IllegalStateException("Startup sound is unavailable");
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
    }

    private void playSoundWithFeedback() {
        try {
            playSound();
        } catch (IllegalStateException failure) {
            reportSoundFailure(failure);
        }
    }

    private boolean soundIsPlayingOrUnavailable() {
        if (startupSound == null) {
            return true;
        }
        try {
            return startupSound.isPlaying()
                    || deathSound != null && deathSound.isPlaying();
        } catch (IllegalStateException failure) {
            reportSoundFailure(failure);
            return true;
        }
    }

    private void reportSoundFailure(final IllegalStateException failure) {
        Log.w("SpecsHelper", "Unable to play machine sound.", failure);
        Toast.makeText(thisContext, R.string.sound_play_failed, Toast.LENGTH_SHORT).show();
    }

    private String generateShareInfo(final String[] machineNames, final String[][] machineSpecs,
                                     final List<Integer> entries) {
        String modelInfo = "";
        for (int i = 0; i < machineNames.length; i++) {
            if (!modelInfo.isEmpty()) {
                modelInfo = modelInfo.concat("\n\n");
            }
            modelInfo = modelInfo.concat(generateShareInfo(machineNames[i], machineSpecs[i],
                    entries, machineNames.length > 1));
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
        final int[] labelIDs = specificationLabelIds(entryCount);
        final String[] labels = new String[entryCount];
        for (int i = 0; i < entryCount; i++) {
            labels[i] = thisContext.getString(labelIDs[i]);
        }
        return labels;
    }

    static int[] specificationLabelIds(final int entryCount) {
        if (entryCount <= 0 || entryCount > SPECIFICATION_LABEL_IDS.length) {
            throw new IllegalArgumentException();
        }
        return Arrays.copyOf(SPECIFICATION_LABEL_IDS, entryCount);
    }

    static List<Integer> modelIdentifierEntries(final int entryCount) {
        final int[] identifierEntries = {1, 2, 3, 4, 6};
        final List<Integer> result = new ArrayList<>(identifierEntries.length);
        for (int entry : identifierEntries) {
            if (entry < entryCount) {
                result.add(entry);
            }
        }
        return result;
    }

    static List<Integer> allSpecificationEntries(final int entryCount) {
        final List<Integer> result = new ArrayList<>(entryCount);
        for (int entry = 0; entry < entryCount; entry++) {
            result.add(entry);
        }
        return result;
    }

    /** Pure projection shared by the single-machine and comparison presentations. */
    String[] specification(final Machine machine) {
        return new String[]{
                machine.introductionDisplayText(),
                machine.modelNumbers(),
                machine.identifiers(),
                machine.gestaltIds(),
                machine.orderNumbers(),
                machine.codenameDisplayText(),
                machine.emcNumbers(),
                machine.processor(),
                machine.graphics(),
                machine.display(),
                machine.ram(),
                machine.rom(),
                machine.software(),
                machine.storage(),
                machine.features(),
                machine.expansion(),
                machine.design(),
                getSupportText(machine.supportStatus())
        };
    }

    String[] specification(final Machine machine, final String comment) {
        final String[] base = specification(machine);
        final String[] withComment = Arrays.copyOf(base, base.length + 1);
        withComment[base.length] = comment;
        return withComment;
    }

    private String generateCompareInfo(final String leftName, final String leftInfo,
                                       final String rightName, final String rightInfo) {
        return leftName + ": " + getDisplayInfo(leftInfo) + "; "
                + rightName + ": " + getDisplayInfo(rightInfo);
    }

    private boolean isAvailable(final String thisInfo) {
        return thisInfo != null;
    }

    String getDisplayInfo(final String thisInfo) {
        return thisInfo == null ? thisContext.getString(R.string.not_applicable) : thisInfo;
    }

    private boolean isAvailable(final String[][] machineSpecs, final int entry) {
        for (String[] specs : machineSpecs) {
            if (isAvailable(specs[entry])) {
                return true;
            }
        }
        return false;
    }

    private boolean copy(final String clipLabel, final String thisInfo, final int toastMessage) {
        return ExceptionHelper.copyText(thisContext, clipLabel, thisInfo, toastMessage);
    }

    private static void performHapticFeedback(final View view) {
        final int feedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY;
        view.performHapticFeedback(feedback);
    }
}
