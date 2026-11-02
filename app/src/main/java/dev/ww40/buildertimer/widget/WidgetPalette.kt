package dev.ww40.buildertimer.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/** Clash-style dark wood and grass colours; the widget is always rendered in this theme. */
object WidgetPalette {
    val background = ColorProvider(Color(0xFF3A2A1C))
    val title = ColorProvider(Color(0xFFFFFFFF))
    val sectionTitle = ColorProvider(Color(0xFFF5C542))
    val muted = ColorProvider(Color(0xFFC9B79C))
    val syncButton = ColorProvider(Color(0xFFF5C542))
    val syncButtonText = ColorProvider(Color(0xFF3A2A1C))
    val settingsButton = ColorProvider(Color(0xFF5A4330))
    val barTrack = ColorProvider(Color(0xFF26331A))
    val barFill = ColorProvider(Color(0xFF6DB33F))
    val rowText = ColorProvider(Color(0xFFFFFFFF))
    val goblinBadge = ColorProvider(Color(0xFF2E7D32))
    val doneFill = ColorProvider(Color(0xFFD9A520))
    val doneText = ColorProvider(Color(0xFF3A2A1C))
    val stale = ColorProvider(Color(0xFFFFA726))
}
