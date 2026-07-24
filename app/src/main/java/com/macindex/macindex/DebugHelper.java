package com.macindex.macindex;

import android.util.Log;

/**
 * MacIndex Debug Helper.
 * July 24, 2026
 */
class DebugHelper {

    public static void log(final String tag, final String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }
}
