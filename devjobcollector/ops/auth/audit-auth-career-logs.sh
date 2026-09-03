#!/usr/bin/env bash
set -euo pipefail

observation_hours="${DJC_OBSERVATION_HOURS:-24}"
max_5xx_basis_points="${DJC_MAX_5XX_BASIS_POINTS:-100}"

if ! [[ "$observation_hours" =~ ^[1-9][0-9]*$ ]]; then
  echo "Invalid DJC_OBSERVATION_HOURS: use a positive integer" >&2
  exit 64
fi
if ! [[ "$max_5xx_basis_points" =~ ^[1-9][0-9]*$ ]]; then
  echo "Invalid DJC_MAX_5XX_BASIS_POINTS: use a positive integer" >&2
  exit 64
fi

auth_requests=0
auth_5xx=0
career_requests=0
career_5xx=0
oauth_failures=0
observation_pattern='AUTH_CAREER_OBSERVATION scope=(AUTH|CAREER) method=[A-Z]+ path=[^[:space:]]+ status=([0-9]{3})'

while IFS= read -r line; do
  if [[ "$line" =~ $observation_pattern ]]; then
    scope="${BASH_REMATCH[1]}"
    status="${BASH_REMATCH[2]}"

    case "$scope" in
      CAREER)
        career_requests=$((career_requests + 1))
        if (( status >= 500 )); then
          career_5xx=$((career_5xx + 1))
        fi
        ;;
      AUTH)
        auth_requests=$((auth_requests + 1))
        if (( status >= 500 )); then
          auth_5xx=$((auth_5xx + 1))
        fi
        ;;
    esac
  fi

  case "$line" in
    *"OAuth2 user processing failed"*|*"OAuth2 success processing failed"*|*"Unhandled OAuth2 callback failure"*)
      oauth_failures=$((oauth_failures + 1))
      ;;
  esac
done

total_requests=$((auth_requests + career_requests))
total_5xx=$((auth_5xx + career_5xx))

if (( total_requests == 0 )); then
  echo "observation_hours=$observation_hours"
  echo "result=INSUFFICIENT_TRAFFIC"
  echo "auth_requests=0"
  echo "career_requests=0"
  echo "oauth_failures=$oauth_failures"
  exit 3
fi

error_rate_basis_points=$((total_5xx * 10000 / total_requests))
error_rate_whole=$((error_rate_basis_points / 100))
error_rate_fraction=$((error_rate_basis_points % 100))
printf -v error_rate_percent '%d.%02d' "$error_rate_whole" "$error_rate_fraction"

result="PASS"
exit_code=0
if (( error_rate_basis_points >= max_5xx_basis_points || oauth_failures > 0 )); then
  result="FAIL"
  exit_code=1
fi

echo "observation_hours=$observation_hours"
echo "result=$result"
echo "auth_requests=$auth_requests"
echo "auth_5xx=$auth_5xx"
echo "career_requests=$career_requests"
echo "career_5xx=$career_5xx"
echo "total_requests=$total_requests"
echo "total_5xx=$total_5xx"
echo "api_5xx_rate_percent=$error_rate_percent"
echo "oauth_failures=$oauth_failures"

exit "$exit_code"
