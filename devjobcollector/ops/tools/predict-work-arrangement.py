#!/usr/bin/env python3
"""Generate experimental work-arrangement predictions for offline evaluation.

Input CSV columns: id, title, description. An optional expected column is
copied to the output for the separate evaluator. Predictions are not approved
for user-facing display or filtering.
"""

import argparse
import csv
import re
import sys

# Different evidence groups are required; repetitions within one group do not
# increase the score. Broad tokens such as SI, 상주 and 파견 are never enough.
RULES = {
    "DISPATCH": {
        "dispatch": r"(?:근로자\s*파견|파견\s*(?:근무|근로|계약|직))",
        "client": r"(?:고객사|클라이언트|협력사|파견처|타사\s*사업장)",
    },
    "SI_OUTSOURCING": {
        "outsourcing": r"(?:시스템\s*통합|시스템\s*구축|외주\s*(?:개발|프로젝트)|SI\s*(?:사업|프로젝트|개발))",
        # A qualification like "구축 프로젝트 경험" does not prove SI delivery.
        "client_project": r"(?:고객사|클라이언트|수주|프로젝트\s*투입)",
    },
    "IN_HOUSE": {
        "own_product": r"(?:자사\s*(?:서비스|제품|프로덕트|플랫폼)|인하우스\s*(?:서비스|제품|개발))",
        "development": r"(?:개발|운영|유지보수|고도화)",
    },
}
NEGATION = re.compile(r"(?:비파견|파견\s*(?:근무\s*)?(?:없음|아님|제외|불가|하지\s*않)|외주\s*(?:없음|아님|제외)|SI\s*(?:없음|아님|제외))", re.I)
COMPILED = {label: {name: re.compile(pattern, re.I) for name, pattern in groups.items()}
            for label, groups in RULES.items()}


def predict(title, description):
    text = f"{title or ''}\n{description or ''}"
    # Mixed or negated text requires a human review until context is evaluated.
    if NEGATION.search(text):
        return "UNKNOWN", "negation"
    candidates = []
    for label, groups in COMPILED.items():
        matched = [name for name, pattern in groups.items() if pattern.search(text)]
        if len(matched) == len(groups):
            candidates.append((label, matched))
    if len(candidates) != 1:
        return "UNKNOWN", "ambiguous" if candidates else "insufficient_evidence"
    label, matched = candidates[0]
    return label, ",".join(matched)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", help="UTF-8 CSV with id, title, description")
    args = parser.parse_args()
    with open(args.csv_path, encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if not reader.fieldnames or not {"id", "title", "description"}.issubset(reader.fieldnames):
            parser.error("CSV must contain id, title and description")
        writer = csv.DictWriter(sys.stdout, fieldnames=["id", "expected", "predicted", "evidence_groups"], lineterminator="\n")
        writer.writeheader()
        seen = set()
        for line, row in enumerate(reader, start=2):
            identifier = (row.get("id") or "").strip()
            if not identifier or identifier in seen:
                parser.error(f"line {line}: missing or duplicate id")
            seen.add(identifier)
            predicted, evidence = predict(row.get("title"), row.get("description"))
            writer.writerow({"id": identifier, "expected": (row.get("expected") or "").strip(),
                             "predicted": predicted, "evidence_groups": evidence})


if __name__ == "__main__":
    main()
