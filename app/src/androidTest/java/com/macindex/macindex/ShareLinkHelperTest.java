package com.macindex.macindex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShareLinkHelperTest {

    @Test
    public void newLinksRoundTripMachineUIDs() {
        final String machineUID = "MI000123";
        final String link = ShareLinkHelper.create(machineUID);
        assertEquals("https://macindex.paizhang.info/share?code=MI000123", link);
        assertEquals(machineUID, ShareLinkHelper.decode(link));
    }

    @Test
    public void comparisonLinksRoundTripBothUIDs() {
        final String leftUID = "MI000001";
        final String rightUID = "MI000439";
        final String link = ShareLinkHelper.createComparison(leftUID, rightUID);
        assertEquals("https://macindex.paizhang.info/share"
                + "?compare=MI000001&with=MI000439", link);
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

    @Test
    public void legacyNameLinksRemainReadable() {
        final String link = "https://macindex.paizhang.info/share?code=Macintosh+LC+520";
        assertEquals("Macintosh LC 520", ShareLinkHelper.decode(link));
    }

    @Test
    public void comparisonLinksCanContainOldNamesAndUIDs() {
        final String link = "https://macindex.paizhang.info/share"
                + "?compare=Macintosh+LC+520&with=MI000439";
        assertArrayEquals(new String[]{"Macintosh LC 520", "MI000439"},
                ShareLinkHelper.decodeComparison(link));
    }
}
