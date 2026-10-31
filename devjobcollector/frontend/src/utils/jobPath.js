export function jobPath(id, title) {
  const slug = String(title ?? '').replace(/<[^>]*>/g, '').normalize('NFC').toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
  const shortened = Array.from(slug).slice(0, 80).join('').replace(/-+$/g, '');
  return `/job/${id}${shortened ? `/${encodeURIComponent(shortened)}` : ''}`;
}
