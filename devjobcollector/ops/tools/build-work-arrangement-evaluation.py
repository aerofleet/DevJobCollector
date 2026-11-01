#!/usr/bin/env python3
"""Join a blinded annotation sheet with predictions after adjudication."""

import argparse
import csv
import importlib.util
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("work_arrangement_predictor", HERE / "predict-work-arrangement.py")
PREDICTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREDICTOR)
LABELS = {"IN_HOUSE", "SI_OUTSOURCING", "DISPATCH", "UNKNOWN"}


def build(snapshot_rows, annotation_rows):
    snapshots = {}
    for row in snapshot_rows:
        if row.get("status") != "OK" or not (row.get("description") or "").strip():
            continue
        identifier = (row.get("id") or "").strip()
        if not identifier or identifier in snapshots:
            raise ValueError("snapshot ids must be present and unique")
        snapshots[identifier] = row
    annotations = {}
    for row in annotation_rows:
        identifier = (row.get("id") or "").strip()
        if not identifier or identifier in annotations:
            raise ValueError("annotation ids must be present and unique")
        if row.get("reviewer_a") not in LABELS or row.get("reviewer_b") not in LABELS:
            raise ValueError(f"both independent reviews are required for id {identifier}")
        if row.get("adjudicated_label") not in LABELS:
            raise ValueError(f"adjudicated_label is required for id {identifier}")
        if row.get("hard_negative", "false").lower() not in {"true", "false", "1", "0", "yes", "no"}:
            raise ValueError(f"invalid hard_negative for id {identifier}")
        annotations[identifier] = row
    if snapshots.keys() != annotations.keys():
        raise ValueError("snapshot and annotation id sets differ")
    output = []
    for identifier, source in snapshots.items():
        annotation = annotations[identifier]
        predicted, _ = PREDICTOR.predict(source.get("title"), source.get("description"))
        output.append({"id": identifier, "cluster_id": source.get("company") or identifier,
                       "expected": annotation["adjudicated_label"],
                       "predicted": predicted, "reviewer_a": annotation["reviewer_a"],
                       "reviewer_b": annotation["reviewer_b"],
                       "hard_negative": annotation.get("hard_negative", "false")})
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot_csv")
    parser.add_argument("annotation_csv")
    parser.add_argument("output_csv")
    args = parser.parse_args()
    output_path = pathlib.Path(args.output_csv)
    if output_path.exists():
        parser.error("output already exists")
    try:
        with open(args.snapshot_csv, encoding="utf-8-sig", newline="") as source_file, \
                open(args.annotation_csv, encoding="utf-8-sig", newline="") as annotation_file:
            source_reader = csv.DictReader(source_file)
            annotation_reader = csv.DictReader(annotation_file)
            required = {"id", "reviewer_a", "reviewer_b", "adjudicated_label", "hard_negative"}
            if not source_reader.fieldnames or not {"id", "title", "description"}.issubset(source_reader.fieldnames):
                raise ValueError("snapshot requires id, title and description")
            if not annotation_reader.fieldnames or not required.issubset(annotation_reader.fieldnames):
                raise ValueError("annotation sheet is missing review columns")
            rows = build(source_reader, annotation_reader)
        with output_path.open("x", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=["id", "cluster_id", "expected", "predicted", "reviewer_a",
                                    "reviewer_b", "hard_negative"], lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
    except (ValueError, UnicodeError) as error:
        parser.error(str(error))
    print(f"Prepared {len(rows)} adjudicated rows: {output_path}")


if __name__ == "__main__":
    main()
