package com.macindex.macindex.userstate

import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope

enum class Appearance {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreferences(
    val sortComments: Boolean = false,
    val playDeathSound: Boolean = true,
    val enableVolumeWarning: Boolean = true,
    val useNavigationButtons: Boolean = true,
    val fixedNavigation: Boolean = false,
    val limitRandomToCurrentBrowse: Boolean = false,
    val rememberMainState: Boolean = true,
    val rememberCompareState: Boolean = true,
    val highlightCompareDifferences: Boolean = true,
    val automaticallyCheckUpdates: Boolean = true,
    val skippedUpdateVersion: String = "",
)

data class UiMemory(
    val mainScope: BrowseScope = BrowseScope.ALL,
    val mainGrouping: BrowseGrouping = BrowseGrouping.NAMES,
)

data class UserComment(
    val machineUid: String,
    val text: String,
)

data class FavouriteFolder(
    val id: Long,
    val name: String,
    val machineUids: List<String>,
)

data class CompareSelection(
    val machineUids: List<String> = emptyList(),
    val leftUid: String = "",
    val rightUid: String = "",
)

data class UserLibrary(
    val comments: List<UserComment> = emptyList(),
    val favouriteFolders: List<FavouriteFolder> = emptyList(),
    val compare: CompareSelection = CompareSelection(),
    val nextFavouriteFolderId: Long = 1,
)

enum class RemovedContentKind {
    COMMENT,
    FAVOURITE,
    COMPARE,
}

data class RemovedUserContent(
    val kind: RemovedContentKind,
    val value: String,
)

data class PendingUserNotice(
    val entireUserStateWasReset: Boolean = false,
    val removedContent: List<RemovedUserContent> = emptyList(),
)

data class UserState(
    val preferences: UserPreferences = UserPreferences(),
    val uiMemory: UiMemory = UiMemory(),
    val library: UserLibrary = UserLibrary(),
    val pendingNotice: PendingUserNotice? = null,
)

fun interface MachineNameResolver {
    /** Returns the current display name, or null when the UID no longer exists. */
    fun resolveDisplayName(uid: String): String?
}

class PreparedUserDataImport internal constructor(
    val library: UserLibrary,
    val removedCount: Int,
) {
    val commentCount: Int = library.comments.size
    val folderCount: Int = library.favouriteFolders.size
    val favouriteCount: Int = library.favouriteFolders.sumOf { it.machineUids.size }
    val compareCount: Int = library.compare.machineUids.size
}

class InvalidUserDataException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class UserStateUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal object UserStateLimits {
    const val MAX_COMMENT_LENGTH = 500
    const val MAX_FOLDERS = 15
    const val MAX_FOLDER_NAME_LENGTH = 30
    const val MAX_COMPARE_MACHINES = 10
    const val MAX_IMPORT_BYTES = 1024 * 1024
    val MACHINE_UID = Regex("MI\\d{6}")
}
