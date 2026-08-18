package com.macindex.macindex.userstate

internal class ReconciledUserState(
    val state: UserState,
    val removedContent: List<RemovedUserContent>,
)

internal object UserStateReconciler {
    fun reconcile(state: UserState, resolver: MachineNameResolver): ReconciledUserState {
        val removed = mutableListOf<RemovedUserContent>()

        val comments = state.library.comments.mapNotNull { comment ->
            if (resolver.resolveDisplayName(comment.machineUid) == null) {
                removed += RemovedUserContent(
                    RemovedContentKind.COMMENT,
                    "${comment.machineUid}│${comment.text}",
                )
                null
            } else {
                comment
            }
        }

        val folders = state.library.favouriteFolders.map { folder ->
            val machineUids = folder.machineUids.mapNotNull { uid ->
                if (resolver.resolveDisplayName(uid) == null) {
                    removed += RemovedUserContent(
                        RemovedContentKind.FAVOURITE,
                        "{${folder.name}}│[$uid]",
                    )
                    null
                } else {
                    uid
                }
            }
            folder.copy(machineUids = machineUids)
        }

        val compareMachines = state.library.compare.machineUids.mapNotNull { uid ->
            if (resolver.resolveDisplayName(uid) == null) {
                removed += RemovedUserContent(
                    RemovedContentKind.COMPARE,
                    "[$uid]",
                )
                null
            } else {
                uid
            }
        }
        val oldCompare = state.library.compare
        val oldSelectionIsEmpty = oldCompare.leftUid.isEmpty() && oldCompare.rightUid.isEmpty()
        val leftName = oldCompare.leftUid.takeIf { it.isNotEmpty() }
            ?.let(resolver::resolveDisplayName)
        val rightName = oldCompare.rightUid.takeIf { it.isNotEmpty() }
            ?.let(resolver::resolveDisplayName)
        val selectionIsValid = oldSelectionIsEmpty ||
            (leftName != null && rightName != null && oldCompare.leftUid != oldCompare.rightUid &&
                oldCompare.leftUid in compareMachines && oldCompare.rightUid in compareMachines)
        if (!oldSelectionIsEmpty && !selectionIsValid) {
            removed += RemovedUserContent(
                RemovedContentKind.COMPARE,
                "[${leftName ?: oldCompare.leftUid}]│[${rightName ?: oldCompare.rightUid}]",
            )
        }
        val compare = CompareSelection(
            machineUids = compareMachines,
            leftUid = if (selectionIsValid) oldCompare.leftUid else "",
            rightUid = if (selectionIsValid) oldCompare.rightUid else "",
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
