package com.macindex.macindex;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MacIndex User Compare Helper
 * Owns the compare list and selection as one JSON record.
 */
class UserCompareHelper {

    private static final int SCHEMA_VERSION = 1;

    static final String EMPTY_JSON = "{\"schema\":" + SCHEMA_VERSION
            + ",\"machines\":[],\"left\":\"\",\"right\":\"\"}";

    static class State {
        final List<String> machineUIDs;
        String leftUID;
        String rightUID;

        State(final List<String> thisMachineUIDs, final String thisLeftUID,
              final String thisRightUID) {
            machineUIDs = thisMachineUIDs;
            leftUID = thisLeftUID;
            rightUID = thisRightUID;
        }
    }

    static State parse(final String raw) {
        try {
            final JSONObject root = new JSONObject(raw);
            UserRecordJsonHelper.requireKeys(root, "schema", "machines", "left", "right");
            if (root.getInt("schema") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported compare schema");
            }
            final JSONArray rawMachines = root.getJSONArray("machines");
            if (rawMachines.length() > 10) {
                throw new IllegalArgumentException("Too many compare machines");
            }
            final List<String> machineUIDs = new ArrayList<>();
            final Set<String> addedUIDs = new HashSet<>();
            for (int i = 0; i < rawMachines.length(); i++) {
                final String machineUID = rawMachines.getString(i);
                if (!UserRecordJsonHelper.isMachineUID(machineUID)
                        || !addedUIDs.add(machineUID)) {
                    throw new IllegalArgumentException("Illegal compare machine");
                }
                machineUIDs.add(machineUID);
            }
            final String leftUID = root.getString("left");
            final String rightUID = root.getString("right");
            final boolean emptySelection = leftUID.isEmpty() && rightUID.isEmpty();
            final boolean validSelection = UserRecordJsonHelper.isMachineUID(leftUID)
                    && UserRecordJsonHelper.isMachineUID(rightUID)
                    && !leftUID.equals(rightUID) && machineUIDs.contains(leftUID)
                    && machineUIDs.contains(rightUID);
            if (!emptySelection && !validSelection) {
                throw new IllegalArgumentException("Illegal compare selection");
            }
            return new State(machineUIDs, leftUID, rightUID);
        } catch (Exception e) {
            throw new UserRecordJsonHelper.InvalidUserRecordException(
                    "Illegal compare JSON", e);
        }
    }

    static String serialize(final State state) {
        try {
            final JSONArray rawMachines = new JSONArray();
            final Set<String> machineUIDs = new HashSet<>();
            if (state.machineUIDs.size() > 10) {
                throw new IllegalArgumentException("Too many compare machines");
            }
            for (String machineUID : state.machineUIDs) {
                if (!UserRecordJsonHelper.isMachineUID(machineUID)
                        || !machineUIDs.add(machineUID)) {
                    throw new IllegalArgumentException("Illegal compare machine");
                }
                rawMachines.put(machineUID);
            }
            final boolean emptySelection = state.leftUID.isEmpty() && state.rightUID.isEmpty();
            final boolean validSelection = UserRecordJsonHelper.isMachineUID(state.leftUID)
                    && UserRecordJsonHelper.isMachineUID(state.rightUID)
                    && !state.leftUID.equals(state.rightUID)
                    && state.machineUIDs.contains(state.leftUID)
                    && state.machineUIDs.contains(state.rightUID);
            if (!emptySelection && !validSelection) {
                throw new IllegalArgumentException("Illegal compare selection");
            }
            final JSONObject root = new JSONObject();
            root.put("schema", SCHEMA_VERSION);
            root.put("machines", rawMachines);
            root.put("left", state.leftUID);
            root.put("right", state.rightUID);
            return root.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize compares", e);
        }
    }

    static State read(final Context thisContext) {
        final State state = parse(UserRecordJsonHelper.readStoredJSON(
                thisContext, "userCompare", EMPTY_JSON));
        for (String machineUID : state.machineUIDs) {
            UserRecordJsonHelper.requireKnownMachineUID(machineUID);
        }
        return state;
    }

    static void write(final State state, final Context thisContext) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        if (!prefsFile.edit().putString("userCompare", serialize(state))
                .putBoolean("isCompareReloadNeeded", true).commit()) {
            throw new IllegalStateException("Unable to save compare record");
        }
    }

    static void clear(final Context thisContext) {
        write(new State(new ArrayList<>(), "", ""), thisContext);
    }

    static void clearSelection(final State state) {
        state.leftUID = "";
        state.rightUID = "";
    }

    static void ensureSelectionValid(final State state) {
        if (state.machineUIDs.size() < 2 || state.leftUID.equals(state.rightUID)
                || !state.machineUIDs.contains(state.leftUID)
                || !state.machineUIDs.contains(state.rightUID)) {
            clearSelection(state);
        }
    }
}
