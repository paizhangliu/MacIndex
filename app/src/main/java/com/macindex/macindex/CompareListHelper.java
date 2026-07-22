package com.macindex.macindex;

import java.util.ArrayList;
import java.util.List;

/**
 * MacIndex Compare List Helper
 * Keeps the original SharedPreferences format.
 */
class CompareListHelper {

    public static List<String> parse(final String raw) {
        final List<String> names = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return names;
        }
        for (String token : raw.split("│")) {
            if (token.length() >= 3 && token.startsWith("[") && token.endsWith("]")) {
                final String name = token.substring(1, token.length() - 1).trim();
                if (!name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    public static String serialize(final List<String> names) {
        String serialized = "";
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (!serialized.isEmpty()) {
                serialized = serialized.concat("│");
            }
            serialized = serialized.concat("[" + name.trim() + "]");
        }
        return serialized;
    }
}
