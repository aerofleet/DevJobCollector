"""Create a single login rate limit only if no rate-limit entrypoint exists."""
import json
import os
import sys
import urllib.error
import urllib.request

token = os.environ["CLOUDFLARE_API_TOKEN"]
base = "https://api.cloudflare.com/client/v4"

def request(path, data=None):
    req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(),
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            body = json.load(error)
            print("Cloudflare error codes:", [item.get("code") for item in body.get("errors", [])])
        except ValueError:
            pass
        return error.code, {}

status, payload = request("/zones?name=itsdev.kr&per_page=20")
zones = payload.get("result") or []
if status != 200 or len(zones) != 1 or (zones[0].get("plan") or {}).get("name") != "Free Website":
    sys.exit("Zone or plan precondition failed")
zone = zones[0]["id"]
endpoint = "/zones/" + zone + "/rulesets/phases/http_ratelimit/entrypoint"
status, _ = request(endpoint)
if status != 404:
    sys.exit("Rate limit entrypoint already exists or cannot be inspected; no changes made")
rule = {"description": "DJC login IP burst protection", "expression": '(http.request.uri.path eq "/api/v1/auth/login")',
        "action": "block", "enabled": True,
        "ratelimit": {"characteristics": ["cf.colo.id", "ip.src"], "period": 10,
                      "requests_per_period": 20, "mitigation_timeout": 10}}
status, result = request("/zones/" + zone + "/rulesets", {"name": "DJC login rate limit", "kind": "zone", "phase": "http_ratelimit", "rules": [rule]})
print("Create ruleset HTTP", status, "success", result.get("success", False))
if status not in (200, 201) or not result.get("success"):
    sys.exit(1)
status, result = request(endpoint)
rules = (result.get("result") or {}).get("rules") or []
if status != 200 or len(rules) != 1:
    sys.exit("Post-create verification failed")
actual = rules[0]
rate = actual.get("ratelimit") or {}
if not (actual.get("enabled") and actual.get("expression") == rule["expression"] and actual.get("action") == "block"
        and all(rate.get(key) == value for key, value in rule["ratelimit"].items())):
    sys.exit("Rule differs from intended configuration")
print("Verified enabled login rule: 20 requests / 10 seconds per IP and colo, block 10 seconds")
