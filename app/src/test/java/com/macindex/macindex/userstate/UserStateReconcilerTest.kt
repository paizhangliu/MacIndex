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
        val resolver = MachineNameResolver { uid ->
            if (uid == "MI000001") "Current" else null
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
            MachineNameResolver { "Machine" },
        )

        assertTrue(result.removedContent.isEmpty())
        assertEquals(state, result.state)
    }

    @Test
    fun resetNoticeSurvivesLaterReconciliation() {
        val state = UserState(
            pendingNotice = PendingUserNotice(entireUserStateWasReset = true),
        )
        val result = UserStateReconciler.reconcile(state, MachineNameResolver { null })
        assertTrue(result.state.pendingNotice!!.entireUserStateWasReset)
    }
}
