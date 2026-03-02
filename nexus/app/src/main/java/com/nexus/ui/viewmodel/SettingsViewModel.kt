package com.nexus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.data.model.AppSettings
import com.nexus.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { _settings.value = it }
        }
    }

    fun updateHost(v: String)  { _settings.value = _settings.value.copy(apiHost = v) }
    fun updatePort(v: String)  { _settings.value = _settings.value.copy(apiPort = v) }
    fun updateModel(v: String) { _settings.value = _settings.value.copy(modelName = v) }
    fun toggleAutoSync(v: Boolean) { _settings.value = _settings.value.copy(autoSync = v) }
    fun toggleImages(v: Boolean)   { _settings.value = _settings.value.copy(includeImages = v) }

    fun removeFolder(folder: String) {
        _settings.value = _settings.value.copy(
            watchedFolders = _settings.value.watchedFolders.filter { it != folder }
        )
    }

    fun addFolder(path: String) {
        if (path.isNotBlank() && path !in _settings.value.watchedFolders) {
            _settings.value = _settings.value.copy(
                watchedFolders = _settings.value.watchedFolders + path
            )
        }
    }

    fun testConnection() = viewModelScope.launch {
        // Ping the API endpoint
    }

    fun save() = viewModelScope.launch {
        settingsRepo.save(_settings.value)
    }
}
