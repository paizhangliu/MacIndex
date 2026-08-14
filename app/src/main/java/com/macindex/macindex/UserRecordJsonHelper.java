package com.macindex.macindex;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * MacIndex User Record JSON Helper
 * Keeps the shared JSON protocol rules in one place.
 */
class UserRecordJsonHelper {

    private static final Pattern MACHINE_UID = Pattern.compile("MI\\d{6}");

    static class InvalidUserRecordException extends IllegalArgumentException {

        InvalidUserRecordException(final String message) {
            super(message);
        }

        InvalidUserRecordException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    static boolean isMachineUID(final String value) {
        return value != null && MACHINE_UID.matcher(value).matches();
    }

    static void requireKnownMachineUID(final String machineUID) {
        final MachineHelper machineHelper = MainActivity.getMachineHelper();
        if (machineHelper == null) {
            throw new IllegalStateException("Machine helper is unavailable");
        }
        if (machineHelper.resolveUID(machineUID) == null) {
            throw new InvalidUserRecordException("Unknown machine UID " + machineUID);
        }
    }

    static String readStoredJSON(final Context thisContext, final String prefsName,
                                 final String defaultValue) {
        try {
            final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                    PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
            return prefsFile.getString(prefsName, defaultValue);
        } catch (Exception e) {
            throw new InvalidUserRecordException(
                    "Unable to read stored user record " + prefsName, e);
        }
    }

    static void requireKeys(final JSONObject object, final String... keys) {
        if (object.length() != keys.length) {
            throw new IllegalArgumentException("Unexpected JSON keys");
        }
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) {
                throw new IllegalArgumentException("Missing JSON key " + key);
            }
        }
    }
}
