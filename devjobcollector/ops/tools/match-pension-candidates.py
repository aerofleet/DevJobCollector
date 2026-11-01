#!/usr/bin/env python3
"""Produce review-only exact-name candidates from a 국민연금 workplace CSV.

This tool never confirms identity, combines branches, or publishes estimates.
The caller supplies the downloaded file and its encoding; no data is fetched.
"""

import argparse
import csv
import re
import sys
import unicodedata


def normalize_name(value):
    # Keep legal forms and location qualifiers: dropping them creates collisions.
    value = unicodedata.normalize("NFKC", value or "").casefold()
    return "".join(value.split())


def safe_cell(value):
    value = str(value or "")
    return "'" + value if value.lstrip().startswith(("=", "+", "-", "@")) else value


def estimated_size_band(value):
    """Subscriber-count band only; this is not a legal SME classification."""
    if not re.fullmatch(r"\d+", str(value or "")):
        return ""
    count = int(value)
    if count < 1:
        return ""
    if count <= 9:
        return "1-9"
    if count <= 49:
        return "10-49"
    if count <= 299:
        return "50-299"
    return "300+"


def column(fields, exact, prefix=None):
    matches = [field for field in fields if field in exact or (prefix and field.startswith(prefix))]
    if len(matches) != 1:
        raise ValueError("missing or ambiguous source column: " + "/".join(exact))
    return matches[0]


def candidate_rows(companies, workplaces, name_col, month_col, count_col, aliases=()):
    index = {}
    company_names = {}
    for company in companies:
        identifier = (company.get("id") or "").strip()
        name = (company.get("name") or "").strip()
        key = normalize_name(name)
        if not identifier or identifier in company_names or not key:
            raise ValueError("company id must be unique and company name must be nonempty")
        company_names[identifier] = name
        index.setdefault(key, {})[identifier] = "NAME"

    for alias in aliases:
        identifier = (alias.get("company_id") or "").strip()
        key = normalize_name(alias.get("alias"))
        if identifier not in company_names or not key:
            raise ValueError("alias must have a known company_id and nonempty alias")
        # A duplicate alias or an alias equal to the company name cannot duplicate a candidate.
        index.setdefault(key, {}).setdefault(identifier, "ALIAS")

    matches = {identifier: [] for identifier in company_names}
    for source_row, workplace in enumerate(workplaces, start=2):
        source_name = (workplace.get(name_col) or "").strip()
        candidates = index.get(normalize_name(source_name), {})
        if not candidates:
            continue
        month = (workplace.get(month_col) or "").strip()
        count = (workplace.get(count_col) or "").strip().replace(",", "")
        # Invalid or missing figures stay blank; never infer zero.
        month_match = re.fullmatch(r"(\d{4})-?(\d{2})", month)
        if month_match and int(month_match.group(1)) > 0 and 1 <= int(month_match.group(2)) <= 12:
            month = f"{month_match.group(1)}-{month_match.group(2)}"
        else:
            month = ""
        if not re.fullmatch(r"\d+", count):
            count = ""
        for identifier, match_type in candidates.items():
            matches[identifier].append((source_name, month, count, source_row, match_type, len(candidates)))

    for identifier, name in sorted(company_names.items()):
        candidates = matches[identifier]
        if not candidates:
            yield {"id": safe_cell(identifier), "company_name": safe_cell(name), "status": "NO_MATCH",
                   "candidate_count": 0, "workplace_name": "", "source_month": "", "subscribers": "", "estimated_size_band": "", "source_record": "", "match_type": "", "competing_companies": ""}
        else:
            for source_name, month, count, source_row, match_type, competing_companies in candidates:
                yield {"id": safe_cell(identifier), "company_name": safe_cell(name), "status": "REVIEW_REQUIRED",
                       "candidate_count": len(candidates), "workplace_name": safe_cell(source_name),
                       "source_month": month, "subscribers": count,
                       "estimated_size_band": estimated_size_band(count),
                       "source_record": source_row, "match_type": match_type,
                       "competing_companies": competing_companies}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("companies", help="UTF-8 CSV with id and name columns")
    parser.add_argument("workplaces", help="downloaded pension workplace CSV")
    parser.add_argument("--encoding", default="utf-8-sig", help="workplace file encoding (e.g. cp949)")
    parser.add_argument("--aliases", help="optional UTF-8 CSV with company_id and alias columns")
    args = parser.parse_args()
    try:
        with open(args.companies, encoding="utf-8-sig", newline="") as company_file, \
                open(args.workplaces, encoding=args.encoding, newline="") as workplace_file:
            companies = csv.DictReader(company_file)
            workplaces = csv.DictReader(workplace_file)
            if not companies.fieldnames or not {"id", "name"}.issubset(companies.fieldnames):
                raise ValueError("company CSV must contain id and name")
            if not workplaces.fieldnames:
                raise ValueError("workplace CSV requires a header")
            name = column(workplaces.fieldnames, ["사업장명", "WKPL_NM"])
            month = column(workplaces.fieldnames, ["자료생성년월", "DATA_CRT_YM"], "자료생성년월(")
            count = column(workplaces.fieldnames, ["가입자수", "JNNGP_CNT"], "가입자수(")
            aliases = []
            if args.aliases:
                with open(args.aliases, encoding="utf-8-sig", newline="") as alias_file:
                    alias_reader = csv.DictReader(alias_file)
                    if not alias_reader.fieldnames or not {"company_id", "alias"}.issubset(alias_reader.fieldnames):
                        raise ValueError("alias CSV must contain company_id and alias")
                    aliases = list(alias_reader)
            rows = candidate_rows(companies, workplaces, name, month, count, aliases)
            writer = csv.DictWriter(sys.stdout, fieldnames=["id", "company_name", "status", "candidate_count",
                                    "workplace_name", "source_month", "subscribers", "estimated_size_band", "source_record", "match_type",
                                    "competing_companies"], lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
    except (ValueError, UnicodeError, LookupError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
