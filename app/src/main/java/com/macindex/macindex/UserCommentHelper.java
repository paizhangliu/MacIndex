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
 * MacIndex User Comment Helper
 * Owns the complete comment JSON protocol.
 */
class UserCommentHelper {

    private static final int SCHEMA_VERSION = 1;

    static final String EMPTY_JSON = "{\"schema\":" + SCHEMA_VERSION
            + ",\"comments\":[]}";

    static class Comment {
        final String machineUID;
        final String text;

        Comment(final String thisMachineUID, final String thisText) {
            machineUID = thisMachineUID;
            text = thisText;
        }
    }

    static List<Comment> parse(final String raw) {
        try {
            final JSONObject root = new JSONObject(raw);
            UserRecordJsonHelper.requireKeys(root, "schema", "comments");
            if (root.getInt("schema") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported comment schema");
            }
            final JSONArray rawComments = root.getJSONArray("comments");
            final List<Comment> comments = new ArrayList<>();
            final Set<String> machineUIDs = new HashSet<>();
            for (int i = 0; i < rawComments.length(); i++) {
                final JSONObject rawComment = rawComments.getJSONObject(i);
                UserRecordJsonHelper.requireKeys(rawComment, "machine", "text");
                final String machineUID = rawComment.getString("machine");
                final String text = rawComment.getString("text");
                if (!UserRecordJsonHelper.isMachineUID(machineUID)
                        || text.trim().isEmpty() || text.length() > 500
                        || !machineUIDs.add(machineUID)) {
                    throw new IllegalArgumentException("Illegal comment record");
                }
                comments.add(new Comment(machineUID, text));
            }
            return comments;
        } catch (Exception e) {
            throw new UserRecordJsonHelper.InvalidUserRecordException(
                    "Illegal comment JSON", e);
        }
    }

    static String serialize(final List<Comment> comments) {
        try {
            final JSONArray rawComments = new JSONArray();
            final Set<String> machineUIDs = new HashSet<>();
            for (Comment comment : comments) {
                if (comment == null || !UserRecordJsonHelper.isMachineUID(comment.machineUID)
                        || comment.text == null || comment.text.trim().isEmpty()
                        || comment.text.length() > 500
                        || !machineUIDs.add(comment.machineUID)) {
                    throw new IllegalArgumentException("Illegal comment record");
                }
                final JSONObject rawComment = new JSONObject();
                rawComment.put("machine", comment.machineUID);
                rawComment.put("text", comment.text);
                rawComments.put(rawComment);
            }
            final JSONObject root = new JSONObject();
            root.put("schema", SCHEMA_VERSION);
            root.put("comments", rawComments);
            return root.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize comments", e);
        }
    }

    static List<Comment> read(final Context thisContext) {
        final List<Comment> comments = parse(UserRecordJsonHelper.readStoredJSON(
                thisContext, "userComments", EMPTY_JSON));
        for (Comment comment : comments) {
            UserRecordJsonHelper.requireKnownMachineUID(comment.machineUID);
        }
        return comments;
    }

    static void write(final List<Comment> comments, final Context thisContext) {
        final SharedPreferences prefsFile = thisContext.getSharedPreferences(
                PrefsHelper.PREFERENCE_FILENAME, Activity.MODE_PRIVATE);
        if (!prefsFile.edit().putString("userComments", serialize(comments))
                .putBoolean("isCommentsReloadNeeded", true)
                .putBoolean("isCompareReloadNeeded", true).commit()) {
            throw new IllegalStateException("Unable to save comment record");
        }
    }

    static void clear(final Context thisContext) {
        write(new ArrayList<>(), thisContext);
    }

    static String getComment(final String machineUID, final Context thisContext) {
        for (Comment comment : read(thisContext)) {
            if (comment.machineUID.equals(machineUID)) {
                return comment.text;
            }
        }
        return null;
    }

    static void setComment(final String machineUID, final String text,
                           final Context thisContext) {
        final List<Comment> comments = read(thisContext);
        int existingPosition = -1;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).machineUID.equals(machineUID)) {
                existingPosition = i;
                break;
            }
        }
        if (existingPosition != -1) {
            comments.remove(existingPosition);
        }
        if (text != null && !text.trim().isEmpty()) {
            comments.add(existingPosition == -1 ? 0 : existingPosition,
                    new Comment(machineUID, text.trim()));
        }
        write(comments, thisContext);
    }
}
