"""Read-only load check against an isolated local preview endpoint."""
import concurrent.futures
import json
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18081"
MAGIC = bytes.fromhex("d0cf11e0a1b11ae1")

def get(path):
    started = time.monotonic()
    try:
        with urllib.request.urlopen(BASE + path, timeout=40) as response:
            body = response.read()
            return {"status": response.status, "bytes": len(body), "valid": body.startswith(MAGIC), "ms": round((time.monotonic() - started) * 1000)}
    except urllib.error.HTTPError as error:
        return {"status": error.code, "bytes": 0, "valid": False, "ms": round((time.monotonic() - started) * 1000)}
    except Exception as error:
        return {"status": str(type(error).__name__), "bytes": 0, "valid": False, "ms": round((time.monotonic() - started) * 1000)}

baseline = [get('/actuator/health') for _ in range(5)]
with concurrent.futures.ThreadPoolExecutor(max_workers=11) as pool:
    futures = [pool.submit(get, '/api/v1/job-files/5/preview') for _ in range(10)]
    health = [get('/actuator/health') for _ in range(5)]
    previews = [future.result() for future in futures]
recovery = get('/api/v1/job-files/5/preview')
result = {"baselineHealth": baseline, "concurrentHealth": health, "preview": previews, "recovery": recovery}
print(json.dumps(result, ensure_ascii=False))
assert all(item['status'] == 200 for item in baseline + health)
assert all(item['valid'] for item in previews if item['status'] == 200)
assert all(item['status'] in (200, 429) for item in previews)
assert sum(item['status'] == 200 for item in previews) >= 1
assert recovery['status'] == 200 and recovery['valid']
