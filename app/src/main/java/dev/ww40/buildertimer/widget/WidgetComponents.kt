package dev.ww40.buildertimer.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.ww40.buildertimer.ClashApp
import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.Upgrade
import dev.ww40.buildertimer.data.VillageSnapshot
import dev.ww40.buildertimer.data.formatFinishTime
import dev.ww40.buildertimer.data.formatRemaining
import java.time.ZoneId

const val BUILDER_FALLBACK_ICON = "🔨"
const val LAB_FALLBACK_ICON = "⚗️"
const val PET_FALLBACK_ICON = "🐾"

fun fallbackIconFor(snapshot: VillageSnapshot, upgrade: Upgrade): String = when (upgrade) {
    in snapshot.labUpgrades, in snapshot.starLabUpgrades -> LAB_FALLBACK_ICON
    in snapshot.petUpgrades -> PET_FALLBACK_ICON
    else -> BUILDER_FALLBACK_ICON
}

/** Bundled icon for the upgrade's target level when available, else the section's emoji. */
@Composable
fun UpgradeIcon(upgrade: Upgrade, fallbackIcon: String, size: Dp) {
    val iconRes = ItemIcons.drawableRes(LocalContext.current, upgrade.dataId, upgrade.targetLevel)
    if (iconRes != null) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            fallbackIcon,
            modifier = GlanceModifier.width(size),
            style = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

/** Countdown ("2d 5h 54m") or finish time ("Tue 14:30"), per the user's setting. */
fun timeLabel(context: Context, upgrade: Upgrade, nowEpochSec: Long, settings: Settings): String =
    if (settings.showFinishTime) {
        formatFinishTime(upgrade.finishesAtEpochSec, nowEpochSec, ZoneId.systemDefault(), DateFormat.is24HourFormat(context))
    } else {
        formatRemaining(upgrade.remainingSeconds(nowEpochSec))
    }

/** Tapping opens Clash of Clans; a no-op when it isn't installed. */
fun GlanceModifier.opensClash(context: Context): GlanceModifier =
    ClashApp.launchIntent(context)?.let { clickable(actionStartActivity(it)) } ?: this
