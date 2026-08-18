package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.macindex.macindex.startup.AppStartupState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Device-level application startup, external-navigation, and update contracts. */
@RunWith(AndroidJUnit4.class)
public final class ApplicationFoundationInstrumentationTest {

    @Test
    public void applicationPublishesOneReadyFoundation() throws Exception {
        final MacIndexApplication application = (MacIndexApplication)
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getApplicationContext();
        final CountDownLatch terminal = new CountDownLatch(1);
        final AtomicReference<AppStartupState> result = new AtomicReference<>();
        final Observer<AppStartupState> observer = state -> {
            if (state instanceof AppStartupState.Ready
                    || state instanceof AppStartupState.Fatal) {
                result.set(state);
                terminal.countDown();
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                application.startup().getState().observeForever(observer));
        try {
            assertTrue("AppStartup did not finish", terminal.await(10, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    application.startup().getState().removeObserver(observer));
        }

        final AppStartupState state = result.get();
        assertNotNull(state);
        assertTrue(state instanceof AppStartupState.Ready);
        final AppStartupState.Ready ready = (AppStartupState.Ready) state;
        assertFalse(ready.getCatalog().machines().isEmpty());
        assertNotNull(ready.getUserStateRepository());
    }

    @Test
    public void newExternalIntentOverridesRestoredConsumedMarker() {
        final Intent ordinary = new Intent(Intent.ACTION_MAIN);
        assertTrue(MainActivity.restoreExternalRequestConsumed(true, ordinary));
        assertFalse(MainActivity.restoreExternalRequestConsumed(false, ordinary));

        final Intent shortcut = new Intent(NavigationContract.ACTION_OPEN_FAVOURITES);
        assertFalse(MainActivity.restoreExternalRequestConsumed(true, shortcut));

        final Intent searchShortcut = new Intent(NavigationContract.ACTION_OPEN_SEARCH);
        assertEquals(NavigationContract.ShortcutDestination.SEARCH,
                NavigationContract.getShortcutDestination(searchShortcut));
        assertFalse(MainActivity.restoreExternalRequestConsumed(true, searchShortcut));

        final Intent deepLink = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://macindex.paizhang.info/MI000001"));
        assertFalse(MainActivity.restoreExternalRequestConsumed(true, deepLink));
    }

    @Test
    public void automaticUpdateResultOutlivesAnyMainObserver() {
        final UpdateChecker checker = new UpdateChecker(Runnable::run, (url, github) ->
                "{\"version\":\"99.0.0\","
                        + "\"releasePage\":\"https://macindex.paizhang.info/releases/99\"}");
        final AutomaticUpdateCoordinator coordinator =
                new AutomaticUpdateCoordinator(checker);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                coordinator.checkIfEnabled(true, "5.0.0", ""));

        final UpdateCheckState retained = coordinator.getState().getValue();
        assertNotNull(retained);
        assertEquals(UpdateCheckState.Status.AVAILABLE, retained.getStatus());
        assertEquals("99.0.0", retained.getResult().getLatest().getVersion());
    }

    @Test
    public void unexpectedCrashIsRecordedBeforeDelegation() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File report = new File(context.getCacheDir(),
                "last-crash-test-" + System.nanoTime() + ".txt");
        final Thread.UncaughtExceptionHandler installed =
                Thread.getDefaultUncaughtExceptionHandler();
        final AtomicReference<Thread> delegatedThread = new AtomicReference<>();
        final AtomicReference<Throwable> delegatedFailure = new AtomicReference<>();
        final Thread.UncaughtExceptionHandler delegate = (thread, failure) -> {
            delegatedThread.set(thread);
            delegatedFailure.set(failure);
        };
        final Thread thread = Thread.currentThread();
        final IllegalStateException failure = new IllegalStateException("recorded failure");
        try {
            Thread.setDefaultUncaughtExceptionHandler(delegate);
            new LastCrashReport(report).install();
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, failure);

            assertSame(thread, delegatedThread.get());
            assertSame(failure, delegatedFailure.get());
            assertTrue(readUtf8(report).contains(
                    "java.lang.IllegalStateException: recorded failure"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(installed);
            if (report.exists()) {
                assertTrue(report.delete());
            }
        }
    }

    @Test
    public void crashStillDelegatesWhenRecordingFails() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File directory = new File(context.getCacheDir(),
                "last-crash-directory-" + System.nanoTime());
        assertTrue(directory.mkdir());
        final Thread.UncaughtExceptionHandler installed =
                Thread.getDefaultUncaughtExceptionHandler();
        final AtomicReference<Throwable> delegatedFailure = new AtomicReference<>();
        final Thread.UncaughtExceptionHandler delegate = (thread, failure) ->
                delegatedFailure.set(failure);
        final IllegalStateException failure = new IllegalStateException("delegate anyway");
        try {
            Thread.setDefaultUncaughtExceptionHandler(delegate);
            new LastCrashReport(directory).install();
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
                    Thread.currentThread(), failure);
            assertSame(failure, delegatedFailure.get());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(installed);
            assertTrue(directory.delete());
        }
    }

    private static String readUtf8(final File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
