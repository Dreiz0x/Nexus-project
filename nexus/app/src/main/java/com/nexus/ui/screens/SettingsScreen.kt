package com.nexus.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexus.ui.components.HudButton
import com.nexus.ui.components.HudGrid
import com.nexus.ui.components.HudPanel
import com.nexus.ui.components.HudTextField
import com.nexus.ui.theme.NexusTheme
import com.nexus.ui.viewmodel.SettingsViewModel

/** Convierte una content:// URI del SAF a un path real /storage/emulated/0/... */
fun uriToRealPath(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        val storageType = parts[0]
        val relativePath = parts.getOrElse(1) { "" }
        if (storageType.equals("primary", ignoreCase = true)) {
            "${Environment.getExternalStorageDirectory()}/$relativePath"
        } else {
            "/storage/$storageType/$relativePath"
        }
    } catch (e: Exception) {
        uri.path
    }
}

@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val colors = NexusTheme.colors
    val settings by vm.settings.collectAsState()

    // ── Folder picker real con Storage Access Framework ──────────────────────
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val realPath = uriToRealPath(uri)
            if (realPath != null) vm.addFolder(realPath)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        HudGrid(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = colors.primary)
                }
                Text("NEXUS CONFIG", style = NexusTheme.typography.displayMedium, color = colors.primary)
            }

            HudPanel(title = "LOCAL AI API") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudTextField("API HOST", settings.apiHost, onValueChange = { vm.updateHost(it) })
                    HudTextField("PORT", settings.apiPort, onValueChange = { vm.updatePort(it) }, keyboardType = KeyboardType.Number)
                    HudTextField("MODEL", settings.modelName, onValueChange = { vm.updateModel(it) })
                    Text("Connection: ${settings.apiHost}:${settings.apiPort}", style = NexusTheme.typography.labelSmall, color = colors.secondary)
                    HudButton("TEST CONNECTION", onClick = { vm.testConnection() }, modifier = Modifier.fillMaxWidth())
                }
            }

            HudPanel(title = "MONITORED SECTORS") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.watchedFolders.isEmpty()) {
                        Text("▷ Sin sectores — toca ADD FOLDER", style = NexusTheme.typography.bodySmall, color = colors.onSurface)
                    }
                    settings.watchedFolders.forEach { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶ $folder", style = NexusTheme.typography.bodySmall, color = colors.onBackground, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.removeFolder(folder) }) {
                                Text("REMOVE", style = NexusTheme.typography.labelSmall, color = colors.accent)
                            }
                        }
                    }
                    HudButton("ADD FOLDER", onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth())
                }
            }

            HudPanel(title = "INDEX PARAMETERS") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AUTO-SYNC", style = NexusTheme.typography.bodySmall, color = colors.onBackground)
                        Switch(
                            checked = settings.autoSync,
                            onCheckedChange = { vm.toggleAutoSync(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary, checkedTrackColor = colors.primary.copy(alpha = 0.3f))
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("INCLUDE IMAGES (OCR)", style = NexusTheme.typography.bodySmall, color = colors.onBackground)
                        Switch(
                            checked = settings.includeImages,
                            onCheckedChange = { vm.toggleImages(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary, checkedTrackColor = colors.primary.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            HudButton("SAVE & APPLY", onClick = { vm.save(); navController.popBackStack() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
