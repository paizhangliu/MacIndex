package com.macindex.macindex;

import android.net.Uri;

import java.util.regex.Pattern;

/**
 * MacIndex Share Link Helper
 */
class ShareLinkHelper {

    private static final String SHARE_BASE = "https://macindex.paizhang.info/share";
    private static final Pattern MACHINE_UID = Pattern.compile("MI\\d{6}");

    public static String create(final String machineUID) {
        validateMachineUID(machineUID);
        return SHARE_BASE + "?code=" + machineUID;
    }

    public static String createComparison(final String leftUID, final String rightUID) {
        validateMachineUID(leftUID);
        validateMachineUID(rightUID);
        if (leftUID.equals(rightUID)) {
            throw new IllegalArgumentException("Comparison requires two machines");
        }
        return SHARE_BASE + "?compare=" + leftUID + "&with=" + rightUID;
    }

    public static boolean isComparison(final String link) {
        return getQueryValue(link, "compare") != null;
    }

    public static String[] decodeComparison(final String link) {
        final String leftMachine = getQueryValue(link, "compare");
        final String rightMachine = getQueryValue(link, "with");
        if (leftMachine == null || rightMachine == null
                || leftMachine.trim().isEmpty() || rightMachine.trim().isEmpty()) {
            throw new IllegalArgumentException("Comparison link has incomplete machine codes");
        }
        return new String[]{leftMachine.trim(), rightMachine.trim()};
    }

    public static String decode(final String link) {
        final String decodedValue = getQueryValue(link, "code");
        if (decodedValue != null) {
            final String decoded = decodedValue.trim();
            if (!decoded.isEmpty()) {
                return decoded;
            }
        }
        throw new IllegalArgumentException("Share link has no machine code");
    }

    private static void validateMachineUID(final String machineUID) {
        if (machineUID == null || !MACHINE_UID.matcher(machineUID).matches()) {
            throw new IllegalArgumentException("Illegal machine UID");
        }
    }

    private static String getQueryValue(final String link, final String name) {
        if (link == null) {
            throw new IllegalArgumentException("Share link has no query");
        }
        final Uri uri = Uri.parse(link);
        if (uri.getQuery() == null) {
            throw new IllegalArgumentException("Share link has no query");
        }
        return uri.getQueryParameter(name);
    }
}
