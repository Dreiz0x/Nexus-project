package com.nexus.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.nexus.data.model.AppSettings
import com.nexus.data.repository.GoogleDriveRepository
import com.nexus.data.repository.SettingsRepository
import com.nexus.service.NexusNetworkService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val driveRepo: GoogleDriveRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    private val _connectionStatus = MutableStateFlow("")
    val connectionStatus = _connectionStatus.asStateFlow()

    // Estado de Google Drive
    private val _driveSignedIn = MutableStateFlow(false)
    val driveSignedIn = _driveSignedIn.asStateFlow()

    private val _driveEmail = MutableStateFlow("")
    val driveEmail = _driveEmail.asStateFlow()

    private val _driveSyncStatus = MutableStateFlow("")
    val driveSyncStatus = _driveSyncStatus.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { _settings.value = it }
        }
        checkDriveSignIn()
    }

    private fun checkDriveSignIn() {
        _driveSignedIn.value = driveRepo.isSignedIn()
        _driveEmail.value = driveRepo.getSignedInAccountEmail() ?: ""
    }

    // ── Settings básicos ────────────────────────────────────────────────────
    fun updateHost(v: String)      { _settings.value = _settings.value.copy(apiHost = v) }
    fun updatePort(v: String)      { _settings.value = _settings.value.copy(apiPort = v) }
    fun updateModel(v: String)     { _settings.value = _settings.value.copy(modelName = v) }
    fun toggleAutoSync(v: Boolean) { _settings.value = _settings.value.copy(autoSync = v) }
    fun toggleImages(v: Boolean)   { _settings.value = _settings.value.copy(includeImages = v) }

    fun addFolder(path: String) {
        if (path.isNotBlank() && path !in _settings.value.watchedFolders) {
            _settings.value = _settings.value.copy(
                watchedFolders = _settings.value.watchedFolders + path
            )
        }
    }

    fun removeFolder(folder: String) {
        _settings.value = _settings.value.copy(
            watchedFolders = _settings.value.watchedFolders.filter { it != folder }
        )
    }

    // ── Carpeta compartida en red WiFi ──────────────────────────────────────
    fun setSharedFolder(path: String) {
        _settings.value = _settings.value.copy(sharedNetworkFolder = path)
        if (path.isNotBlank()) NexusNetworkService.start(context)
    }

    fun clearSharedFolder() {
        _settings.value = _settings.value.copy(sharedNetworkFolder = "")
        NexusNetworkService.stop(context)
    }

    // ── Google Drive ────────────────────────────────────────────────────────
    // Retorna el Intent de SignIn para lanzarlo desde la pantalla
    fun getDriveSignInIntent(): Intent {
        val client: GoogleSignInClient = GoogleSignIn.getClient(
            context, driveRepo.getSignInOptions()
        )
        return client.signInIntent
    }

    // Llamar después de que el usuario completó el SignIn
    fun onDriveSignInSuccess() {
        checkDriveSignIn()
        _driveSyncStatus.value = "✓ Google conectado"
        _settings.value = _settings.value.copy(driveSyncEnabled = true)
    }

    fun onDriveSignInFailed() {
        _driveSyncStatus.value = "✗ Error al conectar con Google"
    }

    fun setDriveFolderId(id: String) {
        // El usuario pega el ID de la carpeta NEXUS de Drive
        _settings.value = _settings.value.copy(driveFolderId = id.trim())
    }

    fun disconnectDrive() {
        val client = GoogleSignIn.getClient(context, driveRepo.getSignInOptions())
        client.signOut()
        _driveSignedIn.value = false
        _driveEmail.value = ""
        _settings.value = _settings.value.copy(driveSyncEnabled = false, driveFolderId = "")
        _driveSyncStatus.value = ""
    }

    fun syncDriveNow() = viewModelScope.launch {
        val folderId = _settings.value.driveFolderId
        if (folderId.isBlank()) {
            _driveSyncStatus.value = "⚠ Primero ingresa el ID de la carpeta"
            return@launch
        }
        _driveSyncStatus.value = "Sincronizando..."
        driveRepo.syncDriveFolder(folderId) { progress, name ->
            _driveSyncStatus.value = "Descargando: $name"
        }
        _driveSyncStatus.value = "✓ Sincronización completa"
    }

    // ── Test conexión IA ────────────────────────────────────────────────────
    fun testConnection() = viewModelScope.launch {
        _connectionStatus.value = "Conectando..."
        try {
            val url = java.net.URL(
                "http://${_settings.value.apiHost}:${_settings.value.apiPort}/v1/models"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            _connectionStatus.value = if (code == 200) "✓ Conectado" else "✗ Error $code"
            conn.disconnect()
        } catch (e: Exception) {
            _connectionStatus.value = "✗ Sin respuesta — ¿está corriendo la IA?"
        }
    }

    fun save() = viewModelScope.launch {
        settingsRepo.save(_settings.value)
    }
}
