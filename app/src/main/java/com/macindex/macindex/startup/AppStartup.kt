package com.macindex.macindex.startup

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.macindex.macindex.catalog.CatalogFormatException
import com.macindex.macindex.catalog.CatalogLoader
import com.macindex.macindex.catalog.MachineCatalog
import com.macindex.macindex.userstate.AppStateStoreFactory
import com.macindex.macindex.userstate.MachineNameResolver
import com.macindex.macindex.userstate.UserStateUnavailableException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The process-owned bootstrap for the immutable catalog and private user state. */
class AppStartup(application: Application) {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableLiveData<AppStartupState>(AppStartupState.Loading)

    val state: LiveData<AppStartupState> = mutableState

    init {
        processScope.launch {
            cleanLegacyV491Artifacts(application)
            val catalog = try {
                CatalogLoader.load(application.assets)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                mutableState.postValue(AppStartupState.Fatal(
                    AppStartupState.FailureKind.CATALOG_ASSET,
                    failure,
                ))
                return@launch
            } catch (failure: CatalogFormatException) {
                mutableState.postValue(AppStartupState.Fatal(
                    AppStartupState.FailureKind.CATALOG_ASSET,
                    failure,
                ))
                return@launch
            }
            try {
                val userState = AppStateStoreFactory.create(
                    application,
                    { name -> catalog.resolveLegacyName(name)?.uid() },
                    processScope,
                )
                // Reconcile's first atomic DataStore access completes the one supported 4.9.1
                // migration; deleted UIDs and its notice then commit together.
                userState.reconcile(catalog.asNameResolver())
                mutableState.postValue(AppStartupState.Ready(catalog, userState))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: UserStateUnavailableException) {
                mutableState.postValue(AppStartupState.Fatal(
                    AppStartupState.FailureKind.USER_STATE,
                    failure,
                ))
            }
        }
    }

    private fun MachineCatalog.asNameResolver() = MachineNameResolver { uid ->
        findByUid(uid)?.name()
    }

}

/** Dispose of the catalog files left by 4.9.1; they are not inputs to the 5.0 foundation. */
private fun cleanLegacyV491Artifacts(application: Application) {
    try {
        val database = application.getDatabasePath("specs.db")
        if (database.exists() && !application.deleteDatabase("specs.db")) {
            Log.w("LegacyDataCleanup", "Unable to delete the legacy catalog file.")
        }
        val interruptedCopy = application.getDatabasePath("specs.db.tmp")
        if (interruptedCopy.exists() && !interruptedCopy.delete()) {
            Log.w("LegacyDataCleanup", "Unable to delete the legacy temporary catalog file.")
        }
    } catch (failure: SecurityException) {
        Log.w("LegacyDataCleanup", "Unable to clean legacy runtime data.", failure)
    }
}
