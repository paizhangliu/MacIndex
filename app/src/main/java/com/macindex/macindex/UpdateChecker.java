package com.macindex.macindex;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Performs one independent update lookup without owning Android UI or lifecycle state. */
final class UpdateChecker {

    private static final String WEBSITE_API =
            "https://macindex.paizhang.info/api/latest.json";
    private static final String GITHUB_API =
            "https://api.github.com/repos/paizhangliu/MacIndex/releases/latest";
    private static final int MAX_RESPONSE_LENGTH = 256 * 1024;

    @FunctionalInterface
    interface Callback {
        void onFinished(Result result, Exception error);
    }

    @FunctionalInterface
    interface Transport {
        String get(String requestUrl, boolean github) throws IOException;
    }

    /** An expected failure of both remote update sources. */
    static final class UpdateUnavailableException extends Exception {
        private UpdateUnavailableException(final String message, final Exception cause) {
            super(message, cause);
        }
    }

    static final class Information {
        private final String version;
        private final String releasePage;

        private Information(final String thisVersion, final String thisReleasePage) {
            version = thisVersion;
            releasePage = thisReleasePage;
        }

        String getVersion() {
            return version;
        }

        String getReleasePage() {
            return releasePage;
        }
    }

    static final class Result {
        private final Information latest;
        private final boolean updateAvailable;

        private Result(final Information thisLatest, final boolean thisUpdateAvailable) {
            latest = thisLatest;
            updateAvailable = thisUpdateAvailable;
        }

        Information getLatest() {
            return latest;
        }

        boolean isUpdateAvailable() {
            return updateAvailable;
        }
    }

    private final Executor executor;
    private final Transport transport;

    UpdateChecker(final Executor thisExecutor) {
        this(thisExecutor, new HttpTransport());
    }

    UpdateChecker(final Executor thisExecutor, final Transport thisTransport) {
        executor = Objects.requireNonNull(thisExecutor);
        transport = Objects.requireNonNull(thisTransport);
    }

    void check(final String currentVersion, final Callback callback) {
        Objects.requireNonNull(currentVersion);
        Objects.requireNonNull(callback);
        executor.execute(() -> {
            try {
                final Information latest = getLatestUpdate();
                callback.onFinished(new Result(latest,
                        compareVersions(latest.version, currentVersion) > 0), null);
            } catch (UpdateUnavailableException expectedFailure) {
                callback.onFinished(null, expectedFailure);
            }
        });
    }

    private Information getLatestUpdate() throws UpdateUnavailableException {
        final UpdateUnavailableException websiteError;
        try {
            final JSONObject response = new JSONObject(transport.get(WEBSITE_API, false));
            return new Information(
                    normalizeVersion(response.getString("version")),
                    normalizeReleasePage(response.getString("releasePage"),
                            "macindex.paizhang.info", "/"));
        } catch (IOException | JSONException | URISyntaxException | IllegalArgumentException e) {
            websiteError = new UpdateUnavailableException(
                    "The MacIndex update service is unavailable.", e);
        }

        try {
            final JSONObject response = new JSONObject(transport.get(GITHUB_API, true));
            return new Information(
                    normalizeVersion(response.getString("tag_name")),
                    normalizeReleasePage(response.getString("html_url"),
                            "github.com", "/paizhangliu/MacIndex/releases"));
        } catch (IOException | JSONException | URISyntaxException | IllegalArgumentException e) {
            final UpdateUnavailableException unavailable = new UpdateUnavailableException(
                    "Unable to retrieve update information.", e);
            unavailable.addSuppressed(websiteError);
            throw unavailable;
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
            throw new IllegalArgumentException("Missing version");
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
                                       final String expectedPath) throws URISyntaxException {
        if (rawPage == null) {
            throw new IllegalArgumentException("Missing release page");
        }
        final String releasePage = rawPage.trim();
        final URI parsedPage = new URI(releasePage);
        final String path = parsedPage.getPath();
        final boolean acceptedPath = "/".equals(expectedPath)
                ? path != null && path.startsWith("/")
                : expectedPath.equals(path)
                || (path != null && path.startsWith(expectedPath + "/"));
        if (!"https".equalsIgnoreCase(parsedPage.getScheme())
                || parsedPage.getHost() == null
                || !parsedPage.getHost().equalsIgnoreCase(expectedHost)
                || parsedPage.getUserInfo() != null
                || parsedPage.getPort() != -1
                || !acceptedPath) {
            throw new IllegalArgumentException("Unexpected release page");
        }
        return releasePage;
    }

    private static int[] parseVersion(final String version) {
        if (version == null) {
            throw new IllegalArgumentException("Missing version");
        }
        final String[] parts = version.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Version must contain three parts");
        }
        final int[] parsed = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d+")) {
                throw new IllegalArgumentException("Version parts must be numeric");
            }
            try {
                parsed[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Version part is too large", e);
            }
        }
        return parsed;
    }

    private static final class HttpTransport implements Transport {
        @Override
        public String get(final String requestUrl, final boolean github) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(requestUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", "MacIndex-Android");
                connection.setRequestProperty("Accept",
                        github ? "application/vnd.github+json" : "application/json");
                if (github) {
                    connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
                }
                final int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Unexpected HTTP response " + responseCode);
                }
                final StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                        if (response.length() > MAX_RESPONSE_LENGTH) {
                            throw new IOException("Update response is too large");
                        }
                    }
                }
                return response.toString();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
