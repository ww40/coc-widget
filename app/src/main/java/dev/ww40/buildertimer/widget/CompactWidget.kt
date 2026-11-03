package dev.ww40.buildertimer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.VillageSnapshot
import dev.ww40.buildertimer.sync.SyncActivity

private val COMPACT_ICON_SIZE = 20.dp

/** 2×1 widget: the next upgrade to finish plus how many builders are free. */
class CompactWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ThemedWidget { CompactContent() } }
    }
}

class CompactWidgetReceiver : TickingWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
}

@Composable
private fun CompactContent() {
    currentState<Preferences>()
    val context = LocalContext.current
    val snapshot = SnapshotStore(context).load()
    val nowEpochSec = System.currentTimeMillis() / 1000
    val titleStyle = TextStyle(color = palette.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val subStyle = TextStyle(color = palette.sectionTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold)

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp).background(palette.background)
            .padding(horizontal = 10.dp, vertical = 6.dp).opensClash(context),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (snapshot == null) {
            Text("Tap to sync", style = titleStyle, modifier = GlanceModifier.clickable(actionStartActivity<SyncActivity>()))
            return@Column
        }
        val next = snapshot.nextUpgrade(nowEpochSec)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (next != null) {
                UpgradeIcon(next, fallbackIconFor(snapshot, next), COMPACT_ICON_SIZE)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "${next.name} → ${next.targetLevel}",
                    modifier = GlanceModifier.defaultWeight(),
                    style = titleStyle,
                    maxLines = 1,
                )
                Text(timeLabel(context, next, nowEpochSec, SettingsStore(context).load()), style = titleStyle)
            } else {
                Text("All upgrades finished", modifier = GlanceModifier.defaultWeight(), style = titleStyle)
            }
        }
        Text(freeSummary(snapshot, nowEpochSec), style = subStyle, maxLines = 1)
    }
}

private fun freeSummary(snapshot: VillageSnapshot, nowEpochSec: Long): String {
    val free = snapshot.freeBuilders(nowEpochSec)
    return if (free == 0) "All ${snapshot.builderCount} builders busy" else "$free builder${if (free == 1) "" else "s"} free"
}
