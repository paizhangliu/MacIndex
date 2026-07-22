package com.macindex.macindex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShareLinkHelperTest {

    @Test
    public void newLinksRoundTripNamesWithPunctuation() {
        final String name = "PowerBook G4 (15-inch/1.67 GHz)";
        assertEquals(name, ShareLinkHelper.decode(ShareLinkHelper.create(name)));
    }

    @Test
    public void oldUnderscoreLinksRemainSupported() {
        assertEquals("Macintosh 128K", ShareLinkHelper.decode(
                "https://paizhang.info/macindex/share?code=Macintosh_128K_"));
    }

    @Test
    public void comparisonLinksRoundTripBothNames() {
        final String leftName = "Macintosh LC 520";
        final String rightName = "PowerBook G4 (15-inch/1.67 GHz)";
        final String link = ShareLinkHelper.createComparison(leftName, rightName);
        assertTrue(ShareLinkHelper.isComparison(link));
        assertArrayEquals(new String[]{leftName, rightName},
                ShareLinkHelper.decodeComparison(link));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incompleteComparisonLinksAreRejected() {
        ShareLinkHelper.decodeComparison(
                "https://paizhang.info/macindex/share?compare=Macintosh+LC+520");
    }

    @Test(expected = IllegalArgumentException.class)
    public void linksWithoutCodeAreRejected() {
        ShareLinkHelper.decode("https://paizhang.info/macindex/share");
    }
}
