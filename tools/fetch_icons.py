"""Downloads item icons from ClashKing's asset CDN into app/src/main/res/drawable-nodpi.

Usage: python3 tools/fetch_icons.py

Icons come from https://assets.clashk.ing (github.com/ClashKingInc/ClashKingAssets), which asks
users to credit ClashKing. Names come from app/src/main/assets/game_data.json; each item found
is saved as icon_<dataId>.webp, plus icon_<dataId>_<level>.webp per level for buildings and traps.
Items without a match fall back to a section emoji in the widget.
"""

import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GAME_DATA_PATH = ROOT / "app/src/main/assets/game_data.json"
OUTPUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"

CDN_URL = "https://assets.clashk.ing"
MANIFEST_URL = f"{CDN_URL}/manifest.json"
USER_AGENT = "coc-widget-icon-fetch/0.1 (personal non-commercial project)"

# Export data IDs are <type> * 1_000_000 + index.
LEVELLED_PATHS = {1: "buildings/home-village/{name}/", 12: "traps/home-village/{name}/"}
SINGLE_PATHS = {
    4: "troops/{name}/icon.webp",
    26: "spells/{name}.webp",
    28: "heroes/{name}/icon.webp",
    73: "pets/{name}/icon.webp",
    107: "guardians/{name}/icon.webp",
}
LEVEL_FILE = re.compile(r"level_(\d+)\.webp$")


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def cleaned_name(name: str) -> str:
    """ClashKing's documented path-naming rule."""
    return (name.lower().replace(" ", "_").replace(".", "").replace("?", "")
            .replace("\\q", "").replace("’", ""))


def level_paths(paths: set[str], prefix: str) -> dict[int, str]:
    return {
        int(match.group(1)): path
        for path in paths
        if path.startswith(prefix) and (match := LEVEL_FILE.search(path))
    }


def icon_files(data_id: str, data_type: int, name: str, paths: set[str]) -> dict[str, str]:
    """Maps output file names to CDN paths: one per level plus a highest-level fallback, or a single icon."""
    clean = cleaned_name(name)
    if data_type in LEVELLED_PATHS:
        levels = level_paths(paths, LEVELLED_PATHS[data_type].format(name=clean))
        if not levels:
            return {}
        files = {f"icon_{data_id}_{level}.webp": path for level, path in levels.items()}
        files[f"icon_{data_id}.webp"] = levels[max(levels)]
        return files
    path = SINGLE_PATHS[data_type].format(name=clean)
    return {f"icon_{data_id}.webp": path} if path in paths else {}


def main() -> None:
    game_data: dict[str, dict] = json.loads(GAME_DATA_PATH.read_text(encoding="utf-8"))
    manifest = json.loads(fetch(MANIFEST_URL))
    paths = {asset["path"] for group in manifest["assets"].values() for asset in group}
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    saved, missing = 0, []
    for data_id, entry in sorted(game_data.items()):
        data_type = int(data_id) // 1_000_000
        if data_type not in LEVELLED_PATHS and data_type not in SINGLE_PATHS:
            continue
        files = icon_files(data_id, data_type, entry["name"], paths)
        if not files:
            missing.append(f"{data_id} {entry['name']}")
            continue
        for file_name, path in files.items():
            try:
                (OUTPUT_DIR / file_name).write_bytes(fetch(f"{CDN_URL}/{path}"))
                saved += 1
            except urllib.error.URLError as error:
                print(f"Download failed for {file_name} ({path}): {error}", file=sys.stderr)

    print(f"Saved {saved} icon files to {OUTPUT_DIR}")
    for item in missing:
        print(f"  no icon: {item}")


if __name__ == "__main__":
    main()
