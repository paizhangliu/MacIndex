package com.macindex.macindex.userstate

import androidx.datastore.core.DataStoreFactory
import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import com.macindex.macindex.userstate.proto.AppState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun semanticCommandsKeepLibraryInvariants() = withRepository { repository ->
        val firstFolder = repository.createFavouriteFolder("First")
        val secondFolder = repository.createFavouriteFolder("Second")
        repository.setFavouriteMembership("MI000001", setOf(firstFolder, secondFolder))
        repository.setComment("MI000001", "  note  ")
        repository.addCompareMachine("MI000001")
        repository.addCompareMachine("MI000002")
        repository.addCompareMachine("MI000003")
        repository.setCompareSelection("MI000001", "MI000002")
        repository.swapCompareSelection()

        val state = repository.snapshot()
        assertEquals("note", state.library.comments.single().text)
        assertTrue(state.library.favouriteFolders.all { "MI000001" in it.machineUids })
        assertEquals(
            listOf("MI000001", "MI000002", "MI000003"),
            state.library.compare.machineUids,
        )
        assertEquals("MI000002", state.library.compare.leftUid)
        assertEquals("MI000001", state.library.compare.rightUid)

        repository.removeCompareMachines(setOf("MI000002", "MI000003"))
        val afterRemoval = repository.snapshot().library.compare
        assertEquals(listOf("MI000001"), afterRemoval.machineUids)
        assertTrue(afterRemoval.leftUid.isEmpty())
        assertTrue(afterRemoval.rightUid.isEmpty())
    }

    @Test
    fun favouriteFolderCreationRejectsDuplicateName() = withRepository { repository ->
        repository.createFavouriteFolder("Existing")
        try {
            repository.createFavouriteFolder("Existing")
            fail("Expected duplicate favourite folder to be rejected")
        } catch (_: InvalidUserDataException) {
            // Expected.
        }
        assertEquals(
            listOf("Existing"),
            repository.snapshot().library.favouriteFolders.map { it.name },
        )
    }

    @Test
    fun submittedCommandOutlivesCancelledUiWaiter() = withRepository { repository ->
        coroutineScope {
            val durableCommand = repository.submit(UserStateCommand<UserState> { commandRepository ->
                delay(50)
                commandRepository.setSortComments(true)
            })
            val uiWaiter = launch { durableCommand.await() }
            uiWaiter.cancelAndJoin()

            assertTrue(durableCommand.await().preferences.sortComments)
            assertTrue(repository.snapshot().preferences.sortComments)
        }
    }

    @Test
    fun concurrentSemanticWritesDoNotLoseUpdates() = withRepository { repository ->
        coroutineScope {
            listOf(
                async { repository.setComment("MI000001", "first") },
                async { repository.setComment("MI000002", "second") },
                async { repository.setAutomaticUpdateChecks(false) },
            ).awaitAll()
        }

        val state = repository.snapshot()
        assertEquals(setOf("MI000001", "MI000002"),
            state.library.comments.map { it.machineUid }.toSet())
        assertFalse(state.preferences.automaticallyCheckUpdates)
    }

    @Test
    fun submittedCommandsCommitInCallOrder() = withRepository { repository ->
        val releaseFirst = CompletableDeferred<Unit>()
        val first = repository.submit(UserStateCommand { commandRepository ->
            releaseFirst.await()
            commandRepository.setSortComments(true)
        })
        val last = repository.submit(UserStateCommands.setSortComments(false))

        releaseFirst.complete(Unit)
        first.await()
        last.await()

        assertFalse(repository.snapshot().preferences.sortComments)
    }

    @Test
    fun failedSubmittedCommandDoesNotBlockItsSuccessor() = withRepository { repository ->
        val failed = repository.submit(UserStateCommand<UserState> {
            throw InvalidUserDataException("expected")
        })
        val successor = repository.submit(UserStateCommands.setSortComments(true))

        try {
            failed.await()
            fail("The first command must fail")
        } catch (_: InvalidUserDataException) {
            // Expected; the FIFO lane must continue to the next command.
        }
        successor.await()
        assertTrue(repository.snapshot().preferences.sortComments)
    }

    @Test
    fun submittedExportObservesAllPreviouslyConfirmedCommands() = withRepository { repository ->
        val releaseWrite = CompletableDeferred<Unit>()
        val write = repository.submit(UserStateCommand { commandRepository ->
            releaseWrite.await()
            commandRepository.setComment("MI000001", "latest")
        })
        val export = repository.submit(UserStateCommands.exportJson())

        releaseWrite.complete(Unit)
        write.await()
        val prepared = repository.prepareImport(
            export.await(),
            MachineNameResolver { "Machine" },
        )

        assertEquals("latest", prepared.library.comments.single().text)
    }

    @Test
    fun importReplacesLibraryWithoutRepeatingItsConfirmationNotice() = withRepository { repository ->
        repository.setComment("MI000009", "old")
        val source = UserLibrary(
            comments = listOf(UserComment("MI000001", "new")),
            favouriteFolders = listOf(FavouriteFolder(1, "Folder", listOf("MI000002"))),
            nextFavouriteFolderId = 2,
        )
        val prepared = repository.prepareImport(
            UserDataJsonCodec.export(source),
            MachineNameResolver { uid ->
                if (uid == "MI000002") null else "Machine"
            },
        )
        repository.applyImport(prepared)

        val imported = repository.snapshot()
        assertEquals(listOf("MI000001"), imported.library.comments.map { it.machineUid })
        assertTrue(imported.library.favouriteFolders.single().machineUids.isEmpty())
        assertNull(imported.pendingNotice)
    }

    @Test
    fun importAllocatesFreshFolderIdsWithoutReusingHistoricalIds() = withRepository(
        UserState(library = UserLibrary(nextFavouriteFolderId = 43)),
    ) { repository ->
        val imported = PreparedUserDataImport(
            library = UserLibrary(
                favouriteFolders = listOf(
                    FavouriteFolder(1, "First", emptyList()),
                    FavouriteFolder(2, "Second", emptyList()),
                ),
                nextFavouriteFolderId = 3,
            ),
            removedCount = 0,
        )

        val updated = repository.applyImport(imported)

        assertEquals(listOf(43L, 44L),
            updated.library.favouriteFolders.map { it.id })
        assertEquals(45L, updated.library.nextFavouriteFolderId)
        try {
            repository.setFavouriteMembership("MI000001", setOf(1L))
            fail("A historical folder ID must not resolve to an imported folder")
        } catch (_: InvalidUserDataException) {
            // Expected: folder IDs are process-local durable identities, not export data.
        }
    }

    @Test
    fun preferencesHaveProductDefaultsAndPersistAtomically() = withRepository { repository ->
        val defaults = repository.snapshot().preferences
        assertFalse(defaults.sortComments)
        assertTrue(defaults.playDeathSound)
        assertTrue(defaults.highlightCompareDifferences)
        assertTrue(defaults.automaticallyCheckUpdates)

        repository.setSortComments(true)
        repository.setAutomaticUpdateChecks(false)
        repository.setSkippedUpdateVersion("5.0.1")
        val updated = repository.snapshot().preferences
        assertTrue(updated.sortComments)
        assertFalse(updated.automaticallyCheckUpdates)
        assertEquals("5.0.1", updated.skippedUpdateVersion)
    }

    @Test
    fun disablingRememberPreferencesClearsOnlyTheirOwnedState() = withRepository { repository ->
        repository.setMainBrowseState(BrowseScope.POWERPC, BrowseGrouping.YEARS)
        repository.addCompareMachine("MI000001")
        repository.addCompareMachine("MI000002")
        repository.setCompareSelection("MI000001", "MI000002")

        val withoutMainMemory = repository.setRememberMainState(false)
        assertFalse(withoutMainMemory.preferences.rememberMainState)
        assertEquals(UiMemory(), withoutMainMemory.uiMemory)
        assertEquals("MI000001", withoutMainMemory.library.compare.leftUid)

        val withoutCompareMemory = repository.setRememberCompareState(false)
        assertFalse(withoutCompareMemory.preferences.rememberCompareState)
        assertEquals(
            listOf("MI000001", "MI000002"),
            withoutCompareMemory.library.compare.machineUids,
        )
        assertTrue(withoutCompareMemory.library.compare.leftUid.isEmpty())
        assertTrue(withoutCompareMemory.library.compare.rightUid.isEmpty())

        repository.setCompareSelection("MI000001", "MI000002")
        val durableState = repository.snapshot()
        assertFalse(durableState.preferences.rememberCompareState)
        assertEquals(
            listOf("MI000001", "MI000002"),
            durableState.library.compare.machineUids,
        )
        assertTrue(durableState.library.compare.leftUid.isEmpty())
        assertTrue(durableState.library.compare.rightUid.isEmpty())

        val memoryEnabledAgain = repository.setRememberCompareState(true)
        assertTrue(memoryEnabledAgain.library.compare.leftUid.isEmpty())
        assertTrue(memoryEnabledAgain.library.compare.rightUid.isEmpty())
        assertEquals(UiMemory(), repository.snapshot().uiMemory)
    }

    private fun withRepository(
        initialState: UserState = UserState(),
        block: suspend (AppStateRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temporaryFolder.newFolder(), "state.pb")
        val store = DataStoreFactory.create<AppState>(
            serializer = AppStateSerializer,
            scope = scope,
            produceFile = { file },
        )
        try {
            runBlocking {
                if (initialState != UserState()) {
                    store.updateData { AppStateProtoMapper.toProto(initialState) }
                }
                block(AppStateRepository(store, scope))
            }
        } finally {
            scope.cancel()
        }
    }
}
