import { useMemo } from 'react';
import type { LocalizedText } from '../../../../../../contracts/website-content';
import { sanitizeWebsiteHtml } from '../../richText';
import { useLab } from './lab-provider';
import { prepareEditorialBody } from '../../editorial-search';
import type { WebsiteSection } from '../../../../../../contracts/website-content';

export function RichTextBody({ html, paragraphs, anchorSection }: { html?: LocalizedText | null; paragraphs: LocalizedText[]; anchorSection?: WebsiteSection }) {
  const { locale, text } = useLab();
  const value = html?.[locale];
  const clean = useMemo(() => anchorSection ? prepareEditorialBody(anchorSection, locale).html
    : value == null ? null : sanitizeWebsiteHtml(value, true), [anchorSection, locale, value]);
  return clean == null ? <>{paragraphs.map((paragraph, index) => <p key={index}>{text(paragraph)}</p>)}</>
    : <div className="website-rich-body" dangerouslySetInnerHTML={{ __html: clean }} />;
}
