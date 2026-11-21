package com.rohit.gallery.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The launcher icon colours the user can pick. Each one is an `activity-alias` in the manifest; exactly one is
 * enabled, and switching enables the new alias before disabling the others so the app never has no launcher entry.
 * The colours here only draw the little previews in Settings; the real icons are the drawables in `res/`.
 */
enum class AppIconStyle(
    val label: String,
    val alias: String,
    val background: List<Color>,
    val sun: List<Color>,
    val lineOne: Color,
    val lineTwo: Color,
) {
    Dusk(
        "Dusk", "IconDusk",
        listOf(Color(0xFF3B2A7A), Color(0xFFC0467D), Color(0xFFFF9A52)),
        listOf(Color.White, Color.White),
        Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.5f),
    ),
    Light(
        "Light", "IconLight",
        listOf(Color.White, Color.White),
        listOf(Color(0xFF5E3A9E), Color(0xFFC0467D), Color(0xFFFF9A52)),
        Color(0xFFD9558A), Color(0xFFFFA866),
    ),
    Midnight(
        "Midnight", "IconMidnight",
        listOf(Color(0xFF0F1226), Color(0xFF2E2F78)),
        listOf(Color(0xFFFFC46B), Color(0xFFFF7A59)),
        Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.4f),
    ),
    Ocean(
        "Ocean", "IconOcean",
        listOf(Color(0xFF1D4ED8), Color(0xFF22C5E8)),
        listOf(Color.White, Color.White),
        Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.5f),
    ),
}

private fun Context.iconComponent(style: AppIconStyle) = ComponentName(this, "com.rohit.gallery.${style.alias}")

/** Which alias is on. A "default" state means whatever the manifest says, and only Dusk is enabled there. */
fun currentIconStyle(context: Context): AppIconStyle {
    val pm = context.packageManager
    return AppIconStyle.entries.firstOrNull { style ->
        when (pm.getComponentEnabledSetting(context.iconComponent(style))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style == AppIconStyle.Dusk
            else -> false
        }
    } ?: AppIconStyle.Dusk
}

/** Turns [style] on and the rest off. DONT_KILL_APP keeps the running app open while the launcher refreshes. */
fun applyIconStyle(context: Context, style: AppIconStyle) {
    val pm = context.packageManager
    pm.setComponentEnabledSetting(
        context.iconComponent(style),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
    )
    AppIconStyle.entries.filter { it != style }.forEach {
        pm.setComponentEnabledSetting(
            context.iconComponent(it),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

@Composable
fun AppIconRow(selected: AppIconStyle, onSelect: (AppIconStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("App icon", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Your home screen may take a moment to update, and could ask you to put the icon back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AppIconStyle.entries.forEach { style ->
                IconChoice(style, selected = style == selected, onClick = { onSelect(style) })
            }
        }
    }
}

@Composable
private fun IconChoice(style: AppIconStyle, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(role = Role.RadioButton, onClick = onClick).padding(4.dp),
    ) {
        val shape = RoundedCornerShape(percent = 28)
        Canvas(
            Modifier
                .size(64.dp)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, ring, shape),
        ) {
            val k = size.width / 108f
            drawRect(Brush.linearGradient(style.background, Offset.Zero, Offset(43f * k, 108f * k)))
            // Same shapes as res/drawable/ic_launcher_foreground*.xml, drawn on the 108 grid.
            val sunBrush = Brush.linearGradient(style.sun, Offset(34f * k, 33f * k), Offset(74f * k, 53f * k))
            drawArc(sunBrush, 180f, 180f, true, Offset(34f * k, 33f * k), Size(40f * k, 40f * k))
            val r = CornerRadius(2.5f * k)
            drawRoundRect(style.lineOne, Offset(32f * k, 60f * k), Size(44f * k, 5f * k), r)
            drawRoundRect(style.lineTwo, Offset(40f * k, 69f * k), Size(28f * k, 5f * k), r)
        }
        Text(
            style.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
