package com.macindex.macindex;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * MacIndex User Data Transfer Helper
 * Owns the complete exported user data JSON protocol.
 */
class UserDataTransferHelper {

    static final String DEFAULT_FILE_NAME = "MacIndex-User-Data.json";

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_IMPORT_LENGTH = 1024 * 1024;

    static class InvalidTransferException extends IllegalArgumentException {

        InvalidTransferException(final String message) {
            super(message);
        }

        InvalidTransferException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    static class UserData {
        final List<UserCommentHelper.Comment> comments;
        final List<UserFavouriteHelper.Folder> favourites;
        final UserCompareHelper.State compare;

        UserData(final List<UserCommentHelper.Comment> thisComments,
                 final List<UserFavouriteHelper.Folder> thisFavourites,
                 final UserCompareHelper.State thisCompare) {
            comments = thisComments;
            favourites = thisFavourites;
            compare = thisCompare;
        }
    }

    static class ImportResult {
        final String commentsJSON;
        final String favouritesJSON;
        final String compareJSON;
        final List<String> removedComments;
        final List<String> removedFavourites;
        final List<String> removedCompares;
        final int commentCount;
        final int folderCount;
        final int favouriteCount;
        final int compareCount;

        ImportResult(final String thisCommentsJSON, final String thisFavouritesJSON,
                     final String thisCompareJSON, final List<String> thisRemovedComments,
                     final List<String> thisRemovedFavourites,
                     final List<String> thisRemovedCompares) {
            commentsJSON = thisCommentsJSON;
            favouritesJSON = thisFavouritesJSON;
            compareJSON = thisCompareJSON;
            removedComments = thisRemovedComments;
            removedFavourites = thisRemovedFavourites;
            removedCompares = thisRemovedCompares;

            final List<UserCommentHelper.Comment> comments = UserCommentHelper.parse(commentsJSON);
            final List<UserFavouriteHelper.Folder> favourites = UserFavouriteHelper.parse(favouritesJSON);
            final UserCompareHelper.State compare = UserCompareHelper.parse(compareJSON);
            commentCount = comments.size();
            folderCount = favourites.size();
            int savedMachines = 0;
            for (UserFavouriteHelper.Folder folder : favourites) {
                savedMachines += folder.machineUIDs.size();
            }
            favouriteCount = savedMachines;
            compareCount = compare.machineUIDs.size();
        }

        int getRemovedCount() {
            return removedComments.size() + removedFavourites.size() + removedCompares.size();
        }
    }

    static String create(final Context thisContext) {
        return serialize(new UserData(UserCommentHelper.read(thisContext),
                UserFavouriteHelper.read(thisContext), UserCompareHelper.read(thisContext)));
    }

    static String serialize(final UserData userData) {
        try {
            final JSONObject root = new JSONObject();
            root.put("schema", SCHEMA_VERSION);
            root.put("comments", new JSONObject(UserCommentHelper.serialize(userData.comments)));
            root.put("favourites", new JSONObject(
                    UserFavouriteHelper.serialize(userData.favourites)));
            root.put("compare", new JSONObject(UserCompareHelper.serialize(userData.compare)));
            return root.toString(2);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize user data", e);
        }
    }

    static UserData parse(final String raw) {
        try {
            if (raw == null || raw.length() > MAX_IMPORT_LENGTH) {
                throw new IllegalArgumentException("Illegal user data length");
            }
            final JSONObject root = new JSONObject(raw);
            UserRecordJsonHelper.requireKeys(
                    root, "schema", "comments", "favourites", "compare");
            if (root.getInt("schema") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported user data schema");
            }
            return new UserData(
                    UserCommentHelper.parse(root.getJSONObject("comments").toString()),
                    UserFavouriteHelper.parse(root.getJSONObject("favourites").toString()),
                    UserCompareHelper.parse(root.getJSONObject("compare").toString()));
        } catch (Exception e) {
            throw new InvalidTransferException("Illegal user data JSON", e);
        }
    }

    static ImportResult prepareImport(final String raw, final MachineHelper machineHelper) {
        if (machineHelper == null) {
            throw new IllegalStateException("Machine helper is unavailable");
        }
        final UserData userData = parse(raw);
        final UserRecordUpgradeHelper.UpgradeResult comments =
                UserRecordUpgradeHelper.upgradeComments(
                        UserCommentHelper.serialize(userData.comments), machineHelper,
                        Collections.emptyMap());
        final UserRecordUpgradeHelper.UpgradeResult favourites =
                UserRecordUpgradeHelper.upgradeFavourites(
                        UserFavouriteHelper.serialize(userData.favourites), machineHelper,
                        Collections.emptyMap());
        final UserRecordUpgradeHelper.UpgradeResult compares =
                UserRecordUpgradeHelper.upgradeCompares(
                        UserCompareHelper.serialize(userData.compare), "", "", "",
                        machineHelper, Collections.emptyMap());
        return new ImportResult(comments.value, favourites.value, compares.value,
                comments.removed, favourites.removed, compares.removed);
    }

    static void applyImport(final ImportResult imported, final Context thisContext) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor prefsEditor = prefsFile.edit()
                .putString("userComments", imported.commentsJSON)
                .putString("userFavourites", imported.favouritesJSON)
                .putString("userCompare", imported.compareJSON)
                .putBoolean("isCommentsReloadNeeded", true)
                .putBoolean("isFavouritesReloadNeeded", true)
                .putBoolean("isCompareReloadNeeded", true);

        final String report = PrefsHelper.buildUpgradeReport(thisContext,
                imported.removedComments, imported.removedFavourites,
                imported.removedCompares);
        if (!report.isEmpty()) {
            final String pendingReport = prefsFile.getString("pendingUpgradeReport", "");
            prefsEditor.putString("pendingUpgradeReport", pendingReport == null
                    || pendingReport.isEmpty() ? report : pendingReport + "\n\n" + report);
        }
        if (!prefsEditor.commit()) {
            throw new IllegalStateException("Unable to import user data");
        }
    }

    static String read(final Context thisContext, final Uri uri) throws IOException {
        try (InputStream input = thisContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open user data");
            }
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int length;
            int totalLength = 0;
            while ((length = input.read(buffer)) != -1) {
                totalLength += length;
                if (totalLength > MAX_IMPORT_LENGTH) {
                    throw new InvalidTransferException("User data file is too large");
                }
                output.write(buffer, 0, length);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (InvalidTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to read user data", e);
        }
    }

    static void write(final Context thisContext, final Uri uri,
                      final String userData) throws IOException {
        try (OutputStream output = thisContext.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IOException("Unable to open user data");
            }
            output.write(userData.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("Unable to write user data", e);
        }
    }
}
