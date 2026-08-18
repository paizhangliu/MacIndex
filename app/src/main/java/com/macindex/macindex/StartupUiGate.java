package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.startup.AppStartupState;
import com.macindex.macindex.userstate.AppStateRepository;

/** The single Views boundary between process startup and machine-dependent screens. */
final class StartupUiGate {

    @FunctionalInterface
    interface ReadyListener {
        void onReady(@NonNull MachineCatalog catalog,
                     @NonNull AppStateRepository userState);
    }

    private StartupUiGate() {
    }

    static void bind(@NonNull final AppCompatActivity activity,
                     @NonNull final ReadyListener ready) {
        bind(activity, () -> { }, ready);
    }

    static void bind(@NonNull final AppCompatActivity activity,
                     @NonNull final Runnable terminal,
                     @NonNull final ReadyListener ready) {
        final MacIndexApplication application =
                (MacIndexApplication) activity.getApplication();
        application.startup().getState().observe(activity, state -> {
            if (state == null || state instanceof AppStartupState.Loading) {
                return;
            }
            terminal.run();
            if (state instanceof AppStartupState.Ready) {
                final AppStartupState.Ready readyState = (AppStartupState.Ready) state;
                ready.onReady(readyState.getCatalog(), readyState.getUserStateRepository());
            } else {
                showFailure(activity, (AppStartupState.Fatal) state);
            }
        });
    }

    private static void showFailure(final AppCompatActivity activity,
                                    final AppStartupState.Fatal state) {
        final Exception failure = state.getFailure();
        final AppStartupState.FailureKind failureKind = state.getKind();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (failureKind == AppStartupState.FailureKind.CATALOG_ASSET) {
            ExceptionHelper.showCatalogStartupFailure(activity, failure);
        } else if (failureKind == AppStartupState.FailureKind.USER_STATE) {
            ExceptionHelper.showUserStateStartupFailure(activity, failure);
        } else {
            throw new IllegalStateException("Unknown startup failure " + failureKind);
        }
    }
}
