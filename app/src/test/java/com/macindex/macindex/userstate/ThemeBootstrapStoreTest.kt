package com.macindex.macindex.userstate

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeBootstrapStoreTest {
    @Test
    fun codecDefaultsUnknownValuesToFollowSystem() {
        assertEquals(Appearance.FOLLOW_SYSTEM, AppearanceCodec.decode(-1))
        assertEquals(Appearance.FOLLOW_SYSTEM, AppearanceCodec.decode(99))
    }

    @Test
    fun codecRoundTripsAllModes() {
        Appearance.entries.forEach { appearance ->
            assertEquals(appearance, AppearanceCodec.decode(AppearanceCodec.encode(appearance)))
        }
    }
}
