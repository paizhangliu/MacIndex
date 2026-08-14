package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class UserRecordUpgradeHelperTest {

    private Map<String, String> validNames;

    @Before
    public void setUp() {
        validNames = new HashMap<>();
        validNames.put("mac a", "Mac A");
        validNames.put("mac b", "Mac B");
    }

    @Test
    public void commentsKeepValidEntriesAndReportRemovedContent() {
        final UserRecordUpgradeHelper.UpgradeResult result =
                UserRecordUpgradeHelper.upgradeComments(
                        "Mac A│Keep││Gone│Save this││mac a│Duplicate", validNames);

        assertEquals("Mac A│Keep", result.value);
        assertEquals(2, result.removed.size());
        assertTrue(result.removed.contains("Gone│Save this"));
        assertTrue(result.removed.contains("mac a│Duplicate"));
    }

    @Test
    public void favouritesKeepFoldersAndRemoveBadOrDuplicateMachines() {
        final UserRecordUpgradeHelper.UpgradeResult result =
                UserRecordUpgradeHelper.upgradeFavourites(
                        "││{Keep}│[Mac A]│[Gone]│[Mac A]││{Empty}││broken", validNames);

        assertEquals("││{Keep}│[Mac A]││{Empty}", result.value);
        assertEquals(3, result.removed.size());
        assertTrue(result.removed.contains("{Keep}│[Gone]"));
        assertTrue(result.removed.contains("{Keep}│[Mac A]"));
        assertTrue(result.removed.contains("broken"));
    }

    @Test
    public void comparesAuditListAndClearInvalidSelectionTogether() {
        final UserRecordUpgradeHelper.CompareUpgradeResult result =
                UserRecordUpgradeHelper.upgradeCompares(
                        "[Mac A]│[Gone]│[mac a]│[Mac B]", "Gone", "Mac B", validNames);

        assertEquals("[Mac A]│[Mac B]", result.compares);
        assertEquals("", result.left);
        assertEquals("", result.right);
        assertEquals(3, result.removed.size());
        assertTrue(result.removed.contains("[Gone]"));
        assertTrue(result.removed.contains("[mac a]"));
        assertTrue(result.removed.contains("[Gone]│[Mac B]"));
    }

    @Test
    public void validRecordsAreCanonicalizedWithoutAReport() {
        final UserRecordUpgradeHelper.UpgradeResult comments =
                UserRecordUpgradeHelper.upgradeComments("mac a│Keep", validNames);
        final UserRecordUpgradeHelper.UpgradeResult favourites =
                UserRecordUpgradeHelper.upgradeFavourites("││{Folder}│[mac b]", validNames);
        final UserRecordUpgradeHelper.CompareUpgradeResult compares =
                UserRecordUpgradeHelper.upgradeCompares(
                        "[mac a]│[mac b]", "mac a", "mac b", validNames);

        assertEquals("Mac A│Keep", comments.value);
        assertEquals("││{Folder}│[Mac B]", favourites.value);
        assertEquals("[Mac A]│[Mac B]", compares.compares);
        assertEquals("Mac A", compares.left);
        assertEquals("Mac B", compares.right);
        assertTrue(comments.removed.isEmpty());
        assertTrue(favourites.removed.isEmpty());
        assertTrue(compares.removed.isEmpty());
    }
}
