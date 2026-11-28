"""Send malformed login requests without account identifiers to test edge throttling."""
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.request

url = "https://djc-api.itsdev.kr/api/v1/auth/login"

def probe(_):
    request = urllib.request.Request(url, data=b'{}', headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return (response.status, response.headers.get("Server", ""), bool(response.headers.get("CF-Ray")))
    except urllib.error.HTTPError as error:
        return (error.code, error.headers.get("Server", ""), bool(error.headers.get("CF-Ray")))
    except (urllib.error.URLError, TimeoutError):
        return ("network_error", "", False)

with ThreadPoolExecutor(max_workers=5) as pool:
    print("(HTTP status, server, CF-Ray present) counts:", dict(Counter(pool.map(probe, range(40)))))
