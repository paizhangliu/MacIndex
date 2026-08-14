package com.macindex.macindex;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * MacIndex Share Link Helper
 */
class ShareLinkHelper {

    private static final String SHARE_BASE = "https://macindex.paizhang.info/share";

    public static String create(final String machineUID) {
        validateMachineUID(machineUID);
        try {
            return SHARE_BASE + "?code=" + URLEncoder.encode(machineUID, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError("UTF-8 is unavailable", impossible);
        }
    }

    public static String createComparison(final String leftUID, final String rightUID) {
        validateMachineUID(leftUID);
        validateMachineUID(rightUID);
        if (leftUID.equals(rightUID)) {
            throw new IllegalArgumentException("Comparison requires two machines");
        }
        try {
            return SHARE_BASE + "?compare="
                    + URLEncoder.encode(leftUID, "UTF-8") + "&with="
                    + URLEncoder.encode(rightUID, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError("UTF-8 is unavailable", impossible);
        }
    }

    public static boolean isComparison(final String link) {
        return getQueryValue(link, "compare") != null;
    }

    public static String[] decodeComparison(final String link) {
        final String leftUID = getQueryValue(link, "compare");
        final String rightUID = getQueryValue(link, "with");
        if (leftUID == null || rightUID == null) {
            throw new IllegalArgumentException("Comparison link has incomplete machine codes");
        }
        validateMachineUID(leftUID.trim());
        validateMachineUID(rightUID.trim());
        return new String[]{leftUID.trim(), rightUID.trim()};
    }

    public static String decode(final String link) {
        final String decodedValue = getQueryValue(link, "code");
        if (decodedValue != null) {
            final String decoded = decodedValue.trim();
            if (!decoded.isEmpty()) {
                validateMachineUID(decoded);
                return decoded;
            }
        }
        throw new IllegalArgumentException("Share link has no machine code");
    }

    private static void validateMachineUID(final String machineUID) {
        if (!UserRecordJsonHelper.isMachineUID(machineUID)) {
            throw new IllegalArgumentException("Illegal machine UID");
        }
    }

    private static String getQueryValue(final String link, final String name) {
        final String rawQuery = URI.create(link).getRawQuery();
        if (rawQuery == null) {
            throw new IllegalArgumentException("Share link has no query");
        }
        for (String entry : rawQuery.split("&")) {
            final String[] pair = entry.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                try {
                    return URLDecoder.decode(pair[1], "UTF-8");
                } catch (UnsupportedEncodingException impossible) {
                    throw new AssertionError("UTF-8 is unavailable", impossible);
                }
            }
        }
        return null;
    }
}
