package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateCheckerTest {

    @Test
    public void websiteResultIsValidatedAndCompared() {
        final UpdateChecker checker = new UpdateChecker(Runnable::run, (url, github) ->
                "{\"version\":\"v4.10.0\","
                        + "\"releasePage\":\"https://macindex.paizhang.info/downloads\"}");

        final Outcome outcome = run(checker, "4.9.1");

        assertNull(outcome.error);
        assertTrue(outcome.result.isUpdateAvailable());
        assertEquals("4.10.0", outcome.result.getLatest().getVersion());
    }

    @Test
    public void githubIsUsedWhenWebsiteFails() {
        final UpdateChecker checker = new UpdateChecker(Runnable::run, (url, github) -> {
            if (!github) {
                throw new IOException("website unavailable");
            }
            return "{\"tag_name\":\"v4.10.0\","
                    + "\"html_url\":\"https://github.com/paizhangliu/MacIndex/"
                    + "releases/tag/v4.10.0\"}";
        });

        final Outcome outcome = run(checker, "4.10.0");

        assertNull(outcome.error);
        assertFalse(outcome.result.isUpdateAvailable());
    }

    @Test
    public void bothFailuresPreserveBothCauses() {
        final UpdateChecker checker = new UpdateChecker(Runnable::run, (url, github) -> {
            throw new IOException(github ? "github" : "website");
        });

        final Outcome outcome = run(checker, "4.10.0");

        assertNull(outcome.result);
        assertNotNull(outcome.error);
        assertTrue(outcome.error instanceof UpdateChecker.UpdateUnavailableException);
        assertEquals("github", outcome.error.getCause().getMessage());
        assertEquals(1, outcome.error.getSuppressed().length);
        assertEquals("website", outcome.error.getSuppressed()[0].getCause().getMessage());
    }

    @Test(expected = NullPointerException.class)
    public void unexpectedTransportFailureIsNotMislabeledAsNetworkFailure() {
        final UpdateChecker checker = new UpdateChecker(Runnable::run, (url, github) -> {
            throw new NullPointerException("bug");
        });

        checker.check("4.10.0", (result, error) -> { });
    }

    @Test
    public void versionAndReleaseValidationRejectAmbiguity() throws Exception {
        assertTrue(UpdateChecker.compareVersions("4.10.0", "4.9.9") > 0);
        assertEquals("4.10.0", UpdateChecker.normalizeVersion(" v4.10.0 "));
        assertEquals("https://github.com/paizhangliu/MacIndex/releases/tag/v4.10.0",
                UpdateChecker.normalizeReleasePage(
                        "https://github.com/paizhangliu/MacIndex/releases/tag/v4.10.0",
                        "github.com", "/paizhangliu/MacIndex/releases"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void versionValidationRejectsPrerelease() {
        UpdateChecker.normalizeVersion("v4.10.0-beta");
    }

    @Test(expected = IllegalArgumentException.class)
    public void releaseValidationRejectsPathPrefixLookalike() throws Exception {
        UpdateChecker.normalizeReleasePage(
                "https://github.com/paizhangliu/MacIndex/releases-malicious/tag/v4.10.0",
                "github.com", "/paizhangliu/MacIndex/releases");
    }

    private static Outcome run(final UpdateChecker checker, final String currentVersion) {
        final AtomicReference<UpdateChecker.Result> result = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();
        checker.check(currentVersion, (thisResult, thisError) -> {
            result.set(thisResult);
            error.set(thisError);
        });
        return new Outcome(result.get(), error.get());
    }

    private static final class Outcome {
        private final UpdateChecker.Result result;
        private final Exception error;

        private Outcome(final UpdateChecker.Result thisResult, final Exception thisError) {
            result = thisResult;
            error = thisError;
        }
    }
}
