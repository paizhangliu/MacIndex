package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class UserRecordUpgradeHelperTest {

    private TestIdentityResolver resolver;

    @Before
    public void setUp() {
        resolver = new TestIdentityResolver();
        resolver.add("Mac A", "MI000001");
        resolver.add("Mac B", "MI000002");
        resolver.names.put("MI000003", "Retired Mac");
        resolver.replacements.put("MI000003", "MI000002");
    }

    @Test
    public void legacyRecordsAreImportedOnceAndReported() {
        final UserRecordUpgradeHelper.UpgradeResult comments =
                UserRecordUpgradeHelper.upgradeComments(
                        "Mac A│Keep││Gone│Save this││mac a│Duplicate", resolver);
        final UserRecordUpgradeHelper.UpgradeResult favourites =
                UserRecordUpgradeHelper.upgradeFavourites(
                        "││{Keep}│[Mac A]│[Gone]│[Mac A]││{Empty}││broken", resolver);
        final UserRecordUpgradeHelper.UpgradeResult compares =
                UserRecordUpgradeHelper.upgradeCompares("", "[Mac A]│[Gone]│[Mac B]",
                        "Gone", "Mac B", resolver);

        assertEquals("Keep", UserCommentHelper.parse(comments.value).get(0).text);
        assertEquals(2, comments.removed.size());
        assertEquals(2, UserFavouriteHelper.parse(favourites.value).size());
        assertEquals(3, favourites.removed.size());
        assertEquals(2, UserCompareHelper.parse(compares.value).machineUIDs.size());
        assertEquals("", UserCompareHelper.parse(compares.value).leftUID);
        assertEquals(2, compares.removed.size());
    }

    @Test
    public void currentJsonIsAuditedAndRetiredUIDsAreRedirected() {
        final String raw = "{\"schema\":1,\"comments\":["
                + "{\"machine\":\"MI000003\",\"text\":\"Keep\"}]}";

        final UserRecordUpgradeHelper.UpgradeResult result =
                UserRecordUpgradeHelper.upgradeComments(raw, resolver);

        assertEquals("MI000002", UserCommentHelper.parse(result.value).get(0).machineUID);
        assertTrue(result.removed.isEmpty());
    }

    @Test
    public void corruptJsonIsResetAndPreservedInReport() {
        final UserRecordUpgradeHelper.UpgradeResult result =
                UserRecordUpgradeHelper.upgradeFavourites("{broken", resolver);

        assertEquals(UserFavouriteHelper.EMPTY_JSON, result.value);
        assertEquals(1, result.removed.size());
        assertEquals("{broken", result.removed.get(0));
    }

    private static class TestIdentityResolver implements MachineIdentityResolver {
        private final Map<String, String> legacy = new HashMap<>();
        private final Map<String, String> names = new HashMap<>();
        private final Map<String, String> replacements = new HashMap<>();

        void add(final String name, final String uid) {
            legacy.put(name.toLowerCase(), uid);
            names.put(uid, name);
        }

        @Override
        public String resolveUID(final String machineUID) {
            if (machineUID == null || machineUID.isEmpty()) {
                return null;
            }
            if (names.containsKey(machineUID) && !replacements.containsKey(machineUID)) {
                return machineUID;
            }
            return replacements.get(machineUID);
        }

        @Override
        public String resolveLegacyName(final String machineName) {
            return machineName == null ? null : legacy.get(machineName.toLowerCase());
        }

        @Override
        public String getIdentityName(final String machineUID) {
            return names.get(machineUID);
        }
    }
}
