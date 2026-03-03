package com.nexus.ui.viewmodel

import com.nexus.data.repository.NetworkDevice
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.data.model.DashboardState
import com.nexus.data.model.NetworkDevice
import com.nexus.data.repository.DocumentRepository
import com.nexus.data.repository.GoogleDriveRepository
import com.nexus.data.repository.NetworkRepository
import com.nexus.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    app: Application,
    private val repo: DocumentRepository,
    private val settingsRepo: SettingsRepository,
    private val driveRepo: GoogleDriveRepository,
    private val networkRepo: NetworkRepository
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _state.asStateFlow()

    // Estado Drive
    private val _driveStatus = MutableStateFlow("")
    val driveStatus: StateFlow<String> = _driveStatus.asStateFlow()

    // Dispositivos en red WiFi
    val networkDevices: StateFlow<List<NetworkDevice>> = networkRepo.discoveredDevices

    init {
        loadStats()
        observeDocs()
        checkDriveStatus()
    }

    private fun observeDocs() {
        repo.allDocuments.onEach { docs ->
            val byExt = docs.groupBy { it.extension.lowercase() }.mapValues { it.value.size }
            _state.update { it.copy(totalDocs = docs.size, docTypes = byExt) }
        }.launchIn(viewModelScope)
    }

    private fun loadStats() = viewModelScope.launch {
        val totalBytes = repo.totalSize()
        val recent = repo.recentlyIndexed()
        val activityLog = recent.take(8).map { "INDEXED: ${it.name}.${it.extension}" }
        _state.update {
            it.copy(
                storageUsed = formatSize(totalBytes),
                recentActivity = activityLog
            )
        }
    }

    private fun checkDriveStatus() {
        if (driveRepo.isSignedIn()) {
            val email = driveRepo.getSignedInAccountEmail() ?: ""
            _driveStatus.value = "✓ Conectado: $email"
        }
    }

    fun startIndexing() = viewModelScope.launch {
        val settings = settingsRepo.getSettings()
        _state.update { it.copy(isIndexing = true, indexingProgress = 0f) }

        // Indexar carpetas locales
        settings.watchedFolders.forEach { folder ->
            repo.indexFolder(folder) { progress, file ->
                _state.update { it.copy(indexingProgress = progress, currentFile = file) }
            }
        }

        // Si Drive está configurado, sincronizar también
        if (settings.driveSyncEnabled && settings.driveFolderId.isNotBlank()) {
            _state.update { it.copy(currentFile = "Sincronizando Drive...") }
            val driveFiles = driveRepo.syncDriveFolder(settings.driveFolderId) { _, name ->
                _state.update { it.copy(currentFile = "Drive: $name") }
            }
            // Indexar los archivos descargados de Drive
            if (driveFiles.isNotEmpty()) {
                repo.indexFolder(GoogleDriveRepository.DRIVE_CACHE_DIR) { progress, file ->
                    _state.update { it.copy(indexingProgress = progress, currentFile = file) }
                }
            }
        }

        val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
        _state.update {
            it.copy(
                isIndexing = false,
                indexingProgress = 1f,
                lastScan = sdf.format(Date())
            )
        }
        loadStats()
    }

    // Sync Drive manual desde Dashboard
    fun syncDrive() = viewModelScope.launch {
        val settings = settingsRepo.getSettings()
        if (!driveRepo.isSignedIn() || settings.driveFolderId.isBlank()) {
            _driveStatus.value = "⚠ Configura Drive en Settings"
            return@launch
        }
        _driveStatus.value = "Sincronizando..."
        driveRepo.syncDriveFolder(settings.driveFolderId) { _, name ->
            _driveStatus.value = "Descargando: $name"
        }
        _driveStatus.value = "✓ Drive sincronizado"
        loadStats()
    }

    // Query IA inline desde Dashboard
    fun queryAi(question: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val answer = repo.smartQuery(question)
            onResult(answer)
        }
    }

    // Iniciar descubrimiento de dispositivos en red WiFi
    fun startNetworkDiscovery() {
        networkRepo.startDiscovery()
    }

    override fun onCleared() {
        networkRepo.stopDiscovery()
        super.onCleared()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes > 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
        bytes > 1_000_000     -> "%.1f MB".format(bytes / 1e6)
        bytes > 1_000         -> "%.1f KB".format(bytes / 1e3)
        else                  -> "$bytes B"
    }
}
