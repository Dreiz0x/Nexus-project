package com.nexus.ui.screens

import android.app.Activity
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.nexus.ui.components.HudButton
import com.nexus.ui.components.HudGrid
import com.nexus.ui.components.HudPanel
import com.nexus.ui.components.HudTextField
import com.nexus.ui.theme.NexusTheme
import com.nexus.ui.viewmodel.SettingsViewModel

fun uriToRealPath(uri: Uri): String? = try {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":")
    val storageType = parts[0]
    val relativePath = parts.getOrElse(1) { "" }
    if (storageType.equals("primary", ignoreCase = true)) {
        "${Environment.getExternalStorageDirectory()}/$relativePath"
    } else {
        "/storage/$storageType/$relativePath"
    }
} catch (e: Exception) { uri.path }

@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val colors = NexusTheme.colors
    val settings by vm.settings.collectAsState()
    val connectionStatus by vm.connectionStatus.collectAsState()
    val driveSignedIn by vm.driveSignedIn.collectAsState()
    val driveEmail by vm.driveEmail.collectAsState()
    val driveSyncStatus by vm.driveSyncStatus.collectAsState()

    // Picker para carpetas locales
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { uriToRealPath(it)?.let { path -> vm.addFolder(path) } } }

    // Picker para carpeta compartida en red
    val sharedFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { uriToRealPath(it)?.let { path -> vm.setSharedFolder(path) } } }

    // Google Sign-In
    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                vm.onDriveSignInSuccess()
            } catch (e: ApiException) {
                vm.onDriveSignInFailed()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        HudGrid(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = colors.primary)
                }
                Text("NEXUS CONFIG",
                    style = NexusTheme.typography.displayMedium,
                    color = colors.primary)
            }

            // ── API Local ────────────────────────────────────────────────────
            HudPanel(title = "LOCAL AI API") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudTextField("API HOST", settings.apiHost, onValueChange = { vm.updateHost(it) })
                    HudTextField("PORT", settings.apiPort, onValueChange = { vm.updatePort(it) },
                        keyboardType = KeyboardType.Number)
                    HudTextField("MODEL", settings.modelName, onValueChange = { vm.updateModel(it) })
                    if (connectionStatus.isNotBlank()) {
                        Text(connectionStatus,
                            style = NexusTheme.typography.labelSmall,
                            color = if (connectionStatus.startsWith("✓")) colors.primary else colors.accent)
                    }
                    HudButton("TEST CONNECTION", onClick = { vm.testConnection() },
                        modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Carpetas locales ─────────────────────────────────────────────
            HudPanel(title = "MONITORED SECTORS") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.watchedFolders.isEmpty()) {
                        Text("▷ Sin sectores — toca ADD FOLDER",
                            style = NexusTheme.typography.bodySmall, color = colors.onSurface)
                    }
                    settings.watchedFolders.forEach { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶ $folder",
                                style = NexusTheme.typography.bodySmall,
                                color = colors.onBackground,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.removeFolder(folder) }) {
                                Text("REMOVE",
                                    style = NexusTheme.typography.labelSmall,
                                    color = colors.accent)
                            }
                        }
                    }
                    HudButton("ADD FOLDER", onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Google Drive ─────────────────────────────────────────────────
            HudPanel(title = "☁ GOOGLE DRIVE — CARPETA NEXUS") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!driveSignedIn) {
                        Text(
                            "Conecta tu cuenta de Google para acceder a la carpeta NEXUS " +
                            "compartida. Solo se pide permiso de lectura.",
                            style = NexusTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                        HudButton("CONECTAR GOOGLE",
                            onClick = { driveSignInLauncher.launch(vm.getDriveSignInIntent()) },
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("✓ Conectado: $driveEmail",
                            style = NexusTheme.typography.bodySmall,
                            color = colors.primary)

                        Spacer(Modifier.height(4.dp))

                        // ID de la carpeta Drive
                        Text(
                            "ID de la carpeta NEXUS en Drive\n" +
                            "(abre la carpeta en drive.google.com → copia el ID del URL)",
                            style = NexusTheme.typography.labelSmall,
                            color = colors.onSurface
                        )
                        HudTextField(
                            label = "FOLDER ID",
                            value = settings.driveFolderId,
                            onValueChange = { vm.setDriveFolderId(it) }
                        )

                        if (driveSyncStatus.isNotBlank()) {
                            Text(driveSyncStatus,
                                style = NexusTheme.typography.labelSmall,
                                color = if (driveSyncStatus.startsWith("✓")) colors.primary
                                        else colors.secondary)
                        }

                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("SYNC AHORA",
                                onClick = { vm.syncDriveNow() },
                                modifier = Modifier.weight(1f))
                            HudButton("DESCONECTAR",
                                onClick = { vm.disconnectDrive() },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Red WiFi local ───────────────────────────────────────────────
            HudPanel(title = "RED LOCAL WiFi — CARPETA COMPARTIDA") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Solo esta carpeta es visible para otros dispositivos NEXUS en el mismo WiFi.",
                        style = NexusTheme.typography.bodySmall,
                        color = colors.onSurface
                    )
                    if (settings.sharedNetworkFolder.isBlank()) {
                        Text("▷ Sin carpeta compartida",
                            style = NexusTheme.typography.bodySmall, color = colors.secondary)
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("📡 ${settings.sharedNetworkFolder}",
                                style = NexusTheme.typography.bodySmall,
                                color = colors.primary,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearSharedFolder() }) {
                                Text("QUITAR",
                                    style = NexusTheme.typography.labelSmall,
                                    color = colors.accent)
                            }
                        }
                    }
                    HudButton(
                        if (settings.sharedNetworkFolder.isBlank()) "ELEGIR CARPETA"
                        else "CAMBIAR CARPETA",
                        onClick = { sharedFolderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Parámetros ───────────────────────────────────────────────────
            HudPanel(title = "INDEX PARAMETERS") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("AUTO-SYNC",
                            style = NexusTheme.typography.bodySmall, color = colors.onBackground)
                        Switch(checked = settings.autoSync,
                            onCheckedChange = { vm.toggleAutoSync(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primary.copy(alpha = 0.3f)))
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("INCLUDE IMAGES (OCR)",
                            style = NexusTheme.typography.bodySmall, color = colors.onBackground)
                        Switch(checked = settings.includeImages,
                            onCheckedChange = { vm.toggleImages(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primary.copy(alpha = 0.3f)))
                    }
                }
            }

            HudButton("SAVE & APPLY",
                onClick = { vm.save(); navController.popBackStack() },
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
