package com.nexus.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.data.model.DocumentResult
import com.nexus.data.repository.DocumentRepository
import com.nexus.data.repository.GoogleDriveRepository
import com.nexus.data.repository.NetworkRepository
import com.nexus.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val driveRepo: GoogleDriveRepository,
    private val networkRepo: NetworkRepository,
    private val settingsRepo: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    // Resultados separados por origen
    private val _results = MutableStateFlow<List<DocumentResult>>(emptyList())
    val results = _results.asStateFlow()

    private val _driveResults = MutableStateFlow<List<DocumentResult>>(emptyList())
    val driveResults = _driveResults.asStateFlow()

    private val _networkResults = MutableStateFlow<List<DocumentResult>>(emptyList())
    val networkResults = _networkResults.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse = _aiResponse.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _aiResponse.value = ""
            _driveResults.value = emptyList()
            _networkResults.value = emptyList()

            // Búsqueda en paralelo: local + Drive + red WiFi
            val localJob = async { repo.search(q) }
            val driveJob = async { searchDrive(q) }
            val networkJob = async { networkRepo.searchAllDevices(q) }

            val local = localJob.await()
            val drive = driveJob.await()
            val network = networkJob.await()

            _results.value = local
            _driveResults.value = drive
            _networkResults.value = network

            // IA solo sobre resultados locales (más rápido, más relevante)
            val allDocs = local + drive
            if (allDocs.isNotEmpty()) {
                val aiAnswer = repo.queryWithAi(q, allDocs.take(6))
                _aiResponse.value = aiAnswer
            }

            _isSearching.value = false
        }
    }

    private suspend fun searchDrive(query: String): List<DocumentResult> {
        val settings = settingsRepo.getSettings()
        if (!driveRepo.isSignedIn() || settings.driveFolderId.isBlank()) return emptyList()
        return driveRepo.searchDriveFiles(query, settings.driveFolderId)
    }

    // Abre doc local con la app del sistema
    fun openDocument(doc: DocumentResult) {
        repo.openDocument(context, doc.path)
    }

    // Descarga doc de Drive y lo abre
    fun openDriveDocument(doc: DocumentResult) {
        viewModelScope.launch {
            val fileId = doc.path.removePrefix("drive://")
            val fileInfo = com.nexus.data.repository.DriveFileInfo(
                id = fileId,
                name = "${doc.name}.${doc.extension}",
                mimeType = "",
                sizeBytes = 0L,
                modifiedTime = 0L
            )
            val localFile = driveRepo.downloadFile(fileInfo)
            if (localFile != null) {
                repo.openDocument(context, localFile.absolutePath)
            }
        }
    }

    // Abre doc de red WiFi (descarga primero el contenido)
    fun openNetworkDocument(doc: DocumentResult) {
        viewModelScope.launch {
            val device = networkRepo.discoveredDevices.value
                .find { it.host == doc.deviceHost } ?: return@launch
            val content = networkRepo.fetchRemoteContent(device, doc.path)
            // Por ahora muestra el contenido en la IA response
            if (content.isNotEmpty()) {
                _aiResponse.value = "📡 ${doc.name}\n\n${content.take(1000)}"
            }
        }
    }
}
