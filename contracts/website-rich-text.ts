import type { LocalizedText } from './website-content';

/** Escape legacy text before it enters an HTML editor, keeping literal markup and line breaks. */
export function paragraphsToHtml(paragraphs: LocalizedText[]): LocalizedText {
  const escape = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/\r?\n/g, '<br>');
  const convert = (locale: keyof LocalizedText) => paragraphs.map(item => `<p>${escape(item[locale])}</p>`).join('');
  return { vi: convert('vi'), en: convert('en') };
}
