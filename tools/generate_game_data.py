"""Generates app/src/main/assets/game_data.json from ClashKing's static game data.

Usage: python3 tools/generate_game_data.py

Data comes from https://assets.clashk.ing/static_data (github.com/ClashKingInc/ClashKingAssets),
which asks users to credit ClashKing. Output maps each export ID to its name, the seconds
needed to reach each level and the Town Hall each level requires. Entries in game_data_overrides.json are merged on top, for items
the static data omits.
"""

import json
import urllib.request
from pathlib import Path
from typing import Any

TOOLS_DIR = Path(__file__).resolve().parent
OUTPUT_PATH = TOOLS_DIR.parent / "app/src/main/assets/game_data.json"
OVERRIDES_PATH = TOOLS_DIR / "game_data_overrides.json"

STATIC_DATA_URL = "https://assets.clashk.ing/static_data/{category}.json"
USER_AGENT = "coc-widget-game-data/0.1 (personal non-commercial project)"

# Buildings and traps store the time to *reach* a level on that level's entry.
BUILD_TIME_CATEGORIES = ("buildings", "traps")
# Troops, spells, heroes, pets and guardians store the time to go *from* a level to the next.
UPGRADE_TIME_CATEGORIES = ("troops", "spells", "heroes", "pets", "guardians")
NAME_ONLY_CATEGORIES = ("helpers",)
CATEGORIES = BUILD_TIME_CATEGORIES + UPGRADE_TIME_CATEGORIES + NAME_ONLY_CATEGORIES


def fetch_items(category: str) -> list[dict[str, Any]]:
    request = urllib.request.Request(STATIC_DATA_URL.format(category=category), headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())["items"]


def build_time_by_target(levels: list[dict[str, Any]]) -> dict[str, int]:
    return {str(lvl["level"]): lvl["build_time"] for lvl in levels if lvl.get("build_time")}


def upgrade_time_by_target(levels: list[dict[str, Any]]) -> dict[str, int]:
    return {str(lvl["level"] + 1): lvl["upgrade_time"] for lvl in levels if lvl.get("upgrade_time")}


def required_town_hall_by_level(levels: list[dict[str, Any]]) -> dict[str, int]:
    return {str(lvl["level"]): lvl["required_townhall"] for lvl in levels if lvl.get("required_townhall") is not None}


def generate(static_data: dict[str, list[dict[str, Any]]]) -> dict[str, dict[str, Any]]:
    game_data: dict[str, dict[str, Any]] = {}
    for category in CATEGORIES:
        for item in static_data[category]:
            entry: dict[str, Any] = {"name": item["name"]}
            levels = item.get("levels", [])
            if category in BUILD_TIME_CATEGORIES:
                entry["upgradeSeconds"] = build_time_by_target(levels)
            elif category in UPGRADE_TIME_CATEGORIES:
                entry["upgradeSeconds"] = upgrade_time_by_target(levels)
            if "upgradeSeconds" in entry:
                required = required_town_hall_by_level(levels)
                entry["requiredTownHall"] = {lvl: required[lvl] for lvl in entry["upgradeSeconds"] if lvl in required}
            game_data[str(item["_id"])] = entry
    return game_data


def main() -> None:
    game_data = generate({category: fetch_items(category) for category in CATEGORIES})
    overrides = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8"))
    for data_id, override in overrides.items():
        game_data[data_id] = {**game_data.get(data_id, {}), **override}
    OUTPUT_PATH.write_text(json.dumps(game_data, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    print(f"Wrote {len(game_data)} entries to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
