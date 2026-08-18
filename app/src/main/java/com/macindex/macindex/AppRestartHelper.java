package com.macindex.macindex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Performs the explicit restart offered after a fatal saved-data access failure. */
final class AppRestartHelper {

    private AppRestartHelper() {
    }

    static void restart(final Context context) {
        final Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        final ComponentName component = launchIntent == null ? null : launchIntent.getComponent();
        if (component == null) {
            throw new IllegalStateException("Unable to resolve the MacIndex launcher");
        }
        context.startActivity(Intent.makeRestartActivityTask(component));
        Runtime.getRuntime().exit(0);
    }
}
