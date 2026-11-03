# Builder Timer Widget

Android home-screen widgets that track your Clash of Clans builder, lab and pet upgrades, with a notification when each one finishes.

<p align="center"><img src="docs/screenshots/widget.png" alt="Builder Timer widget showing builder, lab and pet upgrades with icons, progress bars and countdowns" width="360"></p>

**[Download the latest APK](https://github.com/ww40/coc-widget/releases/latest)**

- **Full widget:** every builder, lab and pet upgrade with icons, progress bars and countdowns (or finish times). Finished rows turn gold and the header shows free builders.
- **Compact 2×1 widget:** the next upgrade to finish and how many builders are free.
- **Notifications** when upgrades finish, with optional quiet hours that hold overnight alerts until morning.
- **Private:** the app has no internet permission. Your village data never leaves your phone.

## How it works

The official Clash of Clans API doesn't expose upgrade timers, so the app reads the game's own export:

1. In Clash of Clans: **Settings → More Settings → Data Export → Copy**.
2. Tap **⟳ Sync** on the widget. It reads the export from your clipboard.

Re-sync whenever you start or boost upgrades. Tapping an upgrade row or the footer opens Clash of Clans, as does tapping anywhere else on the compact widget. The full widget's header row doesn't, so a missed tap on **Settings** or **Sync** won't launch the game.

## Settings

Open settings with the **⚙ Settings** button on the widget, by long-pressing a widget and choosing **Settings**, or from the app icon.

<p align="center"><img src="docs/screenshots/settings.png" alt="Settings screen with player name, finish-time toggle and quiet hours" width="600"></p>

- **Player name:** shown in the widget header (the export only contains your player tag).
- **Show finish time instead of countdown:** e.g. "Tue 14:30" rather than "2d 5h 54m".
- **Dark mode for widgets:** swaps the Clash-style colours on both widgets for true black and greys.
- **Show estimated time to max:** adds a section under the upgrades with a time for Heroes, Guardians, Buildings, Pets and Lab, or **✓ Maxed**. A second switch measures against the game's highest levels instead of your Town Hall's.
- **Quiet hours:** notifications for upgrades finishing in this window arrive when it ends instead.

Tap **Save** to apply; widgets update within a minute. Pressing Back with unsaved changes asks whether to save them or keep the existing settings.

## Updating

Download the newest APK from [Releases](https://github.com/ww40/coc-widget/releases/latest) and open it to install over the old version. Your widgets, settings and last sync are kept.

To get updates automatically, install [Obtainium](https://github.com/ImranR98/Obtainium) and add `https://github.com/ww40/coc-widget`. Or click **Watch → Custom → Releases** on this repo for an email when a new version is out.

## Build

Requires JDK 17 and the Android SDK (platform 35). Set `sdk.dir` in `local.properties`, then:

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:assembleRelease     # signed release APK (needs signing config below)
```

### Release signing

Release builds are signed with credentials read from Gradle properties (e.g. `~/.gradle/gradle.properties`) or environment variables, never from the repo:

```properties
BUILDERTIMER_KEYSTORE=/path/to/buildertimer-release.jks
BUILDERTIMER_KEYSTORE_PASSWORD=...
BUILDERTIMER_KEY_ALIAS=buildertimer
BUILDERTIMER_KEY_PASSWORD=...
```

Android only installs updates signed with the same key, so back the keystore and its password up somewhere safe. Losing them means users must uninstall to update.

## Game data

`app/src/main/assets/game_data.json` maps export IDs to names and per-level upgrade durations, which drive the progress bars. It's generated from [ClashKing's static data](https://github.com/ClashKingInc/ClashKingAssets) (served at assets.clashk.ing/static_data). Regenerate it after a game update:

```bash
python3 tools/generate_game_data.py
```

Items the static data omits can be named (and given `upgradeSeconds`) in `tools/game_data_overrides.json`, which the generator merges in. Anything else missing shows as `Unknown (<id>)` with an empty progress bar.

## Icons

`app/src/main/res/drawable-nodpi/icon_<dataId>[_<level>].webp` are item icons from [ClashKing's assets](https://github.com/ClashKingInc/ClashKingAssets) (served at assets.clashk.ing), which ask for credit when used. Buildings and traps have one icon per level. Refresh them after regenerating game data:

```bash
python3 tools/fetch_icons.py
```

Items without an icon show a section emoji (🔨 builders, ⚗️ lab, 🐾 pets). `potion_builder.webp`, `potion_research.webp` and `potion_pet.webp`, shown with the time left on an active potion, are ClashKing's `magic_items/builder_potion.webp`, `magic_items/research_potion.webp` and `magic_items/pet_potion.webp`.

## Behaviour notes

- Countdowns are minute-precision; widgets refresh every minute while the device is awake.
- Finish notifications use inexact alarms, so they may arrive a few minutes late under Doze.
- Active Builder, Research and Pet Potions are included in finish times, so during a potion the widget shows less time than the game.
- Time to max is a rough estimate from your last sync. Heroes, Guardians and Buildings share your builders, so each time assumes your builders work only on that type, and the times don't add up to an overall total. Items you haven't built or unlocked aren't counted, and speed-ups such as potions, helpers and Gold Pass are ignored.
- Timers are only as current as the last sync; the widget footer turns amber once the last sync is over a day old.

## Credits and licence

Code is MIT licensed (see [LICENSE](LICENSE)). Item icons and game data by ClashKing.

This material is unofficial and is not endorsed by Supercell. For more information see Supercell's [Fan Content Policy](https://supercell.com/fan-content-policy).
