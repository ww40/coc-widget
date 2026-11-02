package dev.ww40.buildertimer.widget

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
import androidx.glance.unit.ColorProvider
import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.Upgrade
import dev.ww40.buildertimer.data.VillageSnapshot
import dev.ww40.buildertimer.data.formatAgo
import dev.ww40.buildertimer.sync.SyncActivity

private val OUTER_PADDING = 12.dp
private val ROW_HEIGHT = 28.dp
private val ROW_GAP = 4.dp
private val ROW_CORNER = 8.dp
private val ICON_SIZE = 22.dp

class VillageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

/** Per-section wording; a finished row reads e.g. "✓ Builder free". */
private enum class Section(val fallbackIcon: String, val freeLabel: String) {
    BUILDERS(BUILDER_FALLBACK_ICON, "Builder free"),
    LAB(LAB_FALLBACK_ICON, "Lab free"),
    PETS(PET_FALLBACK_ICON, "Pet House free"),
}

/** Everything a row needs that's the same for every row in one composition. */
private class RowContext(val nowEpochSec: Long, val settings: Settings, val rowWidth: Dp)

@Composable
private fun WidgetContent() {
    // Reading state subscribes this composition to WidgetRefresher's ticks.
    currentState<Preferences>()
    val context = LocalContext.current
    val snapshot = SnapshotStore(context).load()
    val settings = SettingsStore(context).load()
    val rows = RowContext(System.currentTimeMillis() / 1000, settings, LocalSize.current.width - OUTER_PADDING * 2)

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .background(WidgetPalette.background).padding(OUTER_PADDING).opensClash(context),
    ) {
        Header(snapshot?.headerTitle(settings.playerName) ?: settings.playerName ?: "Builder Timer")
        Spacer(GlanceModifier.height(8.dp))
        if (snapshot == null) {
            Text(
                "No village data yet. In Clash: Settings → More Settings → Data Export → Copy, then tap Sync.",
                style = TextStyle(color = WidgetPalette.muted, fontSize = 13.sp),
            )
            return@Column
        }
        val now = rows.nowEpochSec
        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
            item { SectionTitle(buildersTitle(snapshot, now)) }
            items(snapshot.builderUpgrades) { UpgradeRow(it, Section.BUILDERS, rows) }
            item { SectionTitle(if (snapshot.labUpgrades.all { it.isDone(now) }) "LAB · idle" else "LAB") }
            items(snapshot.labUpgrades) { UpgradeRow(it, Section.LAB, rows) }
            if (snapshot.petUpgrades.isNotEmpty()) {
                item { SectionTitle("PETS") }
                items(snapshot.petUpgrades) { UpgradeRow(it, Section.PETS, rows) }
            }
        }
        Footer(snapshot, now)
    }
}

private fun buildersTitle(snapshot: VillageSnapshot, nowEpochSec: Long): String {
    val free = snapshot.freeBuilders(nowEpochSec)
    val freeText = if (free > 0) " · $free free" else ""
    val goblin = if (snapshot.hasActiveGoblin(nowEpochSec)) " + Goblin" else ""
    return "BUILDERS ${snapshot.busyBuilders(nowEpochSec)}/${snapshot.builderCount}$freeText$goblin"
}

@Composable
private fun Header(title: String) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
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
private fun UpgradeRow(upgrade: Upgrade, section: Section, rows: RowContext) {
    val context = LocalContext.current
    val done = upgrade.isDone(rows.nowEpochSec)
    val textColor = if (done) WidgetPalette.doneText else WidgetPalette.rowText
    // Lazy list items swallow taps, so each row carries its own open-Clash action.
    Column(modifier = GlanceModifier.opensClash(context)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT).cornerRadius(ROW_CORNER)
                .background(if (done) WidgetPalette.doneFill else WidgetPalette.barTrack),
        ) {
            val progress = upgrade.progress(rows.nowEpochSec)
            if (!done && progress != null && progress > 0f) {
                Box(
                    modifier = GlanceModifier.width(rows.rowWidth * progress).fillMaxHeight()
                        .cornerRadius(ROW_CORNER).background(WidgetPalette.barFill),
                ) {}
            }
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UpgradeIcon(upgrade, section.fallbackIcon, ICON_SIZE)
                Spacer(GlanceModifier.width(6.dp))
                if (upgrade.isGoblin) {
                    GoblinBadge()
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    "${upgrade.name} → ${upgrade.targetLevel}",
                    modifier = GlanceModifier.defaultWeight(),
                    style = rowTextStyle(textColor),
                    maxLines = 1,
                )
                Text(
                    if (done) "✓ ${section.freeLabel}" else "⏱ ${timeLabel(context, upgrade, rows.nowEpochSec, rows.settings)}",
                    style = rowTextStyle(textColor),
                )
            }
        }
        Spacer(GlanceModifier.height(ROW_GAP))
    }
}

private fun rowTextStyle(color: ColorProvider) = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
private fun GoblinBadge() {
    Text(
        "GOBLIN",
        modifier = GlanceModifier.cornerRadius(4.dp).background(WidgetPalette.goblinBadge).padding(horizontal = 4.dp),
        style = TextStyle(color = WidgetPalette.rowText, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Footer(snapshot: VillageSnapshot, nowEpochSec: Long) {
    val apprentice = snapshot.apprenticeTarget?.name ?: "idle"
    val assistant = snapshot.labAssistantTarget?.name ?: "idle"
    val style = TextStyle(color = WidgetPalette.muted, fontSize = 11.sp)
    val stale = snapshot.isWidgetStale(nowEpochSec)
    val synced = "synced ${formatAgo(nowEpochSec - snapshot.exportedAtEpochSec)}" + if (stale) " · re-sync" else ""
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            "Apprentice: $apprentice · Assistant: $assistant",
            modifier = GlanceModifier.defaultWeight(),
            style = style,
            maxLines = 1,
        )
        Text(
            synced,
            style = if (stale) style.copy(color = WidgetPalette.stale, fontWeight = FontWeight.Bold) else style,
        )
    }
}
