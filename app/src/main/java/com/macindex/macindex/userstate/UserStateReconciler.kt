package com.macindex.macindex.userstate

internal class ReconciledUserState(
    val state: UserState,
    val removedContent: List<RemovedUserContent>,
)

internal object UserStateReconciler {
    fun reconcile(state: UserState, resolver: MachineUidResolver): ReconciledUserState {
        val removed = mutableListOf<RemovedUserContent>()

        val occupiedCommentUids = state.library.comments.mapNotNull { comment ->
            resolver.resolve(comment.machineUid).currentUid
                ?.takeIf { it == comment.machineUid }
        }.toMutableSet()
        val comments = state.library.comments.mapNotNull { comment ->
            val resolution = resolver.resolve(comment.machineUid)
            val currentUid = resolution.currentUid
            if (currentUid == null || currentUid in occupiedCommentUids &&
                currentUid != comment.machineUid
            ) {
                removed += RemovedUserContent(
                    RemovedContentKind.COMMENT,
                    "${resolution.displayName} [${comment.machineUid}]│${comment.text}",
                )
                null
            } else {
                occupiedCommentUids += currentUid
                if (currentUid == comment.machineUid) comment
                else comment.copy(machineUid = currentUid)
            }
        }

        val folders = state.library.favouriteFolders.map { folder ->
            val machineUids = folder.machineUids.mapNotNull { uid ->
                val resolution = resolver.resolve(uid)
                if (resolution.currentUid == null) {
                    removed += RemovedUserContent(
                        RemovedContentKind.FAVOURITE,
                        "{${folder.name}}│${resolution.displayName} [$uid]",
                    )
                    null
                } else {
                    resolution.currentUid
                }
            }.distinct()
            folder.copy(machineUids = machineUids)
        }

        val compareMachines = state.library.compare.machineUids.mapNotNull { uid ->
            val resolution = resolver.resolve(uid)
            if (resolution.currentUid == null) {
                removed += RemovedUserContent(
                    RemovedContentKind.COMPARE,
                    "${resolution.displayName} [$uid]",
                )
                null
            } else {
                resolution.currentUid
            }
        }.distinct()
        val oldCompare = state.library.compare
        val oldSelectionIsEmpty = oldCompare.leftUid.isEmpty() && oldCompare.rightUid.isEmpty()
        val leftResolution = oldCompare.leftUid.takeIf { it.isNotEmpty() }
            ?.let(resolver::resolve)
        val rightResolution = oldCompare.rightUid.takeIf { it.isNotEmpty() }
            ?.let(resolver::resolve)
        val leftUid = leftResolution?.currentUid
        val rightUid = rightResolution?.currentUid
        val selectionIsValid = oldSelectionIsEmpty ||
            (leftUid != null && rightUid != null && leftUid != rightUid &&
                leftUid in compareMachines && rightUid in compareMachines)
        if (!oldSelectionIsEmpty && !selectionIsValid) {
            removed += RemovedUserContent(
                RemovedContentKind.COMPARE,
                "[${leftResolution?.displayName ?: oldCompare.leftUid}]│" +
                    "[${rightResolution?.displayName ?: oldCompare.rightUid}]",
            )
        }
        val compare = CompareSelection(
            machineUids = compareMachines,
            leftUid = if (selectionIsValid && !oldSelectionIsEmpty) leftUid.orEmpty() else "",
            rightUid = if (selectionIsValid && !oldSelectionIsEmpty) rightUid.orEmpty() else "",
        )

        val library = state.library.copy(
            comments = comments,
            favouriteFolders = folders,
            compare = compare,
        )
        val nextStateWithoutNotice = state.copy(library = library)
        val notice = mergeNotice(state.pendingNotice, removed)
        val nextState = nextStateWithoutNotice.copy(pendingNotice = notice)
        return ReconciledUserState(nextState, removed)
    }

    private fun mergeNotice(
        current: PendingUserNotice?,
        additional: List<RemovedUserContent>,
    ): PendingUserNotice? {
        if (current == null && additional.isEmpty()) return null
        return PendingUserNotice(
            entireUserStateWasReset = current?.entireUserStateWasReset == true,
            removedContent = current?.removedContent.orEmpty() + additional,
        )
    }
}
