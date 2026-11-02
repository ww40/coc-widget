"""Generates app/src/main/assets/game_data.json from coc.py's static_data.json.

Usage: python3 tools/generate_game_data.py <path/to/static_data.json>

static_data.json ships inside the coc.py package (coc/static/static_data.json).
Output maps each export ID to its name and the seconds needed to reach each level.
"""

import json
import sys
from pathlib import Path
from typing import Any

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "app/src/main/assets/game_data.json"

# Buildings and traps store the time to *reach* a level on that level's entry.
BUILD_TIME_CATEGORIES = ("buildings", "traps")
# Troops, spells, heroes and pets store the time to go *from* a level to the next.
UPGRADE_TIME_CATEGORIES = ("troops", "spells", "heroes", "pets")
NAME_ONLY_CATEGORIES = ("helpers", "guardians")


def build_time_by_target(levels: list[dict[str, Any]]) -> dict[str, int]:
    return {str(lvl["level"]): lvl["build_time"] for lvl in levels if lvl.get("build_time")}


def upgrade_time_by_target(levels: list[dict[str, Any]]) -> dict[str, int]:
    return {str(lvl["level"] + 1): lvl["upgrade_time"] for lvl in levels if lvl.get("upgrade_time")}


def generate(static_data: dict[str, list[dict[str, Any]]]) -> dict[str, dict[str, Any]]:
    game_data: dict[str, dict[str, Any]] = {}
    for category in BUILD_TIME_CATEGORIES + UPGRADE_TIME_CATEGORIES + NAME_ONLY_CATEGORIES:
        for item in static_data[category]:
            entry: dict[str, Any] = {"name": item["name"]}
            levels = item.get("levels", [])
            if category in BUILD_TIME_CATEGORIES:
                entry["upgradeSeconds"] = build_time_by_target(levels)
            elif category in UPGRADE_TIME_CATEGORIES:
                entry["upgradeSeconds"] = upgrade_time_by_target(levels)
            game_data[str(item["_id"])] = entry
    return game_data


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    static_data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    game_data = generate(static_data)
    OUTPUT_PATH.write_text(json.dumps(game_data, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    print(f"Wrote {len(game_data)} entries to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
