"""Downloads item icons from ClashKing's asset CDN into app/src/main/res/drawable-nodpi.

Usage: python3 tools/fetch_icons.py

Icons come from https://assets.clashk.ing (github.com/ClashKingInc/ClashKingAssets), which asks
users to credit ClashKing. Names come from app/src/main/assets/game_data.json; each item found
is saved as icon_<dataId>.webp. Items without a match fall back to a section emoji in the widget.
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


def highest_level_path(paths: set[str], prefix: str) -> str | None:
    levels = [
        (int(match.group(1)), path)
        for path in paths
        if path.startswith(prefix) and (match := LEVEL_FILE.search(path))
    ]
    return max(levels)[1] if levels else None


def icon_path(data_type: int, name: str, paths: set[str]) -> str | None:
    clean = cleaned_name(name)
    if data_type in LEVELLED_PATHS:
        return highest_level_path(paths, LEVELLED_PATHS[data_type].format(name=clean))
    if data_type in SINGLE_PATHS:
        path = SINGLE_PATHS[data_type].format(name=clean)
        return path if path in paths else None
    return None


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
        path = icon_path(data_type, entry["name"], paths)
        if path is None:
            missing.append(f"{data_id} {entry['name']}")
            continue
        try:
            data = fetch(f"{CDN_URL}/{path}")
        except urllib.error.URLError as error:
            print(f"Download failed for {data_id} ({path}): {error}", file=sys.stderr)
            missing.append(f"{data_id} {entry['name']}")
            continue
        (OUTPUT_DIR / f"icon_{data_id}.webp").write_bytes(data)
        saved += 1

    print(f"Saved {saved} icons to {OUTPUT_DIR}")
    for item in missing:
        print(f"  no icon: {item}")


if __name__ == "__main__":
    main()
