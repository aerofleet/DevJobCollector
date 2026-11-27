"""Read-only Cloudflare audit. Never prints tokens, zone identifiers, or rule expressions."""
import json
import os
import sys
import urllib.error
import urllib.request

token = os.environ.get("CLOUDFLARE_API_TOKEN", "")
if not token:
    print("API token unavailable")
    sys.exit(1)

def get(path):
    request = urllib.request.Request("https://api.cloudflare.com/client/v4" + path,
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, {}
    except (urllib.error.URLError, TimeoutError, ValueError):
        print("Cloudflare API connection failed")
        sys.exit(1)

status, payload = get("/zones?name=itsdev.kr&per_page=20")
print(f"Zone lookup HTTP {status}")
zones = payload.get("result") or []
if status != 200 or len(zones) != 1:
    print("Cannot resolve a unique zone with the existing token")
    sys.exit(1)

status, payload = get("/zones/" + zones[0]["id"] + "/rulesets/phases/http_ratelimit/entrypoint")
print(f"Rate-limit entrypoint HTTP {status}")
if status == 404:
    print("No rate-limit entrypoint exists")
    sys.exit(0)
if status != 200:
    print("Existing token cannot inspect rate-limit rules")
    sys.exit(1)
rules = (payload.get("result") or {}).get("rules") or []
print("Rules total:", len(rules))
print("Enabled rules:", sum(1 for rule in rules if rule.get("enabled", True)))
print("Enabled expressions explicitly mentioning the login path:", sum(
    1 for rule in rules if rule.get("enabled", True)
    and "/api/v1/auth/login" in rule.get("expression", "")))
print("Other matching expressions may also protect login; inspect policies before changing them")
