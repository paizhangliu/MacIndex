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
 * MacIndex User Favourite Helper
 * Owns the complete favourite JSON protocol.
 */
class UserFavouriteHelper {

    private static final int SCHEMA_VERSION = 1;

    static final String EMPTY_JSON = "{\"schema\":" + SCHEMA_VERSION
            + ",\"folders\":[]}";

    static class Folder {
        String name;
        final List<String> machineUIDs;

        Folder(final String thisName, final List<String> thisMachineUIDs) {
            name = thisName;
            machineUIDs = thisMachineUIDs;
        }
    }

    static List<Folder> parse(final String raw) {
        try {
            final JSONObject root = new JSONObject(raw);
            UserRecordJsonHelper.requireKeys(root, "schema", "folders");
            if (root.getInt("schema") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported favourite schema");
            }
            final JSONArray rawFolders = root.getJSONArray("folders");
            if (rawFolders.length() > 15) {
                throw new IllegalArgumentException("Too many favourite folders");
            }
            final List<Folder> folders = new ArrayList<>();
            final Set<String> folderNames = new HashSet<>();
            for (int i = 0; i < rawFolders.length(); i++) {
                final JSONObject rawFolder = rawFolders.getJSONObject(i);
                UserRecordJsonHelper.requireKeys(rawFolder, "name", "machines");
                final String folderName = rawFolder.getString("name");
                if (folderName.trim().isEmpty() || !folderName.equals(folderName.trim())
                        || folderName.length() > 30 || folderName.contains("\n")
                        || !folderNames.add(folderName)) {
                    throw new IllegalArgumentException("Illegal favourite folder");
                }
                final JSONArray rawMachines = rawFolder.getJSONArray("machines");
                final List<String> machineUIDs = new ArrayList<>();
                final Set<String> addedUIDs = new HashSet<>();
                for (int j = 0; j < rawMachines.length(); j++) {
                    final String machineUID = rawMachines.getString(j);
                    if (!UserRecordJsonHelper.isMachineUID(machineUID)
                            || !addedUIDs.add(machineUID)) {
                        throw new IllegalArgumentException("Illegal favourite machine");
                    }
                    machineUIDs.add(machineUID);
                }
                folders.add(new Folder(folderName, machineUIDs));
            }
            return folders;
        } catch (Exception e) {
            throw new UserRecordJsonHelper.InvalidUserRecordException(
                    "Illegal favourite JSON", e);
        }
    }

    static String serialize(final List<Folder> folders) {
        try {
            if (folders.size() > 15) {
                throw new IllegalArgumentException("Too many favourite folders");
            }
            final JSONArray rawFolders = new JSONArray();
            final Set<String> folderNames = new HashSet<>();
            for (Folder folder : folders) {
                if (folder == null || folder.name == null || folder.name.trim().isEmpty()
                        || !folder.name.equals(folder.name.trim()) || folder.name.length() > 30
                        || folder.name.contains("\n") || !folderNames.add(folder.name)) {
                    throw new IllegalArgumentException("Illegal favourite folder");
                }
                final JSONArray rawMachines = new JSONArray();
                final Set<String> machineUIDs = new HashSet<>();
                for (String machineUID : folder.machineUIDs) {
                    if (!UserRecordJsonHelper.isMachineUID(machineUID)
                            || !machineUIDs.add(machineUID)) {
                        throw new IllegalArgumentException("Illegal favourite machine");
                    }
                    rawMachines.put(machineUID);
                }
                final JSONObject rawFolder = new JSONObject();
                rawFolder.put("name", folder.name);
                rawFolder.put("machines", rawMachines);
                rawFolders.put(rawFolder);
            }
            final JSONObject root = new JSONObject();
            root.put("schema", SCHEMA_VERSION);
            root.put("folders", rawFolders);
            return root.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize favourites", e);
        }
    }

    static List<Folder> read(final Context thisContext) {
        final List<Folder> folders = parse(UserRecordJsonHelper.readStoredJSON(
                thisContext, "userFavourites", EMPTY_JSON));
        for (Folder folder : folders) {
            for (String machineUID : folder.machineUIDs) {
                UserRecordJsonHelper.requireKnownMachineUID(machineUID);
            }
        }
        return folders;
    }

    static void write(final List<Folder> folders, final Context thisContext) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        if (!prefsFile.edit().putString("userFavourites", serialize(folders))
                .putBoolean("isFavouritesReloadNeeded", true).commit()) {
            throw new IllegalStateException("Unable to save favourite record");
        }
    }

    static void clear(final Context thisContext) {
        write(new ArrayList<>(), thisContext);
    }

    static boolean contains(final String machineUID, final List<Folder> folders) {
        for (Folder folder : folders) {
            if (folder.machineUIDs.contains(machineUID)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> getMachineUIDs(final List<Folder> folders) {
        final Set<String> machineUIDs = new HashSet<>();
        for (Folder folder : folders) {
            machineUIDs.addAll(folder.machineUIDs);
        }
        return machineUIDs;
    }

    static boolean contains(final String machineUID, final Context thisContext) {
        return contains(machineUID, read(thisContext));
    }

    static void setMembership(final String machineUID, final boolean[] selections,
                              final Context thisContext) {
        final List<Folder> folders = read(thisContext);
        if (folders.size() != selections.length) {
            throw new IllegalArgumentException("Illegal favourite selection");
        }
        for (int i = 0; i < folders.size(); i++) {
            folders.get(i).machineUIDs.remove(machineUID);
            if (selections[i]) {
                folders.get(i).machineUIDs.add(0, machineUID);
            }
        }
        write(folders, thisContext);
    }
}
