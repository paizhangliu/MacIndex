package com.macindex.macindex.userstate

import java.util.Locale

internal object UserStateValidator {
    fun requireMachineUid(uid: String) {
        if (!UserStateLimits.MACHINE_UID.matches(uid)) {
            throw InvalidUserDataException("Invalid machine UID")
        }
    }

    fun normalizeComment(text: String): String {
        requireWellFormedUtf16(text)
        val normalized = protocolTrim(text)
        if (normalized.isEmpty() || normalized.length > UserStateLimits.MAX_COMMENT_LENGTH) {
            throw InvalidUserDataException("Invalid comment")
        }
        return normalized
    }

    fun normalizeFolderName(name: String): String {
        requireWellFormedUtf16(name)
        val normalized = protocolTrim(name)
        if (normalized.isEmpty() || normalized != name ||
            normalized.length > UserStateLimits.MAX_FOLDER_NAME_LENGTH ||
            normalized.contains('\n')
        ) {
            throw InvalidUserDataException("Invalid favourite folder name")
        }
        return normalized
    }

    fun validateLibrary(library: UserLibrary) {
        if (library.favouriteFolders.size > UserStateLimits.MAX_FOLDERS) {
            throw InvalidUserDataException("Too many favourite folders")
        }
        if (library.nextFavouriteFolderId <= 0) {
            throw InvalidUserDataException("Invalid next favourite folder ID")
        }

        val commentUids = mutableSetOf<String>()
        library.comments.forEach { comment ->
            requireMachineUid(comment.machineUid)
            if (normalizeComment(comment.text) != comment.text) {
                throw InvalidUserDataException("Comment is not in canonical form")
            }
            if (!commentUids.add(comment.machineUid)) {
                throw InvalidUserDataException("Duplicate comment")
            }
        }

        val folderIds = mutableSetOf<Long>()
        val folderNames = mutableSetOf<String>()
        library.favouriteFolders.forEach { folder ->
            if (folder.id <= 0 || !folderIds.add(folder.id)) {
                throw InvalidUserDataException("Invalid favourite folder ID")
            }
            val name = normalizeFolderName(folder.name)
            if (!folderNames.add(name)) {
                throw InvalidUserDataException("Duplicate favourite folder name")
            }
            val machineUids = mutableSetOf<String>()
            folder.machineUids.forEach { uid ->
                requireMachineUid(uid)
                if (!machineUids.add(uid)) {
                    throw InvalidUserDataException("Duplicate favourite")
                }
            }
        }
        val greatestFolderId = library.favouriteFolders.maxOfOrNull { it.id } ?: 0
        if (library.nextFavouriteFolderId <= greatestFolderId) {
            throw InvalidUserDataException("Favourite folder ID would be reused")
        }

        validateCompare(library.compare)
    }

    private fun validateCompare(compare: CompareSelection) {
        if (compare.machineUids.size > UserStateLimits.MAX_COMPARE_MACHINES) {
            throw InvalidUserDataException("Too many compare machines")
        }
        val machineUids = mutableSetOf<String>()
        compare.machineUids.forEach { uid ->
            requireMachineUid(uid)
            if (!machineUids.add(uid)) {
                throw InvalidUserDataException("Duplicate compare machine")
            }
        }
        val emptySelection = compare.leftUid.isEmpty() && compare.rightUid.isEmpty()
        val validSelection = UserStateLimits.MACHINE_UID.matches(compare.leftUid) &&
            UserStateLimits.MACHINE_UID.matches(compare.rightUid) &&
            compare.leftUid != compare.rightUid &&
            compare.leftUid in machineUids && compare.rightUid in machineUids
        if (!emptySelection && !validSelection) {
            throw InvalidUserDataException("Invalid compare selection")
        }
    }

    fun normalizeLegacyName(name: String): String {
        requireWellFormedUtf16(name)
        return protocolTrim(name).lowercase(Locale.ROOT)
    }

    fun requireWellFormedUtf16(value: String): String {
        var position = 0
        while (position < value.length) {
            val character = value[position]
            when {
                character.isHighSurrogate() -> {
                    if (position + 1 >= value.length || !value[position + 1].isLowSurrogate()) {
                        throw InvalidUserDataException("String contains an unpaired high surrogate")
                    }
                    position += 2
                }
                character.isLowSurrogate() ->
                    throw InvalidUserDataException("String contains an unpaired low surrogate")
                else -> position++
            }
        }
        return value
    }

    /** Matches java.lang.String.trim(), which defines both the 4.9.1 and schema-1 protocols. */
    fun protocolTrim(value: String): String {
        var start = 0
        var end = value.length
        while (start < end && value[start].code <= 0x20) start++
        while (end > start && value[end - 1].code <= 0x20) end--
        return if (start == 0 && end == value.length) value else value.substring(start, end)
    }
}
