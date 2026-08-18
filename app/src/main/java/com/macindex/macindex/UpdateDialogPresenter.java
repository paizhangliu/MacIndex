package com.macindex.macindex;

import android.app.Activity;
import android.app.AlertDialog;

/** Stateless rendering of terminal update states. Lifecycle ownership stays in the Activity. */
final class UpdateDialogPresenter {

    interface Listener {
        void onOpen(UpdateChecker.Information information);

        default void onSkip(final UpdateChecker.Information information) {
            throw new IllegalStateException("Update skipping is not enabled");
        }

        default void onRetry() {
            throw new IllegalStateException("Update retry is not enabled");
        }

        void onAcknowledge();
    }

    private UpdateDialogPresenter() {
    }

    static AlertDialog show(final Activity activity,
                            final UpdateCheckState state,
                            final boolean allowSkip,
                            final Listener listener) {
        if (!state.isTerminal()) {
            throw new IllegalArgumentException("Update dialog requires a terminal state");
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        switch (state.getStatus()) {
            case AVAILABLE:
                final UpdateChecker.Information latest =
                        state.getResult().getLatest();
                builder.setTitle(R.string.update_available);
                builder.setMessage(activity.getString(R.string.update_available_message,
                        latest.getVersion(), BuildConfig.VERSION_NAME));
                builder.setPositiveButton(R.string.update_download,
                        (dialog, which) -> listener.onOpen(latest));
                if (allowSkip) {
                    builder.setNeutralButton(R.string.update_skip,
                            (dialog, which) -> listener.onSkip(latest));
                }
                builder.setNegativeButton(R.string.update_not_now,
                        (dialog, which) -> listener.onAcknowledge());
                break;
            case CURRENT:
                builder.setTitle(R.string.update_current);
                builder.setMessage(activity.getString(
                        R.string.update_current_message, BuildConfig.VERSION_NAME));
                builder.setPositiveButton(R.string.link_confirm,
                        (dialog, which) -> listener.onAcknowledge());
                break;
            case FAILED:
                builder.setTitle(R.string.update_failed);
                builder.setMessage(R.string.update_failed_message);
                builder.setPositiveButton(R.string.action_try_again,
                        (dialog, which) -> listener.onRetry());
                builder.setNegativeButton(R.string.link_cancel,
                        (dialog, which) -> listener.onAcknowledge());
                break;
            default:
                throw new IllegalStateException("Unhandled update state " + state.getStatus());
        }
        final AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(ignored -> listener.onAcknowledge());
        dialog.show();
        return dialog;
    }
}
