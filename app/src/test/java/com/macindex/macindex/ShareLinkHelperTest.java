package com.macindex.macindex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShareLinkHelperTest {

    @Test
    public void newLinksRoundTripNamesWithPunctuation() {
        final String name = "PowerBook G4 (15-inch/1.67 GHz)";
        final String link = ShareLinkHelper.create(name);
        assertTrue(link.startsWith("https://macindex.paizhang.info/share?code="));
        assertEquals(name, ShareLinkHelper.decode(link));
    }

    @Test
    public void comparisonLinksRoundTripBothNames() {
        final String leftName = "Macintosh LC 520";
        final String rightName = "PowerBook G4 (15-inch/1.67 GHz)";
        final String link = ShareLinkHelper.createComparison(leftName, rightName);
        assertTrue(link.startsWith("https://macindex.paizhang.info/share?compare="));
        assertTrue(ShareLinkHelper.isComparison(link));
        assertArrayEquals(new String[]{leftName, rightName},
                ShareLinkHelper.decodeComparison(link));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incompleteComparisonLinksAreRejected() {
        ShareLinkHelper.decodeComparison(
                "https://macindex.paizhang.info/share?compare=Macintosh+LC+520");
    }

    @Test(expected = IllegalArgumentException.class)
    public void linksWithoutCodeAreRejected() {
        ShareLinkHelper.decode("https://macindex.paizhang.info/share");
    }
}
