package com.macindex.macindex;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MacIndex Update Helper.
 * July 26, 2026
 */
class UpdateHelper {

    private static final String RELEASE_API =
            "https://api.github.com/repos/paizhangliu/MacIndex/releases/latest";

    private static final String DOWNLOAD_PAGE =
            "https://macindex.paizhang.info/download-and-update-history";

    private static boolean automaticallyChecked = false;

    public static synchronized void checkAutomatically(final Activity thisActivity) {
        if (automaticallyChecked
                || !PrefsHelper.getBooleanPrefs("isAutoCheckUpdate", thisActivity)) {
            return;
        }
        automaticallyChecked = true;
        check(thisActivity, false);
    }

    public static void checkManually(final Activity thisActivity) {
        check(thisActivity, true);
    }

    private static void check(final Activity thisActivity, final boolean isManual) {
        final ProgressDialog waitDialog;
        if (isManual) {
            waitDialog = new ProgressDialog(thisActivity);
            waitDialog.setMessage(thisActivity.getString(R.string.loading_update));
            waitDialog.setCancelable(false);
            waitDialog.show();
        } else {
            waitDialog = null;
        }

        new Thread(() -> {
            String latestVersion = null;
            Exception updateError = null;
            try {
                latestVersion = getLatestVersion();
            } catch (Exception e) {
                updateError = e;
            }

            final String finalLatestVersion = latestVersion;
            final Exception finalUpdateError = updateError;
            thisActivity.runOnUiThread(() -> {
                if (waitDialog != null && waitDialog.isShowing()) {
                    waitDialog.dismiss();
                }
                if (!isActivityAvailable(thisActivity)) {
                    return;
                }
                if (finalUpdateError != null) {
                    Log.w("UpdateHelper", "Unable to check for updates.", finalUpdateError);
                    if (isManual) {
                        showCheckFailed(thisActivity);
                    }
                    return;
                }

                try {
                    final int comparison = compareVersions(
                            finalLatestVersion, BuildConfig.VERSION_NAME);
                    if (comparison <= 0) {
                        if (isManual) {
                            showUpToDate(thisActivity);
                        }
                        return;
                    }
                    if (!shouldNotifyUpdate(finalLatestVersion,
                            PrefsHelper.getStringPrefs("skippedUpdateVersion", thisActivity),
                            isManual)) {
                        return;
                    }
                    showUpdateAvailable(thisActivity, finalLatestVersion);
                } catch (Exception e) {
                    Log.w("UpdateHelper", "Unable to compare update versions.", e);
                    if (isManual) {
                        showCheckFailed(thisActivity);
                    }
                }
            });
        }, "MacIndex-UpdateCheck").start();
    }

    private static String getLatestVersion() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            connection.setRequestProperty("User-Agent", "MacIndex-Android");

            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Unexpected HTTP response " + responseCode);
            }

            final StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String thisLine;
                while ((thisLine = reader.readLine()) != null) {
                    response.append(thisLine);
                    if (response.length() > 256 * 1024) {
                        throw new IllegalStateException("Update response is too large");
                    }
                }
            }
            return normalizeVersion(new JSONObject(response.toString()).getString("tag_name"));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static int compareVersions(final String firstVersion, final String secondVersion) {
        final int[] first = parseVersion(firstVersion);
        final int[] second = parseVersion(secondVersion);
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                return Integer.compare(first[i], second[i]);
            }
        }
        return 0;
    }

    static String normalizeVersion(final String rawVersion) {
        if (rawVersion == null) {
            throw new IllegalArgumentException();
        }
        String version = rawVersion.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        parseVersion(version);
        return version;
    }

    static boolean shouldNotifyUpdate(final String latestVersion,
                                      final String skippedVersion,
                                      final boolean isManual) {
        return isManual || !latestVersion.equals(skippedVersion);
    }

    private static int[] parseVersion(final String version) {
        if (version == null) {
            throw new IllegalArgumentException();
        }
        final String[] versionParts = version.split("\\.", -1);
        if (versionParts.length != 3) {
            throw new IllegalArgumentException();
        }
        final int[] parsedVersion = new int[versionParts.length];
        for (int i = 0; i < versionParts.length; i++) {
            if (!versionParts[i].matches("\\d+")) {
                throw new IllegalArgumentException();
            }
            parsedVersion[i] = Integer.parseInt(versionParts[i]);
            if (parsedVersion[i] < 0) {
                throw new IllegalArgumentException();
            }
        }
        return parsedVersion;
    }

    private static void showUpdateAvailable(final Activity thisActivity,
                                            final String latestVersion) {
        final AlertDialog.Builder updateDialog = new AlertDialog.Builder(thisActivity);
        updateDialog.setTitle(R.string.update_available);
        updateDialog.setMessage(thisActivity.getString(R.string.update_available_message,
                latestVersion, BuildConfig.VERSION_NAME));
        updateDialog.setPositiveButton(R.string.update_download, (dialog, which) ->
                LinkLoadingHelper.startBrowser(null, DOWNLOAD_PAGE, thisActivity));
        updateDialog.setNeutralButton(R.string.update_skip, (dialog, which) ->
                PrefsHelper.editPrefs("skippedUpdateVersion", latestVersion, thisActivity));
        updateDialog.setNegativeButton(R.string.update_not_now, (dialog, which) -> {
            // Cancelled, nothing to do.
        });
        updateDialog.show();
    }

    private static void showUpToDate(final Activity thisActivity) {
        final AlertDialog.Builder updateDialog = new AlertDialog.Builder(thisActivity);
        updateDialog.setTitle(R.string.update_current);
        updateDialog.setMessage(thisActivity.getString(
                R.string.update_current_message, BuildConfig.VERSION_NAME));
        updateDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
            // Confirmed.
        });
        updateDialog.show();
    }

    private static void showCheckFailed(final Activity thisActivity) {
        final AlertDialog.Builder updateDialog = new AlertDialog.Builder(thisActivity);
        updateDialog.setTitle(R.string.update_failed);
        updateDialog.setMessage(R.string.update_failed_message);
        updateDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
            // Confirmed.
        });
        updateDialog.show();
    }

    private static boolean isActivityAvailable(final Activity thisActivity) {
        return !thisActivity.isFinishing()
                && !thisActivity.isDestroyed();
    }
}
