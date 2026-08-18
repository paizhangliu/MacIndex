package com.macindex.macindex.userstate

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun interface UserStateCommand<T> {
    suspend fun execute(repository: AppStateRepository): T
}

fun interface UserStateSuccess<T> {
    fun onSuccess(value: T)
}

fun interface UserStateFailure {
    fun onFailure(error: Exception)
}

fun interface UserStateObserver {
    fun onStateChanged(state: UserState)
}

/** Java-facing lifecycle boundary for observing state and executing semantic commands. */
class UserStateLifecycleAdapter(
    private val owner: LifecycleOwner,
    private val repository: AppStateRepository,
    private val stateObserver: UserStateObserver,
    private val stateFailure: UserStateFailure,
) {
    private var lastPublishedState: UserState? = null

    init {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    repository.state.collect { state ->
                        // DataStore is a cold flow. A lifecycle restart replays its current
                        // value even when nothing changed, which must not be mistaken for a
                        // domain mutation by Views that rebuild their complete hierarchy.
                        if (state != lastPublishedState) {
                            lastPublishedState = state
                            stateObserver.onStateChanged(state)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: UserStateUnavailableException) {
                    // End this active collection after reporting the failure. repeatOnLifecycle
                    // starts a fresh collection after the owner leaves and re-enters STARTED.
                    stateFailure.onFailure(error)
                }
            }
        }
    }

    fun <T> execute(
        command: UserStateCommand<T>,
        success: UserStateSuccess<T>,
        failure: UserStateFailure,
    ) {
        val durableCommand = repository.submit(command)
        owner.lifecycleScope.launch {
            try {
                success.onSuccess(durableCommand.await())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: UserStateUnavailableException) {
                failure.onFailure(error)
            } catch (error: InvalidUserDataException) {
                failure.onFailure(error)
            }
        }
    }
}

/** Java-callable factories keep suspend implementation details out of Activities. */
object UserStateCommands {
    @JvmStatic fun setSortComments(value: Boolean) = command { setSortComments(value) }
    @JvmStatic fun setPlayDeathSound(value: Boolean) = command { setPlayDeathSound(value) }
    @JvmStatic fun setVolumeWarning(value: Boolean) = command { setVolumeWarning(value) }
    @JvmStatic fun setUseNavigationButtons(value: Boolean) = command { setUseNavigationButtons(value) }
    @JvmStatic fun setFixedNavigation(value: Boolean) = command { setFixedNavigation(value) }
    @JvmStatic fun setLimitRandomToCurrentBrowse(value: Boolean) =
        command { setLimitRandomToCurrentBrowse(value) }
    @JvmStatic fun setRememberMainState(value: Boolean) = command { setRememberMainState(value) }
    @JvmStatic fun setRememberCompareState(value: Boolean) = command { setRememberCompareState(value) }
    @JvmStatic fun setHighlightCompareDifferences(value: Boolean) = command { setHighlightCompareDifferences(value) }
    @JvmStatic fun setAutomaticUpdateChecks(value: Boolean) = command { setAutomaticUpdateChecks(value) }
    @JvmStatic fun setSkippedUpdateVersion(value: String) = command { setSkippedUpdateVersion(value) }

    @JvmStatic fun setMainBrowseState(scope: BrowseScope, grouping: BrowseGrouping) =
        command { setMainBrowseState(scope, grouping) }

    @JvmStatic fun setComment(machineUid: String, text: String?) =
        command { setComment(machineUid, text) }

    @JvmStatic fun clearComments() = command { clearComments() }
    @JvmStatic fun removeComments(machineUids: Set<String>) = command { removeComments(machineUids) }
    @JvmStatic fun createFavouriteFolder(name: String) = command { createFavouriteFolder(name) }
    @JvmStatic fun renameFavouriteFolder(folderId: Long, name: String) =
        command { renameFavouriteFolder(folderId, name) }

    @JvmStatic fun deleteFavouriteFolders(folderIds: Set<Long>) =
        command { deleteFavouriteFolders(folderIds) }

    @JvmStatic fun setFavouriteMembership(machineUid: String, selectedFolderIds: Set<Long>) =
        command { setFavouriteMembership(machineUid, selectedFolderIds) }

    @JvmStatic fun clearFavouriteFolders() = command { clearFavouriteFolders() }
    @JvmStatic fun addCompareMachine(machineUid: String) = command { addCompareMachine(machineUid) }
    @JvmStatic fun removeCompareMachine(machineUid: String) = command { removeCompareMachine(machineUid) }
    @JvmStatic fun removeCompareMachines(machineUids: Set<String>) =
        command { removeCompareMachines(machineUids) }
    @JvmStatic fun setCompareSelection(leftUid: String, rightUid: String) =
        command { setCompareSelection(leftUid, rightUid) }

    @JvmStatic fun swapCompareSelection() = command { swapCompareSelection() }
    @JvmStatic fun clearCompareSelection() = command { clearCompareSelection() }
    @JvmStatic fun clearCompareList() = command { clearCompareList() }
    @JvmStatic fun applyImport(imported: PreparedUserDataImport) = command { applyImport(imported) }
    @JvmStatic fun exportJson() = command { exportJson() }
    @JvmStatic fun acknowledgePendingNotice() = command { acknowledgePendingNotice() }

    private fun <T> command(block: suspend AppStateRepository.() -> T): UserStateCommand<T> =
        UserStateCommand { repository -> repository.block() }
}
