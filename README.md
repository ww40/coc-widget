# coc-widget

Android home-screen widget that tracks Clash of Clans builder, lab and pet upgrade timers, with a notification when each upgrade finishes.

Data comes from the game's own export (**Settings → More Settings → Data Export → Copy**). Tap **Sync** on the widget and it reads the export from the clipboard. The official Clash of Clans API doesn't expose upgrade timers, so the export is the only source.

## Build

Requires JDK 17 and the Android SDK (platform 35). Set `sdk.dir` in `local.properties`, then:

```bash
./gradlew :app:testDebugUnitTest   # parser + formatting tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` or by sideloading the APK.

## Game data

`app/src/main/assets/game_data.json` maps export IDs to names and per-level upgrade durations, which drive the progress bars. It's generated from the static data bundled in the [coc.py](https://pypi.org/project/coc.py/) package:

```bash
pip download --no-deps coc.py -d /tmp/cocpy && unzip -o /tmp/cocpy/coc_py-*.whl -d /tmp/cocpy/ext
python3 tools/generate_game_data.py /tmp/cocpy/ext/coc/static/static_data.json
```

IDs newer than that data show as `Unknown (<id>)` with an empty progress bar until it's regenerated.

## Behaviour notes

- Countdowns are minute-precision; the widget refreshes every minute while the device is awake.
- Finish notifications use inexact alarms, so they may arrive a few minutes late under Doze.
- Timers are only as current as the last sync; re-sync after starting or boosting upgrades.
