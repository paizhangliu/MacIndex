package com.macindex.macindex;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * MacIndex Window Insets Helper
 * Adapts the original layouts to the enforced edge-to-edge mode.
 */
class WindowInsetsHelper {

    static void apply(final Activity activity) {
        WindowCompat.enableEdgeToEdge(activity.getWindow());
        final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);

        // Preserve horizontal and bottom padding. The framework content
        // container's top padding is owned by AppCompat and can already contain
        // the ActionBar height, so it must not be folded into our calculation.
        final View content = activity.findViewById(android.R.id.content);
        final int initialLeft = content.getPaddingLeft();
        final int initialRight = content.getPaddingRight();
        final int initialBottom = content.getPaddingBottom();

        // Android 15+ makes the status bar transparent. Draw the app-bar color
        // behind it so the status bar and ActionBar remain one continuous bar.
        final View statusBarProtection = new View(activity);
        statusBarProtection.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorPrimary));
        statusBarProtection.setClickable(false);
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        // Keep the protection above the AppCompat decor. It ends exactly at
        // the status bar inset, so it masks the ActionBar's upward shadow but
        // can never cover its navigation, title, or menu views.
        decor.addView(statusBarProtection, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(decor);
            final Insets statusBar = (rootInsets == null ? windowInsets : rootInsets).getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            final FrameLayout.LayoutParams protectionParams =
                    (FrameLayout.LayoutParams) statusBarProtection.getLayoutParams();
            if (protectionParams.height != statusBar.top) {
                protectionParams.height = statusBar.top;
                statusBarProtection.setLayoutParams(protectionParams);
            }
            view.setPadding(initialLeft + bars.left,
                    bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
