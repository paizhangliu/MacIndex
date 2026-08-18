package com.macindex.macindex.userstate

import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V491StateConverterTest {
    private val names = mapOf(
        "mac a" to "MI000001",
        "mac b" to "MI000002",
        "renamed mac" to "MI000002",
    )
    private val resolveName: (String) -> String? = names::get

    @Test
    fun convertsOnlyPublished491Formats() {
        val state = V491StateConverter.convert(
            mapOf(
                "isSortComment" to true,
                "isPlayDeathSound" to false,
                "isEnableVolWarning" to false,
                "isUseNavButtons" to false,
                "isFixedNav" to true,
                "isRandomAll" to true,
                "isSaveMainUsage" to false,
                "isSaveCompareUsage" to false,
                "isAutoCheckUpdate" to false,
                "isOpenEveryMac" to true,
                "skippedUpdateVersion" to "4.9.2",
                "lastMainManufacturer" to "appleppc",
                "lastMainFilter" to "years",
                "userComments" to "Mac A│First││Mac B│Second",
                "userFavourites" to "││{Folder}│[Mac A]│[Missing Mac]",
                "userCompares" to "[Mac A]│[Mac B]",
                "userComparesLeft" to "Mac A",
                "userComparesRight" to "Mac B",
            ),
            resolveName,
        )

        assertTrue(state.preferences.sortComments)
        assertFalse(state.preferences.playDeathSound)
        assertFalse(state.preferences.enableVolumeWarning)
        assertFalse(state.preferences.useNavigationButtons)
        assertTrue(state.preferences.fixedNavigation)
        assertTrue(state.preferences.limitRandomToCurrentBrowse)
        assertFalse(state.preferences.rememberMainState)
        assertFalse(state.preferences.rememberCompareState)
        assertFalse(state.preferences.automaticallyCheckUpdates)
        assertTrue(state.preferences.highlightCompareDifferences)
        assertEquals("4.9.2", state.preferences.skippedUpdateVersion)
        assertEquals(BrowseScope.ALL, state.uiMemory.mainScope)
        assertEquals(BrowseGrouping.NAMES, state.uiMemory.mainGrouping)
        assertEquals(listOf("MI000001", "MI000002"), state.library.comments.map { it.machineUid })
        assertEquals(listOf("MI000001"), state.library.favouriteFolders.single().machineUids)
        assertEquals(1L, state.library.favouriteFolders.single().id)
        assertEquals(2L, state.library.nextFavouriteFolderId)
        assertEquals(listOf("MI000001", "MI000002"), state.library.compare.machineUids)
        assertTrue(state.library.compare.leftUid.isEmpty())
        assertTrue(state.library.compare.rightUid.isEmpty())
        assertEquals(
            listOf("{Folder}│[Missing Mac]"),
            state.pendingNotice!!.removedContent.map { it.value },
        )
    }

    @Test
    fun malformedCollectionIsAbandonedWithoutDamagingOtherCollections() {
        val state = V491StateConverter.convert(
            mapOf(
                "userComments" to "Mac A│valid││broken",
                "userFavourites" to "││{Folder}│[Mac B]",
            ),
            resolveName,
        )

        assertTrue(state.library.comments.isEmpty())
        assertEquals(listOf("MI000002"), state.library.favouriteFolders.single().machineUids)
        assertEquals(RemovedContentKind.COMMENT, state.pendingNotice!!.removedContent.single().kind)
        assertEquals("Mac A│valid││broken", state.pendingNotice.removedContent.single().value)
    }

    @Test
    fun machineChangesAreRemovedIndividually() {
        val state = V491StateConverter.convert(
            mapOf(
                "userComments" to "Unknown│lost││Mac A│kept",
                "userCompares" to "[Mac A]│[Unknown]",
                "userComparesLeft" to "Mac A",
                "userComparesRight" to "Unknown",
            ),
            resolveName,
        )

        assertEquals(listOf("MI000001"), state.library.comments.map { it.machineUid })
        assertEquals(listOf("MI000001"), state.library.compare.machineUids)
        assertTrue(state.library.compare.leftUid.isEmpty())
        assertTrue(state.library.compare.rightUid.isEmpty())
        assertEquals(3, state.pendingNotice!!.removedContent.size)
    }

    @Test
    fun wrongRecordTypeIsDiscardedAndReported() {
        val state = V491StateConverter.convert(
            mapOf("userComments" to true),
            resolveName,
        )
        assertTrue(state.library.comments.isEmpty())
        assertEquals("true", state.pendingNotice!!.removedContent.single().value)
    }

    @Test
    fun restoredLegacyPreferencesNeverOverwriteCommittedProtoState() {
        val current = AppStateProtoMapper.toProto(UserState(
            library = UserLibrary(comments = listOf(UserComment("MI000002", "new"))),
        ))
        val legacy = UserState(
            library = UserLibrary(comments = listOf(UserComment("MI000001", "old"))),
        )

        assertEquals(current, mergeLegacyMigration(current, legacy))
    }

}
