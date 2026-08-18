package com.macindex.macindex;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Records one unexpected process crash and presents it only after a later successful startup. */
final class LastCrashReport {

    private static final String TAG = "LastCrashReport";
    private static final String FILE_NAME = "last_crash.txt";
    private static final int MAX_REPORT_CHARS = 64 * 1024;
    private static final int MAX_STACK_FRAMES = 160;
    private static final int MAX_CAUSES = 32;

    private final File reportFile;
    private boolean acknowledgedInProcess;

    LastCrashReport(@NonNull final Application owner) {
        this(new File(owner.getNoBackupFilesDir(), FILE_NAME));
    }

    LastCrashReport(@NonNull final File reportFile) {
        this.reportFile = reportFile;
    }

    void install() {
        final Thread.UncaughtExceptionHandler original =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                write(thread, failure);
            } catch (Throwable recordingFailure) {
                Log.e(TAG, "Unable to record the last crash.", recordingFailure);
            } finally {
                if (original != null) {
                    original.uncaughtException(thread, failure);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    AlertDialog presentIfAvailable(@NonNull final Activity activity,
                                   @NonNull final Runnable afterAcknowledged) {
        if (acknowledgedInProcess || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        final String report;
        try {
            report = read();
        } catch (IOException | SecurityException failure) {
            Log.w(TAG, "Unable to read the last crash report.", failure);
            return null;
        }
        if (report == null) {
            return null;
        }
        if (report.trim().isEmpty()) {
            acknowledgedInProcess = true;
            deleteAcknowledgedReport();
            return null;
        }
        return ExceptionHelper.showCrashReport(activity, report, () -> {
            acknowledgedInProcess = true;
            deleteAcknowledgedReport();
            afterAcknowledged.run();
        });
    }

    private void write(final Thread thread, final Throwable failure) throws IOException {
        final String report = buildReport(thread, failure);
        try (FileOutputStream output = new FileOutputStream(reportFile, false);
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(report);
            writer.flush();
            output.getFD().sync();
        }
    }

    private String read() throws IOException {
        if (!reportFile.isFile()) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(reportFile), StandardCharsets.UTF_8))) {
            final char[] buffer = new char[4096];
            int length;
            while ((length = reader.read(buffer)) >= 0 && result.length() < MAX_REPORT_CHARS) {
                result.append(buffer, 0, Math.min(length, MAX_REPORT_CHARS - result.length()));
            }
        }
        return result.toString();
    }

    private void deleteAcknowledgedReport() {
        try {
            if (reportFile.exists() && !reportFile.delete()) {
                Log.w(TAG, "Unable to delete the acknowledged crash report.");
            }
        } catch (SecurityException failure) {
            Log.w(TAG, "Unable to delete the acknowledged crash report.", failure);
        }
    }

    private String buildReport(final Thread thread, final Throwable failure) {
        final StringBuilder report = new StringBuilder();
        append(report, "Generated: ", new Date().toString());
        append(report, "MacIndex Version: ", BuildConfig.VERSION_NAME + " ("
                + BuildConfig.VERSION_CODE + ")");
        append(report, "Android Version: ", Build.VERSION.RELEASE);
        append(report, "Hardware Model: ", Build.BRAND + " " + Build.MODEL);
        append(report, "Thread: ", thread == null ? "unknown" : thread.getName());
        report.append("\nUnexpected exception:\n");
        Throwable current = failure;
        int frames = 0;
        int causes = 0;
        while (current != null && report.length() < MAX_REPORT_CHARS
                && frames < MAX_STACK_FRAMES && causes < MAX_CAUSES) {
            appendThrowable(report, current);
            for (StackTraceElement element : current.getStackTrace()) {
                if (report.length() >= MAX_REPORT_CHARS || frames >= MAX_STACK_FRAMES) {
                    break;
                }
                appendLimited(report, "    at " + element + '\n');
                frames++;
            }
            current = current.getCause();
            causes++;
            if (current != null) {
                appendLimited(report, "Caused by: ");
            }
        }
        return report.toString();
    }

    private static void appendThrowable(final StringBuilder target, final Throwable failure) {
        appendLimited(target, failure.getClass().getName());
        final String message = failure.getMessage();
        if (message != null && !message.isEmpty()) {
            appendLimited(target, ": ");
            appendLimited(target, message);
        }
        appendLimited(target, "\n");
    }

    private static void appendLimited(final StringBuilder target, final CharSequence value) {
        final int available = MAX_REPORT_CHARS - target.length();
        if (available <= 0) {
            return;
        }
        target.append(value, 0, Math.min(available, value.length()));
    }

    private static void append(final StringBuilder target, final String label,
                               final String value) {
        target.append(label).append(value).append('\n');
    }
}
