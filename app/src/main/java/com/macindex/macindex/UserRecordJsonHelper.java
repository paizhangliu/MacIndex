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

    static final int SCHEMA_VERSION = 1;

    private static final Pattern MACHINE_UID = Pattern.compile("MI\\d{6}");

    static boolean isMachineUID(final String value) {
        return value != null && MACHINE_UID.matcher(value).matches();
    }

    static String readStoredJSON(final Context thisContext, final String prefsName,
                                 final String defaultValue) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        return prefsFile.getString(prefsName, defaultValue);
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
