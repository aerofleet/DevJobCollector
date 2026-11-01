#!/usr/bin/env python3
"""Create a deterministic source- and company-balanced review snapshot."""

import argparse
import csv
import pathlib
import random
import sys
from collections import defaultdict


def balance(rows, size, seed=20261101):
    sources = defaultdict(lambda: defaultdict(list))
    seen = set()
    for row in rows:
        identifier = str(row.get("id", "")).strip()
        if not identifier or identifier in seen:
            continue
        seen.add(identifier)
        if row.get("status") != "OK" or not all((row.get(key) or "").strip()
                                                  for key in ("company", "title", "description")):
            continue
        sources[row["source"] or "UNKNOWN"][row["company"].strip()].append(row)
    if size < 1 or size > sum(len(jobs) for companies in sources.values() for jobs in companies.values()):
        raise ValueError("requested size exceeds eligible unique records")
    rng = random.Random(seed)
    company_queues = {}
    for source, companies in sources.items():
        queues = []
        for company, jobs in companies.items():
            jobs.sort(key=lambda row: int(row["id"]) if row["id"].isdigit() else row["id"])
            rng.shuffle(jobs)
            queues.append([company, jobs])
        rng.shuffle(queues)
        queue = []
        while any(jobs for _, jobs in queues):
            for item in queues:
                if item[1]:
                    queue.append(item[1].pop())
        company_queues[source] = queue
    result = []
    source_names = sorted(company_queues)
    while len(result) < size:
        for source in source_names:
            if company_queues[source] and len(result) < size:
                result.append(company_queues[source].pop())
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output")
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("--size", type=int, default=200)
    args = parser.parse_args()
    output = pathlib.Path(args.output)
    if output.exists():
        parser.error("output already exists")
    try:
        rows = []
        for filename in args.inputs:
            with open(filename, encoding="utf-8-sig", newline="") as source:
                reader = csv.DictReader(source)
                if not reader.fieldnames or not {"id", "source", "company", "title", "description", "status"}.issubset(reader.fieldnames):
                    raise ValueError(f"snapshot missing required columns: {filename}")
                rows.extend(reader)
        selected = balance(rows, args.size)
        with output.open("x", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=selected[0].keys(), lineterminator="\n")
            writer.writeheader()
            writer.writerows(selected)
    except (ValueError, UnicodeError) as error:
        parser.error(str(error))
    print(f"Saved {len(selected)} balanced records to {output}")


if __name__ == "__main__":
    main()
