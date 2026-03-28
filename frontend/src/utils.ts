/** Truncate a hex ID to first 12 chars + ellipsis */
export function short(id: string | null | undefined): string {
  if (!id) return '\u2014';
  return id.length > 14 ? id.substring(0, 12) + '...' : id;
}

/** Human-friendly relative time */
export function ago(t: string | null | undefined): string {
  if (!t) return 'never';
  const s = Math.floor((Date.now() - new Date(t).getTime()) / 1000);
  if (s < 0) return 'just now';
  if (s < 5) return 'just now';
  if (s < 60) return s + 's ago';
  if (s < 3600) return Math.floor(s / 60) + 'm ago';
  if (s < 86400) return Math.floor(s / 3600) + 'h ago';
  return Math.floor(s / 86400) + 'd ago';
}
