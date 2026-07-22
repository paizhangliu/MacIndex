package com.macindex.macindex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CompareListHelperTest {

    @Test
    public void parseRetainsLegacyOrderAndDropsBadOrDuplicateItems() {
        assertEquals(Arrays.asList("Macintosh 128K", "Mac mini"),
                CompareListHelper.parse("[Macintosh 128K]│bad│[Mac mini]│[Macintosh 128K]"));
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
}
