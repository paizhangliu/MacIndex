package com.macindex.macindex;

/** Immutable lifecycle state for one update-check owner. */
final class UpdateCheckState {

    enum Status {
        IDLE,
        CHECKING,
        AVAILABLE,
        CURRENT,
        FAILED
    }

    private final Status status;
    private final UpdateChecker.Result result;
    private final Exception error;

    private UpdateCheckState(final Status thisStatus,
                             final UpdateChecker.Result thisResult,
                             final Exception thisError) {
        status = thisStatus;
        result = thisResult;
        error = thisError;
    }

    static UpdateCheckState idle() {
        return new UpdateCheckState(Status.IDLE, null, null);
    }

    static UpdateCheckState checking() {
        return new UpdateCheckState(Status.CHECKING, null, null);
    }

    static UpdateCheckState completed(final UpdateChecker.Result result) {
        return new UpdateCheckState(result.isUpdateAvailable()
                ? Status.AVAILABLE : Status.CURRENT, result, null);
    }

    static UpdateCheckState failed(final Exception failure) {
        return new UpdateCheckState(Status.FAILED, null, failure);
    }

    Status getStatus() {
        return status;
    }

    UpdateChecker.Result getResult() {
        return result;
    }

    Exception getError() {
        return error;
    }

    boolean isTerminal() {
        return status == Status.AVAILABLE || status == Status.CURRENT || status == Status.FAILED;
    }
}
