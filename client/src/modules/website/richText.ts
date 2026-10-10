import DOMPurify from 'dompurify';
import type { LocalizedText } from '../../../../contracts/website-content';
import { websiteAssetUrl } from './api';

const tags = ['p', 'br', 'h2', 'h3', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li', 'blockquote', 'a', 'img'];
const attributes = ['href', 'target', 'rel', 'title', 'src', 'alt', 'start'];
export const isWebsiteLink = (value: string) => /^(https?:\/\/[^\s]+|\/(?!\/)[^\s]*|#[^\s]*)$/.test(value);
export const isWebsiteImage = (value: string) => /^(https?:\/\/[^\s]+|\/(?!\/)[^\s]*)$/.test(value);

export function sanitizeWebsiteHtml(value: string, resolveImages = false): string {
  const cleaned = DOMPurify.sanitize(value, { ALLOWED_TAGS: tags, ALLOWED_ATTR: attributes,
    ALLOW_DATA_ATTR: false, ALLOW_ARIA_ATTR: false });
  const template = document.createElement('template');
  template.innerHTML = cleaned;
  template.content.querySelectorAll('a').forEach(link => {
    if (!isWebsiteLink(link.getAttribute('href') ?? '')) link.removeAttribute('href');
    if (link.getAttribute('target') === '_blank') link.setAttribute('rel', 'noopener noreferrer');
    else link.removeAttribute('target');
  });
  template.content.querySelectorAll('img').forEach(img => {
    const src = img.getAttribute('src') ?? '';
    if (!isWebsiteImage(src)) { img.remove(); return; }
    if (resolveImages) img.setAttribute('src', websiteAssetUrl(src));
  });
  return template.innerHTML;
}

export function paragraphsToHtml(paragraphs: LocalizedText[]): LocalizedText {
  const escape = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/\r?\n/g, '<br>');
  const convert = (locale: keyof LocalizedText) => paragraphs.map(item => `<p>${escape(item[locale])}</p>`).join('');
  return { vi: convert('vi'), en: convert('en') };
}
