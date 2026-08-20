package com.macindex.macindex.userstate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStateReconcilerTest {
    @Test
    fun missingEntriesAreRemovedAndReported() {
        val state = UserState(
            library = UserLibrary(
                comments = listOf(
                    UserComment("MI000001", "kept"),
                    UserComment("MI000002", "missing too"),
                    UserComment("MI000003", "missing"),
                ),
                favouriteFolders = listOf(
                    FavouriteFolder(1, "Folder", listOf("MI000001", "MI000003")),
                ),
                compare = CompareSelection(
                    listOf("MI000001", "MI000003"),
                    "MI000001",
                    "MI000003",
                ),
                nextFavouriteFolderId = 2,
            ),
            pendingNotice = PendingUserNotice(
                removedContent = listOf(
                    RemovedUserContent(RemovedContentKind.COMMENT, "older notice"),
                ),
            ),
        )
        val resolver = MachineUidResolver { uid ->
            if (uid == "MI000001") MachineUidResolution(uid, "Current")
            else MachineUidResolution(null, uid)
        }

        val reconciled = UserStateReconciler.reconcile(state, resolver)

        assertEquals(listOf("MI000001"), reconciled.state.library.comments.map { it.machineUid })
        assertEquals(listOf("MI000001"), reconciled.state.library.favouriteFolders.single().machineUids)
        assertEquals(listOf("MI000001"), reconciled.state.library.compare.machineUids)
        assertTrue(reconciled.state.library.compare.leftUid.isEmpty())
        assertEquals(5, reconciled.removedContent.size)
        assertEquals(6, reconciled.state.pendingNotice!!.removedContent.size)
    }

    @Test
    fun unchangedStateIsNotRewrittenOrReported() {
        val state = UserState(
            library = UserLibrary(
                comments = listOf(UserComment("MI000001", "note")),
            ),
        )
        val result = UserStateReconciler.reconcile(
            state,
            MachineUidResolver { uid -> MachineUidResolution(uid, "Machine") },
        )

        assertTrue(result.removedContent.isEmpty())
        assertEquals(state, result.state)
    }

    @Test
    fun resetNoticeSurvivesLaterReconciliation() {
        val state = UserState(
            pendingNotice = PendingUserNotice(entireUserStateWasReset = true),
        )
        val result = UserStateReconciler.reconcile(
            state,
            MachineUidResolver { uid -> MachineUidResolution(null, uid) },
        )
        assertTrue(result.state.pendingNotice!!.entireUserStateWasReset)
    }

    @Test
    fun retiredUidsMoveToTheirCurrentReplacementAndDeduplicate() {
        val state = UserState(
            library = UserLibrary(
                comments = listOf(UserComment("MI000010", "moved")),
                favouriteFolders = listOf(
                    FavouriteFolder(1, "Folder", listOf("MI000010", "MI000020")),
                ),
                compare = CompareSelection(
                    listOf("MI000010", "MI000030"),
                    "MI000010",
                    "MI000030",
                ),
                nextFavouriteFolderId = 2,
            ),
        )
        val resolver = MachineUidResolver { uid ->
            when (uid) {
                "MI000010" -> MachineUidResolution("MI000020", "Old Machine")
                else -> MachineUidResolution(uid, "Current Machine")
            }
        }
        val result = UserStateReconciler.reconcile(state, resolver)

        assertEquals("MI000020", result.state.library.comments.single().machineUid)
        assertEquals(
            listOf("MI000020"),
            result.state.library.favouriteFolders.single().machineUids,
        )
        assertEquals(
            listOf("MI000020", "MI000030"),
            result.state.library.compare.machineUids,
        )
        assertEquals("MI000020", result.state.library.compare.leftUid)
        assertTrue(result.removedContent.isEmpty())
        val repeated = UserStateReconciler.reconcile(result.state, resolver)
        assertEquals(result.state, repeated.state)
        assertTrue(repeated.removedContent.isEmpty())
    }

    @Test
    fun commentCollisionKeepsCurrentCommentAndReportsTheRetiredText() {
        val state = UserState(
            library = UserLibrary(
                comments = listOf(
                    UserComment("MI000010", "old text"),
                    UserComment("MI000020", "current text"),
                ),
            ),
        )
        val result = UserStateReconciler.reconcile(state, MachineUidResolver { uid ->
            if (uid == "MI000010") {
                MachineUidResolution("MI000020", "Old Machine")
            } else {
                MachineUidResolution(uid, "Current Machine")
            }
        })

        assertEquals(
            listOf(UserComment("MI000020", "current text")),
            result.state.library.comments,
        )
        assertEquals(1, result.removedContent.size)
        assertTrue(result.removedContent.single().value.contains("old text"))
        assertTrue(result.removedContent.single().value.contains("Old Machine"))
    }
}
