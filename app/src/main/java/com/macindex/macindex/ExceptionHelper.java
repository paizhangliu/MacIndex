package com.macindex.macindex;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
                                                 final String exceptionModule, final String exceptionMessage) {
        if (thisContext != null) {
            try {
                final boolean versionInvalidated = thisContext.getSharedPreferences(
                                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE).edit()
                        .putInt("lastKnownVersion", 0).commit();
                if (!versionInvalidated) {
                    Log.e("ExceptionHelper", "Unable to register the current version again.");
                }
            } catch (Exception e) {
                Log.e("ExceptionHelper", "Unable to register the current version again.", e);
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

    private static void handleExceptionDialog(final Context thisContext, final String exceptionInfo) {
        if (thisContext instanceof Activity) {
            final Activity activity = (Activity) thisContext;
            if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && activity.isDestroyed())) {
                Log.e("ExceptionHelper", exceptionInfo);
                return;
            }
        }
        final AlertDialog.Builder exceptionDialog = new AlertDialog.Builder(thisContext);
        exceptionDialog.setTitle(R.string.error);
        exceptionDialog.setMessage(R.string.error_information);
        exceptionDialog.setCancelable(false);
        exceptionDialog.setPositiveButton(R.string.error_restart, (dialogInterface, i) ->
                PrefsHelper.triggerRebirth(thisContext));
        exceptionDialog.setNeutralButton(R.string.error_copy_button, (dialogInterface, i) -> {
            // To be override
        });

        final View infoChunk = ((LayoutInflater) thisContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.chunk_exception_dialog, null);
        final TextView exceptionInfoBox = infoChunk.findViewById(R.id.exceptionInfo);

        exceptionInfoBox.setText(exceptionInfo);
        exceptionDialog.setView(infoChunk);

        final AlertDialog exceptionDialogCreated = exceptionDialog.create();
        exceptionDialogCreated.show();

        // Override the neutral button
        exceptionDialogCreated.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) thisContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ExceptionInfo", exceptionInfo);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(thisContext,
                    MainActivity.getRes().getString(R.string.error_copy_information), Toast.LENGTH_LONG).show();
        });
    }

    private static String getStackTrace(final Exception thisException) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        thisException.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
