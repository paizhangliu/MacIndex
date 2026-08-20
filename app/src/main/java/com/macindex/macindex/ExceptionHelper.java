package com.macindex.macindex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.macindex.macindex.userstate.InvalidUserDataException;
import com.macindex.macindex.userstate.UserStateUnavailableException;

import java.util.Objects;

/** Small UI primitives for failures that the caller has already classified as expected. */
final class ExceptionHelper {

    private static final String LOG_TAG = "ExpectedFailure";

    private ExceptionHelper() {
    }

    static void showCatalogStartupFailure(final Context context, final Exception failure) {
        Log.e("AppStartup", "The machine catalog is unavailable.", failure);
        showFatalDialog(context, R.string.startup_failure_title,
                R.string.startup_catalog_failure_information, false);
    }

    static void showUserStateStartupFailure(final Context context, final Exception failure) {
        Log.e("AppStartup", "Saved user data is unavailable during startup.", failure);
        showFatalDialog(context, R.string.startup_failure_title,
                R.string.startup_user_state_failure_information, true);
    }

    static void showUserStateReadFailure(final Context context, final Exception failure) {
        if (!(failure instanceof UserStateUnavailableException)) {
            throw unexpected(failure);
        }
        Log.e("UserState", "Saved user data became unavailable.", failure);
        showFatalDialog(context, R.string.user_state_unavailable_title,
                R.string.user_state_read_failure_information, true);
    }

    static void showUserStateWriteFailure(final Context context, final Exception failure,
                                          final int title, final int message) {
        if (!(failure instanceof UserStateUnavailableException)) {
            throw unexpected(failure);
        }
        showMessageDialog(context, title, message);
    }

    static void showUserStateEditFailure(final Context context, final Exception failure,
                                         final int title, final int message) {
        if (!(failure instanceof UserStateUnavailableException)
                && !(failure instanceof InvalidUserDataException)) {
            throw unexpected(failure);
        }
        showMessageDialog(context, title, message);
    }

    static void showUpgradeReport(final Context context, final String report,
                                  final Runnable confirmedAction) {
        showInformationDialog(context,
                R.string.upgrade_report_title, R.string.upgrade_report_information,
                R.string.link_confirm, R.string.upgrade_report_copy_button,
                R.string.upgrade_report_copy_information, report, confirmedAction);
    }

    static AlertDialog showCrashReport(final Context context, final String report,
                                       final Runnable confirmedAction) {
        return showInformationDialog(context,
                R.string.crash_report_title, R.string.crash_report_information,
                R.string.crash_report_continue, R.string.crash_report_copy_button,
                R.string.crash_report_copy_information, report, confirmedAction);
    }

    static void showMessageDialog(final Context context, final int title, final int message) {
        showMessageDialog(context, title, message, null);
    }

    static void showMessageDialog(final Context context, final int title, final int message,
                                  final Runnable dismissedAction) {
        Objects.requireNonNull(context, "context");
        if (activityCannotShowDialog(context)) {
            Log.w(LOG_TAG, "Message dialog skipped because its Activity is no longer active.");
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.link_confirm, null)
                .create();
        if (dismissedAction != null) {
            dialog.setOnDismissListener(unused -> dismissedAction.run());
        }
        dialog.show();
    }

    static boolean copyText(final Context context, final String label,
                            final CharSequence text, final int successToast) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(text, "text");
        final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showToast(context, R.string.copy_failed_information);
            return false;
        }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (SecurityException denied) {
            Log.w("Clipboard", "Android denied clipboard access.", denied);
            showToast(context, R.string.copy_failed_information);
            return false;
        }
        if (successToast != 0) {
            showToast(context, successToast);
        }
        return true;
    }

    static void showToast(final Context context, final int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    static RuntimeException unexpected(final Throwable failure) {
        if (failure instanceof RuntimeException) {
            return (RuntimeException) failure;
        }
        return new IllegalStateException("Unexpected checked failure", failure);
    }

    private static void showFatalDialog(final Context context, final int title,
                                        final int message, final boolean canRestart) {
        Objects.requireNonNull(context, "context");
        if (activityCannotShowDialog(context)) {
            Log.w(LOG_TAG, "Fatal dialog skipped because its Activity is no longer active.");
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false);
        if (canRestart) {
            builder.setPositiveButton(R.string.action_restart,
                    (dialog, which) -> AppRestartHelper.restart(context));
            builder.setNegativeButton(R.string.action_close,
                    (dialog, which) -> closeActivity(context));
        } else {
            builder.setPositiveButton(R.string.action_close,
                    (dialog, which) -> closeActivity(context));
        }
        builder.show();
    }

    private static AlertDialog showInformationDialog(final Context context, final int title,
                                                     final int message, final int positiveButton,
                                                     final int copyButton, final int copyToast,
                                                     final String information,
                                                     final Runnable positiveAction) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(information, "information");
        if (activityCannotShowDialog(context)) {
            Log.w(LOG_TAG, "Information dialog skipped because its Activity is no longer active.");
            return null;
        }
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View content = inflater.inflate(R.layout.chunk_exception_dialog, null);
        final TextView informationView = content.findViewById(R.id.exceptionInfo);
        informationView.setText(information);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(positiveButton, (unused, which) -> {
                    if (positiveAction != null) {
                        positiveAction.run();
                    }
                })
                .setNeutralButton(copyButton, null)
                .create();
        dialog.show();
        final View copy = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (copy != null) {
            copy.setOnClickListener(unused ->
                    copyText(context, "MacIndex diagnostic information", information, copyToast));
        }
        return dialog;
    }

    private static void closeActivity(final Context context) {
        if (context instanceof Activity) {
            ((Activity) context).finishAffinity();
        }
    }

    private static boolean activityCannotShowDialog(final Context context) {
        if (!(context instanceof Activity)) {
            return false;
        }
        final Activity activity = (Activity) context;
        return activity.isFinishing() || activity.isDestroyed();
    }

}
