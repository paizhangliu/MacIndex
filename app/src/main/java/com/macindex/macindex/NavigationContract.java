package com.macindex.macindex;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.macindex.macindex.catalog.Machine;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Owns the Intent protocol used by the current multi-Activity UI.
 *
 * <p>Machine destinations contain stable UIDs only. Runtime catalog indexes must never cross an
 * Activity boundary. The extra values deliberately retain their existing spellings so the UI can
 * move to this contract without creating a second compatibility protocol.</p>
 */
final class NavigationContract {

    private static final Pattern MACHINE_UID = Pattern.compile("MI\\d{6}");

    static final String ACTION_OPEN_SEARCH =
            "com.macindex.macindex.action.OPEN_SEARCH";
    static final String ACTION_OPEN_RANDOM =
            "com.macindex.macindex.action.OPEN_RANDOM";
    static final String ACTION_OPEN_FAVOURITES =
            "com.macindex.macindex.action.OPEN_FAVOURITES";
    static final String ACTION_OPEN_COMMENTS =
            "com.macindex.macindex.action.OPEN_COMMENTS";

    static final String EXTRA_MACHINE_UID = "machineUID";
    static final String EXTRA_NAVIGATION_UIDS = "navigationUIDs";
    static final String EXTRA_FORCE_NAVIGATION_BUTTONS = "forceNavigationButtons";
    static final String EXTRA_COMPARE_LEFT_UID = "compareLeft";
    static final String EXTRA_COMPARE_RIGHT_UID = "compareRight";

    private NavigationContract() {
    }

    @NonNull
    static Intent machineSpecsIntent(@NonNull final Context context,
                                     @NonNull final MachineRequest request) {
        return request.putInto(new Intent(context, SpecsActivity.class));
    }

    @NonNull
    static Intent machineImageIntent(@NonNull final Context context,
                                     @NonNull final String machineUID) {
        return new Intent(context, ViewImageActivity.class)
                .putExtra(EXTRA_MACHINE_UID, requireUID(machineUID, "machine"));
    }

    @NonNull
    static Intent comparisonIntent(@NonNull final Context context,
                                   @NonNull final ComparisonRequest request) {
        return request.putInto(new Intent(context, CompareActivity.class));
    }

    @Nullable
    static ShortcutDestination getShortcutDestination(@Nullable final Intent intent) {
        if (intent == null) {
            return null;
        }
        final String action = intent.getAction();
        if (ACTION_OPEN_SEARCH.equals(action)) {
            return ShortcutDestination.SEARCH;
        }
        if (ACTION_OPEN_RANDOM.equals(action)) {
            return ShortcutDestination.RANDOM;
        }
        if (ACTION_OPEN_FAVOURITES.equals(action)) {
            return ShortcutDestination.FAVOURITES;
        }
        if (ACTION_OPEN_COMMENTS.equals(action)) {
            return ShortcutDestination.COMMENTS;
        }
        return null;
    }

    enum ShortcutDestination {
        SEARCH,
        RANDOM,
        FAVOURITES,
        COMMENTS
    }

    static final class MachineRequest {

        private final String machineUID;
        private final String[] navigationUIDs;
        private final boolean forceNavigationButtons;

        private MachineRequest(@NonNull final String thisMachineUID,
                               @NonNull final String[] thisNavigationUIDs,
                               final boolean shouldForceNavigationButtons) {
            machineUID = requireUID(thisMachineUID, "machine");
            navigationUIDs = copyUIDs(thisNavigationUIDs);
            forceNavigationButtons = shouldForceNavigationButtons;

            boolean containsMachine = false;
            for (String navigationUID : navigationUIDs) {
                if (machineUID.equals(navigationUID)) {
                    containsMachine = true;
                    break;
                }
            }
            if (!containsMachine) {
                throw new IllegalArgumentException(
                        "Navigation UIDs do not contain the selected machine");
            }
        }

        @NonNull
        static MachineRequest create(@NonNull final String machineUID,
                                     @NonNull final String[] navigationUIDs,
                                     final boolean forceNavigationButtons) {
            return new MachineRequest(machineUID, navigationUIDs, forceNavigationButtons);
        }

        @NonNull
        static MachineRequest create(@NonNull final Machine machine,
                                     @NonNull final List<Machine> navigation,
                                     final boolean forceNavigationButtons) {
            final String[] navigationUIDs = new String[navigation.size()];
            for (int index = 0; index < navigation.size(); index++) {
                navigationUIDs[index] = navigation.get(index).uid();
            }
            return new MachineRequest(machine.uid(), navigationUIDs, forceNavigationButtons);
        }

        @NonNull
        static MachineRequest from(@NonNull final Intent intent) {
            final String machineUID = intent.getStringExtra(EXTRA_MACHINE_UID);
            final String[] navigationUIDs = intent.getStringArrayExtra(EXTRA_NAVIGATION_UIDS);
            if (machineUID == null || navigationUIDs == null) {
                throw new IllegalArgumentException("Incomplete machine navigation request");
            }
            return new MachineRequest(machineUID, navigationUIDs,
                    intent.getBooleanExtra(EXTRA_FORCE_NAVIGATION_BUTTONS, false));
        }

        @NonNull
        Intent putInto(@NonNull final Intent intent) {
            return intent.putExtra(EXTRA_MACHINE_UID, machineUID)
                    .putExtra(EXTRA_NAVIGATION_UIDS, navigationUIDs.clone())
                    .putExtra(EXTRA_FORCE_NAVIGATION_BUTTONS, forceNavigationButtons);
        }

        @NonNull
        String getMachineUID() {
            return machineUID;
        }

        @NonNull
        String[] getNavigationUIDs() {
            return navigationUIDs.clone();
        }

        boolean shouldForceNavigationButtons() {
            return forceNavigationButtons;
        }

    }

    static final class ComparisonRequest {

        private final String leftUID;
        private final String rightUID;

        private ComparisonRequest(@NonNull final String thisLeftUID,
                                  @NonNull final String thisRightUID) {
            leftUID = requireUID(thisLeftUID, "left comparison machine");
            rightUID = requireUID(thisRightUID, "right comparison machine");
            if (leftUID.equals(rightUID)) {
                throw new IllegalArgumentException("A machine cannot be compared with itself");
            }
        }

        @NonNull
        static ComparisonRequest create(@NonNull final String leftUID,
                                        @NonNull final String rightUID) {
            return new ComparisonRequest(leftUID, rightUID);
        }

        @Nullable
        static ComparisonRequest from(@Nullable final Intent intent) {
            if (intent == null) {
                return null;
            }
            final String leftUID = intent.getStringExtra(EXTRA_COMPARE_LEFT_UID);
            final String rightUID = intent.getStringExtra(EXTRA_COMPARE_RIGHT_UID);
            if (leftUID == null && rightUID == null) {
                return null;
            }
            if (leftUID == null || rightUID == null) {
                throw new IllegalArgumentException("Incomplete comparison navigation request");
            }
            return new ComparisonRequest(leftUID, rightUID);
        }

        @NonNull
        Intent putInto(@NonNull final Intent intent) {
            return intent.putExtra(EXTRA_COMPARE_LEFT_UID, leftUID)
                    .putExtra(EXTRA_COMPARE_RIGHT_UID, rightUID);
        }

        @NonNull
        String getLeftUID() {
            return leftUID;
        }

        @NonNull
        String getRightUID() {
            return rightUID;
        }
    }

    @NonNull
    private static String[] copyUIDs(@NonNull final String[] machineUIDs) {
        if (machineUIDs.length == 0) {
            throw new IllegalArgumentException("Navigation UIDs are empty");
        }
        final String[] copiedUIDs = machineUIDs.clone();
        for (int i = 0; i < copiedUIDs.length; i++) {
            copiedUIDs[i] = requireUID(copiedUIDs[i], "navigation machine");
        }
        return copiedUIDs;
    }

    @NonNull
    private static String requireUID(@Nullable final String machineUID,
                                     @NonNull final String label) {
        if (machineUID == null || !MACHINE_UID.matcher(machineUID).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " UID");
        }
        return machineUID;
    }
}
