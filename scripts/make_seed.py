#!/usr/bin/env python3
# Kitae for iPhone
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes Kitae/Database/Seed.json from Kitae for Android's starter database.

Kitae for Android ships its exercises and its starter plan in app/src/main/assets/kenko.db.
Kitae for iPhone starts from the same exercises, with the same ids, and the same plan, so
both apps begin alike. The plan's description is left out: it's text from Kenko, and
Kitae for iPhone doesn't include Kenko's work. The exercises Kitae's library adds after the
starters have no id and aren't in Android's database: they're written in Seed.json by hand,
and this keeps them. Run this again when the Android app's starter data changes:

    python3 scripts/make_seed.py path/to/kitae/app/src/main/assets/kenko.db
"""

import json
import sqlite3
import sys
from pathlib import Path

# Kitae's library key for each starter exercise, by its id: the exercise's lasting name in
# the database (`exercises.libraryKey`), never changed or given to another exercise.
KEYS = {
    1: "dumbbell-curls",
    2: "barbell-curls",
    3: "ez-bar-preacher-curls",
    4: "incline-dumbbell-curls",
    5: "tricep-pushdowns",
    6: "ez-bar-skull-crushers",
    7: "dumbbell-overhead-tricep-extensions",
    8: "dumbbell-lateral-raises",
    9: "dumbbell-shoulder-press",
    10: "face-pulls",
    11: "barbell-back-squats",
    12: "leg-press",
    13: "hack-squats",
    14: "barbell-stiff-legged-deadlift",
    15: "lying-leg-curls",
    16: "standing-calf-raises",
    17: "barbell-hip-thrusts",
    18: "dumbbell-lunges",
    19: "sit-ups",
    20: "lying-leg-raises",
    21: "barbell-bench-press",
    22: "barbell-incline-bench-press",
    23: "pec-deck",
    24: "dumbbell-flyes",
    25: "dumbbell-shrugs",
    26: "lat-pulldowns",
    27: "pull-ups",
    28: "lat-prayers",
    29: "barbell-bent-over-rows",
    30: "chest-supported-dumbbell-rows",
    31: "leg-extensions",
    32: "behind-the-back-cable-curls",
    33: "smith-machine-squats",
    34: "cable-lateral-raises",
    35: "barbell-upright-rows",
    36: "dumbbell-bench-press",
    37: "incline-dumbbell-press",
    38: "barbell-decline-bench-press",
    39: "cable-crossovers",
    40: "push-ups",
    41: "machine-chest-press",
    42: "chin-ups",
    43: "straight-arm-pulldowns",
    44: "seated-cable-rows",
    45: "single-arm-dumbbell-rows",
    46: "t-bar-rows",
    47: "barbell-deadlift",
    48: "barbell-overhead-press",
    49: "arnold-press",
    50: "dumbbell-front-raises",
    51: "dumbbell-rear-delt-flyes",
    52: "hammer-curls",
    53: "cable-curls",
    54: "concentration-curls",
    55: "dips",
    56: "close-grip-bench-press",
    57: "dumbbell-tricep-kickbacks",
    58: "barbell-front-squats",
    59: "dumbbell-bulgarian-split-squats",
    60: "goblet-squats",
    61: "wall-sit",
    62: "barbell-romanian-deadlift",
    63: "seated-leg-curls",
    64: "good-mornings",
    65: "glute-bridges",
    66: "cable-glute-kickbacks",
    67: "machine-hip-abductions",
    68: "seated-calf-raises",
    69: "plank",
    70: "side-plank",
    71: "crunches",
    72: "cable-crunches",
    73: "russian-twists",
    74: "hanging-leg-raises",
    75: "ab-wheel-rollouts",
}

# The starter exercises done with the body alone, by library key: their sets are reps (or
# seconds) without weight (`exercises.usesWeight`). Kitae for Android's data has no such column.
WEIGHTLESS = {
    "sit-ups", "lying-leg-raises", "pull-ups", "push-ups", "chin-ups", "dips", "wall-sit",
    "glute-bridges", "plank", "side-plank", "crunches", "hanging-leg-raises", "ab-wheel-rollouts",
}


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    db = sqlite3.connect(f"file:{sys.argv[1]}?mode=ro", uri=True)
    db.row_factory = sqlite3.Row

    exercises = []
    for row in db.execute("SELECT * FROM exercises ORDER BY id"):
        exercise = {
            "id": row["id"],
            "key": KEYS[row["id"]],
            "name": row["name"],
            "target": row["target"],
            "isIsometric": bool(row["isIsometric"]),
            "usesWeight": KEYS[row["id"]] not in WEIGHTLESS,
        }
        for column in ("reference", "restSeconds", "instructions"):
            if row[column] is not None:
                exercise[column] = row[column]
        exercises.append(exercise)

    plans = []
    for plan in db.execute("SELECT * FROM plans ORDER BY id"):
        days = []
        routines = db.execute(
            "SELECT * FROM routines WHERE planId = ? ORDER BY position, id", (plan["id"],)
        ).fetchall()
        for routine in routines:
            day_exercises = []
            slots = db.execute(
                "SELECT * FROM routine_exercises WHERE routineId = ? ORDER BY position, id",
                (routine["id"],),
            ).fetchall()
            for slot in slots:
                sets = []
                planned = db.execute(
                    "SELECT * FROM routine_sets WHERE routineExerciseId = ? ORDER BY position, id",
                    (slot["id"],),
                ).fetchall()
                for planned_set in planned:
                    entry = {
                        "reps": planned_set["reps"],
                        "weight": planned_set["weight"],
                        "type": planned_set["type"],
                    }
                    if planned_set["restSeconds"] is not None:
                        entry["restSeconds"] = planned_set["restSeconds"]
                    sets.append(entry)
                day_exercises.append({"exercise": slot["exerciseId"], "sets": sets})
            days.append({"name": routine["name"], "exercises": day_exercises})
        entry = {"name": plan["name"]}
        for column in ("difficulty", "focus", "equipment", "time"):
            if plan[column] is not None:
                entry[column] = plan[column]
        entry["days"] = days
        plans.append(entry)

    out = Path(__file__).resolve().parent.parent / "Kitae" / "Database" / "Seed.json"
    # Kitae's library: the exercises after the starters, which Kitae for Android's database
    # doesn't have. They're written in Seed.json by hand, without ids, and kept as they are.
    if out.exists():
        exercises += [e for e in json.loads(out.read_text())["exercises"] if "id" not in e]
    out.write_text(json.dumps({"exercises": exercises, "plans": plans}, indent=1, ensure_ascii=False) + "\n")
    print(f"{out}: {len(exercises)} exercises, {len(plans)} plans")


if __name__ == "__main__":
    main()
