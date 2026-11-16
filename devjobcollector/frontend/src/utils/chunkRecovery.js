const KEY = 'djc-chunk-reload-at';
const RETRY_INTERVAL_MS = 60_000;

// A tab left open across a deployment can request a chunk from the old build.
// Only reload once per minute, so a persistent network failure cannot create a reload loop.
export function recoverChunkFailure(event, storage, reload, now = Date.now()) {
  try {
    const previous = Number(storage.getItem(KEY));
    if (previous && now - previous < RETRY_INTERVAL_MS) return false;
    storage.setItem(KEY, String(now));
  } catch { return false; }
  event.preventDefault();
  reload();
  return true;
}
