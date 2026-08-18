package com.macindex.macindex.userstate

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import com.macindex.macindex.userstate.proto.AppState
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AppStateRepository(
    private val dataStore: DataStore<AppState>,
    private val commandScope: CoroutineScope,
) {
    private val logger = Logger.getLogger("UserState")
    private val commandQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        commandScope.launch {
            for (execute in commandQueue) {
                execute()
            }
        }
    }

    val state: Flow<UserState> = dataStore.data
        .map { proto -> applyPersistencePolicy(AppStateProtoMapper.toDomain(proto)) }
        .catch { exception ->
            if (exception is IOException) {
                throw UserStateUnavailableException("Unable to read app state", exception)
            }
            throw exception
        }

    suspend fun snapshot(): UserState = try {
        applyPersistencePolicy(AppStateProtoMapper.toDomain(dataStore.data.first()))
    } catch (exception: IOException) {
        throw UserStateUnavailableException("Unable to read app state", exception)
    }

    suspend fun setSortComments(enabled: Boolean) = updatePreferences {
        copy(sortComments = enabled)
    }

    suspend fun setPlayDeathSound(enabled: Boolean) = updatePreferences {
        copy(playDeathSound = enabled)
    }

    suspend fun setVolumeWarning(enabled: Boolean) = updatePreferences {
        copy(enableVolumeWarning = enabled)
    }

    suspend fun setUseNavigationButtons(enabled: Boolean) = updatePreferences {
        copy(useNavigationButtons = enabled)
    }

    suspend fun setFixedNavigation(enabled: Boolean) = updatePreferences {
        copy(fixedNavigation = enabled)
    }

    suspend fun setLimitRandomToCurrentBrowse(enabled: Boolean) = updatePreferences {
        copy(limitRandomToCurrentBrowse = enabled)
    }

    suspend fun setRememberMainState(enabled: Boolean): UserState = update { state ->
        state.copy(
            preferences = state.preferences.copy(rememberMainState = enabled),
            uiMemory = if (enabled) state.uiMemory else UiMemory(),
        )
    }

    suspend fun setRememberCompareState(enabled: Boolean): UserState = update { state ->
        state.copy(
            preferences = state.preferences.copy(rememberCompareState = enabled),
        )
    }

    suspend fun setHighlightCompareDifferences(enabled: Boolean) = updatePreferences {
        copy(highlightCompareDifferences = enabled)
    }

    suspend fun setAutomaticUpdateChecks(enabled: Boolean) = updatePreferences {
        copy(automaticallyCheckUpdates = enabled)
    }

    suspend fun setSkippedUpdateVersion(version: String) = updatePreferences {
        copy(skippedUpdateVersion = version)
    }

    suspend fun setMainBrowseState(
        scope: BrowseScope,
        grouping: BrowseGrouping,
    ): UserState = update { state ->
        state.copy(uiMemory = state.uiMemory.copy(
            mainScope = scope,
            mainGrouping = grouping,
        ))
    }

    suspend fun setComment(machineUid: String, text: String?): UserState {
        UserStateValidator.requireMachineUid(machineUid)
        val normalizedText = text?.let(UserStateValidator::protocolTrim).orEmpty()
        if (normalizedText.isNotEmpty()) {
            UserStateValidator.normalizeComment(normalizedText)
        }
        return update { state ->
            val comments = state.library.comments.toMutableList()
            val position = comments.indexOfFirst { it.machineUid == machineUid }
            if (position >= 0) comments.removeAt(position)
            if (normalizedText.isNotEmpty()) {
                comments.add(
                    if (position >= 0) position else 0,
                    UserComment(machineUid, normalizedText),
                )
            }
            state.copy(library = state.library.copy(comments = comments))
        }
    }

    suspend fun clearComments(): UserState = update { state ->
        state.copy(library = state.library.copy(comments = emptyList()))
    }

    suspend fun removeComments(machineUids: Set<String>): UserState {
        machineUids.forEach(UserStateValidator::requireMachineUid)
        return update { state ->
            state.copy(library = state.library.copy(
                comments = state.library.comments.filterNot { it.machineUid in machineUids },
            ))
        }
    }

    suspend fun createFavouriteFolder(name: String): Long {
        val normalized = UserStateValidator.normalizeFolderName(name)
        var folderId = 0L
        update { state ->
            val library = state.library
            if (library.favouriteFolders.any { it.name == normalized }) {
                throw InvalidUserDataException("Favourite folder already exists")
            }
            if (library.favouriteFolders.size >= UserStateLimits.MAX_FOLDERS) {
                throw InvalidUserDataException("Too many favourite folders")
            }
            folderId = library.nextFavouriteFolderId
            state.copy(library = library.copy(
                favouriteFolders = listOf(FavouriteFolder(folderId, normalized, emptyList())) +
                    library.favouriteFolders,
                nextFavouriteFolderId = Math.addExact(folderId, 1),
            ))
        }
        return folderId
    }

    suspend fun renameFavouriteFolder(folderId: Long, name: String): UserState {
        val normalized = UserStateValidator.normalizeFolderName(name)
        return update { state ->
            requireFolder(state.library, folderId)
            if (state.library.favouriteFolders.any {
                    it.id != folderId && it.name == normalized
                }
            ) {
                throw InvalidUserDataException("Favourite folder already exists")
            }
            state.copy(library = state.library.copy(
                favouriteFolders = state.library.favouriteFolders.map { folder ->
                    if (folder.id == folderId) folder.copy(name = normalized) else folder
                },
            ))
        }
    }

    suspend fun deleteFavouriteFolders(folderIds: Set<Long>): UserState = update { state ->
        val existingIds = state.library.favouriteFolders.mapTo(mutableSetOf()) { it.id }
        if (!existingIds.containsAll(folderIds)) {
            throw InvalidUserDataException("Unknown favourite folder")
        }
        state.copy(library = state.library.copy(
            favouriteFolders = state.library.favouriteFolders.filterNot { it.id in folderIds },
        ))
    }

    suspend fun setFavouriteMembership(
        machineUid: String,
        selectedFolderIds: Set<Long>,
    ): UserState {
        UserStateValidator.requireMachineUid(machineUid)
        return update { state ->
            val existingIds = state.library.favouriteFolders.mapTo(mutableSetOf()) { it.id }
            if (!existingIds.containsAll(selectedFolderIds)) {
                throw InvalidUserDataException("Unknown favourite folder")
            }
            state.copy(library = state.library.copy(
                favouriteFolders = state.library.favouriteFolders.map { folder ->
                    val machines = folder.machineUids.filterNot { it == machineUid }
                    folder.copy(machineUids = if (folder.id in selectedFolderIds) {
                        listOf(machineUid) + machines
                    } else {
                        machines
                    })
                },
            ))
        }
    }

    suspend fun clearFavouriteFolders(): UserState = update { state ->
        state.copy(library = state.library.copy(favouriteFolders = emptyList()))
    }

    suspend fun addCompareMachine(machineUid: String): UserState {
        UserStateValidator.requireMachineUid(machineUid)
        return update { state ->
            val compare = state.library.compare
            if (machineUid in compare.machineUids) return@update state
            if (compare.machineUids.size >= UserStateLimits.MAX_COMPARE_MACHINES) {
                throw InvalidUserDataException("Compare list is full")
            }
            state.copy(library = state.library.copy(
                compare = compare.copy(machineUids = compare.machineUids + machineUid),
            ))
        }
    }

    suspend fun removeCompareMachine(machineUid: String): UserState =
        removeCompareMachines(setOf(machineUid))

    suspend fun removeCompareMachines(machineUids: Set<String>): UserState {
        machineUids.forEach(UserStateValidator::requireMachineUid)
        return update { state ->
            removeCompareMachines(state, machineUids)
        }
    }

    private fun removeCompareMachines(state: UserState, machineUids: Set<String>): UserState {
        val compare = state.library.compare
        val machines = compare.machineUids.filterNot { it in machineUids }
        val selectionRemainsValid = compare.leftUid in machines && compare.rightUid in machines &&
            compare.leftUid != compare.rightUid
        return state.copy(library = state.library.copy(
            compare = compare.copy(
                machineUids = machines,
                leftUid = if (selectionRemainsValid) compare.leftUid else "",
                rightUid = if (selectionRemainsValid) compare.rightUid else "",
            ),
        ))
    }

    suspend fun setCompareSelection(leftUid: String, rightUid: String): UserState {
        UserStateValidator.requireMachineUid(leftUid)
        UserStateValidator.requireMachineUid(rightUid)
        return update { state ->
            val compare = state.library.compare
            if (leftUid == rightUid || leftUid !in compare.machineUids ||
                rightUid !in compare.machineUids
            ) {
                throw InvalidUserDataException("Invalid compare selection")
            }
            state.copy(library = state.library.copy(
                compare = compare.copy(leftUid = leftUid, rightUid = rightUid),
            ))
        }
    }

    suspend fun swapCompareSelection(): UserState = update { state ->
        val compare = state.library.compare
        if (compare.leftUid.isEmpty() || compare.rightUid.isEmpty()) return@update state
        state.copy(library = state.library.copy(
            compare = compare.copy(leftUid = compare.rightUid, rightUid = compare.leftUid),
        ))
    }

    suspend fun clearCompareSelection(): UserState = update { state ->
        state.copy(library = state.library.copy(
            compare = state.library.compare.copy(leftUid = "", rightUid = ""),
        ))
    }

    suspend fun clearCompareList(): UserState = update { state ->
        state.copy(library = state.library.copy(compare = CompareSelection()))
    }

    suspend fun prepareImport(
        raw: String,
        resolver: MachineNameResolver,
    ): PreparedUserDataImport = UserDataJsonCodec.prepareImport(raw, resolver)

    suspend fun applyImport(imported: PreparedUserDataImport): UserState {
        UserStateValidator.validateLibrary(imported.library)
        return update { state ->
            var nextFolderId = state.library.nextFavouriteFolderId
            val rekeyedFolders = imported.library.favouriteFolders.map { folder ->
                val importedFolder = folder.copy(id = nextFolderId)
                nextFolderId = Math.addExact(nextFolderId, 1L)
                importedFolder
            }
            val replacementLibrary = imported.library.copy(
                favouriteFolders = rekeyedFolders,
                nextFavouriteFolderId = nextFolderId,
            )
            state.copy(
                library = replacementLibrary,
            )
        }
    }

    suspend fun exportJson(): String = UserDataJsonCodec.export(snapshot())

    suspend fun reconcile(resolver: MachineNameResolver) {
        update { state ->
            UserStateReconciler.reconcile(state, resolver).state
        }
    }

    suspend fun acknowledgePendingNotice(): UserState = update { state ->
        state.copy(pendingNotice = null)
    }

    /**
     * A confirmed durable command belongs to the process repository, not to the Activity that
     * initiated it. The UI may stop waiting after destruction, but the atomic DataStore write
     * must still complete.
     */
    internal fun <T> submit(command: UserStateCommand<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        val accepted = commandQueue.trySend {
            try {
                result.complete(command.execute(this@AppStateRepository))
            } catch (cancelled: CancellationException) {
                result.cancel(cancelled)
            } catch (failure: UserStateUnavailableException) {
                logger.log(Level.SEVERE, "A durable saved-data command failed.", failure)
                result.completeExceptionally(failure)
            } catch (failure: InvalidUserDataException) {
                logger.log(Level.SEVERE, "A durable saved-data command was rejected.", failure)
                result.completeExceptionally(failure)
            }
        }
        if (accepted.isFailure) {
            val failure = UserStateUnavailableException(
                "User-state command queue is unavailable",
            )
            logger.log(Level.SEVERE,
                "A durable saved-data command could not be queued.", failure)
            result.completeExceptionally(failure)
        }
        return result
    }

    private suspend fun updatePreferences(
        transform: UserPreferences.() -> UserPreferences,
    ): UserState = update { state ->
        state.copy(preferences = state.preferences.transform())
    }

    private suspend fun update(transform: (UserState) -> UserState): UserState = try {
        val updated = dataStore.updateData { currentProto ->
            val stored = AppStateProtoMapper.toDomain(currentProto)
            val current = applyPersistencePolicy(stored)
            val next = applyPersistencePolicy(transform(current))
            if (next == stored) currentProto else AppStateProtoMapper.toProto(next)
        }
        applyPersistencePolicy(AppStateProtoMapper.toDomain(updated))
    } catch (exception: IOException) {
        throw UserStateUnavailableException("Unable to write app state", exception)
    }

    private fun applyPersistencePolicy(state: UserState): UserState {
        val compare = state.library.compare
        if (state.preferences.rememberCompareState ||
            compare.leftUid.isEmpty() && compare.rightUid.isEmpty()
        ) {
            return state
        }
        return state.copy(library = state.library.copy(
            compare = compare.copy(leftUid = "", rightUid = ""),
        ))
    }

    private fun requireFolder(library: UserLibrary, folderId: Long) {
        if (library.favouriteFolders.none { it.id == folderId }) {
            throw InvalidUserDataException("Unknown favourite folder")
        }
    }

}

object AppStateStoreFactory {
    private const val FILE_NAME = "app_state.pb"

    fun create(
        context: Context,
        resolveLegacyMachineUid: (String) -> String?,
        scope: CoroutineScope,
    ): AppStateRepository {
        val applicationContext = context.applicationContext
        val legacyPreferences = applicationContext.getSharedPreferences(
            V491PreferencesMigration.LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val store = DataStoreFactory.create(
            serializer = AppStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler {
                AppStateProtoMapper.corruptionResetProto()
            },
            migrations = listOf(V491PreferencesMigration(
                legacyPreferences,
                resolveLegacyMachineUid,
            )),
            scope = scope,
            produceFile = { applicationContext.dataStoreFile(FILE_NAME) },
        )
        return AppStateRepository(store, scope)
    }
}
