package dev.ww40.buildertimer.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ww40.buildertimer.MainActivity
import dev.ww40.buildertimer.R
import dev.ww40.buildertimer.data.MaxEstimate
import dev.ww40.buildertimer.data.MaxTarget
import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.Upgrade
import dev.ww40.buildertimer.data.VillageSnapshot
import dev.ww40.buildertimer.data.formatAgo
import dev.ww40.buildertimer.data.formatEstimate
import dev.ww40.buildertimer.data.formatRemaining
import dev.ww40.buildertimer.sync.SyncActivity

private val OUTER_PADDING = 12.dp
private val ROW_HEIGHT = 28.dp
private val ROW_GAP = 4.dp
private val ROW_CORNER = 8.dp
private val ICON_SIZE = 22.dp
private val POTION_ICON_SIZE = 16.dp
private val TIME_TO_MAX_TOP_GAP = 22.dp

class VillageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ThemedWidget { WidgetContent() } }
    }
}

/** Per-section wording; a finished row reads e.g. "✓ Builder free". */
private enum class Section(val fallbackIcon: String, val freeLabel: String) {
    BUILDERS(BUILDER_FALLBACK_ICON, "Builder free"),
    LAB(LAB_FALLBACK_ICON, "Lab free"),
    PETS(PET_FALLBACK_ICON, "Pet House free"),
    BUILDER_BASE(BUILDER_FALLBACK_ICON, "Builder free"),
    STAR_LAB(LAB_FALLBACK_ICON, "Star Lab free"),
}

private enum class Potion(@DrawableRes val icon: Int, val label: String) {
    BUILDER(R.drawable.potion_builder, "Builder Potion"),
    RESEARCH(R.drawable.potion_research, "Research Potion"),
    PET(R.drawable.potion_pet, "Pet Potion"),
    CLOCK_TOWER(R.drawable.potion_clock_tower, "Clock Tower boost"),
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

    // Open-Clash goes on the content, not this root, so taps near the header buttons don't launch the game.
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .background(palette.background).padding(OUTER_PADDING),
    ) {
        Header(snapshot?.headerTitle(settings.playerName) ?: settings.playerName ?: "Builder Timer")
        Spacer(GlanceModifier.height(8.dp))
        if (snapshot == null) {
            Text(
                "No village data yet. In Clash: Settings → More Settings → Data Export → Copy, then tap Sync.",
                modifier = GlanceModifier.opensClash(context),
                style = TextStyle(color = palette.muted, fontSize = 13.sp),
            )
            return@Column
        }
        val now = rows.nowEpochSec
        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
            item { SectionTitle(buildersTitle(snapshot, now), Potion.BUILDER, snapshot.builderBoostRemaining(now)) }
            items(snapshot.builderUpgrades) { UpgradeRow(it, Section.BUILDERS, rows) }
            item {
                SectionTitle(
                    if (snapshot.labSecondsUntilFree(now) == 0L) "LAB · idle" else "LAB",
                    Potion.RESEARCH,
                    snapshot.labBoostRemaining(now),
                )
            }
            items(snapshot.labUpgrades) { UpgradeRow(it, Section.LAB, rows) }
            if (snapshot.petUpgrades.isNotEmpty()) {
                item { SectionTitle("PETS", Potion.PET, snapshot.petBoostRemaining(now)) }
                items(snapshot.petUpgrades) { UpgradeRow(it, Section.PETS, rows) }
            }
            if (settings.showBuilderBase && snapshot.builderBaseUpgrades.isNotEmpty()) {
                item { SectionTitle("BUILDER BASE", Potion.CLOCK_TOWER, snapshot.clockTowerBoostRemaining(now)) }
                items(snapshot.builderBaseUpgrades) { UpgradeRow(it, Section.BUILDER_BASE, rows) }
            }
            if (settings.showBuilderBase && snapshot.builderBaseMaxed) {
                item { SectionTitle("BUILDER BASE · ✓ Maxed") }
            }
            if (settings.showBuilderBase && snapshot.starLabUpgrades.isNotEmpty()) {
                item { SectionTitle("STAR LAB", Potion.CLOCK_TOWER, snapshot.clockTowerBoostRemaining(now)) }
                items(snapshot.starLabUpgrades) { UpgradeRow(it, Section.STAR_LAB, rows) }
            }
            val estimates = if (settings.showTimeToMax) snapshot.timeToMax[settings.maxTarget].orEmpty() else emptyList()
            if (estimates.isNotEmpty()) {
                item { TimeToMaxTitle(timeToMaxTitle(snapshot, settings.maxTarget)) }
                items(estimates) { TimeToMaxRow(it) }
            }
        }
        Footer(snapshot, now, settings)
    }
}

private fun buildersTitle(snapshot: VillageSnapshot, nowEpochSec: Long): String {
    val free = snapshot.freeBuilders(nowEpochSec)
    val freeText = if (free > 0) " · $free free" else ""
    val goblin = if (snapshot.hasActiveGoblin(nowEpochSec)) " + Goblin" else ""
    return "BUILDERS ${snapshot.busyBuilders(nowEpochSec)}/${snapshot.builderCount}$freeText$goblin"
}

private fun timeToMaxTitle(snapshot: VillageSnapshot, target: MaxTarget): String {
    val scope = if (target == MaxTarget.GAME) "GAME" else "TH${snapshot.townHallLevel}"
    return "EST. TIME TO MAX · $scope"
}

@Composable
private fun Header(title: String) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = palette.title, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Box(
            modifier = GlanceModifier.cornerRadius(12.dp).background(palette.settingsButton)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                "⚙ Settings",
                style = TextStyle(color = palette.title, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier.cornerRadius(12.dp).background(palette.syncButton)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(actionStartActivity<SyncActivity>()),
        ) {
            Text(
                "⟳ Sync",
                style = TextStyle(color = palette.syncButtonText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Section heading, followed by the potion icon and time left while a boost is active. */
@Composable
private fun SectionTitle(text: String, potion: Potion? = null, boostRemainingSeconds: Long = 0) {
    val style = TextStyle(color = palette.sectionTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Row(
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = style)
        if (potion == null || boostRemainingSeconds <= 0) return@Row
        Spacer(GlanceModifier.width(8.dp))
        Image(
            provider = ImageProvider(potion.icon),
            contentDescription = potion.label,
            modifier = GlanceModifier.size(POTION_ICON_SIZE),
            contentScale = ContentScale.Fit,
        )
        Spacer(GlanceModifier.width(2.dp))
        Text(formatRemaining(boostRemainingSeconds), style = style.copy(color = palette.boost))
    }
}

@Composable
private fun UpgradeRow(upgrade: Upgrade, section: Section, rows: RowContext) {
    val context = LocalContext.current
    val done = upgrade.isDone(rows.nowEpochSec)
    val textColor = if (done) palette.doneText else palette.rowText
    // Lazy list items swallow taps, so each row carries its own open-Clash action.
    Column(modifier = GlanceModifier.opensClash(context)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT).cornerRadius(ROW_CORNER)
                .background(if (done) palette.doneFill else palette.barTrack),
        ) {
            val progress = upgrade.progress(rows.nowEpochSec)
            if (!done && progress != null && progress > 0f) {
                Box(
                    modifier = GlanceModifier.width(rows.rowWidth * progress).fillMaxHeight()
                        .cornerRadius(ROW_CORNER).background(palette.barFill),
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

@Composable
private fun TimeToMaxTitle(text: String) {
    Text(
        text,
        modifier = GlanceModifier.padding(top = TIME_TO_MAX_TOP_GAP, bottom = 4.dp),
        style = TextStyle(color = palette.title, fontSize = 13.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun TimeToMaxRow(estimate: MaxEstimate) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).opensClash(context),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            estimate.category.label,
            modifier = GlanceModifier.defaultWeight(),
            style = rowTextStyle(palette.sectionTitle),
        )
        Text(
            if (estimate.isMaxed) "✓ Maxed" else formatEstimate(estimate.seconds),
            style = rowTextStyle(palette.rowText),
        )
    }
}

private fun rowTextStyle(color: ColorProvider) = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
private fun GoblinBadge() {
    Text(
        "GOBLIN",
        modifier = GlanceModifier.cornerRadius(4.dp).background(palette.goblinBadge).padding(horizontal = 4.dp),
        style = TextStyle(color = palette.rowText, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    )
}

/** Amber once the helper can take a new job. */
@Composable
private fun HelperStatus(name: String, cooldownSeconds: Long, style: TextStyle) {
    if (cooldownSeconds > 0) {
        Text("$name ready in ${formatRemaining(cooldownSeconds)}", style = style, maxLines = 1)
    } else {
        Text("$name ready", style = style.copy(color = palette.stale, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun Footer(snapshot: VillageSnapshot, nowEpochSec: Long, settings: Settings) {
    val apprentice = snapshot.apprenticeTarget?.name ?: "idle"
    val assistant = snapshot.labAssistantTarget?.name ?: "idle"
    val style = TextStyle(color = palette.muted, fontSize = 11.sp)
    val stale = snapshot.isWidgetStale(nowEpochSec, settings.showBuilderBase)
    val context = LocalContext.current
    val synced = "synced ${formatAgo(nowEpochSec - snapshot.exportedAtEpochSec)}" + if (stale) " · re-sync" else ""
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp).opensClash(context)) {
        val showApprentice = snapshot.apprenticeReadyAtEpochSec != null
        if (showApprentice) HelperStatus("Apprentice", snapshot.apprenticeCooldownRemaining(nowEpochSec), style)
        if (snapshot.labAssistantReadyAtEpochSec != null) {
            if (showApprentice) Text(" · ", style = style)
            HelperStatus("Assistant", snapshot.labAssistantCooldownRemaining(nowEpochSec), style)
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp).opensClash(context)) {
        Text(
            "Apprentice: $apprentice · Assistant: $assistant",
            modifier = GlanceModifier.defaultWeight(),
            style = style,
            maxLines = 1,
        )
        Text(
            synced,
            style = if (stale) style.copy(color = palette.stale, fontWeight = FontWeight.Bold) else style,
        )
    }
}
