package com.macindex.macindex.userstate

import com.macindex.macindex.catalog.BrowseGrouping
import com.macindex.macindex.catalog.BrowseScope
import com.macindex.macindex.userstate.proto.AppState
import com.macindex.macindex.userstate.proto.CompareState
import com.macindex.macindex.userstate.proto.FavouriteFolder as ProtoFavouriteFolder
import com.macindex.macindex.userstate.proto.MainGrouping as ProtoMainGrouping
import com.macindex.macindex.userstate.proto.ManufacturerScope as ProtoManufacturerScope
import com.macindex.macindex.userstate.proto.PendingNotice as ProtoPendingNotice
import com.macindex.macindex.userstate.proto.RemovedContentKind as ProtoRemovedContentKind
import com.macindex.macindex.userstate.proto.RemovedUserContent as ProtoRemovedUserContent
import com.macindex.macindex.userstate.proto.UiMemory as ProtoUiMemory
import com.macindex.macindex.userstate.proto.UserComment as ProtoUserComment
import com.macindex.macindex.userstate.proto.UserLibrary as ProtoUserLibrary
import com.macindex.macindex.userstate.proto.UserPreferences as ProtoUserPreferences

internal object AppStateProtoMapper {
    fun defaultProto(): AppState = toProto(UserState())

    fun corruptionResetProto(): AppState = toProto(
        UserState(
            pendingNotice = PendingUserNotice(entireUserStateWasReset = true),
        ),
    )

    fun requireValidPersistedProto(proto: AppState) {
        if (!proto.hasPreferences() || !proto.hasUiMemory() || !proto.hasLibrary() ||
            !proto.library.hasCompare() || proto.library.nextFavouriteFolderId <= 0 ||
            proto.uiMemory.mainManufacturer == ProtoManufacturerScope.UNRECOGNIZED ||
            proto.uiMemory.mainGrouping == ProtoMainGrouping.UNRECOGNIZED ||
            (proto.hasPendingNotice() && proto.pendingNotice.removedContentList.any {
                it.kind == ProtoRemovedContentKind.REMOVED_CONTENT_KIND_UNSPECIFIED ||
                    it.kind == ProtoRemovedContentKind.UNRECOGNIZED
            })
        ) {
            throw InvalidUserDataException("Invalid persisted app state")
        }
        UserStateValidator.validateLibrary(toDomain(proto).library)
    }

    fun toDomain(proto: AppState): UserState {
        val preferences = proto.preferences.toDomain()
        val uiMemory = proto.uiMemory.toDomain()
        val library = proto.library.toDomain()
        val pendingNotice = if (proto.hasPendingNotice()) {
            proto.pendingNotice.toDomain().takeIf {
                it.entireUserStateWasReset || it.removedContent.isNotEmpty()
            }
        } else {
            null
        }
        return UserState(
            preferences = preferences,
            uiMemory = uiMemory,
            library = library,
            pendingNotice = pendingNotice,
            registeredAppVersionCode = proto.registeredAppVersionCode,
        )
    }

    fun toProto(state: UserState): AppState {
        UserStateValidator.validateLibrary(state.library)
        return AppState.newBuilder()
            .setPreferences(state.preferences.toProto())
            .setUiMemory(state.uiMemory.toProto())
            .setLibrary(state.library.toProto())
            .setRegisteredAppVersionCode(state.registeredAppVersionCode)
            .also { builder -> state.pendingNotice?.let { builder.setPendingNotice(it.toProto()) } }
            .build()
    }

    private fun ProtoUserPreferences.toDomain() = UserPreferences(
        sortComments = sortComments,
        playDeathSound = playDeathSound,
        enableVolumeWarning = enableVolumeWarning,
        useNavigationButtons = useNavigationButtons,
        fixedNavigation = fixedNavigation,
        limitRandomToCurrentBrowse = limitRandomToCurrentBrowse,
        rememberMainState = rememberMainState,
        rememberCompareState = rememberCompareState,
        highlightCompareDifferences = highlightCompareDifferences,
        automaticallyCheckUpdates = automaticallyCheckUpdates,
        skippedUpdateVersion = skippedUpdateVersion,
    )

    private fun UserPreferences.toProto() = ProtoUserPreferences.newBuilder()
        .setSortComments(sortComments)
        .setPlayDeathSound(playDeathSound)
        .setEnableVolumeWarning(enableVolumeWarning)
        .setUseNavigationButtons(useNavigationButtons)
        .setFixedNavigation(fixedNavigation)
        .setLimitRandomToCurrentBrowse(limitRandomToCurrentBrowse)
        .setRememberMainState(rememberMainState)
        .setRememberCompareState(rememberCompareState)
        .setHighlightCompareDifferences(highlightCompareDifferences)
        .setAutomaticallyCheckUpdates(automaticallyCheckUpdates)
        .setSkippedUpdateVersion(skippedUpdateVersion)
        .build()

    private fun ProtoUiMemory.toDomain() = UiMemory(
        mainScope = mainManufacturer.toDomain(),
        mainGrouping = mainGrouping.toDomain(),
    )

    private fun UiMemory.toProto() = ProtoUiMemory.newBuilder()
        .setMainManufacturer(mainScope.toProto())
        .setMainGrouping(mainGrouping.toProto())
        .build()

    private fun ProtoUserLibrary.toDomain() = UserLibrary(
        comments = commentsList.map { UserComment(it.machineUid, it.text) },
        favouriteFolders = favouriteFoldersList.map {
            FavouriteFolder(it.id, it.name, it.machineUidsList.toList())
        },
        compare = CompareSelection(
            compare.machineUidsList.toList(), compare.leftUid, compare.rightUid,
        ),
        nextFavouriteFolderId = nextFavouriteFolderId,
    )

    private fun UserLibrary.toProto() = ProtoUserLibrary.newBuilder()
        .addAllComments(comments.map {
            ProtoUserComment.newBuilder().setMachineUid(it.machineUid).setText(it.text).build()
        })
        .addAllFavouriteFolders(favouriteFolders.map {
            ProtoFavouriteFolder.newBuilder()
                .setId(it.id)
                .setName(it.name)
                .addAllMachineUids(it.machineUids)
                .build()
        })
        .setCompare(
            CompareState.newBuilder()
                .addAllMachineUids(compare.machineUids)
                .setLeftUid(compare.leftUid)
                .setRightUid(compare.rightUid)
                .build(),
        )
        .setNextFavouriteFolderId(nextFavouriteFolderId)
        .build()

    private fun ProtoPendingNotice.toDomain() = PendingUserNotice(
        entireUserStateWasReset = entireUserStateWasReset,
        removedContent = removedContentList.mapNotNull { removed ->
            removed.kind.toDomain()?.let { RemovedUserContent(it, removed.value) }
        },
    )

    private fun PendingUserNotice.toProto() = ProtoPendingNotice.newBuilder()
        .setEntireUserStateWasReset(entireUserStateWasReset)
        .addAllRemovedContent(removedContent.map { removed ->
            ProtoRemovedUserContent.newBuilder()
                .setKind(removed.kind.toProto())
                .setValue(removed.value)
                .build()
        })
        .build()

    private fun ProtoManufacturerScope.toDomain() = when (this) {
        ProtoManufacturerScope.MANUFACTURER_SCOPE_68K -> BrowseScope.APPLE_68K
        ProtoManufacturerScope.MANUFACTURER_SCOPE_POWERPC -> BrowseScope.POWERPC
        ProtoManufacturerScope.MANUFACTURER_SCOPE_INTEL -> BrowseScope.INTEL
        ProtoManufacturerScope.MANUFACTURER_SCOPE_APPLE_SILICON -> BrowseScope.APPLE_SILICON
        else -> BrowseScope.ALL
    }

    private fun BrowseScope.toProto() = when (this) {
        BrowseScope.ALL -> ProtoManufacturerScope.MANUFACTURER_SCOPE_ALL
        BrowseScope.APPLE_68K -> ProtoManufacturerScope.MANUFACTURER_SCOPE_68K
        BrowseScope.POWERPC -> ProtoManufacturerScope.MANUFACTURER_SCOPE_POWERPC
        BrowseScope.INTEL -> ProtoManufacturerScope.MANUFACTURER_SCOPE_INTEL
        BrowseScope.APPLE_SILICON -> ProtoManufacturerScope.MANUFACTURER_SCOPE_APPLE_SILICON
    }

    private fun ProtoMainGrouping.toDomain() = when (this) {
        ProtoMainGrouping.MAIN_GROUPING_PROCESSORS -> BrowseGrouping.PROCESSORS
        ProtoMainGrouping.MAIN_GROUPING_YEARS -> BrowseGrouping.YEARS
        else -> BrowseGrouping.NAMES
    }

    private fun BrowseGrouping.toProto() = when (this) {
        BrowseGrouping.NAMES -> ProtoMainGrouping.MAIN_GROUPING_NAMES
        BrowseGrouping.PROCESSORS -> ProtoMainGrouping.MAIN_GROUPING_PROCESSORS
        BrowseGrouping.YEARS -> ProtoMainGrouping.MAIN_GROUPING_YEARS
    }

    private fun ProtoRemovedContentKind.toDomain() = when (this) {
        ProtoRemovedContentKind.REMOVED_CONTENT_KIND_COMMENT -> RemovedContentKind.COMMENT
        ProtoRemovedContentKind.REMOVED_CONTENT_KIND_FAVOURITE -> RemovedContentKind.FAVOURITE
        ProtoRemovedContentKind.REMOVED_CONTENT_KIND_COMPARE -> RemovedContentKind.COMPARE
        else -> null
    }

    private fun RemovedContentKind.toProto() = when (this) {
        RemovedContentKind.COMMENT -> ProtoRemovedContentKind.REMOVED_CONTENT_KIND_COMMENT
        RemovedContentKind.FAVOURITE -> ProtoRemovedContentKind.REMOVED_CONTENT_KIND_FAVOURITE
        RemovedContentKind.COMPARE -> ProtoRemovedContentKind.REMOVED_CONTENT_KIND_COMPARE
    }
}
