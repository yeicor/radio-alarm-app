package com.github.yeicor.radioalarmapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.yeicor.radioalarmapp.ui.theme.Coral
import com.github.yeicor.radioalarmapp.ui.theme.Hairline
import com.github.yeicor.radioalarmapp.ui.theme.Mint
import com.github.yeicor.radioalarmapp.ui.theme.Night
import com.github.yeicor.radioalarmapp.ui.theme.NightCard
import com.github.yeicor.radioalarmapp.ui.theme.Sunrise
import com.github.yeicor.radioalarmapp.ui.theme.TextPrimary
import com.github.yeicor.radioalarmapp.ui.theme.TextSecondary
import com.github.yeicor.radioalarmapp.ui.theme.Warning

data class ReliabilityPermissions(
    val exactAlarms: Boolean,
    val notifications: Boolean,
)

@Composable
fun RadioAlarmAppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF11182B),
                        Night,
                        Color(0xFF05080F),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Coral.copy(alpha = 0.20f), Color.Transparent),
                    ),
                    shape = RoundedCornerShape(bottomStart = 320.dp, bottomEnd = 320.dp),
                ),
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = NightCard.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: Hairline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3A271F),
                        Color(0xFF1C2238),
                        Color(0xFF12182A),
                    ),
                ),
                shape = RoundedCornerShape(30.dp),
            )
            .padding(1.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(29.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0x26FFB45E),
                                Color(0x12FF7A66),
                                Color.Transparent,
                            ),
                        ),
                    ),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    tint: Color = Sunrise,
    size: Int = 44,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.46f).dp))
    }
}

@Composable
fun StatusRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    ok: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    warningIsCritical: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (ok) Mint.copy(alpha = 0.12f)
                    else (if (warningIsCritical) Coral.copy(alpha = 0.13f) else Warning.copy(alpha = 0.12f)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (ok) Mint else if (warningIsCritical) Coral else Warning,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (!ok && actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel, color = Sunrise, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline),
    )
}

/** The small "?" affordance used instead of paragraphs of explanatory text. */
@Composable
fun HelpButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(34.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = "Help: $title",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Got it", color = Sunrise) }
            },
        )
    }
}
