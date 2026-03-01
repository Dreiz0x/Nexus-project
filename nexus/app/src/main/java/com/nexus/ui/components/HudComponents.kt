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
        shape = RoundedCornerShape(2.dp)
    ) {
        Text(text.uppercase(), style = NexusTheme.typography.labelSmall)
    }
}

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

@Composable
fun RadarPulse(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    active: Boolean = true
) {
    val primary = NexusTheme.colors.primary
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing)),
        label = "pulse1"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = this.size.minDimension / 2f
        drawCircle(primary.copy(alpha = 0.05f), radius, center)
        drawCircle(primary.copy(alpha = 0.3f), radius, center, style = Stroke(1f))
        if (active) {
            drawCircle(
                primary.copy(alpha = (1f - pulse1) * 0.5f),
                radius * pulse1,
                center,
                style = Stroke(2f * (1f - pulse1))
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val colors = NexusTheme.colors
    val accentColor = accent ?: colors.primary
    HudPanel(modifier = modifier.padding(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = NexusTheme.typography.displayMedium, color = accentColor)
            Text(label.uppercase(), style = NexusTheme.typography.labelSmall, color = colors.onSurface)
        }
    }
}

@Composable
fun HudProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String = "INDEXING"
) {
    val colors = NexusTheme.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = NexusTheme.typography.labelSmall, color = colors.primary)
            Text("${(progress * 100).toInt()}%", style = NexusTheme.typography.labelSmall, color = colors.primary)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceVariant, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.primary))
        }
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = NexusTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    var displayedText by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { i, _ ->
            kotlinx.coroutines.delay(18)
            displayedText = text.substring(0, i + 1)
        }
    }
    Text(displayedText, modifier = modifier, style = style, color = color)
}

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
    HudPanel(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(name, style = NexusTheme.typography.titleMedium, color = colors.onBackground)
            Text(snippet, style = NexusTheme.typography.bodySmall, color = colors.onSurface)
        }
    }
}
