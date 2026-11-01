#!/usr/bin/env python3
"""Collect a bounded public DJC job snapshot for offline manual evaluation.

Writes a UTF-8 CSV locally. It does not assign labels, run predictions, modify
production data, or make AI requests. Use a path outside the Git repository.
"""

import argparse
import csv
import json
import pathlib
import re
import urllib.parse
import time
import urllib.error
import urllib.request

BASE = "https://djc-api.itsdev.kr/api/v1/jobs"


def get_json(url):
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "DJC-offline-quality-evaluation/1.0"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def collect(limit, delay, get=get_json, sleep=time.sleep, pages=8, start_page=0,
            keywords=("개발자", "SI 개발자", "시스템 구축 개발자"), title_pattern=None, exclude_ids=()):
    if not 1 <= limit <= 500 or delay < 0.5:
        raise ValueError("limit must be 1..500 and delay must be at least 0.5 seconds")
    if pages < 1 or start_page < 0:
        raise ValueError("pages must be positive and start_page nonnegative")
    listings = []
    page_size = min(100, limit)
    for keyword in keywords:
        for number in range(start_page, start_page + pages):
            params = {"page": number, "size": page_size}
            if keyword:
                params["keyword"] = keyword
            query = urllib.parse.urlencode(params)
            page = get(f"{BASE}/search?{query}")
            content = page.get("content")
            if not isinstance(content, list):
                raise ValueError("unexpected search response")
            listings.extend(content)
            if len(content) < page_size:
                break
    # Round-robin across platforms to avoid a single source dominating page zero.
    deduplicated = {}
    excluded = {str(value) for value in exclude_ids}
    for job in listings:
        job_id = job.get("id")
        if isinstance(job_id, int) and str(job_id) not in excluded:
            deduplicated.setdefault(job_id, job)
    groups = {}
    for job in deduplicated.values():
        if title_pattern and not re.search(title_pattern, job.get("title") or "", re.I):
            continue
        company_key = re.sub(r"\s+", " ", (job.get("companyName") or "").strip().casefold())
        groups.setdefault(company_key or f"unknown:{job.get('id')}", []).append(job)
    selected = []
    platforms = sorted(groups)
    while len(selected) < limit and any(groups.values()):
        for platform in platforms:
            if groups[platform] and len(selected) < limit:
                selected.append(groups[platform].pop(0))
    seen = set()
    if len(selected) < limit:
        raise ValueError(f"only {len(selected)} listing records available across {pages} pages")
    for job in selected:
        job_id = job.get("id")
        if not isinstance(job_id, int) or isinstance(job_id, bool) or job_id <= 0 or job_id in seen:
            raise ValueError("search returned invalid or duplicate job id")
        seen.add(job_id)
        sleep(delay)
        try:
            detail = get(f"{BASE}/{job_id}")
            if detail.get("id") != job_id:
                raise ValueError("detail id does not match search result")
            text = detail.get("applyQual")
            description = text if isinstance(text, str) else ""
            yield {"id": job_id, "source": detail.get("sourcePlatform") or "",
                   "company": detail.get("companyName") or "", "title": detail.get("title") or "", "description": description,
                   "status": "OK" if description.strip() else "NO_DESCRIPTION"}
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            yield {"id": job_id, "source": job.get("sourcePlatform") or "", "company": job.get("companyName") or "",
                   "title": job.get("title") or "", "description": "", "status": "FETCH_FAILED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", help="new output CSV path; existing files are never overwritten")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--delay", type=float, default=1.0)
    parser.add_argument("--start-page", type=int, default=0)
    parser.add_argument("--pages", type=int, default=8)
    parser.add_argument("--keywords", nargs="+", default=["개발자", "SI 개발자", "시스템 구축 개발자"])
    parser.add_argument("--title-pattern", help="optional regular expression to retain matching titles")
    parser.add_argument("--exclude-csv", help="CSV with id column; matching jobs will be skipped")
    args = parser.parse_args()
    output = pathlib.Path(args.output)
    if output.exists():
        parser.error("output already exists")
    try:
        # Exclusive create prevents overwriting an existing evaluation snapshot.
        with output.open("x", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=["id", "source", "company", "title", "description", "status"])
            writer.writeheader()
            count = 0
            excluded = []
            if args.exclude_csv:
                with open(args.exclude_csv, encoding="utf-8-sig", newline="") as source:
                    reader = csv.DictReader(source)
                    if not reader.fieldnames or "id" not in reader.fieldnames:
                        raise ValueError("exclude CSV must contain id")
                    excluded = [row["id"] for row in reader]
            for row in collect(args.limit, args.delay, pages=args.pages, start_page=args.start_page,
                               keywords=tuple(args.keywords), title_pattern=args.title_pattern,
                               exclude_ids=excluded):
                writer.writerow(row)
                target.flush()
                count += 1
        print(f"Saved {count} records to {output}")
    except (ValueError, urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        parser.exit(1, f"Collection stopped; partial CSV may exist at {output}: {error}\n")


if __name__ == "__main__":
    main()
