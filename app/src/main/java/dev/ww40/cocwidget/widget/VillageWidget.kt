package dev.ww40.cocwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.ww40.cocwidget.data.SnapshotStore
import dev.ww40.cocwidget.data.Upgrade
import dev.ww40.cocwidget.data.VillageSnapshot
import dev.ww40.cocwidget.data.formatAgo
import dev.ww40.cocwidget.data.formatRemaining
import dev.ww40.cocwidget.sync.SyncActivity

private val OUTER_PADDING = 12.dp
private val ROW_HEIGHT = 28.dp
private val ROW_GAP = 4.dp
private val ROW_CORNER = 8.dp

class VillageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

@Composable
private fun WidgetContent() {
    // Reading state subscribes this composition to WidgetRefresher's ticks.
    currentState<Preferences>()
    val snapshot = SnapshotStore(LocalContext.current).load()
    val nowEpochSec = System.currentTimeMillis() / 1000
    val rowWidth = LocalSize.current.width - OUTER_PADDING * 2

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .background(WidgetPalette.background).padding(OUTER_PADDING),
    ) {
        Header(snapshot)
        Spacer(GlanceModifier.height(8.dp))
        if (snapshot == null) {
            Text(
                "No village data yet. In Clash: Settings → More Settings → Data Export → Copy, then tap Sync.",
                style = TextStyle(color = WidgetPalette.muted, fontSize = 13.sp),
            )
            return@Column
        }
        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
            item { SectionTitle(buildersTitle(snapshot)) }
            items(snapshot.builderUpgrades) { UpgradeRow(it, nowEpochSec, rowWidth) }
            if (snapshot.labUpgrades.isNotEmpty()) {
                item { SectionTitle("LAB") }
                items(snapshot.labUpgrades) { UpgradeRow(it, nowEpochSec, rowWidth) }
            }
            if (snapshot.petUpgrades.isNotEmpty()) {
                item { SectionTitle("PETS") }
                items(snapshot.petUpgrades) { UpgradeRow(it, nowEpochSec, rowWidth) }
            }
        }
        Footer(snapshot, nowEpochSec)
    }
}

private fun buildersTitle(snapshot: VillageSnapshot): String {
    val goblin = if (snapshot.hasGoblin) " + Goblin" else ""
    return "BUILDERS ${snapshot.busyBuilders}/${snapshot.builderCount}$goblin"
}

@Composable
private fun Header(snapshot: VillageSnapshot?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val title = snapshot?.let { s -> listOfNotNull(s.tag, s.townHallLevel?.let { "TH$it" }).joinToString("  ") }
        Text(
            title ?: "Clash Tracker",
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = WidgetPalette.title, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Box(
            modifier = GlanceModifier.cornerRadius(12.dp).background(WidgetPalette.syncButton)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(actionStartActivity<SyncActivity>()),
        ) {
            Text(
                "⟳ Sync",
                style = TextStyle(color = WidgetPalette.syncButtonText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 4.dp),
        style = TextStyle(color = WidgetPalette.sectionTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun UpgradeRow(upgrade: Upgrade, nowEpochSec: Long, rowWidth: Dp) {
    Column {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT).cornerRadius(ROW_CORNER)
                .background(WidgetPalette.barTrack),
        ) {
            val progress = upgrade.progress(nowEpochSec)
            if (progress != null && progress > 0f) {
                Box(
                    modifier = GlanceModifier.width(rowWidth * progress).fillMaxHeight()
                        .cornerRadius(ROW_CORNER).background(WidgetPalette.barFill),
                ) {}
            }
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (upgrade.isGoblin) {
                    Text(
                        "GOBLIN",
                        modifier = GlanceModifier.cornerRadius(4.dp).background(WidgetPalette.goblinBadge)
                            .padding(horizontal = 4.dp),
                        style = TextStyle(color = WidgetPalette.rowText, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    "${upgrade.name} → ${upgrade.targetLevel}",
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetPalette.rowText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    "⏱ ${formatRemaining(upgrade.remainingSeconds(nowEpochSec))}",
                    style = TextStyle(color = WidgetPalette.rowText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(GlanceModifier.height(ROW_GAP))
    }
}

@Composable
private fun Footer(snapshot: VillageSnapshot, nowEpochSec: Long) {
    val apprentice = snapshot.apprenticeTarget?.name ?: "idle"
    val assistant = snapshot.labAssistantTarget?.name ?: "idle"
    val style = TextStyle(color = WidgetPalette.muted, fontSize = 11.sp)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            "Apprentice: $apprentice · Assistant: $assistant",
            modifier = GlanceModifier.defaultWeight(),
            style = style,
            maxLines = 1,
        )
        Text("synced ${formatAgo(nowEpochSec - snapshot.exportedAtEpochSec)}", style = style)
    }
}
