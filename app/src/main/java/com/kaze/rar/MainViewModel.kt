package com.kaze.rar

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState {
    data object Home : UiState
    data class Loading(@StringRes val message: Int) : UiState
    data class Password(val name: String, val wrong: Boolean) : UiState
    data class Browse(
        val name: String,
        val kind: Kind,
        val items: List<Item>,
        val fileCount: Int,
        val totalBytes: Long
    ) : UiState

    data class Extracting(
        val name: String,
        val fraction: Float?,
        val current: String,
        val doneFiles: Int,
        val totalFiles: Int
    ) : UiState

    data class Done(val name: String, val result: ExtractResult) : UiState
    data class Error(@StringRes val message: Int) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ArchiveEngine(app)
    private val _state = MutableStateFlow<UiState>(UiState.Home)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var workFile: File? = null
    private var kind: Kind? = null
    private var displayName: String = ""
    private var password: String? = null
    private var items: List<Item> = emptyList()
    private var pendingExtract = false
    private var job: Job? = null

    // ------------------------------------------------------------------ open

    fun open(uri: Uri) {
        job?.cancel()
        password = null
        pendingExtract = false
        job = viewModelScope.launch {
            _state.value = UiState.Loading(R.string.preparing)
            try {
                val (file, name) = withContext(Dispatchers.IO) { copyToCache(uri) }
                val detected = withContext(Dispatchers.IO) { Formats.detect(file, name) }
                if (detected == null) {
                    _state.value = UiState.Error(R.string.err_unsupported)
                    return@launch
                }
                workFile = file
                displayName = name
                kind = detected
                readContents()
            } catch (e: CancellationException) {
                throw e
            } catch (e: KazeException) {
                _state.value = UiState.Error(messageFor(e.problem))
            } catch (e: Exception) {
                _state.value = UiState.Error(R.string.err_io)
            }
        }
    }

    private suspend fun readContents() {
        val file = workFile ?: return
        val k = kind ?: return
        _state.value = UiState.Loading(R.string.reading)
        try {
            val list = withContext(Dispatchers.IO) { engine.list(file, k, password, displayName) }
            items = list
            val files = list.filter { !it.isDir }
            _state.value = UiState.Browse(
                name = displayName,
                kind = k,
                items = list,
                fileCount = files.size,
                totalBytes = files.sumOf { maxOf(0L, it.size) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: NeedPasswordException) {
            pendingExtract = false
            _state.value = UiState.Password(displayName, e.wrong)
        } catch (e: KazeException) {
            _state.value = UiState.Error(messageFor(e.problem))
        } catch (e: Exception) {
            _state.value = UiState.Error(R.string.err_corrupt)
        }
    }

    private fun copyToCache(uri: Uri): Pair<File, String> {
        val ctx = getApplication<Application>()
        var name = "archive"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.let { name = it }
            }
        }
        val dir = File(ctx.cacheDir, "work").apply { deleteRecursively(); mkdirs() }
        val f = File(dir, "source.bin")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw KazeException(Problem.IO)
        input.use { i -> f.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        return f to name
    }

    // -------------------------------------------------------------- password

    fun submitPassword(pw: String) {
        password = pw
        job?.cancel()
        job = viewModelScope.launch {
            if (pendingExtract) {
                pendingExtract = false
                doExtract()
            } else {
                readContents()
            }
        }
    }

    // --------------------------------------------------------------- extract

    fun extract() {
        job?.cancel()
        job = viewModelScope.launch { doExtract() }
    }

    private suspend fun doExtract() {
        val file = workFile ?: return
        val k = kind ?: return
        val files = items.filter { !it.isDir }
        val totalFiles = files.size
        val totalBytes = files.sumOf { maxOf(0L, it.size) }
        _state.value = UiState.Extracting(displayName, null, "", 0, totalFiles)
        try {
            val result = withContext(Dispatchers.IO) {
                engine.extract(
                    file, k, password, displayName, totalFiles, totalBytes,
                    onProgress = { p ->
                        if (_state.value is UiState.Extracting) {
                            val fraction = when {
                                p.totalBytes > 0 -> (p.doneBytes.toDouble() / p.totalBytes).toFloat()
                                p.totalFiles > 1 -> p.doneFiles.toFloat() / p.totalFiles
                                else -> null
                            }
                            _state.value = UiState.Extracting(
                                displayName, fraction?.coerceIn(0f, 1f), p.current, p.doneFiles, p.totalFiles
                            )
                        }
                    },
                    isCancelled = { !isActive }
                )
            }
            _state.value = UiState.Done(displayName, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CancelledException) {
            // user cancelled; state already restored by cancelExtraction()
        } catch (e: NeedPasswordException) {
            pendingExtract = true
            _state.value = UiState.Password(displayName, e.wrong)
        } catch (e: KazeException) {
            _state.value = UiState.Error(messageFor(e.problem))
        } catch (e: Exception) {
            _state.value = UiState.Error(R.string.err_corrupt)
        }
    }

    private fun cancelExtraction() {
        job?.cancel()
        val k = kind
        if (k != null) {
            val files = items.filter { !it.isDir }
            _state.value = UiState.Browse(
                displayName, k, items, files.size, files.sumOf { maxOf(0L, it.size) }
            )
        } else {
            reset()
        }
    }

    // ------------------------------------------------------------ navigation

    fun onBack() {
        when (_state.value) {
            is UiState.Extracting -> cancelExtraction()
            UiState.Home -> Unit
            else -> reset()
        }
    }

    fun reset() {
        job?.cancel()
        pendingExtract = false
        password = null
        items = emptyList()
        kind = null
        workFile = null
        File(getApplication<Application>().cacheDir, "work").deleteRecursively()
        _state.value = UiState.Home
    }

    override fun onCleared() {
        File(getApplication<Application>().cacheDir, "work").deleteRecursively()
        super.onCleared()
    }

    @StringRes
    private fun messageFor(p: Problem): Int = when (p) {
        Problem.UNSUPPORTED -> R.string.err_unsupported
        Problem.RAR5 -> R.string.err_rar5
        Problem.ENCRYPTED_ZIP -> R.string.err_zip_encrypted
        Problem.CORRUPT -> R.string.err_corrupt
        Problem.IO -> R.string.err_io
    }
}
