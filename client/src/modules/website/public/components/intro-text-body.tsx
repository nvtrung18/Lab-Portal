import { useMemo } from 'react';
import type { LocalizedText } from '../../../../../../contracts/website-content';
import { sanitizeWebsiteHtml } from '../../richText';
import { useLab } from './lab-provider';

/** Prose only: keep each template's typography and safely render rich text without guessing whether legacy text is HTML. */
export function IntroTextBody({ html, plain }: { html?: LocalizedText | null; plain: LocalizedText }) {
  const { locale, text } = useLab();
  const value = html?.[locale];
  const clean = useMemo(() => value == null ? null : sanitizeWebsiteHtml(value, false, 'intro'), [value]);
  return <div className="website-intro-prose">{clean == null
    ? <p>{text(plain)}</p>
    : <div dangerouslySetInnerHTML={{ __html: clean }} />}</div>;
}
