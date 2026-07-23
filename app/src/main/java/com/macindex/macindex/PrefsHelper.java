package com.macindex.macindex;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
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

        /* User Record */
        DEFAULT_VALUES.put("userCompares", "");
        DEFAULT_VALUES.put("userComparesLeft", "");
        DEFAULT_VALUES.put("userComparesRight", "");
        DEFAULT_VALUES.put("userFavourites", "");
        DEFAULT_VALUES.put("userComments", "");

        /* Runtime Record */
        DEFAULT_VALUES.put("lastMainManufacturer", "all");
        DEFAULT_VALUES.put("lastMainFilter", "names");
        DEFAULT_VALUES.put("lastSearchFiltersSpinner", 0);
        DEFAULT_VALUES.put("lastSearchOptionsSpinner", 0);
        DEFAULT_VALUES.put("lastKnownVersion", 0);
        DEFAULT_VALUES.put("lastCachedM0F0", "");
        DEFAULT_VALUES.put("lastCachedM0F1", "");
        DEFAULT_VALUES.put("lastCachedM0F2", "");
        DEFAULT_VALUES.put("lastCachedM1F0", "");
        DEFAULT_VALUES.put("lastCachedM1F1", "");
        DEFAULT_VALUES.put("lastCachedM1F2", "");
        DEFAULT_VALUES.put("lastCachedM2F0", "");
        DEFAULT_VALUES.put("lastCachedM2F1", "");
        DEFAULT_VALUES.put("lastCachedM2F2", "");
        DEFAULT_VALUES.put("lastCachedM3F0", "");
        DEFAULT_VALUES.put("lastCachedM3F1", "");
        DEFAULT_VALUES.put("lastCachedM3F2", "");
        DEFAULT_VALUES.put("lastCachedM4F0", "");
        DEFAULT_VALUES.put("lastCachedM4F1", "");
        DEFAULT_VALUES.put("lastCachedM4F2", "");

        /* Runtime Parameters */
        DEFAULT_VALUES.put("isFirstLunch", Boolean.TRUE);
        DEFAULT_VALUES.put("isEnableVolWarningThisTime", Boolean.TRUE);
        DEFAULT_VALUES.put("isReloadNeeded", Boolean.FALSE);
        DEFAULT_VALUES.put("isCommentsReloadNeeded", Boolean.FALSE);
        DEFAULT_VALUES.put("isFavouritesReloadNeeded", Boolean.FALSE);
        DEFAULT_VALUES.put("isCompareReloadNeeded", Boolean.FALSE);
    }

    public static int getIntPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof Integer) {
                int value = prefsFile.getInt(thisPrefsName, (Integer) DEFAULT_VALUES.get(thisPrefsName));
                Log.i("Preference Helper", "Got Int preference " + thisPrefsName
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
                Log.i("Preference Helper", "Got Boolean preference: " + thisPrefsName
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

    public static String getStringPrefs(final String thisPrefsName, final Context thisContext) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            if (DEFAULT_VALUES.containsKey(thisPrefsName) && DEFAULT_VALUES.get(thisPrefsName) instanceof String) {
                String value = prefsFile.getString(thisPrefsName, (String) DEFAULT_VALUES.get(thisPrefsName));
                Log.i("Preference Helper", "Got String preference: " + thisPrefsName
                        + " with value " + value);
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
                    Log.i("Preference Helper", "Edited Int preference "
                            + thisPrefsName + " with value " + thisPrefsValue);
                } else if (thisPrefsValue instanceof Boolean) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof Boolean)) {
                        throw new IllegalArgumentException();
                    }
                    prefsFile.edit().putBoolean(thisPrefsName, (Boolean) thisPrefsValue).commit();
                    Log.i("Preference Helper", "Edited Boolean preference "
                            + thisPrefsName + " with value " + thisPrefsValue);
                } else if (thisPrefsValue instanceof String) {
                    if (!(DEFAULT_VALUES.get(thisPrefsName) instanceof String)) {
                        throw new IllegalArgumentException();
                    }
                    prefsFile.edit().putString(thisPrefsName, (String) thisPrefsValue).commit();
                    Log.i("Preference Helper", "Edited String preference "
                            + thisPrefsName + " with value " + thisPrefsValue);
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper",
                    "Unable to edit preference "
                    + thisPrefsName + " with value " + thisPrefsValue);
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
            Toast.makeText(thisContext, R.string.setting_defaults_cleared, Toast.LENGTH_LONG).show();
            triggerRebirth(thisContext);
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e, "Preference Helper", "Unable to clear preference");
        }
    }

    public static boolean registerNewVersion(final Context thisContext) {
        try {
            if (getIntPrefs("lastKnownVersion", thisContext) < BuildConfig.VERSION_CODE) {
                Log.w("VersionControl", "Registering new known version");
                editPrefs("lastKnownVersion", BuildConfig.VERSION_CODE, thisContext);
                return true;
            } else if (getIntPrefs("lastKnownVersion", thisContext) == BuildConfig.VERSION_CODE) {
                Log.i("VersionControl", "No new known version");
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
