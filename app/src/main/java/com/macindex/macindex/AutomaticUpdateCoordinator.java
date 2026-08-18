package com.macindex.macindex;

import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process owner for the one automatic update attempt and its unconsumed result.
 *
 * <p>The request and its gate intentionally share the same lifetime. An Activity may disappear
 * while the network request is running; a later MainActivity in the same process can still
 * observe and consume the result.</p>
 */
final class AutomaticUpdateCoordinator {

    private static final String TAG = "AutomaticUpdate";

    private final AtomicBoolean checkStarted = new AtomicBoolean();
    private final UpdateChecker checker;
    private final MutableLiveData<UpdateCheckState> state =
            new MutableLiveData<>(UpdateCheckState.idle());

    AutomaticUpdateCoordinator() {
        this(new UpdateChecker(Executors.newSingleThreadExecutor()));
    }

    AutomaticUpdateCoordinator(final UpdateChecker updateChecker) {
        checker = updateChecker;
    }

    LiveData<UpdateCheckState> getState() {
        return state;
    }

    void checkIfEnabled(final boolean enabled,
                        final String currentVersion,
                        final String skippedVersion) {
        if (!enabled || !checkStarted.compareAndSet(false, true)) {
            return;
        }
        checker.check(currentVersion, (result, error) -> {
            if (error instanceof UpdateChecker.UpdateUnavailableException) {
                Log.w(TAG, "Unable to check for updates.", error);
            } else if (error != null) {
                throw ExceptionHelper.unexpected(error);
            } else if (result.isUpdateAvailable()
                    && !result.getLatest().getVersion().equals(skippedVersion)) {
                publish(UpdateCheckState.completed(result));
            }
        });
    }

    void acknowledge(final UpdateCheckState handled) {
        if (handled != null && handled.getStatus() == UpdateCheckState.Status.AVAILABLE
                && state.getValue() == handled) {
            publish(UpdateCheckState.idle());
        }
    }

    private void publish(final UpdateCheckState update) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state.setValue(update);
        } else {
            state.postValue(update);
        }
    }
}
