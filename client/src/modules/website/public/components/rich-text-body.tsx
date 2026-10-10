import { useMemo } from 'react';
import type { LocalizedText } from '../../../../../../contracts/website-content';
import { sanitizeWebsiteHtml } from '../../richText';
import { useLab } from './lab-provider';

export function RichTextBody({ html, paragraphs }: { html?: LocalizedText | null; paragraphs: LocalizedText[] }) {
  const { locale, text } = useLab();
  const value = html?.[locale];
  const clean = useMemo(() => value == null ? null : sanitizeWebsiteHtml(value, true), [value]);
  return clean == null ? <>{paragraphs.map((paragraph, index) => <p key={index}>{text(paragraph)}</p>)}</>
    : <div className="website-rich-body" dangerouslySetInnerHTML={{ __html: clean }} />;
}
