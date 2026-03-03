package com.nexus.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexus.ui.components.*
import com.nexus.ui.theme.NexusTheme
import com.nexus.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    navController: NavController,
    vm: SearchViewModel = hiltViewModel()
) {
    val colors = NexusTheme.colors
    val query by vm.query.collectAsState()
    val localResults by vm.results.collectAsState()
    val driveResults by vm.driveResults.collectAsState()
    val networkResults by vm.networkResults.collectAsState()
    val aiResponse by vm.aiResponse.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val totalResults = localResults.size + driveResults.size + networkResults.size

    Column(Modifier.fillMaxSize().background(colors.background)) {

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(colors.surface)
                .statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.primary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { vm.onQueryChange(it) },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = {
                    Text("QUERY INTELLIGENCE BASE...",
                        style = NexusTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.5f))
                },
                textStyle = NexusTheme.typography.bodyMedium.copy(color = colors.primary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primaryDim.copy(alpha = 0.4f),
                    cursorColor = colors.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                singleLine = true,
                trailingIcon = {
                    if (isSearching)
                        CircularProgressIndicator(Modifier.size(20.dp),
                            color = colors.primary, strokeWidth = 2.dp)
                    else
                        IconButton(onClick = { vm.search() }) {
                            Icon(Icons.Default.Search, null, tint = colors.primary)
                        }
                }
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Respuesta IA ───────────────────────────────────────────────
            if (aiResponse.isNotEmpty()) {
                item {
                    HudPanel(title = "⚡ AI ANALYSIS", modifier = Modifier.padding(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            TypewriterText(aiResponse,
                                style = NexusTheme.typography.bodyMedium,
                                color = colors.onBackground)
                        }
                    }
                }
            }

            // ── Contador total ─────────────────────────────────────────────
            if (totalResults > 0) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("$totalResults SIGNALS DETECTED",
                            style = NexusTheme.typography.labelSmall, color = colors.secondary)
                        if (localResults.isNotEmpty())
                            Text("📱 ${localResults.size} local",
                                style = NexusTheme.typography.labelSmall, color = colors.onSurface)
                        if (driveResults.isNotEmpty())
                            Text("☁ ${driveResults.size} drive",
                                style = NexusTheme.typography.labelSmall, color = Color(0xFF4285F4))
                        if (networkResults.isNotEmpty())
                            Text("📡 ${networkResults.size} red",
                                style = NexusTheme.typography.labelSmall, color = colors.accent)
                    }
                }
            }

            // ── Resultados locales ─────────────────────────────────────────
            if (localResults.isNotEmpty()) {
                item {
                    SectionHeader("📱 ESTE DISPOSITIVO")
                }
                items(localResults) { doc ->
                
              DocumentCard(
    name = doc.name, path = doc.path,
    extension = doc.extension, snippet = doc.snippet,
    onClick = { vm.openDriveDocument(doc) }
)
                }
            }

            // Resultados Google Drive
if (driveResults.isNotEmpty()) {
    item {
        SectionHeader("☁ GOOGLE DRIVE — NEXUS COMPARTIDO")
    }
    items(driveResults) { doc ->
        DocumentCard(
            name = doc.name,
            path = doc.path,
            extension = doc.extension,
            snippet = doc.snippet,
            onClick = { vm.openDriveDocument(doc) }
        )
    }
}

// Resultados red WiFi
if (networkResults.isNotEmpty()) {
    item {
        SectionHeader("📡 RED LOCAL WiFi")
    }
    items(networkResults) { doc ->
        DocumentCard(
            name = doc.name,
            path = doc.path,
            extension = doc.extension,
            snippet = doc.snippet,
            onClick = { vm.openNetworkDocument(doc) }
        )
    }
}

     
            // ── Sin resultados ─────────────────────────────────────────────
            if (totalResults == 0 && !isSearching && query.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RadarPulse(active = false)
                            Spacer(Modifier.height(16.dp))
                            Text("NO SIGNALS FOUND",
                                style = NexusTheme.typography.titleMedium, color = colors.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text("Buscado en: local · drive · red WiFi",
                                style = NexusTheme.typography.labelSmall, color = colors.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    val colors = NexusTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = NexusTheme.typography.labelSmall, color = colors.primary)
        Spacer(Modifier.width(8.dp))
        Divider(Modifier.weight(1f), color = colors.primary.copy(alpha = 0.2f))
    }
}
