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
        try {
            if (raw.trim().startsWith("{")) {
                comments = UserCommentHelper.parse(raw);
            } else {
                comments = importLegacyComments(raw, oldMachineNames, removed);
            }
        } catch (UserRecordJsonHelper.InvalidUserRecordException e) {
            return discardRecord(raw, UserCommentHelper.EMPTY_JSON);
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
        try {
            if (raw.trim().startsWith("{")) {
                folders = UserFavouriteHelper.parse(raw);
            } else {
                folders = importLegacyFavourites(raw, oldMachineNames, removed);
            }
        } catch (UserRecordJsonHelper.InvalidUserRecordException e) {
            return discardRecord(raw, UserFavouriteHelper.EMPTY_JSON);
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
        try {
            if (rawJSON != null && !rawJSON.isEmpty()) {
                state = UserCompareHelper.parse(rawJSON);
            } else {
                state = importLegacyCompares(
                        rawList, rawLeft, rawRight, oldMachineNames, removed);
            }
        } catch (UserRecordJsonHelper.InvalidUserRecordException e) {
            final String discarded = rawJSON != null && !rawJSON.isEmpty()
                    ? rawJSON : getLegacyCompareRecord(rawList, rawLeft, rawRight);
            return discardRecord(discarded, UserCompareHelper.EMPTY_JSON);
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
        final Set<String> addedNames = new HashSet<>();
        final Set<String> addedUIDs = new HashSet<>();
        for (String entry : raw.split("││", -1)) {
            final String[] parts = entry.split("│", -1);
            if (parts.length != 2 || parts[0].isEmpty()
                    || !parts[0].equals(parts[0].trim())
                    || parts[1].isEmpty() || !parts[1].equals(parts[1].trim())
                    || parts[1].length() > 500
                    || !addedNames.add(parts[0].toLowerCase(Locale.ROOT))) {
                throw new UserRecordJsonHelper.InvalidUserRecordException(
                        "Illegal legacy comment record");
            }
            final String comment = parts[1];
            final String machineUID = resolveOldMachineName(parts[0], oldMachineNames);
            if (machineUID == null || !addedUIDs.add(machineUID)) {
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
        if (!rawFolders[0].isEmpty() || rawFolders.length > 16) {
            throw new UserRecordJsonHelper.InvalidUserRecordException(
                    "Illegal legacy favourite record");
        }
        for (int i = 1; i < rawFolders.length; i++) {
            final String[] entries = rawFolders[i].split("│", -1);
            if (entries.length == 0 || entries[0].length() < 2
                    || !entries[0].startsWith("{") || !entries[0].endsWith("}")) {
                throw new UserRecordJsonHelper.InvalidUserRecordException(
                        "Illegal legacy favourite record");
            }
            final String rawFolderName = entries[0].substring(1, entries[0].length() - 1);
            final String folderName = rawFolderName.trim();
            if (folderName.isEmpty() || !folderName.equals(rawFolderName)
                    || folderName.length() > 30 || folderName.contains("\n")
                    || !addedFolders.add(folderName)) {
                throw new UserRecordJsonHelper.InvalidUserRecordException(
                        "Illegal legacy favourite record");
            }
            final List<String> machineUIDs = new ArrayList<>();
            final Set<String> addedNames = new HashSet<>();
            final Set<String> addedUIDs = new HashSet<>();
            for (int j = 1; j < entries.length; j++) {
                final String entry = entries[j];
                if (entry.length() < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                    throw new UserRecordJsonHelper.InvalidUserRecordException(
                            "Illegal legacy favourite record");
                }
                final String machineName = entry.substring(1, entry.length() - 1);
                if (machineName.isEmpty() || !machineName.equals(machineName.trim())
                        || !addedNames.add(machineName.toLowerCase(Locale.ROOT))) {
                    throw new UserRecordJsonHelper.InvalidUserRecordException(
                            "Illegal legacy favourite record");
                }
                final String machineUID = resolveOldMachineName(machineName, oldMachineNames);
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
        final Set<String> machineNames = new HashSet<>();
        final Set<String> addedUIDs = new HashSet<>();
        if (rawList != null && !rawList.isEmpty()) {
            final String[] entries = rawList.split("│", -1);
            if (entries.length > 10) {
                throw new UserRecordJsonHelper.InvalidUserRecordException(
                        "Illegal legacy compare record");
            }
            for (String entry : entries) {
                if (entry.length() < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                    throw new UserRecordJsonHelper.InvalidUserRecordException(
                            "Illegal legacy compare record");
                }
                final String machineName = entry.substring(1, entry.length() - 1);
                final String normalizedName = machineName.toLowerCase(Locale.ROOT);
                if (machineName.isEmpty() || !machineName.equals(machineName.trim())
                        || !machineNames.add(normalizedName)) {
                    throw new UserRecordJsonHelper.InvalidUserRecordException(
                            "Illegal legacy compare record");
                }
                final String machineUID = resolveOldMachineName(machineName, oldMachineNames);
                if (machineUID == null || !addedUIDs.add(machineUID)) {
                    removed.add(entry);
                    continue;
                }
                machineUIDs.add(machineUID);
            }
        }
        final String leftName = valueOrEmpty(rawLeft);
        final String rightName = valueOrEmpty(rawRight);
        final boolean emptySelection = leftName.isEmpty() && rightName.isEmpty();
        if (!emptySelection && (leftName.isEmpty() || rightName.isEmpty()
                || !leftName.equals(leftName.trim()) || !rightName.equals(rightName.trim())
                || leftName.equalsIgnoreCase(rightName)
                || !machineNames.contains(leftName.toLowerCase(Locale.ROOT))
                || !machineNames.contains(rightName.toLowerCase(Locale.ROOT)))) {
            throw new UserRecordJsonHelper.InvalidUserRecordException(
                    "Illegal legacy compare record");
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

    private static UpgradeResult discardRecord(final String raw, final String emptyValue) {
        final List<String> removed = new ArrayList<>();
        removed.add(valueOrEmpty(raw));
        return new UpgradeResult(emptyValue, removed);
    }

    private static String getLegacyCompareRecord(final String rawList,
                                                 final String rawLeft,
                                                 final String rawRight) {
        return valueOrEmpty(rawList) + "\n[" + valueOrEmpty(rawLeft) + "]│["
                + valueOrEmpty(rawRight) + "]";
    }

}
