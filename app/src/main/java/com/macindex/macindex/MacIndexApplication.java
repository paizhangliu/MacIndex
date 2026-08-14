package com.macindex.macindex;

import android.app.Application;

public class MacIndexApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applySaved(this);
    }
}
