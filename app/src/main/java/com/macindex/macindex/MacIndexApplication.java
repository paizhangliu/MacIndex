package com.macindex.macindex;

import android.app.Application;
import android.app.AlertDialog;

import androidx.annotation.NonNull;

import com.macindex.macindex.startup.AppStartup;
import com.macindex.macindex.userstate.ThemeBootstrapStore;

public class MacIndexApplication extends Application {

    private ThemeBootstrapStore themeBootstrapStore;
    private AppStartup startup;
    private VolumeWarningSession volumeWarningSession;
    private AutomaticUpdateCoordinator automaticUpdateCoordinator;
    private LastCrashReport lastCrashReport;

    @Override
    public void onCreate() {
        super.onCreate();
        lastCrashReport = new LastCrashReport(this);
        lastCrashReport.install();
        themeBootstrapStore = new ThemeBootstrapStore(this);
        volumeWarningSession = new VolumeWarningSession();
        automaticUpdateCoordinator = new AutomaticUpdateCoordinator();
        ThemeHelper.apply(themeBootstrapStore.read());
        SystemBarController.install(this);
        startup = new AppStartup(this);
    }

    @NonNull
    public AppStartup startup() {
        return startup;
    }

    @NonNull
    public ThemeBootstrapStore themeBootstrapStore() {
        return themeBootstrapStore;
    }

    @NonNull
    VolumeWarningSession volumeWarningSession() {
        return volumeWarningSession;
    }

    @NonNull
    AutomaticUpdateCoordinator automaticUpdateCoordinator() {
        return automaticUpdateCoordinator;
    }

    AlertDialog presentLastCrashReport(@NonNull final android.app.Activity activity,
                                       @NonNull final Runnable afterAcknowledged) {
        return lastCrashReport.presentIfAvailable(activity, afterAcknowledged);
    }

}
