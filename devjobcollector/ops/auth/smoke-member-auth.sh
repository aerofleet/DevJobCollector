#!/usr/bin/env bash
set -euo pipefail

api_base_url="${DJC_API_BASE_URL:?Set DJC_API_BASE_URL, for example https://<API_DOMAIN>}"
api_base_url="${api_base_url%/}"
expected_frontend_origin="${DJC_EXPECTED_FRONTEND_ORIGIN:-}"
smoke_local_email="${DJC_SMOKE_LOCAL_EMAIL:-}"
smoke_local_password="${DJC_SMOKE_LOCAL_PASSWORD:-}"

work_dir="$(mktemp -d)"
case "$work_dir" in
  /tmp/*|/var/tmp/*) ;;
  *) echo "Refusing unsafe temporary directory: $work_dir" >&2; exit 1 ;;
esac
cleanup() {
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

pass_count=0

assert_status() {
  local name="$1"
  local expected="$2"
  shift 2
  local actual
  actual="$(curl --silent --show-error --output "$work_dir/body" \
    --dump-header "$work_dir/headers" --write-out '%{http_code}' "$@")"
  if [ "$actual" != "$expected" ]; then
    echo "FAIL $name: expected HTTP $expected, got $actual" >&2
    sed -n '1,20p' "$work_dir/body" >&2
    exit 1
  fi
  pass_count=$((pass_count + 1))
  echo "PASS $name: HTTP $actual"
}

assert_oauth_redirect() {
  local provider="$1"
  local location_pattern="$2"
  assert_status "${provider} OAuth entry" 302 \
    "$api_base_url/oauth2/authorization/$provider"
  local location
  location="$(sed -n 's/^[Ll]ocation:[[:space:]]*//p' "$work_dir/headers" | tr -d '\r' | head -n 1)"
  if ! printf '%s' "$location" | grep -Eq "$location_pattern"; then
    echo "FAIL ${provider} OAuth entry: unexpected redirect target" >&2
    exit 1
  fi
  echo "PASS ${provider} OAuth redirect target"
}

assert_status "actuator health" 200 "$api_base_url/actuator/health"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$work_dir/body" || {
  echo 'FAIL actuator health: status is not UP' >&2
  exit 1
}

assert_status "public job search" 200 \
  "$api_base_url/api/v1/jobs/search?page=0&size=1"

assert_status "former default LOCAL credential rejection" 401 \
  --request POST --header 'Content-Type: application/json' \
  --data '{"identifier":"admin","password":"admin1234"}' \
  "$api_base_url/api/v1/auth/login"

assert_oauth_redirect google 'https://accounts\.google\.com/'
assert_oauth_redirect github 'https://github\.com/login/oauth/'

if [ -n "$smoke_local_email" ] || [ -n "$smoke_local_password" ]; then
  if [ -z "$smoke_local_email" ] || [ -z "$smoke_local_password" ]; then
    echo 'FAIL active LOCAL login: set both DJC_SMOKE_LOCAL_EMAIL and DJC_SMOKE_LOCAL_PASSWORD' >&2
    exit 1
  fi
  command -v jq >/dev/null 2>&1 || {
    echo 'FAIL active LOCAL login: jq is required when smoke credentials are set' >&2
    exit 1
  }
  jq -n --arg identifier "$smoke_local_email" --arg password "$smoke_local_password" \
    '{identifier: $identifier, password: $password}' > "$work_dir/login.json"
  chmod 600 "$work_dir/login.json"
  assert_status "active LOCAL login" 200 \
    --request POST --header 'Content-Type: application/json' \
    --data-binary "@$work_dir/login.json" "$api_base_url/api/v1/auth/login"
  grep -Eq '"accessToken"[[:space:]]*:[[:space:]]*"[^"]+"' "$work_dir/body" || {
    echo 'FAIL active LOCAL login: accessToken is missing' >&2
    exit 1
  }
  grep -Eq '"tokenType"[[:space:]]*:[[:space:]]*"Bearer"' "$work_dir/body" || {
    echo 'FAIL active LOCAL login: tokenType is not Bearer' >&2
    exit 1
  }
else
  echo 'SKIP active LOCAL login: smoke credentials were not supplied'
fi

if [ -n "$expected_frontend_origin" ]; then
  echo "INFO interactive OAuth callback target must remain under: $expected_frontend_origin"
fi

echo "PASS member auth non-destructive smoke: $pass_count HTTP checks"
echo 'MANUAL REQUIRED: complete Google and GitHub browser login, verify callback success, then test an existing-email conflict for ACCOUNT_LINK_REQUIRED.'
