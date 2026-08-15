package com.macindex.macindex;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MacIndex Update Helper.
 * July 26, 2026
 */
class UpdateHelper {

    private static final String WEBSITE_API =
            "https://macindex.paizhang.info/api/latest.json";

    private static final String GITHUB_API =
            "https://api.github.com/repos/paizhangliu/MacIndex/releases/latest";

    private static boolean automaticallyChecked = false;

    public static synchronized void checkAutomatically(final AppCompatActivity thisActivity) {
        if (automaticallyChecked
                || !PrefsHelper.getBooleanPrefs("isAutoCheckUpdate", thisActivity)) {
            return;
        }
        automaticallyChecked = true;
        check(thisActivity, false);
    }

    public static void checkManually(final AppCompatActivity thisActivity) {
        check(thisActivity, true);
    }

    private static void check(final AppCompatActivity thisActivity, final boolean isManual) {
        final WeakReference<AppCompatActivity> activityReference =
                new WeakReference<>(thisActivity);
        final WeakReference<UpdateProgressDialog> waitDialogReference;
        if (isManual) {
            final UpdateProgressDialog waitDialog = new UpdateProgressDialog(thisActivity);
            waitDialog.setMessage(thisActivity.getString(R.string.loading_update));
            waitDialog.setCancelable(false);
            waitDialog.show();
            waitDialogReference = new WeakReference<>(waitDialog);
        } else {
            waitDialogReference = null;
        }

        new Thread(() -> {
            UpdateInformation latestUpdate = null;
            Exception updateError = null;
            try {
                latestUpdate = getLatestUpdate();
            } catch (Exception e) {
                updateError = e;
            }

            final UpdateInformation finalLatestUpdate = latestUpdate;
            final Exception finalUpdateError = updateError;
            new Handler(Looper.getMainLooper()).post(() -> {
                final AppCompatActivity currentActivity = activityReference.get();
                if (currentActivity == null || !isActivityAvailable(currentActivity)) {
                    return;
                }
                if (waitDialogReference != null) {
                    final UpdateProgressDialog waitDialog = waitDialogReference.get();
                    if (waitDialog != null) {
                        waitDialog.finish();
                    }
                }
                if (finalUpdateError != null) {
                    Log.w("UpdateHelper", "Unable to check for updates.", finalUpdateError);
                    if (isManual) {
                        showCheckFailed(currentActivity);
                    }
                    return;
                }

                try {
                    final int comparison = compareVersions(
                            finalLatestUpdate.version, BuildConfig.VERSION_NAME);
                    if (comparison <= 0) {
                        if (isManual) {
                            showUpToDate(currentActivity);
                        }
                        return;
                    }
                    if (!shouldNotifyUpdate(finalLatestUpdate.version,
                            PrefsHelper.getStringPrefs("skippedUpdateVersion", currentActivity),
                            isManual)) {
                        return;
                    }
                    showUpdateAvailable(currentActivity, finalLatestUpdate);
                } catch (Exception e) {
                    Log.w("UpdateHelper", "Unable to compare update versions.", e);
                    if (isManual) {
                        showCheckFailed(currentActivity);
                    }
                }
            });
        }, "MacIndex-UpdateCheck").start();
    }

    private static UpdateInformation getLatestUpdate() throws Exception {
        Exception websiteError;
        try {
            return getWebsiteUpdate();
        } catch (Exception e) {
            websiteError = e;
            Log.w("UpdateHelper", "Website update API unavailable, trying GitHub.", e);
        }

        try {
            return getGitHubUpdate();
        } catch (Exception e) {
            e.addSuppressed(websiteError);
            throw e;
        }
    }

    private static UpdateInformation getWebsiteUpdate() throws Exception {
        final JSONObject response = getResponse(WEBSITE_API, false);
        return new UpdateInformation(
                normalizeVersion(response.getString("version")),
                normalizeReleasePage(response.getString("releasePage"),
                        "macindex.paizhang.info", "/"));
    }

    private static UpdateInformation getGitHubUpdate() throws Exception {
        final JSONObject response = getResponse(GITHUB_API, true);
        return new UpdateInformation(
                normalizeVersion(response.getString("tag_name")),
                normalizeReleasePage(response.getString("html_url"),
                        "github.com", "/paizhangliu/MacIndex/releases"));
    }

    private static JSONObject getResponse(final String requestURL,
                                          final boolean isGitHub) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(requestURL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", "MacIndex-Android");
            if (isGitHub) {
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            } else {
                connection.setRequestProperty("Accept", "application/json");
            }

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
            return new JSONObject(response.toString());
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

    static String normalizeReleasePage(final String rawPage,
                                       final String expectedHost,
                                       final String expectedPath) throws Exception {
        if (rawPage == null) {
            throw new IllegalArgumentException();
        }
        final String releasePage = rawPage.trim();
        final URL parsedPage = new URL(releasePage);
        if (!parsedPage.getProtocol().equals("https")
                || !parsedPage.getHost().equalsIgnoreCase(expectedHost)
                || !parsedPage.getPath().startsWith(expectedPath)
                || parsedPage.getPort() != -1) {
            throw new IllegalArgumentException();
        }
        return releasePage;
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
                                            final UpdateInformation latestUpdate) {
        final AlertDialog.Builder updateDialog = new AlertDialog.Builder(thisActivity);
        updateDialog.setTitle(R.string.update_available);
        updateDialog.setMessage(thisActivity.getString(R.string.update_available_message,
                latestUpdate.version, BuildConfig.VERSION_NAME));
        updateDialog.setPositiveButton(R.string.update_download, (dialog, which) ->
                LinkLoadingHelper.startBrowser(latestUpdate.releasePage, thisActivity));
        updateDialog.setNeutralButton(R.string.update_skip, (dialog, which) ->
                PrefsHelper.editPrefs("skippedUpdateVersion", latestUpdate.version, thisActivity));
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

    private static class UpdateProgressDialog extends ProgressDialog
            implements DefaultLifecycleObserver {

        private LifecycleOwner owner;

        private UpdateProgressDialog(final AppCompatActivity activity) {
            super(activity);
            owner = activity;
            owner.getLifecycle().addObserver(this);
        }

        @Override
        public void onDestroy(@NonNull final LifecycleOwner lifecycleOwner) {
            finish();
        }

        private void finish() {
            if (isShowing()) {
                dismiss();
            }
            if (owner != null) {
                owner.getLifecycle().removeObserver(this);
                owner = null;
            }
        }
    }

    private static class UpdateInformation {

        private final String version;

        private final String releasePage;

        private UpdateInformation(final String version, final String releasePage) {
            this.version = version;
            this.releasePage = releasePage;
        }
    }
}
