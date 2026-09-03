#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
audit_script="$script_dir/audit-auth-career-logs.sh"

pass_output="$({
  echo 'AUTH_CAREER_OBSERVATION scope=AUTH method=GET path=/api/v1/members/me status=200'
  echo 'AUTH_CAREER_OBSERVATION scope=CAREER method=GET path=/api/v1/members/me/bookmarks status=200'
  echo 'AUTH_CAREER_OBSERVATION scope=CAREER method=GET path=/api/v1/members/me/recent-jobs status=401'
} | DJC_OBSERVATION_HOURS=24 bash "$audit_script")"

grep -q '^result=PASS$' <<< "$pass_output"
grep -q '^total_requests=3$' <<< "$pass_output"
grep -q '^total_5xx=0$' <<< "$pass_output"

set +e
fail_output="$({
  echo 'AUTH_CAREER_OBSERVATION scope=AUTH method=GET path=/api/v1/members/me status=500'
  echo 'OAuth2 success processing failed: provider=google, exception=RuntimeException'
} | DJC_OBSERVATION_HOURS=24 bash "$audit_script" 2>&1)"
fail_status=$?
set -e

if [[ "$fail_status" -ne 1 ]]; then
  echo "Expected failing audit exit code 1, got $fail_status" >&2
  exit 1
fi
grep -q '^result=FAIL$' <<< "$fail_output"
grep -q '^total_5xx=1$' <<< "$fail_output"
grep -q '^oauth_failures=1$' <<< "$fail_output"

set +e
empty_output="$(printf '%s\n' 'unrelated log line' | DJC_OBSERVATION_HOURS=24 bash "$audit_script" 2>&1)"
empty_status=$?
set -e

if [[ "$empty_status" -ne 3 ]]; then
  echo "Expected insufficient traffic exit code 3, got $empty_status" >&2
  exit 1
fi
grep -q '^result=INSUFFICIENT_TRAFFIC$' <<< "$empty_output"

echo 'PASS auth/career log audit fixtures: pass, fail, insufficient traffic'
