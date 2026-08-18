package com.macindex.macindex;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains a manual update check and its user-visible result across About recreation. */
public final class ManualUpdateViewModel extends ViewModel {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UpdateChecker checker = new UpdateChecker(executor);
    private final MutableLiveData<UpdateCheckState> state =
            new MutableLiveData<>(UpdateCheckState.idle());
    private volatile boolean cleared;

    LiveData<UpdateCheckState> getState() {
        return state;
    }

    void check(final String currentVersion) {
        final UpdateCheckState current = state.getValue();
        if (current != null && current.getStatus() == UpdateCheckState.Status.CHECKING) {
            return;
        }
        state.setValue(UpdateCheckState.checking());
        checker.check(currentVersion, (result, error) -> {
            if (!cleared) {
                state.postValue(error == null
                        ? UpdateCheckState.completed(result)
                        : UpdateCheckState.failed(error));
            }
        });
    }

    void acknowledge(final UpdateCheckState handled) {
        if (handled != null && handled.isTerminal() && state.getValue() == handled) {
            state.setValue(UpdateCheckState.idle());
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        executor.shutdownNow();
        super.onCleared();
    }
}
