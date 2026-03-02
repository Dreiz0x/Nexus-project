package com.nexus.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexus.ui.Screen
import com.nexus.ui.components.*
import com.nexus.ui.theme.NexusTheme
import com.nexus.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    navController: NavController,
    vm: DashboardViewModel = hiltViewModel()
) {
    val colors = NexusTheme.colors
    val state by vm.uiState.collectAsState()

    var booted by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (booted) 1f else 0f, tween(800), label = "boot")
    LaunchedEffect(Unit) { delay(300); booted = true }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        HudGrid(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .alpha(alpha)
        ) {
            // ── Header + Stats flotantes ───────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("NEXUS", style = NexusTheme.typography.displayLarge, color = colors.primary)
                    Text(
                        "PERSONAL DOCUMENT INTELLIGENCE",
                        style = NexusTheme.typography.labelSmall,
                        color = colors.onSurface
                    )
                }
                IconButton(
                    onClick = { navController.navigate(Screen.Settings.route) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Settings, "settings", tint = colors.primary)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    FloatingStatBadge(label = "LAST SCAN", value = state.lastScan, valueColor = colors.secondary)
                    FloatingStatBadge(label = "DOCS INDEXED", value = "${state.totalDocs}", valueColor = colors.primary)
                    FloatingStatBadge(label = "STORAGE", value = state.storageUsed, valueColor = colors.accent)
                }
            }

            // ── Contenido scrolleable ──────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scan progress
                if (state.isIndexing) {
                    HudPanel(title = "ACTIVE SCAN") {
                        Column(Modifier.padding(12.dp)) {
                            HudProgressBar(state.indexingProgress, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            TypewriterText(
                                text = state.currentFile,
                                style = NexusTheme.typography.bodySmall,
                                color = colors.onSurface
                            )
                        }
                    }
                }

                // Document Matrix
                HudPanel(title = "DOCUMENT MATRIX") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.docTypes.forEach { (ext, count) ->
                            DocTypeRow(ext, count, state.totalDocs)
                        }
                    }
                }

                // Powered by Gemma
                HudPanel(title = "POWERED BY GEMMA") {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Local AI · ${state.totalDocs} docs · ${state.storageUsed}",
                            style = NexusTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Endpoint: offline inference engine active",
                            style = NexusTheme.typography.labelSmall,
                            color = colors.primary.copy(alpha = 0.6f)
                        )
                    }
                }

                // Activity Log estilo terminal hacker
                HackerActivityLog(logs = state.recentActivity)

                Spacer(Modifier.height(8.dp))
            }

            // ── Query bar prominente fija ──────────────────────────────
            Surface(
                Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Search.route) },
                color = colors.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search, "search",
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "QUERY INTELLIGENCE BASE...",
                        style = NexusTheme.typography.titleMedium,
                        color = colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Botones FORCE SCAN / SEARCH ───────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HudButton(
                    text = if (state.isIndexing) "SCANNING..." else "FORCE SCAN",
                    onClick = { vm.startIndexing() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isIndexing,
                    icon = Icons.Default.Refresh
                )
                HudButton(
                    text = "SEARCH",
                    onClick = { navController.navigate(Screen.Search.route) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Search
                )
            }
        }
    }
}

// ── Terminal hacker Activity Log ───────────────────────────────────────────────
@Composable
fun HackerActivityLog(logs: List<String>) {
    val colors = NexusTheme.colors

    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF020E14),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {

            // Header terminal
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(8.dp).background(colors.warning, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(8.dp).background(colors.secondary, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "nexus@device:~/logs",
                        style = NexusTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.primary.copy(alpha = 0.7f)
                    )
                }
                Text(
                    "UP ${tick / 60}m${tick % 60}s",
                    style = NexusTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.secondary.copy(alpha = 0.6f)
                )
            }

            Divider(Modifier.padding(vertical = 8.dp), color = colors.primary.copy(alpha = 0.15f))

            // Líneas del log
            logs.forEachIndexed { index, log ->
                val isLast = index == logs.lastIndex
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "[${String.format("%02d:%02d", (index * 7) / 60, (index * 7) % 60)}]",
                        style = NexusTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.primary.copy(alpha = 0.4f),
                        modifier = Modifier.width(52.dp)
                    )
                    Spacer(Modifier.width(6.dp))

                    val (prefix, prefixColor) = when {
                        log.contains("INDEXED", ignoreCase = true) -> "INX" to colors.secondary
                        log.contains("ERROR", ignoreCase = true)   -> "ERR" to colors.error
                        log.contains("WARN", ignoreCase = true)    -> "WRN" to colors.warning
                        else                                        -> "SYS" to colors.primary
                    }
                    Text(
                        "[$prefix]",
                        style = NexusTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = prefixColor,
                        modifier = Modifier.width(40.dp)
                    )
                    Spacer(Modifier.width(6.dp))

                    Text(
                        log.removePrefix("INDEXED: "),
                        style = NexusTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (isLast) colors.onBackground else colors.onSurface.copy(alpha = 0.75f)
                    )

                    if (isLast && cursorVisible) {
                        Text("█", style = NexusTheme.typography.bodySmall, color = colors.primary)
                    }
                }
            }

            // Prompt activo
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "root@nexus:~# ",
                    style = NexusTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.secondary
                )
                if (cursorVisible) {
                    Text("▌", style = NexusTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}

// ── Componentes reutilizables ──────────────────────────────────────────────────
@Composable
fun FloatingStatBadge(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    val colors = NexusTheme.colors
    Surface(
        color = colors.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, valueColor.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(value, style = NexusTheme.typography.titleMedium, color = valueColor)
            Text(label, style = NexusTheme.typography.labelSmall, color = colors.onSurface)
        }
    }
}

@Composable
fun DocTypeRow(ext: String, count: Int, total: Int) {
    val colors = NexusTheme.colors
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val extColor = when (ext.lowercase()) {
        "pdf"            -> colors.accent
        "xlsx", "xls"   -> colors.secondary
        "docx", "doc"   -> colors.primary
        "pptx", "ppt"   -> colors.warning
        else             -> colors.onSurface
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            ext.uppercase().padEnd(5),
            style = NexusTheme.typography.labelSmall,
            color = extColor,
            modifier = Modifier.width(44.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(6.dp).background(colors.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(extColor.copy(alpha = 0.7f)))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = NexusTheme.typography.labelSmall,
            color = colors.onSurface,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val colors = NexusTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary.copy(alpha = 0.15f),
            contentColor = colors.primary,
            disabledContainerColor = colors.surfaceVariant,
            disabledContentColor = colors.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) colors.primary.copy(alpha = 0.5f) else colors.onSurface.copy(alpha = 0.2f)
        )
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = NexusTheme.typography.labelSmall)
    }
}
