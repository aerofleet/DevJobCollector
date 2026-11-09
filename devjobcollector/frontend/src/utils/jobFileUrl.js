export function jobFileUrl(value) {
  try {
    const url = new URL(value);
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    if (url.protocol === 'https:' && url.hostname === 'opendata.alio.go.kr' && !url.port && !url.username && !url.password && !url.hash && url.pathname === '/recruit/downloadAtchFile') {
      const match = url.search.match(/^\?recrutAtchFileNo=(\d{1,20})$/);
      if (match) return `https://www.alio.go.kr/download/download.json?fileNo=${match[1]}`;
    }
    return url.href;
  } catch { return null; }
}

export function canPreviewJobFile(file) {
  if (!/\.(?:hwp|hwpx)$/i.test(file.fileName || '')) return false;
  try {
    const url = new URL(file.fileUrl);
    return url.protocol === 'https:' && ['alio.go.kr', 'www.alio.go.kr', 'opendata.alio.go.kr'].includes(url.hostname)
      && !url.port && !url.username && !url.password && !url.hash;
  } catch { return false; }
}
