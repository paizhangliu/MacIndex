package com.macindex.macindex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShareLinkHelperTest {

    @Test
    public void newLinksRoundTripMachineUIDs() {
        final String machineUID = "MI000123";
        final String link = ShareLinkHelper.create(machineUID);
        assertTrue(link.startsWith("https://macindex.paizhang.info/share?code="));
        assertEquals(machineUID, ShareLinkHelper.decode(link));
    }

    @Test
    public void comparisonLinksRoundTripBothNames() {
        final String leftUID = "MI000001";
        final String rightUID = "MI000439";
        final String link = ShareLinkHelper.createComparison(leftUID, rightUID);
        assertTrue(link.startsWith("https://macindex.paizhang.info/share?compare="));
        assertTrue(ShareLinkHelper.isComparison(link));
        assertArrayEquals(new String[]{leftUID, rightUID},
                ShareLinkHelper.decodeComparison(link));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incompleteComparisonLinksAreRejected() {
        ShareLinkHelper.decodeComparison(
                "https://macindex.paizhang.info/share?compare=MI000001");
    }

    @Test(expected = IllegalArgumentException.class)
    public void linksWithoutCodeAreRejected() {
        ShareLinkHelper.decode("https://macindex.paizhang.info/share");
    }

    @Test(expected = IllegalArgumentException.class)
    public void legacyNameLinksAreRejectedWithoutSpecialHandling() {
        ShareLinkHelper.decode(
                "https://macindex.paizhang.info/share?code=Macintosh+LC+520");
    }
}
