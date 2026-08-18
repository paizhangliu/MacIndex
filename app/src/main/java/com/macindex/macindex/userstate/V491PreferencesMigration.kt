package com.macindex.macindex.userstate

import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import com.macindex.macindex.userstate.proto.AppState
import java.io.IOException
import java.util.Locale

/** Imports the preference formats shipped by 4.9.1. */
class V491PreferencesMigration(
    private val preferences: SharedPreferences,
    private val resolveLegacyMachineUid: (String) -> String?,
) : DataMigration<AppState> {
    override suspend fun shouldMigrate(currentData: AppState): Boolean =
        preferences.all.isNotEmpty()

    override suspend fun migrate(currentData: AppState): AppState = mergeLegacyMigration(
        currentData,
        V491StateConverter.convert(preferences.all, resolveLegacyMachineUid),
    )

    override suspend fun cleanUp() {
        if (!preferences.edit().clear().commit()) {
            throw IOException("Unable to remove migrated 4.9.1 preferences")
        }
    }

    companion object {
        const val LEGACY_PREFERENCES_NAME = "MacIndex_Preference"
    }
}

/** A restored 4.9.1 preference file must never overwrite committed 5.0 state. */
internal fun mergeLegacyMigration(currentData: AppState, legacyState: UserState): AppState {
    if (currentData == AppStateProtoMapper.defaultProto()) {
        return AppStateProtoMapper.toProto(legacyState)
    }
    return currentData
}

internal object V491StateConverter {
    fun convert(values: Map<String, *>, resolveLegacyMachineUid: (String) -> String?): UserState {
        val comments = values.legacyRecord("userComments").let { record ->
            if (record.invalid) discardedComments(record.value)
            else parseComments(record.value, resolveLegacyMachineUid)
        }
        val favourites = values.legacyRecord("userFavourites").let { record ->
            if (record.invalid) discardedFavourites(record.value)
            else parseFavourites(record.value, resolveLegacyMachineUid)
        }
        val compareList = values.legacyRecord("userCompares")
        val compareLeft = values.legacyRecord("userComparesLeft")
        val compareRight = values.legacyRecord("userComparesRight")
        val compare = if (compareList.invalid || compareLeft.invalid || compareRight.invalid) {
            discardedCompare(legacyCompareRecord(
                compareList.value,
                compareLeft.value,
                compareRight.value,
            ))
        } else {
            parseCompare(
                compareList.value,
                compareLeft.value,
                compareRight.value,
                resolveLegacyMachineUid,
            )
        }
        val removed = comments.removed + favourites.removed + compare.removed
        val importedPreferences = UserPreferences(
            sortComments = values.boolean("isSortComment", false),
            playDeathSound = values.boolean("isPlayDeathSound", true),
            enableVolumeWarning = values.boolean("isEnableVolWarning", true),
            useNavigationButtons = values.boolean("isUseNavButtons", true),
            fixedNavigation = values.boolean("isFixedNav", false),
            limitRandomToCurrentBrowse = values.boolean("isRandomAll", false),
            rememberMainState = values.boolean("isSaveMainUsage", true),
            rememberCompareState = values.boolean("isSaveCompareUsage", true),
            highlightCompareDifferences = true,
            automaticallyCheckUpdates = values.boolean("isAutoCheckUpdate", true),
            skippedUpdateVersion = values.string("skippedUpdateVersion"),
        )
        val library = UserLibrary(
            comments = comments.value,
            favouriteFolders = favourites.value,
            compare = if (importedPreferences.rememberCompareState) {
                compare.value
            } else {
                compare.value.copy(leftUid = "", rightUid = "")
            },
            nextFavouriteFolderId = (favourites.value.maxOfOrNull { it.id } ?: 0) + 1,
        )
        return UserState(
            preferences = importedPreferences,
            uiMemory = if (importedPreferences.rememberMainState) {
                UiMemory(
                    mainScope = parseManufacturer(values.string("lastMainManufacturer")),
                    mainGrouping = when (values.string("lastMainFilter")) {
                        "processors" -> BrowseGrouping.PROCESSORS
                        "years" -> BrowseGrouping.YEARS
                        else -> BrowseGrouping.NAMES
                    },
                )
            } else {
                UiMemory()
            },
            library = library,
            pendingNotice = removed.takeIf { it.isNotEmpty() }?.let {
                PendingUserNotice(removedContent = it)
            },
        )
    }

    private fun parseComments(
        raw: String,
        resolveLegacyMachineUid: (String) -> String?,
    ): Parsed<List<UserComment>> {
        if (raw.isEmpty()) return Parsed(emptyList())
        return try {
            val comments = mutableListOf<UserComment>()
            val removed = mutableListOf<RemovedUserContent>()
            val seenNames = mutableSetOf<String>()
            val seenUids = mutableSetOf<String>()
            splitPreservingEmpty(raw, "││").forEach { entry ->
                val parts = splitPreservingEmpty(entry, "│")
                if (parts.size != 2 || parts[0].isEmpty() ||
                    parts[0] != UserStateValidator.protocolTrim(parts[0]) ||
                    parts[1].isEmpty() || parts[1] != UserStateValidator.protocolTrim(parts[1]) ||
                    parts[1].length > UserStateLimits.MAX_COMMENT_LENGTH ||
                    !seenNames.add(parts[0].lowercase(Locale.ROOT))
                ) {
                    throw InvalidUserDataException("Invalid 4.9.1 comments")
                }
                UserStateValidator.normalizeComment(parts[1])
                val uid = resolveLegacyName(parts[0], resolveLegacyMachineUid)
                if (uid == null || !seenUids.add(uid)) {
                    removed += RemovedUserContent(RemovedContentKind.COMMENT, entry)
                } else {
                    comments += UserComment(uid, parts[1])
                }
            }
            Parsed(comments, removed)
        } catch (_: InvalidUserDataException) {
            discardedComments(raw)
        }
    }

    private fun parseFavourites(
        raw: String,
        resolveLegacyMachineUid: (String) -> String?,
    ): Parsed<List<FavouriteFolder>> {
        if (raw.isEmpty()) return Parsed(emptyList())
        return try {
            val rawFolders = splitPreservingEmpty(raw, "││")
            if (rawFolders.firstOrNull() != "" || rawFolders.size > UserStateLimits.MAX_FOLDERS + 1) {
                throw InvalidUserDataException("Invalid 4.9.1 favourites")
            }
            val folders = mutableListOf<FavouriteFolder>()
            val removed = mutableListOf<RemovedUserContent>()
            val folderNames = mutableSetOf<String>()
            rawFolders.drop(1).forEachIndexed { index, rawFolder ->
                val entries = splitPreservingEmpty(rawFolder, "│")
                val marker = entries.firstOrNull().orEmpty()
                if (marker.length < 2 || !marker.startsWith("{") || !marker.endsWith("}")) {
                    throw InvalidUserDataException("Invalid 4.9.1 favourite folder")
                }
                val rawName = marker.substring(1, marker.length - 1)
                val name = UserStateValidator.normalizeFolderName(rawName)
                if (!folderNames.add(name)) {
                    throw InvalidUserDataException("Duplicate 4.9.1 favourite folder")
                }
                val seenNames = mutableSetOf<String>()
                val seenUids = mutableSetOf<String>()
                val machineUids = mutableListOf<String>()
                entries.drop(1).forEach { entry ->
                    if (entry.length < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                        throw InvalidUserDataException("Invalid 4.9.1 favourite")
                    }
                    val machineName = entry.substring(1, entry.length - 1)
                    if (machineName.isEmpty() ||
                        machineName != UserStateValidator.protocolTrim(machineName) ||
                        !seenNames.add(machineName.lowercase(Locale.ROOT))
                    ) {
                        throw InvalidUserDataException("Invalid 4.9.1 favourite")
                    }
                    val uid = resolveLegacyName(machineName, resolveLegacyMachineUid)
                    if (uid == null || !seenUids.add(uid)) {
                        removed += RemovedUserContent(
                            RemovedContentKind.FAVOURITE,
                            "$marker│$entry",
                        )
                    } else {
                        machineUids += uid
                    }
                }
                folders += FavouriteFolder(index + 1L, name, machineUids)
            }
            Parsed(folders, removed)
        } catch (_: InvalidUserDataException) {
            discardedFavourites(raw)
        }
    }

    private fun parseCompare(
        rawList: String,
        rawLeft: String,
        rawRight: String,
        resolveLegacyMachineUid: (String) -> String?,
    ): Parsed<CompareSelection> {
        return try {
            val machineUids = mutableListOf<String>()
            val rawMachineNames = mutableSetOf<String>()
            val canonicalUids = mutableSetOf<String>()
            val removed = mutableListOf<RemovedUserContent>()
            if (rawList.isNotEmpty()) {
                val entries = splitPreservingEmpty(rawList, "│")
                if (entries.size > UserStateLimits.MAX_COMPARE_MACHINES) {
                    throw InvalidUserDataException("Too many 4.9.1 compare machines")
                }
                entries.forEach { entry ->
                    if (entry.length < 3 || !entry.startsWith("[") || !entry.endsWith("]")) {
                        throw InvalidUserDataException("Invalid 4.9.1 compare machine")
                    }
                    val machineName = entry.substring(1, entry.length - 1)
                    val normalizedName = machineName.lowercase(Locale.ROOT)
                    if (machineName.isEmpty() ||
                        machineName != UserStateValidator.protocolTrim(machineName) ||
                        !rawMachineNames.add(normalizedName)
                    ) {
                        throw InvalidUserDataException("Invalid 4.9.1 compare machine")
                    }
                    val uid = resolveLegacyName(machineName, resolveLegacyMachineUid)
                    if (uid == null || !canonicalUids.add(uid)) {
                        removed += RemovedUserContent(RemovedContentKind.COMPARE, entry)
                    } else {
                        machineUids += uid
                    }
                }
            }

            val emptySelection = rawLeft.isEmpty() && rawRight.isEmpty()
            if (!emptySelection && (rawLeft.isEmpty() || rawRight.isEmpty() ||
                    rawLeft != UserStateValidator.protocolTrim(rawLeft) ||
                    rawRight != UserStateValidator.protocolTrim(rawRight) ||
                    rawLeft.equals(rawRight, ignoreCase = true) ||
                    rawLeft.lowercase(Locale.ROOT) !in rawMachineNames ||
                    rawRight.lowercase(Locale.ROOT) !in rawMachineNames)
            ) {
                throw InvalidUserDataException("Invalid 4.9.1 compare selection")
            }
            val leftUid = resolveLegacyName(rawLeft, resolveLegacyMachineUid).orEmpty()
            val rightUid = resolveLegacyName(rawRight, resolveLegacyMachineUid).orEmpty()
            val selectionIsValid = emptySelection ||
                (leftUid.isNotEmpty() && rightUid.isNotEmpty() && leftUid != rightUid &&
                    leftUid in machineUids && rightUid in machineUids)
            if (!selectionIsValid) {
                removed += RemovedUserContent(
                    RemovedContentKind.COMPARE,
                    "[$rawLeft]│[$rawRight]",
                )
            }
            Parsed(
                CompareSelection(
                    machineUids,
                    if (selectionIsValid) leftUid else "",
                    if (selectionIsValid) rightUid else "",
                ),
                removed,
            )
        } catch (_: InvalidUserDataException) {
            discardedCompare(legacyCompareRecord(rawList, rawLeft, rawRight))
        }
    }

    private fun parseManufacturer(raw: String): BrowseScope = when (raw) {
        "apple68k" -> BrowseScope.APPLE_68K
        "appleppc" -> BrowseScope.POWERPC
        "appleintel" -> BrowseScope.INTEL
        "applearm" -> BrowseScope.APPLE_SILICON
        else -> BrowseScope.ALL
    }

    private fun resolveLegacyName(
        name: String,
        resolveLegacyMachineUid: (String) -> String?,
    ): String? {
        if (name.isEmpty()) return null
        return resolveLegacyMachineUid(UserStateValidator.normalizeLegacyName(name))
            ?.takeIf(UserStateLimits.MACHINE_UID::matches)
    }

    private fun legacyCompareRecord(rawList: String, rawLeft: String, rawRight: String) =
        "$rawList\n[$rawLeft]│[$rawRight]"

    private fun discardedComments(raw: String) = Parsed(
        value = emptyList<UserComment>(),
        removed = listOf(RemovedUserContent(RemovedContentKind.COMMENT, raw)),
    )

    private fun discardedFavourites(raw: String) = Parsed(
        value = emptyList<FavouriteFolder>(),
        removed = listOf(RemovedUserContent(RemovedContentKind.FAVOURITE, raw)),
    )

    private fun discardedCompare(raw: String) = Parsed(
        value = CompareSelection(),
        removed = listOf(RemovedUserContent(RemovedContentKind.COMPARE, raw)),
    )

    private fun splitPreservingEmpty(value: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (true) {
            val position = value.indexOf(delimiter, start)
            if (position < 0) {
                result += value.substring(start)
                return result
            }
            result += value.substring(start, position)
            start = position + delimiter.length
        }
    }

    private fun Map<String, *>.boolean(key: String, default: Boolean) = this[key] as? Boolean ?: default
    private fun Map<String, *>.string(key: String) = this[key] as? String ?: ""
    private fun Map<String, *>.legacyRecord(key: String): LegacyRecord {
        val stored = this[key] ?: return LegacyRecord("")
        return if (stored is String) LegacyRecord(stored) else LegacyRecord(stored.toString(), true)
    }

    private data class Parsed<T>(
        val value: T,
        val removed: List<RemovedUserContent> = emptyList(),
    )

    private data class LegacyRecord(
        val value: String,
        val invalid: Boolean = false,
    )
}
