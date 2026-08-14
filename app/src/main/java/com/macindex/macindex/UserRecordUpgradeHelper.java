package com.macindex.macindex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MacIndex User Record Upgrade Helper
 * Audits the original SharedPreferences formats against the current database.
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

    static class CompareUpgradeResult {
        final String compares;
        final String left;
        final String right;
        final List<String> removed;

        CompareUpgradeResult(final String upgradedCompares, final String upgradedLeft,
                             final String upgradedRight, final List<String> removedValues) {
            compares = upgradedCompares;
            left = upgradedLeft;
            right = upgradedRight;
            removed = removedValues;
        }
    }

    static UpgradeResult upgradeComments(final String raw, final Map<String, String> validNames) {
        final List<String> upgraded = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final Set<String> addedNames = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return new UpgradeResult("", removed);
        }

        for (String entry : raw.split("││", -1)) {
            final String[] parts = entry.split("│", -1);
            final String comment = parts.length == 2 ? parts[1].trim() : "";
            if (parts.length != 2 || comment.isEmpty() || comment.length() > 500) {
                removed.add(entry);
                continue;
            }
            final String machineName = resolveName(parts[0], validNames);
            if (machineName == null || !addedNames.add(machineName)) {
                removed.add(entry);
                continue;
            }
            upgraded.add(machineName + "│" + comment);
        }
        return new UpgradeResult(join(upgraded, "││"), removed);
    }

    static UpgradeResult upgradeFavourites(final String raw, final Map<String, String> validNames) {
        final StringBuilder upgraded = new StringBuilder();
        final List<String> removed = new ArrayList<>();
        final Set<String> addedFolders = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return new UpgradeResult("", removed);
        }

        final String[] folders = raw.split("││", -1);
        if (!folders[0].isEmpty()) {
            removed.add(folders[0]);
        }
        int folderCount = 0;
        for (int i = 1; i < folders.length; i++) {
            final String folder = folders[i];
            final String[] entries = folder.split("│", -1);
            if (entries.length == 0 || entries[0].length() < 2
                    || !entries[0].startsWith("{") || !entries[0].endsWith("}")) {
                removed.add(folder);
                continue;
            }

            final String folderName = entries[0].substring(1, entries[0].length() - 1).trim();
            if (folderName.isEmpty() || folderName.length() > 30
                    || folderName.contains("\n") || folderName.contains("│")
                    || !addedFolders.add(folderName) || folderCount >= 15) {
                removed.add(folder);
                continue;
            }

            final List<String> machineNames = new ArrayList<>();
            final Set<String> addedMachines = new HashSet<>();
            for (int j = 1; j < entries.length; j++) {
                final String entry = entries[j];
                if (entry.length() < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                    removed.add(entries[0] + "│" + entry);
                    continue;
                }
                final String machineName = resolveName(
                        entry.substring(1, entry.length() - 1), validNames);
                if (machineName == null || !addedMachines.add(machineName)) {
                    removed.add(entries[0] + "│" + entry);
                    continue;
                }
                machineNames.add(machineName);
            }

            upgraded.append("││{").append(folderName).append("}");
            for (String machineName : machineNames) {
                upgraded.append("│[").append(machineName).append("]");
            }
            folderCount++;
        }
        return new UpgradeResult(upgraded.toString(), removed);
    }

    static CompareUpgradeResult upgradeCompares(final String raw, final String rawLeft,
                                                 final String rawRight,
                                                 final Map<String, String> validNames) {
        final List<String> upgraded = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final Set<String> addedNames = new HashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String entry : raw.split("│", -1)) {
                if (entry.length() < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                    removed.add(entry);
                    continue;
                }
                final String machineName = resolveName(
                        entry.substring(1, entry.length() - 1), validNames);
                if (machineName == null || addedNames.contains(machineName)
                        || upgraded.size() >= 10) {
                    removed.add(entry);
                    continue;
                }
                addedNames.add(machineName);
                upgraded.add(machineName);
            }
        }

        String left = resolveName(rawLeft, validNames);
        String right = resolveName(rawRight, validNames);
        if (left == null || right == null || left.equals(right)
                || !upgraded.contains(left) || !upgraded.contains(right)) {
            if ((rawLeft != null && !rawLeft.isEmpty())
                    || (rawRight != null && !rawRight.isEmpty())) {
                removed.add("[" + valueOrEmpty(rawLeft) + "]│[" + valueOrEmpty(rawRight) + "]");
            }
            left = "";
            right = "";
        }

        return new CompareUpgradeResult(CompareListHelper.serialize(upgraded),
                left, right, removed);
    }

    private static String resolveName(final String storedName,
                                      final Map<String, String> validNames) {
        if (storedName == null || storedName.trim().isEmpty()) {
            return null;
        }
        return validNames.get(storedName.trim().toLowerCase(Locale.ROOT));
    }

    private static String valueOrEmpty(final String value) {
        return value == null ? "" : value;
    }

    private static String join(final List<String> values, final String separator) {
        final StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) {
                result.append(separator);
            }
            result.append(value);
        }
        return result.toString();
    }
}
