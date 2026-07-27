package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateHelperTest {

    @Test
    public void normalizeVersionAcceptsGitHubTag() {
        assertEquals("4.9.1", UpdateHelper.normalizeVersion("v4.9.1"));
    }

    @Test
    public void compareVersionsUsesNumericParts() {
        assertTrue(UpdateHelper.compareVersions("4.10.0", "4.9.9") > 0);
        assertTrue(UpdateHelper.compareVersions("4.9.1", "4.9.0") > 0);
        assertEquals(0, UpdateHelper.compareVersions("4.9.0", "4.9.0"));
        assertTrue(UpdateHelper.compareVersions("4.8.4", "4.9.0") < 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeVersionRejectsIncompleteTag() {
        UpdateHelper.normalizeVersion("v4.9");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeVersionRejectsPrereleaseTag() {
        UpdateHelper.normalizeVersion("v4.9.1-beta");
    }

    @Test
    public void skippedVersionOnlySuppressesAutomaticCheck() {
        assertFalse(UpdateHelper.shouldNotifyUpdate("4.9.1", "4.9.1", false));
        assertTrue(UpdateHelper.shouldNotifyUpdate("4.9.1", "4.9.0", false));
        assertTrue(UpdateHelper.shouldNotifyUpdate("4.9.1", "4.9.1", true));
    }

    @Test
    public void updatePagesMustUseTheirExpectedHosts() throws Exception {
        assertEquals("https://macindex.paizhang.info/#版本",
                UpdateHelper.normalizeReleasePage(
                        "https://macindex.paizhang.info/#版本",
                        "macindex.paizhang.info", "/"));
        assertEquals("https://github.com/paizhangliu/MacIndex/releases/tag/v4.9.0",
                UpdateHelper.normalizeReleasePage(
                        "https://github.com/paizhangliu/MacIndex/releases/tag/v4.9.0",
                        "github.com", "/paizhangliu/MacIndex/releases"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void updatePagesRejectUnexpectedHosts() throws Exception {
        UpdateHelper.normalizeReleasePage(
                "https://example.com/download",
                "macindex.paizhang.info", "/");
    }
}
