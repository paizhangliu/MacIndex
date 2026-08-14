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
import java.util.Locale;
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
        DEFAULT_VALUES.put("isAutoCheckUpdate", Boolean.TRUE);

        /* User Record */
        DEFAULT_VALUES.put("userCompares", "");
        DEFAULT_VALUES.put("userComparesLeft", "");
        DEFAULT_VALUES.put("userComparesRight", "");
        DEFAULT_VALUES.put("userFavourites", "");
        DEFAULT_VALUES.put("userComments", "");
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
            if (DEFAULT_VALUES.containsKey(thisPrefsName)) {
                if (thisPrefsValue instanceof Integer) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof Integer)) {
                        throw new IllegalArgumentException();
                    }
                    prefsFile.edit().putInt(thisPrefsName, (Integer) thisPrefsValue).commit();
                    DebugHelper.log("Preference Helper", "Edited Int preference "
                            + thisPrefsName + " with value " + thisPrefsValue);
                } else if (thisPrefsValue instanceof Boolean) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof Boolean)) {
                        throw new IllegalArgumentException();
                    }
                    prefsFile.edit().putBoolean(thisPrefsName, (Boolean) thisPrefsValue).commit();
                    DebugHelper.log("Preference Helper", "Edited Boolean preference "
                            + thisPrefsName + " with value " + thisPrefsValue);
                } else if (thisPrefsValue instanceof String) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof String)) {
                        throw new IllegalArgumentException();
                    }
                    prefsFile.edit().putString(thisPrefsName, (String) thisPrefsValue).commit();
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                throw new IllegalArgumentException();
            }
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
            prefsFile.edit().clear().commit();
            Log.w("Preference Helper", "Preference file cleared");
            triggerRebirth(thisContext);
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to clear preference");
        }
    }

    public static boolean isNewVersion(final Context thisContext) {
        try {
            final int lastKnownVersion = getIntPrefs("lastKnownVersion", thisContext);
            if (lastKnownVersion < BuildConfig.VERSION_CODE) {
                return true;
            } else if (lastKnownVersion == BuildConfig.VERSION_CODE) {
                DebugHelper.log("VersionControl", "No new known version");
                return false;
            } else {
                Log.e("VersionControl", "Newer version was already registered.");
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "VersionControl", "Downgrading is not allowed. Please clear the preference file.");
            return false;
        }
    }

    public static boolean registerNewVersion(final Context thisContext) {
        try {
            final int lastKnownVersion = getIntPrefs("lastKnownVersion", thisContext);
            if (lastKnownVersion < BuildConfig.VERSION_CODE) {
                Log.w("VersionControl", "Registering new known version");
                final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                        PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
                final SharedPreferences.Editor prefsEditor = prefsFile.edit();
                final Map<String, String> validNames = getValidMachineNames();
                final UserRecordUpgradeHelper.UpgradeResult comments =
                        UserRecordUpgradeHelper.upgradeComments(
                                prefsFile.getString("userComments", ""), validNames);
                final UserRecordUpgradeHelper.UpgradeResult favourites =
                        UserRecordUpgradeHelper.upgradeFavourites(
                                prefsFile.getString("userFavourites", ""), validNames);
                final UserRecordUpgradeHelper.CompareUpgradeResult compares =
                        UserRecordUpgradeHelper.upgradeCompares(
                                prefsFile.getString("userCompares", ""),
                                prefsFile.getString("userComparesLeft", ""),
                                prefsFile.getString("userComparesRight", ""), validNames);

                prefsEditor.putString("userComments", comments.value);
                prefsEditor.putString("userFavourites", favourites.value);
                prefsEditor.putString("userCompares", compares.compares);
                prefsEditor.putString("userComparesLeft", compares.left);
                prefsEditor.putString("userComparesRight", compares.right);

                final String upgradeReport = buildUpgradeReport(
                        thisContext, comments.removed, favourites.removed, compares.removed);
                if (!upgradeReport.isEmpty()) {
                    final String pendingReport = prefsFile.getString("pendingUpgradeReport", "");
                    prefsEditor.putString("pendingUpgradeReport", pendingReport.isEmpty()
                            ? upgradeReport : pendingReport + "\n\n" + upgradeReport);
                }
                prefsEditor.putInt("lastKnownVersion", BuildConfig.VERSION_CODE);
                if (!prefsEditor.commit()) {
                    throw new IllegalStateException();
                }
                return true;
            } else if (lastKnownVersion == BuildConfig.VERSION_CODE) {
                DebugHelper.log("VersionControl", "No new known version");
                return true;
            } else {
                Log.e("VersionControl", "Newer version was already registered.");
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "VersionControl", "Unable to register the current version.");
            return false;
        }
    }

    public static boolean showUpgradeReport(final Context thisContext) {
        final String report = getStringPrefs("pendingUpgradeReport", thisContext);
        if (!report.isEmpty()) {
            clearPrefs("pendingUpgradeReport", thisContext);
            ExceptionHelper.showUpgradeReport(thisContext, report);
            return true;
        }
        return false;
    }

    private static Map<String, String> getValidMachineNames() {
        final MachineHelper machineHelper = MainActivity.getMachineHelper();
        if (machineHelper == null) {
            throw new IllegalStateException("Machine helper is unavailable");
        }
        final Map<String, String> validNames = new HashMap<>();
        for (int machineID = 0; machineID < machineHelper.getMachineCount(); machineID++) {
            final String machineName = machineHelper.getName(machineID);
            final String oldValue = validNames.put(
                    machineName.toLowerCase(Locale.ROOT), machineName);
            if (oldValue != null && !oldValue.equals(machineName)) {
                throw new IllegalStateException("Duplicate machine name");
            }
        }
        return validNames;
    }

    private static String buildUpgradeReport(final Context thisContext,
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
