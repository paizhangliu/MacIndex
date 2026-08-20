package com.macindex.macindex;

import android.content.Context;
import android.os.SystemClock;

import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.startup.AppStartupState;

/** Android-test access to the same process-owned Catalog used by production screens. */
final class StartupTestCatalog {
    private StartupTestCatalog() {
    }

    static MachineCatalog get(final Context context) {
        final MacIndexApplication application = (MacIndexApplication) context.getApplicationContext();
        final long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            final AppStartupState state = application.startup().getState().getValue();
            if (state instanceof AppStartupState.Ready) {
                return ((AppStartupState.Ready) state).getCatalog();
            }
            if (state instanceof AppStartupState.Fatal) {
                throw new AssertionError("App startup failed",
                        ((AppStartupState.Fatal) state).getFailure());
            }
            SystemClock.sleep(20);
        }
        throw new AssertionError("Timed out waiting for the bundled Catalog");
    }
}
