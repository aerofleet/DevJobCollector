#!/usr/bin/env python3
"""Prepare a seeded, source-balanced, unlabelled offline review sample.

The input is a local UTF-8 CSV with id, source, title and description columns.
This tool does not fetch or predict, and deliberately leaves expected blank.
"""

import argparse
import csv
import random
import sys
from collections import defaultdict


def sample(rows, size, seed):
    if size < 1:
        raise ValueError("size must be positive")
    groups = defaultdict(list)
    ids = set()
    for row in rows:
        identifier = (row.get("id") or "").strip()
        source = (row.get("source") or "").strip()
        title = row.get("title") or ""
        description = row.get("description") or ""
        if not identifier or identifier in ids:
            raise ValueError("ids must be present and unique")
        ids.add(identifier)
        if source and title.strip() and description.strip():
            groups[source].append({"id": identifier, "source": source, "company": row.get("company", ""), "title": title,
                                   "description": description, "reviewer_a": "", "reviewer_b": "",
                                   "adjudicated_label": "", "evidence_quote": "", "hard_negative": "false"})
    available = sum(map(len, groups.values()))
    if size > available:
        raise ValueError(f"requested {size} samples but only {available} eligible records are available")
    rng = random.Random(seed)
    # Sorting removes dependence on input ordering before deterministic shuffling.
    sources = sorted(groups)
    for source in sources:
        group = groups[source]
        group.sort(key=lambda row: row["id"])
        rng.shuffle(group)
    chosen = []
    while len(chosen) < size:
        for source in sources:
            if groups[source] and len(chosen) < size:
                chosen.append(groups[source].pop())
    rng.shuffle(chosen)
    return chosen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_csv")
    parser.add_argument("--size", type=int, default=200)
    parser.add_argument("--seed", type=int, default=20261101)
    args = parser.parse_args()
    try:
        with open(args.input_csv, encoding="utf-8-sig", newline="") as source:
            rows = csv.DictReader(source)
            if not rows.fieldnames or not {"id", "source", "title", "description"}.issubset(rows.fieldnames):
                raise ValueError("input requires id, source, title and description columns")
            chosen = sample(rows, args.size, args.seed)
        writer = csv.DictWriter(sys.stdout, fieldnames=["id", "source", "company", "title", "description", "reviewer_a",
                                "reviewer_b", "adjudicated_label", "evidence_quote", "hard_negative"], lineterminator="\n")
        writer.writeheader()
        writer.writerows(chosen)
    except (ValueError, UnicodeError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
