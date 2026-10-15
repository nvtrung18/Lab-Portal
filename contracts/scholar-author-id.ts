/** Parse an identifier only. Neither browser nor backend fetches a CMS-supplied URL. */
export function scholarAuthorId(url: string): string | null {
  try {
    const parsed = new URL(url);
    const ids = parsed.searchParams.getAll('user');
    return ['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password
      && parsed.hostname.toLowerCase() === 'scholar.google.com' && parsed.pathname === '/citations'
      && ids.length === 1 && /^[A-Za-z0-9_-]{1,64}$/.test(ids[0]) ? ids[0] : null;
  } catch { return null; }
}
