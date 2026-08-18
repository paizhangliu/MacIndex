package com.macindex.macindex;

import android.app.Activity;

import com.macindex.macindex.userstate.PendingUserNotice;
import com.macindex.macindex.userstate.RemovedContentKind;
import com.macindex.macindex.userstate.RemovedUserContent;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

import java.util.ArrayList;
import java.util.List;

/** Presents and acknowledges the single durable notice owned by Proto user state. */
final class PendingNoticePresenter {

    private PendingNoticePresenter() {
    }

    static void show(final Activity activity,
                     final PendingUserNotice notice,
                     final UserStateLifecycleAdapter stateAdapter) {
        final List<String> comments = new ArrayList<>();
        final List<String> favourites = new ArrayList<>();
        final List<String> compares = new ArrayList<>();
        for (RemovedUserContent content : notice.getRemovedContent()) {
            if (content.getKind() == RemovedContentKind.COMMENT) {
                comments.add(content.getValue());
            } else if (content.getKind() == RemovedContentKind.FAVOURITE) {
                favourites.add(content.getValue());
            } else if (content.getKind() == RemovedContentKind.COMPARE) {
                compares.add(content.getValue());
            }
        }
        final StringBuilder report = new StringBuilder("MacIndex ")
                .append(BuildConfig.VERSION_NAME).append("\n\n");
        if (notice.getEntireUserStateWasReset()) {
            report.append(activity.getString(R.string.upgrade_report_reset_entry));
        }
        append(report, activity.getString(R.string.menu_comment), comments, activity);
        append(report, activity.getString(R.string.menu_favourite), favourites, activity);
        append(report, activity.getString(R.string.menu_compare), compares, activity);
        ExceptionHelper.showUpgradeReport(activity, report.toString().trim(),
                () -> stateAdapter.execute(
                        UserStateCommands.acknowledgePendingNotice(),
                        ignored -> { },
                        error -> ExceptionHelper.showUserStateWriteFailure(activity, error,
                                R.string.upgrade_report_title,
                                R.string.upgrade_report_acknowledge_failed)));
    }

    private static void append(final StringBuilder report,
                               final String title,
                               final List<String> values,
                               final Activity activity) {
        if (values.isEmpty()) {
            return;
        }
        if (report.charAt(report.length() - 1) != '\n') {
            report.append("\n\n");
        }
        report.append(title).append(":\n");
        for (String value : values) {
            report.append("- ").append(value.isEmpty()
                    ? activity.getString(R.string.upgrade_report_empty_entry) : value)
                    .append('\n');
        }
    }
}
