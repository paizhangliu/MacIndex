package com.macindex.macindex;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.insets.ColorProtection;
import androidx.core.view.insets.Protection;
import androidx.core.view.insets.ProtectionLayout;

import java.util.Collections;

/**
 * Applies the shared system-bar appearance to every Activity whose theme opts in.
 * It deliberately does not change Activity content padding or decor fitting.
 */
final class SystemBarController implements Application.ActivityLifecycleCallbacks {

    private SystemBarController() {
    }

    static void install(final Application application) {
        application.registerActivityLifecycleCallbacks(new SystemBarController());
    }

    @Override
    public void onActivityCreated(@NonNull final Activity activity,
                                  @Nullable final Bundle savedInstanceState) {
        if (usesMacIndexSystemBars(activity)) {
            apply(activity);
        }
    }

    private static boolean usesMacIndexSystemBars(final Activity activity) {
        final TypedValue value = new TypedValue();
        return activity.getTheme().resolveAttribute(
                R.attr.macIndexSystemBars, value, true)
                && value.type == TypedValue.TYPE_INT_BOOLEAN
                && value.data != 0;
    }

    @SuppressWarnings("deprecation")
    private static void apply(final Activity activity) {
        final Window window = activity.getWindow();
        final View decor = window.getDecorView();
        final WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decor);
        final boolean isNightMode = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        controller.setAppearanceLightStatusBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 8 and 9 support dark navigation buttons, but an Activity
            // that does not opt into edge-to-edge can still inherit the
            // platform's black navigation bar. Own both sides of the contrast
            // pair on these versions instead of assuming a transparent bar.
            window.setNavigationBarColor(resolveThemeColor(
                    activity, android.R.attr.colorBackground, R.color.colorBackground));
            controller.setAppearanceLightNavigationBars(!isNightMode);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            controller.setAppearanceLightNavigationBars(!isNightMode);
        } else {
            // Android 6 and 7 cannot draw dark navigation buttons.
            window.setNavigationBarColor(Color.BLACK);
            controller.setAppearanceLightNavigationBars(false);
        }

        final int statusBarColor = resolveThemeColor(
                activity, androidx.appcompat.R.attr.colorPrimary, R.color.colorPrimary);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            installStatusBarProtection(activity, statusBarColor);
        } else {
            window.setStatusBarColor(statusBarColor);
        }
        ViewCompat.requestApplyInsets(decor);
    }

    private static int resolveThemeColor(final Activity activity,
                                         final int attribute,
                                         final int fallbackColor) {
        final TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(activity, value.resourceId);
            }
            return value.data;
        }
        return ContextCompat.getColor(activity, fallbackColor);
    }

    private static void installStatusBarProtection(final Activity activity,
                                                   final int color) {
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        final Protection protection = new ColorProtection(WindowInsetsCompat.Side.TOP, color);
        final View existing = decor.findViewById(R.id.macindex_status_bar_protection);
        if (existing instanceof ProtectionLayout) {
            ((ProtectionLayout) existing).setProtections(
                    Collections.singletonList(protection));
            return;
        }

        final ProtectionLayout layout = new ProtectionLayout(
                activity, Collections.singletonList(protection));
        layout.setId(R.id.macindex_status_bar_protection);
        layout.setClickable(false);
        layout.setFocusable(false);
        layout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        decor.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        // Activity.onCreate() dispatches onActivityCreated() from inside the
        // Activity's super call. Activities and libraries may enable
        // edge-to-edge after that callback returns, so reapply the final chrome
        // once their complete onCreate() has finished.
        if (usesMacIndexSystemBars(activity)) {
            apply(activity);
        }
    }

    @Override
    public void onActivityResumed(@NonNull final Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull final Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull final Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity,
                                            @NonNull final Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull final Activity activity) {
    }
}
