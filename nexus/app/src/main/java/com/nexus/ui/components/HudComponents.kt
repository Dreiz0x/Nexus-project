package com.nexus.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.ui.theme.NexusTheme
import kotlin.math.*

// -- Grid background --
@Composable
fun HudGrid(modifier: Modifier = Modifier) {
    val gridColor = NexusTheme.colors.grid
    Canvas(modifier = modifier) {
        val step = 40f
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            y += step
        }
    }
}

// -- Hud Panel --
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val primary = NexusTheme.colors.primary
    val surface = NexusTheme.colors.surface

    Box(
        modifier = modifier
            .background(surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .border(1.dp, primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(1.dp)
    ) {
        if (title != null) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(primary, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title.uppercase(),
                        style = NexusTheme.typography.labelSmall,
                        color = primary
                    )
                }
                Box(Modifier.fillMaxWidth(), content = content)
            }
        } else {
            Box(Modifier.fillMaxWidth(), content = content)
        }
    }
}

// -- Hud Button (Indispensable para Settings) --
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = NexusTheme.colors.primary
    Button(
        onClick = onClick,
        modifier = modifier.border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = primary.copy(alpha = 0.1f),
            contentColor = primary
        ),
        shape = RoundedCornerShape(2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text.uppercase(), style = NexusTheme.typography.labelSmall)
    }
}

// -- Hud TextField (Para que funcione NEXUS CONFIG) --
@Composable
fun HudTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = NexusTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = NexusTheme.typography.labelSmall, color = colors.onSurface) },
        textStyle = NexusTheme.typography.bodyMedium.copy(color = colors.primary),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.primaryDim.copy(alpha = 0.4f),
            cursorColor = colors.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        singleLine = true
    )
}

// -- Document Card (Con clickable corregido) --
@Composable
fun DocumentCard(
    name: String,
    path: String,
    extension: String,
    snippet: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val colors = NexusTheme.colors
    val extColor = when (extension.lowercase()) {
        "pdf"  -> colors.accent
        "xlsx", "xls", "csv" -> colors.secondary
        "docx", "doc"        -> colors.primary
        "pptx", "ppt"        -> colors.warning
        else                 -> colors.onSurface
    }

    HudPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() }, // Corrección aquí
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(extColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).border(1.dp, extColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(extension.uppercase().take(3), style = NexusTheme.typography.labelSmall, color = extColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = NexusTheme.typography.titleMedium, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snippet, style = NexusTheme.typography.bodySmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(path, style = NexusTheme.typography.labelSmall, color = colors.primaryDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
