export function readPaginationSession(key) {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || '{}');
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  } catch {
    return {};
  }
}

export function paginationPage(value) {
  return Number.isSafeInteger(value) && value >= 0 && value <= 1000000 ? value : 0;
}

export function paginationSize(value) {
  return [20, 50, 100].includes(value) ? value : 20;
}

export function savePaginationSession(key, value) {
  try { sessionStorage.setItem(key, JSON.stringify(value)); } catch { /* Storage may be unavailable. */ }
}
