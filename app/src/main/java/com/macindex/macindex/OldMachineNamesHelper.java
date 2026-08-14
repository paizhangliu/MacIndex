package com.macindex.macindex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MacIndex Old Machine Names Helper
 * Reads the original name-to-UID data used by version upgrades and old share links.
 */
class OldMachineNamesHelper {

    private static final String ASSET_NAME = "old_machine_names.json";

    static Map<String, String> read(final Context thisContext) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                thisContext.getAssets().open(ASSET_NAME), StandardCharsets.UTF_8))) {
            final StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            final JSONArray rawNames = new JSONObject(raw.toString()).getJSONArray("names");
            final Map<String, String> oldMachineNames = new HashMap<>();
            for (int i = 0; i < rawNames.length(); i++) {
                final JSONObject rawName = rawNames.getJSONObject(i);
                oldMachineNames.put(rawName.getString("name").toLowerCase(Locale.ROOT),
                        rawName.getString("uid"));
            }
            return oldMachineNames;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load old machine names", e);
        }
    }
}
