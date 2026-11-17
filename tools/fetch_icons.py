"""Downloads item icons from ClashKing's asset CDN into app/src/main/res/drawable-nodpi.

Usage: python3 tools/fetch_icons.py

Icons come from https://assets.clashk.ing (github.com/ClashKingInc/ClashKingAssets), which asks
users to credit ClashKing. Names come from app/src/main/assets/game_data.json; each item found
is saved as icon_<dataId>.webp, plus icon_<dataId>_<level>.webp per level for buildings and traps.
Builder Base items share names with Home Village ones, so static data says which folder to use.
Items without a match fall back to a section emoji in the widget.
"""

import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GAME_DATA_PATH = ROOT / "app/src/main/assets/game_data.json"
OUTPUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"

CDN_URL = "https://assets.clashk.ing"
MANIFEST_URL = f"{CDN_URL}/manifest.json"
STATIC_DATA_URL = f"{CDN_URL}/static_data/{{category}}.json"
BUILDER_BASE_VILLAGE = "builderBase"
USER_AGENT = "coc-widget-icon-fetch/0.1 (personal non-commercial project)"
FETCH_ATTEMPTS = 4
RETRY_DELAY_SECONDS = 20

# Export data IDs are <type> * 1_000_000 + index.
LEVELLED_PATHS = {1: "buildings/{village}/{name}/", 12: "traps/{village}/{name}/"}
LEVELLED_CATEGORIES = ("buildings", "traps")
SINGLE_PATHS = {
    4: "troops/{name}/icon.webp",
    26: "spells/{name}.webp",
    28: "heroes/{name}/icon.webp",
    73: "pets/{name}/icon.webp",
    107: "guardians/{name}/icon.webp",
}
LEVEL_FILE = re.compile(r"level_(\d+)\.webp$")


def fetch(url: str) -> bytes:
    """Retries network failures, since a burst of lookups can trip a local DNS rate limit."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(1, FETCH_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except urllib.error.HTTPError:
            raise
        except urllib.error.URLError:
            if attempt == FETCH_ATTEMPTS:
                raise
            time.sleep(RETRY_DELAY_SECONDS)
    raise AssertionError("unreachable")


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


def builder_base_ids() -> set[str]:
    items = [item for category in LEVELLED_CATEGORIES
             for item in json.loads(fetch(STATIC_DATA_URL.format(category=category)))["items"]]
    return {str(item["_id"]) for item in items if item.get("village") == BUILDER_BASE_VILLAGE}


def icon_files(data_id: str, data_type: int, name: str, paths: set[str], builder_base: set[str]) -> dict[str, str]:
    """Maps output file names to CDN paths: one per level plus a highest-level fallback, or a single icon."""
    clean = cleaned_name(name)
    if data_type in LEVELLED_PATHS:
        village = "builder-base" if data_id in builder_base else "home-village"
        levels = level_paths(paths, LEVELLED_PATHS[data_type].format(village=village, name=clean))
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
    builder_base = builder_base_ids()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    saved, missing = 0, []
    for data_id, entry in sorted(game_data.items()):
        data_type = int(data_id) // 1_000_000
        if data_type not in LEVELLED_PATHS and data_type not in SINGLE_PATHS:
            continue
        files = icon_files(data_id, data_type, entry["name"], paths, builder_base)
        if not files:
            missing.append(f"{data_id} {entry['name']}")
            continue
        # Download everything first so a failed fetch leaves the item's existing icons in place.
        try:
            downloads = {file_name: fetch(f"{CDN_URL}/{path}") for file_name, path in files.items()}
        except urllib.error.URLError as error:
            print(f"Download failed for {data_id} {entry['name']}, kept existing icons: {error}", file=sys.stderr)
            continue
        for old_file in OUTPUT_DIR.glob(f"icon_{data_id}_*.webp"):
            old_file.unlink()
        for file_name, content in downloads.items():
            (OUTPUT_DIR / file_name).write_bytes(content)
            saved += 1

    print(f"Saved {saved} icon files to {OUTPUT_DIR}")
    for item in missing:
        print(f"  no icon: {item}")


if __name__ == "__main__":
    main()
