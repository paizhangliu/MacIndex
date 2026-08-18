package com.macindex.macindex.startup

import com.macindex.macindex.catalog.MachineCatalog
import com.macindex.macindex.userstate.AppStateRepository

/** The three valid process-startup outcomes; invalid field combinations cannot be constructed. */
sealed interface AppStartupState {
    object Loading : AppStartupState

    data class Ready(
        val catalog: MachineCatalog,
        val userStateRepository: AppStateRepository,
    ) : AppStartupState

    data class Fatal(
        val kind: FailureKind,
        val failure: Exception,
    ) : AppStartupState

    enum class FailureKind {
        CATALOG_ASSET,
        USER_STATE,
    }
}
