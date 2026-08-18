package com.macindex.macindex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class SpecsHelperTest {

    @Test
    public void modelIdentifierCopyExcludesCodenameAndKeepsEmc() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 6),
                SpecsHelper.modelIdentifierEntries(19));
        assertEquals(Arrays.asList(1, 2, 3, 4),
                SpecsHelper.modelIdentifierEntries(6));
    }

    @Test
    public void allSpecificationCopyIncludesIntroduction() {
        assertEquals(Arrays.asList(0, 1, 2, 3),
                SpecsHelper.allSpecificationEntries(4));
    }
}
