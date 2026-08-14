package com.macindex.macindex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CompareListHelperTest {

    @Test
    public void parseRetainsLegacyOrder() {
        assertEquals(Arrays.asList("Macintosh 128K", "Mac mini"),
                CompareListHelper.parse("[Macintosh 128K]│[Mac mini]"));
    }

    @Test
    public void parseAcceptsEmptyStoredValue() {
        assertEquals(Collections.emptyList(), CompareListHelper.parse(""));
    }

    @Test
    public void serializeKeepsTheExistingPreferenceFormat() {
        assertEquals("[Macintosh 128K]│[Mac mini]",
                CompareListHelper.serialize(Arrays.asList("Macintosh 128K", "Mac mini")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseRejectsBadItems() {
        CompareListHelper.parse("[Macintosh 128K]│bad");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseRejectsDuplicateItems() {
        CompareListHelper.parse("[Macintosh 128K]│[Macintosh 128K]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void serializeRejectsBadItems() {
        CompareListHelper.serialize(Arrays.asList("Macintosh 128K", ""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void serializeRejectsDuplicateItems() {
        CompareListHelper.serialize(Arrays.asList("Macintosh 128K", "Macintosh 128K"));
    }
}
