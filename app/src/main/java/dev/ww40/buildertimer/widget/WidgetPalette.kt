package dev.ww40.buildertimer.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.unit.ColorProvider
import dev.ww40.buildertimer.data.SettingsStore

private fun color(argb: Long) = ColorProvider(Color(argb))

class WidgetPalette(
    val background: ColorProvider,
    val title: ColorProvider,
    val sectionTitle: ColorProvider,
    val muted: ColorProvider,
    val syncButton: ColorProvider,
    val syncButtonText: ColorProvider,
    val settingsButton: ColorProvider,
    val barTrack: ColorProvider,
    val barFill: ColorProvider,
    val rowText: ColorProvider,
    val goblinBadge: ColorProvider,
    val doneFill: ColorProvider,
    val doneText: ColorProvider,
    val stale: ColorProvider,
    val boost: ColorProvider,
) {
    companion object {
        /** Clash-style dark wood and grass colours. */
        val Clash = WidgetPalette(
            background = color(0xFF3A2A1C),
            title = color(0xFFFFFFFF),
            sectionTitle = color(0xFFF5C542),
            muted = color(0xFFC9B79C),
            syncButton = color(0xFFF5C542),
            syncButtonText = color(0xFF3A2A1C),
            settingsButton = color(0xFF5A4330),
            barTrack = color(0xFF26331A),
            barFill = color(0xFF6DB33F),
            rowText = color(0xFFFFFFFF),
            goblinBadge = color(0xFF2E7D32),
            doneFill = color(0xFFD9A520),
            doneText = color(0xFF3A2A1C),
            stale = color(0xFFFFA726),
            boost = color(0xFFB39DFF),
        )

        /** True black and greys, with colour only on progress bars and warnings. */
        val Dark = WidgetPalette(
            background = color(0xFF000000),
            title = color(0xFFE6E6E6),
            sectionTitle = color(0xFFB0B0B0),
            muted = color(0xFF8A8A8A),
            syncButton = color(0xFF1F1F1F),
            syncButtonText = color(0xFFE6E6E6),
            settingsButton = color(0xFF1F1F1F),
            barTrack = color(0xFF161616),
            barFill = color(0xFF1F4D2B),
            rowText = color(0xFFE6E6E6),
            goblinBadge = color(0xFF14401A),
            doneFill = color(0xFF333333),
            doneText = color(0xFFE6E6E6),
            stale = color(0xFFC77F1A),
            boost = color(0xFF8F7FCC),
        )
    }
}

private val LocalWidgetPalette = staticCompositionLocalOf { WidgetPalette.Clash }

val palette: WidgetPalette
    @Composable get() = LocalWidgetPalette.current

/** Applies the colour scheme chosen in settings to [content]. */
@Composable
fun ThemedWidget(content: @Composable () -> Unit) {
    // Reading state re-applies the scheme when settings are saved.
    currentState<Preferences>()
    val darkMode = SettingsStore(LocalContext.current).load().darkMode
    CompositionLocalProvider(
        LocalWidgetPalette provides if (darkMode) WidgetPalette.Dark else WidgetPalette.Clash,
        content = content,
    )
}
