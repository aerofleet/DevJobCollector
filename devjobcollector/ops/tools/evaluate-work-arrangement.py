#!/usr/bin/env python3
"""Evaluate independently labelled work-arrangement predictions from a CSV file."""

import argparse
import csv
import json
import random
from collections import Counter

LABELS = {"IN_HOUSE", "SI_OUTSOURCING", "DISPATCH", "UNKNOWN"}
PREDICTED_LABELS = LABELS - {"UNKNOWN"}


def evaluate(rows, seed=20261101, bootstrap_iterations=2000):
    counts = Counter()
    ids = set()
    normalized = []
    clusters = {}
    reviewer_agreement = []
    reviewer_pairs = []
    for line, row in enumerate(rows, start=2):
        identifier = (row.get("id") or "").strip()
        expected = (row.get("adjudicated_label") or row.get("expected") or "").strip()
        predicted = (row.get("predicted") or "").strip()
        if not identifier or identifier in ids:
            raise ValueError(f"line {line}: missing or duplicate id")
        if expected not in LABELS or predicted not in LABELS:
            raise ValueError(f"line {line}: invalid or missing label")
        reviewer_a = (row.get("reviewer_a") or "").strip()
        reviewer_b = (row.get("reviewer_b") or "").strip()
        if reviewer_a or reviewer_b:
            if reviewer_a not in LABELS or reviewer_b not in LABELS:
                raise ValueError(f"line {line}: both reviewer labels are required and must be valid")
            reviewer_agreement.append(reviewer_a == reviewer_b)
            reviewer_pairs.append((reviewer_a, reviewer_b))
        hard_negative = (row.get("hard_negative") or "false").strip().lower()
        if hard_negative not in {"true", "false", "1", "0", "yes", "no"}:
            raise ValueError(f"line {line}: hard_negative must be true/false")
        hard_negative = hard_negative in {"true", "1", "yes"}
        if hard_negative and expected not in {"IN_HOUSE", "UNKNOWN"}:
            raise ValueError(f"line {line}: hard negatives must be IN_HOUSE or UNKNOWN")
        ids.add(identifier)
        counts[(expected, predicted)] += 1
        cluster_id = (row.get("cluster_id") or identifier).strip()
        normalized.append((expected, predicted, hard_negative, cluster_id))
        clusters.setdefault(cluster_id, []).append((expected, predicted, hard_negative, cluster_id))
    per_label = {}
    for label in sorted(PREDICTED_LABELS):
        true_positive = counts[(label, label)]
        predicted_count = sum(count for (expected, predicted), count in counts.items() if predicted == label)
        actual_count = sum(count for (expected, _), count in counts.items() if expected == label)
        per_label[label] = {"correct": true_positive, "predicted": predicted_count, "actual": actual_count,
                            "precision": true_positive / predicted_count if predicted_count else None,
                            "recall": true_positive / actual_count if actual_count else None}
    total_predicted = sum(item["predicted"] for item in per_label.values())
    total_correct = sum(item["correct"] for item in per_label.values())
    precision = total_correct / total_predicted if total_predicted else None
    coverage = total_predicted / len(ids) if ids else None
    agreement_rate = sum(reviewer_agreement) / len(reviewer_agreement) if reviewer_agreement else None
    kappa = None
    if reviewer_agreement:
        labels_a = Counter(a for a, _ in reviewer_pairs)
        labels_b = Counter(b for _, b in reviewer_pairs)
        expected_agreement = sum(labels_a[label] * labels_b[label] for label in LABELS) / len(reviewer_pairs) ** 2
        kappa = ((agreement_rate - expected_agreement) / (1 - expected_agreement)
                 if expected_agreement < 1 else (1.0 if agreement_rate == 1 else 0.0))
    negatives = [row for row in normalized if row[2]]
    false_positive_negatives = sum(predicted in {"SI_OUTSOURCING", "DISPATCH"}
                                   for expected, predicted, _, _ in negatives)
    hard_negative_error = false_positive_negatives / len(negatives) if negatives else None
    rng = random.Random(seed)
    bootstrap = []
    if normalized:
        for _ in range(bootstrap_iterations):
            cluster_keys = list(clusters)
            selected_clusters = [cluster_keys[rng.randrange(len(cluster_keys))] for _ in cluster_keys]
            draw = [row for key in selected_clusters for row in clusters[key]]
            predicted_rows = [(expected, predicted) for expected, predicted, _, _ in draw if predicted != "UNKNOWN"]
            bootstrap.append(sum(expected == predicted for expected, predicted in predicted_rows) / len(predicted_rows)
                             if predicted_rows else 0.0)
    bootstrap.sort()
    lower_index = int(0.025 * (len(bootstrap) - 1)) if bootstrap else None
    ci_lower = bootstrap[lower_index] if bootstrap else None
    per_label_pass = all(per_label[label]["precision"] is not None and per_label[label]["precision"] >= .90
                         and per_label[label]["recall"] is not None and per_label[label]["recall"] >= .60
                         for label in ("SI_OUTSOURCING", "DISPATCH"))
    gate_passed = (len(ids) >= 200 and len(clusters) >= 30 and coverage is not None and coverage >= .30 and per_label_pass
                   and ci_lower is not None and ci_lower >= .80 and len(negatives) >= 30
                   and hard_negative_error is not None and hard_negative_error <= .05
                   and kappa is not None and kappa >= .80)
    return {"samples": len(ids), "company_clusters": len(clusters), "classified": total_predicted, "correct": total_correct,
            "precision": precision, "coverage": coverage, "precision_95ci_lower": ci_lower,
            "reviewed_by_two": len(reviewer_agreement), "reviewer_agreement": agreement_rate,
            "reviewer_cohen_kappa": kappa,
            "hard_negative_samples": len(negatives), "hard_negative_error_rate": hard_negative_error,
            "per_label": per_label, "gate_passed": gate_passed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", help="CSV with id, expected and predicted columns")
    args = parser.parse_args()
    with open(args.csv_path, encoding="utf-8-sig", newline="") as source:
        rows = csv.DictReader(source)
        if not rows.fieldnames or not {"id", "predicted"}.issubset(rows.fieldnames) or not ({"expected", "adjudicated_label"} & set(rows.fieldnames)):
            parser.error("CSV must contain id, predicted, and expected or adjudicated_label columns")
        try:
            result = evaluate(rows)
        except ValueError as error:
            parser.error(str(error))
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
