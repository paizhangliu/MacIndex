package com.macindex.macindex;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MacIndex Preference Helper.
 * July 11, 2020
 */
class PrefsHelper {

    public static final String PREFERENCE_FILENAME = "MacIndex_Preference";

    private static final Map<String, Object> DEFAULT_VALUES;
    static {
        DEFAULT_VALUES = new HashMap<>();

        /* User Preferences */
        DEFAULT_VALUES.put("isSortComment", Boolean.FALSE);
        DEFAULT_VALUES.put("isOpenEveryMac", Boolean.FALSE);
        DEFAULT_VALUES.put("isPlayDeathSound", Boolean.TRUE);
        DEFAULT_VALUES.put("isEnableVolWarning", Boolean.TRUE);
        DEFAULT_VALUES.put("isUseNavButtons", Boolean.TRUE);
        DEFAULT_VALUES.put("isFixedNav", Boolean.FALSE);
        DEFAULT_VALUES.put("isRandomAll", Boolean.FALSE);
        DEFAULT_VALUES.put("isOpenDirectly", Boolean.TRUE);
        DEFAULT_VALUES.put("isSaveMainUsage", Boolean.TRUE);
        DEFAULT_VALUES.put("isSaveSearchUsage", Boolean.TRUE);
        DEFAULT_VALUES.put("isSaveCompareUsage", Boolean.TRUE);
        DEFAULT_VALUES.put("isHighlightCompareDifferences", Boolean.TRUE);
        DEFAULT_VALUES.put("isAutoCheckUpdate", Boolean.TRUE);
        DEFAULT_VALUES.put("appearanceMode", ThemeHelper.APPEARANCE_SYSTEM);

        /* User Record */
        DEFAULT_VALUES.put("userCompare", UserCompareHelper.EMPTY_JSON);
        DEFAULT_VALUES.put("userFavourites", UserFavouriteHelper.EMPTY_JSON);
        DEFAULT_VALUES.put("userComments", UserCommentHelper.EMPTY_JSON);
        DEFAULT_VALUES.put("skippedUpdateVersion", "");
        DEFAULT_VALUES.put("pendingUpgradeReport", "");

        /* Runtime Record */
        DEFAULT_VALUES.put("lastMainManufacturer", "all");
        DEFAULT_VALUES.put("lastMainFilter", "names");
        DEFAULT_VALUES.put("lastSearchFiltersSpinner", 0);
        DEFAULT_VALUES.put("lastSearchOptionsSpinner", 0);
        DEFAULT_VALUES.put("lastKnownVersion", 0);

        /* Runtime Parameters */
        DEFAULT_VALUES.put("isEnableVolWarningThisTime", Boolean.TRUE);
        DEFAULT_VALUES.put("isCommentsReloadNeeded", Boolean.FALSE);
        DEFAULT_VALUES.put("isFavouritesReloadNeeded", Boolean.FALSE);
        DEFAULT_VALUES.put("isCompareReloadNeeded", Boolean.FALSE);
    }

    public static int getIntPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof Integer) {
                int value = prefsFile.getInt(thisPrefsName, (Integer) DEFAULT_VALUES.get(thisPrefsName));
                DebugHelper.log("Preference Helper", "Got Int preference " + thisPrefsName
                        + " with value " + value);
                return value;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to get Int preference: " + thisPrefsName);
            return 0;
        }
    }

    public static int getIntPrefsSafe(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                    PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName)
                    && DEFAULT_VALUES.get(thisPrefsName) instanceof Integer) {
                return prefsFile.getInt(thisPrefsName,
                        (Integer) DEFAULT_VALUES.get(thisPrefsName));
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            Log.e("Preference Helper", "Unable to get Int preference: " + thisPrefsName);
            return 0;
        }
    }

    public static Boolean getBooleanPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof Boolean) {
                Boolean value = prefsFile.getBoolean(thisPrefsName, (Boolean) DEFAULT_VALUES.get(thisPrefsName));
                DebugHelper.log("Preference Helper", "Got Boolean preference: " + thisPrefsName
                        + " with value " + value);
                return value;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to get Boolean preference: " + thisPrefsName);
            return false;
        }
    }

    public static Boolean getBooleanPrefsSafe(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof Boolean) {
                Boolean value = prefsFile.getBoolean(thisPrefsName, (Boolean) DEFAULT_VALUES.get(thisPrefsName));
                DebugHelper.log("Preference Helper", "Got Boolean preference: " + thisPrefsName
                        + " with value " + value);
                return value;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            Log.e("Preference Helper", "Unable to get Boolean preference: " + thisPrefsName);
            e.printStackTrace();
            return false;
        }
    }

    public static String getStringPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof String) {
                String value = prefsFile.getString(thisPrefsName, (String) DEFAULT_VALUES.get(thisPrefsName));
                return value;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to get String preference: " + thisPrefsName);
            return "";
        }
    }

    public static void editPrefs(final String thisPrefsName, final Object thisPrefsValue, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            final SharedPreferences.Editor prefsEditor = prefsFile.edit();
            if (DEFAULT_VALUES.containsKey(thisPrefsName)) {
                if (thisPrefsValue instanceof Integer) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof Integer)) {
                        throw new IllegalArgumentException();
                    }
                    prefsEditor.putInt(thisPrefsName, (Integer) thisPrefsValue);
                } else if (thisPrefsValue instanceof Boolean) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof Boolean)) {
                        throw new IllegalArgumentException();
                    }
                    prefsEditor.putBoolean(thisPrefsName, (Boolean) thisPrefsValue);
                } else if (thisPrefsValue instanceof String) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof String)) {
                        throw new IllegalArgumentException();
                    }
                    prefsEditor.putString(thisPrefsName, (String) thisPrefsValue);
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                throw new IllegalArgumentException();
            }
            if (!prefsEditor.commit()) {
                throw new IllegalStateException("Unable to write preference");
            }
            DebugHelper.log("Preference Helper", "Edited preference "
                    + thisPrefsName + " with value " + thisPrefsValue);
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper",
                    "Unable to edit preference " + thisPrefsName);
        }
    }

    public static void clearPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            if (DEFAULT_VALUES.containsKey(thisPrefsName)) {
                editPrefs(thisPrefsName, DEFAULT_VALUES.get(thisPrefsName), thisContext);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper",
                    "Unable to clear preference " + thisPrefsName);
        }
    }

    public static void clearPrefs(final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (!prefsFile.edit().clear().commit()) {
                throw new IllegalStateException("Unable to clear preference file");
            }
            Log.w("Preference Helper", "Preference file cleared");
            triggerRebirth(thisContext);
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to clear preference");
        }
    }

    public static boolean isNewVersion(final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                    PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            // A restored or damaged preference with the wrong type is treated as an
            // unregistered version. registerNewVersion() will normalize it together
            // with every other known preference in its single transaction.
            final int lastKnownVersion = getStoredInt(prefsFile, "lastKnownVersion", 0);
            if (lastKnownVersion > BuildConfig.VERSION_CODE) {
                Log.e("VersionControl", "Newer version was already registered.");
                throw new IllegalStateException();
            }
            if (lastKnownVersion < BuildConfig.VERSION_CODE
                    || hasKnownPreferenceTypeMismatch(prefsFile)) {
                return true;
            }
            DebugHelper.log("VersionControl", "No new known version");
            return false;
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "VersionControl", "Downgrading is not allowed. Please clear the preference file.");
            return false;
        }
    }

    public static boolean registerNewVersion(final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                    PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            final int lastKnownVersion = getStoredInt(prefsFile, "lastKnownVersion", 0);
            if (lastKnownVersion > BuildConfig.VERSION_CODE) {
                Log.e("VersionControl", "Newer version was already registered.");
                throw new IllegalStateException();
            }
            if (lastKnownVersion < BuildConfig.VERSION_CODE
                    || hasKnownPreferenceTypeMismatch(prefsFile)) {
                Log.w("VersionControl", "Registering new known version");
                final SharedPreferences.Editor prefsEditor = prefsFile.edit();
                final MachineHelper machineHelper = MainActivity.getMachineHelper();
                if (machineHelper == null) {
                    throw new IllegalStateException("Machine helper is unavailable");
                }
                final String rawComments = getStoredRecord(prefsFile, "userComments");
                final String rawFavourites = getStoredRecord(prefsFile, "userFavourites");
                final String rawCompareJSON = getStoredRecord(prefsFile, "userCompare");
                final String rawCompareList = getStoredRecord(prefsFile, "userCompares");
                final String rawCompareLeft = getStoredRecord(prefsFile, "userComparesLeft");
                final String rawCompareRight = getStoredRecord(prefsFile, "userComparesRight");
                final Map<String, String> oldMachineNames =
                        OldMachineNamesHelper.read(thisContext);
                final UserRecordUpgradeHelper.UpgradeResult comments =
                        UserRecordUpgradeHelper.upgradeComments(
                                rawComments, machineHelper, oldMachineNames);
                final UserRecordUpgradeHelper.UpgradeResult favourites =
                        UserRecordUpgradeHelper.upgradeFavourites(
                                rawFavourites, machineHelper, oldMachineNames);
                final UserRecordUpgradeHelper.UpgradeResult compares =
                        UserRecordUpgradeHelper.upgradeCompares(
                                rawCompareJSON, rawCompareList, rawCompareLeft,
                                rawCompareRight, machineHelper,
                                oldMachineNames);

                prefsEditor.putString("userComments", comments.value);
                prefsEditor.putString("userFavourites", favourites.value);
                prefsEditor.putString("userCompare", compares.value);

                normalizeKnownPreferenceTypes(prefsFile, prefsEditor);

                final String manufacturer = getStoredString(
                        prefsFile, "lastMainManufacturer");
                if (!manufacturer.equals("all") && !manufacturer.equals("apple68k")
                        && !manufacturer.equals("appleppc")
                        && !manufacturer.equals("appleintel")
                        && !manufacturer.equals("applearm")) {
                    prefsEditor.putString("lastMainManufacturer", "all");
                }
                final String filter = getStoredString(prefsFile, "lastMainFilter");
                if (!filter.equals("names") && !filter.equals("processors")
                        && !filter.equals("years")) {
                    prefsEditor.putString("lastMainFilter", "names");
                }
                final int searchFilter = getStoredInt(
                        prefsFile, "lastSearchFiltersSpinner", 0);
                if (searchFilter < 0 || searchFilter > 4) {
                    prefsEditor.putInt("lastSearchFiltersSpinner", 0);
                }
                final int searchOption = getStoredInt(
                        prefsFile, "lastSearchOptionsSpinner", 0);
                if (searchOption < 0 || searchOption > 1) {
                    prefsEditor.putInt("lastSearchOptionsSpinner", 0);
                }
                for (String prefsName : prefsFile.getAll().keySet()) {
                    if (!DEFAULT_VALUES.containsKey(prefsName)) {
                        prefsEditor.remove(prefsName);
                    }
                }

                final String upgradeReport = buildUpgradeReport(
                        thisContext, comments.removed, favourites.removed, compares.removed);
                if (!upgradeReport.isEmpty()) {
                    final String pendingReport = getStoredString(
                            prefsFile, "pendingUpgradeReport");
                    prefsEditor.putString("pendingUpgradeReport", pendingReport.isEmpty()
                            ? upgradeReport : pendingReport + "\n\n" + upgradeReport);
                }
                prefsEditor.putInt("lastKnownVersion", BuildConfig.VERSION_CODE);
                if (!prefsEditor.commit()) {
                    throw new IllegalStateException();
                }
                return true;
            }
            DebugHelper.log("VersionControl", "No new known version");
            return true;
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "VersionControl", "Unable to register the current version.");
            return false;
        }
    }

    public static boolean showUpgradeReport(final Context thisContext) {
        final String report = getStringPrefs("pendingUpgradeReport", thisContext);
        if (!report.isEmpty()) {
            ExceptionHelper.showUpgradeReport(thisContext, report,
                    () -> clearPrefs("pendingUpgradeReport", thisContext));
            return true;
        }
        return false;
    }

    static String buildUpgradeReport(final Context thisContext,
                                     final List<String> comments,
                                     final List<String> favourites,
                                     final List<String> compares) {
        final StringBuilder report = new StringBuilder();
        appendUpgradeReport(report, thisContext.getString(R.string.menu_comment),
                comments, thisContext);
        appendUpgradeReport(report, thisContext.getString(R.string.menu_favourite),
                favourites, thisContext);
        appendUpgradeReport(report, thisContext.getString(R.string.menu_compare),
                compares, thisContext);
        if (report.length() == 0) {
            return "";
        }
        return "MacIndex " + BuildConfig.VERSION_NAME + "\n\n" + report.toString().trim();
    }

    private static void appendUpgradeReport(final StringBuilder report, final String title,
                                            final List<String> removed,
                                            final Context thisContext) {
        if (removed.isEmpty()) {
            return;
        }
        if (report.length() != 0) {
            report.append("\n\n");
        }
        report.append(title).append(":\n");
        for (String entry : removed) {
            report.append("- ").append(entry.isEmpty()
                    ? thisContext.getString(R.string.upgrade_report_empty_entry) : entry)
                    .append("\n");
        }
    }

    private static String getStoredString(final SharedPreferences prefsFile,
                                          final String prefsName) {
        final Object storedValue = prefsFile.getAll().get(prefsName);
        if (storedValue == null) {
            return "";
        }
        return storedValue instanceof String ? (String) storedValue : "";
    }

    private static String getStoredRecord(final SharedPreferences prefsFile,
                                          final String prefsName) {
        final Object storedValue = prefsFile.getAll().get(prefsName);
        if (storedValue == null) {
            return "";
        }
        return storedValue instanceof String
                ? (String) storedValue : String.valueOf(storedValue);
    }

    private static int getStoredInt(final SharedPreferences prefsFile,
                                    final String prefsName, final int defaultValue) {
        final Object storedValue = prefsFile.getAll().get(prefsName);
        return storedValue instanceof Integer ? (Integer) storedValue : defaultValue;
    }

    private static void normalizeKnownPreferenceTypes(final SharedPreferences prefsFile,
                                                      final SharedPreferences.Editor prefsEditor) {
        final Map<String, ?> storedValues = prefsFile.getAll();
        for (Map.Entry<String, Object> defaultEntry : DEFAULT_VALUES.entrySet()) {
            final String prefsName = defaultEntry.getKey();
            if (prefsName.equals("userComments") || prefsName.equals("userFavourites")
                    || prefsName.equals("userCompare") || !storedValues.containsKey(prefsName)) {
                continue;
            }

            final Object defaultValue = defaultEntry.getValue();
            final Object storedValue = storedValues.get(prefsName);
            if (storedValue != null && storedValue.getClass().equals(defaultValue.getClass())) {
                continue;
            }

            if (defaultValue instanceof Boolean) {
                prefsEditor.putBoolean(prefsName, (Boolean) defaultValue);
            } else if (defaultValue instanceof Integer) {
                prefsEditor.putInt(prefsName, (Integer) defaultValue);
            } else if (defaultValue instanceof String) {
                prefsEditor.putString(prefsName, (String) defaultValue);
            } else {
                throw new IllegalStateException("Unsupported preference type: " + prefsName);
            }
        }
    }

    private static boolean hasKnownPreferenceTypeMismatch(
            final SharedPreferences prefsFile) {
        final Map<String, ?> storedValues = prefsFile.getAll();
        for (Map.Entry<String, Object> defaultEntry : DEFAULT_VALUES.entrySet()) {
            if (!storedValues.containsKey(defaultEntry.getKey())) {
                continue;
            }
            final Object storedValue = storedValues.get(defaultEntry.getKey());
            if (storedValue == null
                    || !storedValue.getClass().equals(defaultEntry.getValue().getClass())) {
                return true;
            }
        }
        return false;
    }

    static boolean hasKnownPreferenceTypeMismatch(final Context thisContext) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        return hasKnownPreferenceTypeMismatch(prefsFile);
    }

    // https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
    public static void triggerRebirth(final Context thisContext) {
        PackageManager packageManager = thisContext.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(thisContext.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        thisContext.startActivity(mainIntent);
        System.exit(0);
    }
}
