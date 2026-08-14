package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserRecordJsonHelperTest {

    @Test
    public void commentsRoundTripUnicodeAndPunctuation() {
        final List<UserCommentHelper.Comment> comments = Arrays.asList(
                new UserCommentHelper.Comment("MI000001", "A │ comment [with] braces {}"),
                new UserCommentHelper.Comment("MI000002", "中文备注"));

        final List<UserCommentHelper.Comment> decoded = UserCommentHelper.parse(
                UserCommentHelper.serialize(comments));

        assertEquals(2, decoded.size());
        assertEquals("MI000001", decoded.get(0).machineUID);
        assertEquals("A │ comment [with] braces {}", decoded.get(0).text);
        assertEquals("中文备注", decoded.get(1).text);
    }

    @Test
    public void favouritesRoundTripFolderOrderAndMembership() {
        final List<UserFavouriteHelper.Folder> folders = new ArrayList<>();
        folders.add(new UserFavouriteHelper.Folder("Desktop │ Portable",
                new ArrayList<>(Arrays.asList("MI000002", "MI000001"))));

        final List<UserFavouriteHelper.Folder> decoded = UserFavouriteHelper.parse(
                UserFavouriteHelper.serialize(folders));

        assertEquals(1, decoded.size());
        assertEquals("Desktop │ Portable", decoded.get(0).name);
        assertEquals(Arrays.asList("MI000002", "MI000001"),
                decoded.get(0).machineUIDs);
    }

    @Test
    public void comparesRoundTripListAndSelectionAtomically() {
        final UserCompareHelper.State state = new UserCompareHelper.State(
                new ArrayList<>(Arrays.asList("MI000001", "MI000002", "MI000003")),
                "MI000001", "MI000003");

        final UserCompareHelper.State decoded = UserCompareHelper.parse(
                UserCompareHelper.serialize(state));

        assertEquals(state.machineUIDs, decoded.machineUIDs);
        assertEquals("MI000001", decoded.leftUID);
        assertEquals("MI000003", decoded.rightUID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void commentsRejectDuplicateMachineUIDs() {
        UserCommentHelper.parse("{\"schema\":1,\"comments\":["
                + "{\"machine\":\"MI000001\",\"text\":\"A\"},"
                + "{\"machine\":\"MI000001\",\"text\":\"B\"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void comparesRejectPartialSelection() {
        UserCompareHelper.parse("{\"schema\":1,\"machines\":[\"MI000001\"],"
                + "\"left\":\"MI000001\",\"right\":\"\"}");
    }

    @Test
    public void emptyRecordsRemainCanonical() {
        assertTrue(UserCommentHelper.parse(UserCommentHelper.EMPTY_JSON).isEmpty());
        assertTrue(UserFavouriteHelper.parse(UserFavouriteHelper.EMPTY_JSON).isEmpty());
        assertTrue(UserCompareHelper.parse(UserCompareHelper.EMPTY_JSON).machineUIDs.isEmpty());
    }
}
