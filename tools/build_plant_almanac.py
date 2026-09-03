#!/usr/bin/env python3
"""Writes the almanac text for every plant out of the project spreadsheet.

The engine only needs the numbers, which live in data/plants.csv. The
collection menu also shows what each plant actually does, what its plant food
does, and what the three upgrade levels give — all of it written in the sheet
and none of it derivable from the numbers.

Those fields contain commas, so they go in a TSV rather than the CSV: no field
in the sheet contains a tab or a newline, which this script checks.

    python3 tools/build_plant_almanac.py "AP project 2026.xlsx"

writes src/main/resources/data/plant-almanac.tsv, keyed by the plant id the
rest of the game uses.
"""
import re
import sys
from pathlib import Path

import openpyxl

OUT = Path("src/main/resources/data/plant-almanac.tsv")
CSV = Path("src/main/resources/data/plants.csv")

COLUMNS = ["Damage", "Action Interval (s)", "Base Ability", "Plant Food Effect",
           "Lvl 2", "Lvl 3", "Lvl 4"]


def key(name):
    """The sheet and our data files spell names differently; compare on letters."""
    return re.sub(r"[^a-z0-9]", "", str(name).lower())


def our_ids():
    lines = CSV.read_text(encoding="utf-8").splitlines()[1:]
    return [line.split(",")[0] for line in lines if line.strip()]


def main():
    book = openpyxl.load_workbook(sys.argv[1], data_only=True)
    sheet = book["Plants"]
    header = [cell.value for cell in sheet[1]]

    rows = {}
    for values in sheet.iter_rows(min_row=2, max_row=70, values_only=True):
        if values[0] is None:
            continue
        row = dict(zip(header, values))
        rows[key(row["Name"])] = row

    lines = ["\t".join(["id", "damage", "interval", "ability", "plantFood",
                        "lvl2", "lvl3", "lvl4"])]
    missing = []
    for plant in our_ids():
        row = rows.get(key(plant))
        if row is None:
            missing.append(plant)
            continue
        fields = [plant]
        for column in COLUMNS:
            text = str(row.get(column) or "-").strip()
            if "\t" in text or "\n" in text:
                raise SystemExit(f"{plant}: {column} contains a tab or newline")
            fields.append(text)
        lines.append("\t".join(fields))

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT} for {len(lines) - 1} plants")
    if missing:
        print(f"\n{len(missing)} plants are ours rather than the sheet's, "
              f"so they get no almanac row:")
        for plant in missing:
            print("   ", plant)


if __name__ == "__main__":
    main()
