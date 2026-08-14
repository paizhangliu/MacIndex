package com.macindex.macindex;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

class ExceptionHelper {

    public static void handleException(final Context thisContext, final Exception thisException,
                                       final String exceptionModule,
                                       final String exceptionMessage) {
        handleException(thisContext, thisException, exceptionModule, exceptionMessage,
                requiresDataRecovery(thisException));
    }

    public static void handleDatabaseException(final Context thisContext,
                                               final Exception thisException,
                                               final String exceptionModule,
                                               final String exceptionMessage) {
        handleException(thisContext, thisException, exceptionModule, exceptionMessage, true);
    }

    private static void handleException(final Context thisContext,
                                        final Exception thisException,
                                        final String exceptionModule,
                                        final String exceptionMessage,
                                        final boolean requiresDataRecovery) {
        if (thisContext != null) {
            if (requiresDataRecovery) {
                invalidateDataVersion(thisContext);
            }

            final String basicInfo = "Generated: " + Calendar.getInstance().getTime() + "\n"
                    + "MacIndex Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                    + "Android Version: " + Build.VERSION.RELEASE + "\n"
                    + "Hardware Model: " + Build.BRAND + " " + Build.MODEL + "\n";

            final String exceptionLog;
            if (exceptionModule != null && exceptionMessage != null) {
                Log.e(exceptionModule, exceptionMessage);
                exceptionLog = "Exception Module: " + exceptionModule + "\n"
                        + "Exception Message: " + exceptionMessage + "\n";
            } else {
                exceptionLog = "Module is not available" + "\n";
            }

            final String exceptionDetails;
            if (thisException == null) {
                exceptionDetails = "Detail is not available" + "\n";
            } else {
                thisException.printStackTrace();
                exceptionDetails = "Exception Details:" + "\n" + getStackTrace(thisException);
            }

            handleExceptionDialog(thisContext, basicInfo + exceptionLog + exceptionDetails);
        }
    }

    static boolean requiresDataRecovery(final Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            if (currentThrowable instanceof UserRecordJsonHelper.InvalidUserRecordException
                    || currentThrowable instanceof MachineHelper.UnknownMachineUIDException
                    || currentThrowable instanceof SQLiteException) {
                return true;
            }
            if (currentThrowable == currentThrowable.getCause()) {
                break;
            }
            currentThrowable = currentThrowable.getCause();
        }
        return false;
    }

    private static void invalidateDataVersion(final Context thisContext) {
        try {
            final boolean versionInvalidated = thisContext.getSharedPreferences(
                            PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE).edit()
                    .putInt("lastKnownVersion", 0).commit();
            if (!versionInvalidated) {
                Log.e("ExceptionHelper", "Unable to register the current version again.");
            } else {
                // Do not allow a cached process to register the same open
                // database again after a data error.
                MainActivity.closeDatabase();
            }
        } catch (Exception e) {
            Log.e("ExceptionHelper", "Unable to register the current version again.", e);
        }
    }

    private static void handleExceptionDialog(final Context thisContext, final String exceptionInfo) {
        showInformationDialog(thisContext, R.string.error, R.string.error_information,
                R.string.error_restart, R.string.error_copy_button,
                R.string.error_copy_information, exceptionInfo,
                () -> PrefsHelper.triggerRebirth(thisContext));
    }

    public static void showUpgradeReport(final Context thisContext, final String report,
                                         final Runnable confirmedAction) {
        showInformationDialog(thisContext, R.string.upgrade_report_title,
                R.string.upgrade_report_information, R.string.link_confirm,
                R.string.upgrade_report_copy_button, R.string.upgrade_report_copy_information,
                report, confirmedAction);
    }

    private static void showInformationDialog(final Context thisContext, final int title,
                                              final int message, final int positiveButton,
                                              final int copyButton, final int copyToast,
                                              final String information,
                                              final Runnable positiveAction) {
        if (thisContext instanceof Activity) {
            final Activity activity = (Activity) thisContext;
            if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && activity.isDestroyed())) {
                Log.e("InformationDialog", information);
                return;
            }
        }
        final AlertDialog.Builder informationDialog = new AlertDialog.Builder(thisContext);
        informationDialog.setTitle(title);
        informationDialog.setMessage(message);
        informationDialog.setCancelable(false);
        informationDialog.setPositiveButton(positiveButton, (dialogInterface, i) ->
                positiveAction.run());
        informationDialog.setNeutralButton(copyButton, (dialogInterface, i) -> {
            // To be override
        });

        final View infoChunk = ((LayoutInflater) thisContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.chunk_exception_dialog, null);
        final TextView exceptionInfoBox = infoChunk.findViewById(R.id.exceptionInfo);

        exceptionInfoBox.setText(information);
        informationDialog.setView(infoChunk);

        final AlertDialog informationDialogCreated = informationDialog.create();
        informationDialogCreated.show();

        // Override the neutral button
        informationDialogCreated.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) thisContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ExceptionInfo", information);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(thisContext, thisContext.getString(copyToast), Toast.LENGTH_LONG).show();
        });
    }

    private static String getStackTrace(final Exception thisException) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        thisException.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
