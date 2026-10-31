let savedPosition = null;

export function saveJobListPosition(position) {
  savedPosition = { ...position, scrollRestoration: window.history.scrollRestoration };
  window.history.scrollRestoration = 'manual';
}

export function getJobListPosition(key) {
  return savedPosition?.key === key ? savedPosition : null;
}

export function clearJobListPosition() {
  if (savedPosition) window.history.scrollRestoration = savedPosition.scrollRestoration;
  savedPosition = null;
}
