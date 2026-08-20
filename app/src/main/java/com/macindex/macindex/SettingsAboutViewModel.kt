package com.macindex.macindex

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.macindex.macindex.catalog.MachineCatalog
import com.macindex.macindex.userstate.AppStateRepository
import com.macindex.macindex.userstate.InvalidUserDataException
import com.macindex.macindex.userstate.MachineUidResolver
import com.macindex.macindex.userstate.uidResolver
import com.macindex.macindex.userstate.PreparedUserDataImport
import com.macindex.macindex.userstate.UserStateCommands
import com.macindex.macindex.userstate.UserStateLimits
import com.macindex.macindex.userstate.UserStateUnavailableException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retains the only long-running user-data workflow across Settings recreation. */
class SettingsAboutViewModel(application: Application) : AndroidViewModel(application) {
    private enum class PendingTransferKind {
        EXPORT,
        IMPORT,
    }

    private data class PendingTransfer(
        val kind: PendingTransferKind,
        val uri: Uri,
    )

    enum class Status {
        IDLE,
        EXPORTING,
        READING_IMPORT,
        CONFIRMING_IMPORT,
        APPLYING_IMPORT,
        EXPORT_SUCCEEDED,
        IMPORT_SUCCEEDED,
        EXPORT_FAILED,
        IMPORT_READ_FAILED,
        IMPORT_APPLY_FAILED,
    }

    class State private constructor(
        @JvmField val status: Status,
        @JvmField val imported: PreparedUserDataImport? = null,
        @JvmField val error: Exception? = null,
    ) {
        fun isRunning(): Boolean = status == Status.EXPORTING ||
            status == Status.READING_IMPORT || status == Status.APPLYING_IMPORT

        fun isTerminal(): Boolean = status == Status.EXPORT_SUCCEEDED ||
            status == Status.IMPORT_SUCCEEDED || status == Status.EXPORT_FAILED ||
            status == Status.IMPORT_READ_FAILED || status == Status.IMPORT_APPLY_FAILED

        companion object {
            fun of(status: Status) = State(status)
            fun confirming(imported: PreparedUserDataImport) =
                State(Status.CONFIRMING_IMPORT, imported)
            fun imported() = State(Status.IMPORT_SUCCEEDED)
            fun failed(
                status: Status,
                failure: Exception,
                imported: PreparedUserDataImport? = null,
            ) = State(status, imported = imported, error = failure)
        }
    }

    private val mutableState = MutableLiveData(State.of(Status.IDLE))
    private var repository: AppStateRepository? = null
    private var resolver: MachineUidResolver? = null
    private var pendingTransfer: PendingTransfer? = null

    val state: LiveData<State> get() = mutableState

    fun initialize(repository: AppStateRepository, catalog: MachineCatalog) {
        val existing = this.repository
        if (existing != null && existing !== repository) {
            throw IllegalStateException("Transfer ViewModel was rebound to another repository")
        }
        this.repository = repository
        resolver = catalog.uidResolver()
        startPendingTransferIfReady()
    }

    fun isIdle(): Boolean = mutableState.value?.status == Status.IDLE

    fun exportUserData(uri: Uri) {
        if (!isIdle()) return
        pendingTransfer = PendingTransfer(PendingTransferKind.EXPORT, uri)
        mutableState.value = State.of(Status.EXPORTING)
        startPendingTransferIfReady()
    }

    fun readImport(uri: Uri) {
        if (!isIdle()) return
        pendingTransfer = PendingTransfer(PendingTransferKind.IMPORT, uri)
        mutableState.value = State.of(Status.READING_IMPORT)
        startPendingTransferIfReady()
    }

    private fun startPendingTransferIfReady() {
        val transfer = pendingTransfer ?: return
        val repository = repository ?: return
        val resolver = resolver ?: return
        pendingTransfer = null
        viewModelScope.launch {
            when (transfer.kind) {
                PendingTransferKind.EXPORT -> {
                    val durableExport = repository.submit(UserStateCommands.exportJson())
                    try {
                        withContext(Dispatchers.IO) {
                            write(transfer.uri, durableExport.await())
                        }
                        mutableState.value = State.of(Status.EXPORT_SUCCEEDED)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: IOException) {
                        mutableState.value = State.failed(Status.EXPORT_FAILED, failure)
                    } catch (failure: UserStateUnavailableException) {
                        mutableState.value = State.failed(Status.EXPORT_FAILED, failure)
                    }
                }
                PendingTransferKind.IMPORT -> {
                    try {
                        val imported = withContext(Dispatchers.IO) {
                            repository.prepareImport(read(transfer.uri), resolver)
                        }
                        mutableState.value = State.confirming(imported)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: IOException) {
                        mutableState.value = State.failed(Status.IMPORT_READ_FAILED, failure)
                    } catch (failure: InvalidUserDataException) {
                        mutableState.value = State.failed(Status.IMPORT_READ_FAILED, failure)
                    }
                }
            }
        }
    }

    fun confirmImport() {
        val imported = mutableState.value?.imported ?: return
        if (mutableState.value?.status != Status.CONFIRMING_IMPORT) return
        applyImport(imported)
    }

    fun retryImport(imported: PreparedUserDataImport) {
        if (mutableState.value?.status != Status.IMPORT_APPLY_FAILED) return
        applyImport(imported)
    }

    private fun applyImport(imported: PreparedUserDataImport) {
        val repository = requireRepository()
        mutableState.value = State.of(Status.APPLYING_IMPORT)
        val durableImport = repository.submit(UserStateCommands.applyImport(imported))
        viewModelScope.launch {
            try {
                durableImport.await()
                mutableState.value = State.imported()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: UserStateUnavailableException) {
                mutableState.value = State.failed(Status.IMPORT_APPLY_FAILED, failure, imported)
            }
        }
    }

    fun cancelImport() {
        if (mutableState.value?.status == Status.CONFIRMING_IMPORT) {
            mutableState.value = State.of(Status.IDLE)
        }
    }

    fun acknowledge(handled: State) {
        if (handled.isTerminal() && mutableState.value === handled) {
            mutableState.value = State.of(Status.IDLE)
        }
    }

    private fun requireRepository(): AppStateRepository =
        requireNotNull(repository) { "User state is not ready" }

    private fun read(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    total += length
                    if (total > UserStateLimits.MAX_IMPORT_BYTES) {
                        throw InvalidUserDataException("User data file is too large")
                    }
                    output.write(buffer, 0, length)
                }
                output.toByteArray()
            } ?: throw IOException("Unable to open user data")
        } catch (failure: SecurityException) {
            throw IOException("Unable to open user data", failure)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (failure: CharacterCodingException) {
            throw InvalidUserDataException("User data is not valid UTF-8", failure)
        }
    }

    private fun write(uri: Uri, json: String) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } ?: throw IOException("Unable to open user data")
        } catch (failure: SecurityException) {
            throw IOException("Unable to open user data", failure)
        }
    }

    companion object {
        const val DEFAULT_FILE_NAME = "MacIndex-User-Data.json"
    }
}
