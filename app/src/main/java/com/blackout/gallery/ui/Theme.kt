package com.blackout.gallery.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.blackout.gallery.data.Prefs

/** The Night look, matching Communications. */
object C {
    val Bg: Color get() = if (Prefs.black) Color.Black else Color(0xFF0E0E10)
    val Card: Color get() = if (Prefs.black) Color(0xFF141416) else Color(0xFF1C1C1F)
    val Line: Color get() = if (Prefs.black) Color(0xFF222226) else Color(0xFF2A2A2E)
    val Text = Color(0xFFF2F2F2)
    val Sub = Color(0xFFA0A0A8)
    val Accent: Color get() = Color(Prefs.accent)
    val OnAccent: Color get() = onColor(Accent)
    val Warn = Color(0xFFFFB86B)
    val Danger = Color(0xFFFF5A5F)
    val Good = Color(0xFF22C55E)

    fun onColor(c: Color): Color = if (c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f > 0.6f) Color(0xFF141416) else Color.White
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.OnAccent,
            background = C.Bg, onBackground = C.Text,
            surface = C.Bg, onSurface = C.Text,
            surfaceVariant = C.Card, onSurfaceVariant = C.Sub,
            surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
            surfaceContainerLow = C.Card, surfaceContainerLowest = C.Bg,
            outline = C.Line, outlineVariant = C.Line, error = C.Danger,
        ),
        content = content,
    )
}
