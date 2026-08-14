package com.macindex.macindex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MacIndex User Record Upgrade Helper
 * Imports the original SharedPreferences formats and audits current JSON records.
 */
class UserRecordUpgradeHelper {

    static class UpgradeResult {
        final String value;
        final List<String> removed;

        UpgradeResult(final String upgradedValue, final List<String> removedValues) {
            value = upgradedValue;
            removed = removedValues;
        }
    }

    static UpgradeResult upgradeComments(final String raw,
                                         final MachineHelper machineHelper,
                                         final Map<String, String> oldMachineNames) {
        final List<UserCommentHelper.Comment> comments;
        final List<String> removed = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return new UpgradeResult(UserCommentHelper.EMPTY_JSON, removed);
        }
        if (raw.trim().startsWith("{")) {
            try {
                comments = UserCommentHelper.parse(raw);
            } catch (Exception e) {
                removed.add(raw);
                return new UpgradeResult(UserCommentHelper.EMPTY_JSON, removed);
            }
        } else {
            comments = importLegacyComments(raw, oldMachineNames, removed);
        }

        final List<UserCommentHelper.Comment> upgraded = new ArrayList<>();
        final Set<String> addedUIDs = new HashSet<>();
        for (UserCommentHelper.Comment comment : comments) {
            final String resolvedUID = machineHelper.resolveUID(comment.machineUID);
            if (resolvedUID == null || !addedUIDs.add(resolvedUID)) {
                removed.add(getIdentityName(comment.machineUID, machineHelper)
                        + "│" + comment.text);
                continue;
            }
            upgraded.add(new UserCommentHelper.Comment(resolvedUID, comment.text));
        }
        return new UpgradeResult(UserCommentHelper.serialize(upgraded), removed);
    }

    static UpgradeResult upgradeFavourites(final String raw,
                                           final MachineHelper machineHelper,
                                           final Map<String, String> oldMachineNames) {
        final List<UserFavouriteHelper.Folder> folders;
        final List<String> removed = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return new UpgradeResult(UserFavouriteHelper.EMPTY_JSON, removed);
        }
        if (raw.trim().startsWith("{")) {
            try {
                folders = UserFavouriteHelper.parse(raw);
            } catch (Exception e) {
                removed.add(raw);
                return new UpgradeResult(UserFavouriteHelper.EMPTY_JSON, removed);
            }
        } else {
            folders = importLegacyFavourites(raw, oldMachineNames, removed);
        }

        final List<UserFavouriteHelper.Folder> upgraded = new ArrayList<>();
        for (UserFavouriteHelper.Folder folder : folders) {
            final List<String> machineUIDs = new ArrayList<>();
            final Set<String> addedUIDs = new HashSet<>();
            for (String machineUID : folder.machineUIDs) {
                final String resolvedUID = machineHelper.resolveUID(machineUID);
                if (resolvedUID == null || !addedUIDs.add(resolvedUID)) {
                    removed.add("{" + folder.name + "}│["
                            + getIdentityName(machineUID, machineHelper) + "]");
                    continue;
                }
                machineUIDs.add(resolvedUID);
            }
            upgraded.add(new UserFavouriteHelper.Folder(folder.name, machineUIDs));
        }
        return new UpgradeResult(UserFavouriteHelper.serialize(upgraded), removed);
    }

    static UpgradeResult upgradeCompares(final String rawJSON, final String rawList,
                                         final String rawLeft, final String rawRight,
                                         final MachineHelper machineHelper,
                                         final Map<String, String> oldMachineNames) {
        final UserCompareHelper.State state;
        final List<String> removed = new ArrayList<>();
        if (rawJSON != null && !rawJSON.isEmpty()) {
            try {
                state = UserCompareHelper.parse(rawJSON);
            } catch (Exception e) {
                removed.add(rawJSON);
                return new UpgradeResult(UserCompareHelper.EMPTY_JSON, removed);
            }
        } else {
            state = importLegacyCompares(rawList, rawLeft, rawRight, oldMachineNames, removed);
        }

        final List<String> upgradedUIDs = new ArrayList<>();
        final Set<String> addedUIDs = new HashSet<>();
        for (String machineUID : state.machineUIDs) {
            final String resolvedUID = machineHelper.resolveUID(machineUID);
            if (resolvedUID == null || !addedUIDs.add(resolvedUID)
                    || upgradedUIDs.size() >= 10) {
                removed.add("[" + getIdentityName(machineUID, machineHelper) + "]");
                continue;
            }
            upgradedUIDs.add(resolvedUID);
        }
        final String leftUID = machineHelper.resolveUID(state.leftUID);
        final String rightUID = machineHelper.resolveUID(state.rightUID);
        final UserCompareHelper.State upgraded = new UserCompareHelper.State(
                upgradedUIDs, leftUID == null ? "" : leftUID,
                rightUID == null ? "" : rightUID);
        if (upgraded.leftUID.equals(upgraded.rightUID)
                || !upgraded.machineUIDs.contains(upgraded.leftUID)
                || !upgraded.machineUIDs.contains(upgraded.rightUID)) {
            if (!state.leftUID.isEmpty() || !state.rightUID.isEmpty()) {
                removed.add("[" + getIdentityName(state.leftUID, machineHelper)
                        + "]│[" + getIdentityName(state.rightUID, machineHelper) + "]");
            }
            UserCompareHelper.clearSelection(upgraded);
        }
        return new UpgradeResult(UserCompareHelper.serialize(upgraded), removed);
    }

    private static List<UserCommentHelper.Comment> importLegacyComments(
            final String raw, final Map<String, String> oldMachineNames,
            final List<String> removed) {
        final List<UserCommentHelper.Comment> comments = new ArrayList<>();
        final Set<String> addedUIDs = new HashSet<>();
        for (String entry : raw.split("││", -1)) {
            final String[] parts = entry.split("│", -1);
            final String comment = parts.length == 2 ? parts[1].trim() : "";
            final String machineUID = parts.length == 2
                    ? resolveOldMachineName(parts[0], oldMachineNames) : null;
            if (machineUID == null || comment.isEmpty() || comment.length() > 500
                    || !addedUIDs.add(machineUID)) {
                removed.add(entry);
                continue;
            }
            comments.add(new UserCommentHelper.Comment(machineUID, comment));
        }
        return comments;
    }

    private static List<UserFavouriteHelper.Folder> importLegacyFavourites(
            final String raw, final Map<String, String> oldMachineNames,
            final List<String> removed) {
        final List<UserFavouriteHelper.Folder> folders = new ArrayList<>();
        final Set<String> addedFolders = new HashSet<>();
        final String[] rawFolders = raw.split("││", -1);
        if (!rawFolders[0].isEmpty()) {
            removed.add(rawFolders[0]);
        }
        for (int i = 1; i < rawFolders.length; i++) {
            if (folders.size() >= 15) {
                if (!rawFolders[i].isEmpty()) {
                    removed.add(rawFolders[i]);
                }
                continue;
            }
            final String[] entries = rawFolders[i].split("│", -1);
            if (entries.length == 0 || entries[0].length() < 2
                    || !entries[0].startsWith("{") || !entries[0].endsWith("}")) {
                removed.add(rawFolders[i]);
                continue;
            }
            final String folderName = entries[0].substring(1, entries[0].length() - 1).trim();
            if (folderName.isEmpty() || folderName.length() > 30 || folderName.contains("\n")
                    || !addedFolders.add(folderName)) {
                removed.add(rawFolders[i]);
                continue;
            }
            final List<String> machineUIDs = new ArrayList<>();
            final Set<String> addedUIDs = new HashSet<>();
            for (int j = 1; j < entries.length; j++) {
                final String entry = entries[j];
                final String machineUID = entry.length() >= 3 && entry.startsWith("[")
                        && entry.endsWith("]")
                        ? resolveOldMachineName(
                        entry.substring(1, entry.length() - 1), oldMachineNames) : null;
                if (machineUID == null || !addedUIDs.add(machineUID)) {
                    removed.add(entries[0] + "│" + entry);
                    continue;
                }
                machineUIDs.add(machineUID);
            }
            folders.add(new UserFavouriteHelper.Folder(folderName, machineUIDs));
        }
        return folders;
    }

    private static UserCompareHelper.State importLegacyCompares(
            final String rawList, final String rawLeft, final String rawRight,
            final Map<String, String> oldMachineNames, final List<String> removed) {
        final List<String> machineUIDs = new ArrayList<>();
        final Set<String> addedUIDs = new HashSet<>();
        if (rawList != null && !rawList.isEmpty()) {
            for (String entry : rawList.split("│", -1)) {
                final String machineUID = entry.length() >= 3 && entry.startsWith("[")
                        && entry.endsWith("]")
                        ? resolveOldMachineName(
                        entry.substring(1, entry.length() - 1), oldMachineNames) : null;
                if (machineUID == null || !addedUIDs.add(machineUID)
                        || machineUIDs.size() >= 10) {
                    removed.add(entry);
                    continue;
                }
                machineUIDs.add(machineUID);
            }
        }
        final String leftUID = resolveOldMachineName(rawLeft, oldMachineNames);
        final String rightUID = resolveOldMachineName(rawRight, oldMachineNames);
        final UserCompareHelper.State state = new UserCompareHelper.State(machineUIDs,
                leftUID == null ? "" : leftUID, rightUID == null ? "" : rightUID);
        if (state.leftUID.equals(state.rightUID) || !machineUIDs.contains(state.leftUID)
                || !machineUIDs.contains(state.rightUID)) {
            if ((rawLeft != null && !rawLeft.isEmpty())
                    || (rawRight != null && !rawRight.isEmpty())) {
                removed.add("[" + valueOrEmpty(rawLeft) + "]│["
                        + valueOrEmpty(rawRight) + "]");
            }
            UserCompareHelper.clearSelection(state);
        }
        return state;
    }

    private static String resolveOldMachineName(final String machineName,
                                                final Map<String, String> oldMachineNames) {
        return machineName == null ? null
                : oldMachineNames.get(machineName.trim().toLowerCase(Locale.ROOT));
    }

    private static String getIdentityName(final String machineUID,
                                          final MachineHelper machineHelper) {
        final String machineName = machineHelper.getIdentityName(machineUID);
        return machineName == null ? valueOrEmpty(machineUID) : machineName;
    }

    private static String valueOrEmpty(final String value) {
        return value == null ? "" : value;
    }

}
