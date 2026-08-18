package com.macindex.macindex;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Adapts MacIndex-owned layouts to edge-to-edge without changing system-bar chrome.
 */
final class ContentInsetsHelper {

    private ContentInsetsHelper() {
    }

    static void apply(final Activity activity) {
        WindowCompat.enableEdgeToEdge(activity.getWindow());

        final View content = activity.findViewById(android.R.id.content);
        // Preserve horizontal and bottom padding. The framework content
        // container's top padding is owned by AppCompat and can already contain
        // the ActionBar height, so it must not be folded into our calculation.
        final int initialLeft = content.getPaddingLeft();
        final int initialRight = content.getPaddingRight();
        final int initialBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(initialLeft + bars.left,
                    bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
