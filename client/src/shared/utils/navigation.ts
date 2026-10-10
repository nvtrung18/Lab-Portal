export function getSafeReturnPath(returnUrl: string | null, fallback: string) {
  if (
    !returnUrl
    || !returnUrl.startsWith('/')
    || returnUrl.startsWith('//')
    || returnUrl.includes('\\')
  ) {
    return fallback;
  }

  try {
    const parsed = new URL(returnUrl, 'https://lab-portal.invalid');
    return parsed.origin === 'https://lab-portal.invalid'
      ? `${parsed.pathname}${parsed.search}${parsed.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}
