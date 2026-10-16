import type { LabData, Locale, LocalizedText, NewsItem, Project, Publication, ResearchArea, WebsiteSection } from '../../../../contracts/website-content';
import type { SearchDocument, SearchMetadata } from '../../../../contracts/website-search';
import { paragraphsToHtml, sanitizeWebsiteHtml } from './richText';

export type SearchCollection = 'publications' | 'projects' | 'research' | 'news';
export type SearchRecord = Publication | Project | ResearchArea | NewsItem;
export interface EditorialEntry {
  sectionId: string; sectionTitle: string; anchor: string; text: string;
  kind: 'list-item' | 'passage'; number?: number;
  urls?: string[];
}

/** Text at block boundaries stays separated; formatting and links remain in the original DOM. */
function nodeText(node: Element) {
  const clone = node.cloneNode(true) as Element;
  clone.querySelectorAll('br, p, li, h2, h3, blockquote').forEach(element => element.appendChild(document.createTextNode(' ')));
  return (clone.textContent ?? '').replace(/\s+/g, ' ').trim();
}
export function plainHtmlText(html: string) {
  const root = document.createElement('div'); root.innerHTML = sanitizeWebsiteHtml(html);
  return nodeText(root);
}

/** Explicit HTML list items and prose blocks, not guessed bibliographic records. No CMS mutation. */
export function prepareEditorialBody(section: Pick<WebsiteSection, 'id' | 'title' | 'body' | 'bodyHtml'>, locale: Locale) {
  const root = document.createElement('div');
  root.innerHTML = sanitizeWebsiteHtml(section.bodyHtml?.[locale] ?? paragraphsToHtml(section.body)[locale], true);
  const entries: EditorialEntry[] = [];
  const blocks = [...root.querySelectorAll('li, p, h2, h3, blockquote')].filter(element => {
    if (element.parentElement?.closest('li, blockquote')) return false;
    return true;
  });
  blocks.forEach((element, index) => {
    const anchor = `section-${section.id}-content-${locale}-${index}`;
    element.id = anchor;
    const text = nodeText(element);
    if (!text) return;
    const list = element.parentElement;
    const number = element.tagName === 'LI' && list?.tagName === 'OL'
      ? (list as HTMLOListElement).start + [...list.children].filter(child => child.tagName === 'LI').indexOf(element) : undefined;
    entries.push({ sectionId: section.id, sectionTitle: section.title[locale], anchor, text,
      kind: element.tagName === 'LI' ? 'list-item' : 'passage', number,
      urls: [...element.querySelectorAll('a[href]')].map(link => link.getAttribute('href')!) });
  });
  return { html: root.innerHTML, entries };
}
export function editorialSearchDocuments(sections: WebsiteSection[], locale: Locale): SearchDocument<EditorialEntry>[] {
  return sections.filter(section => section.visible !== false).flatMap(section => {
    const { entries } = prepareEditorialBody(section, locale);
    // A section description can be searched separately; an empty heading alone is not academic content.
    if (section.description[locale].trim()) entries.unshift({ sectionId: section.id, sectionTitle: section.title[locale],
      anchor: `section-${section.id}-title`, text: section.description[locale], kind: 'passage' });
    return entries.map(entry => ({ id: entry.anchor, text: `${section.title[locale]} ${section.eyebrow[locale]} ${entry.text} ${(entry.urls ?? []).join(' ')}`,
      metadata: {}, value: entry }));
  });
}
export function recordSearchDocument<T extends SearchRecord>(item: T, data: LabData, locale: Locale): SearchDocument<T> {
  const text = (value?: LocalizedText | null) => value?.[locale] ?? '';
  const fields = [item.id, text(item.title), text(item.description)];
  const metadata: SearchMetadata = {};
  if ('bodyHtml' in item) fields.push(plainHtmlText(item.bodyHtml?.[locale] ?? ''));
  if ('body' in item && item.bodyHtml?.[locale] == null) fields.push(...item.body.map(text));
  if ('area' in item && item.area) { metadata.area = item.area; fields.push(text(data.research.find(area => area.id === item.area)?.title)); }
  if ('authors' in item) {
    metadata.type = item.type; metadata.author = item.authors;
    if (item.year != null && Number.isInteger(item.year) && item.year > 0) metadata.year = String(item.year);
    fields.push(text(item.venue), String(item.year ?? ''), item.type, ...item.authors.map(id => text(data.members.find(member => member.id === id)?.title) || id), ...Object.values(item.links ?? {}));
  }
  if ('status' in item) { metadata.status = item.status; fields.push(text(item.category), item.period, ...item.objectives.map(text)); }
  if ('date' in item) { metadata.category = item.category; fields.push(item.date, item.category, text(item.location)); }
  if ('topics' in item) fields.push(...item.topics.map(text));
  return { id: item.id, text: fields.join(' '), metadata, value: item };
}

export function scrollToEditorialTarget(element: HTMLElement) {
  const header = element.closest('.lab-public')?.querySelector('.site-header');
  const offset = (header?.getBoundingClientRect().height ?? 120) + 16;
  window.scrollTo({ top: Math.max(0, window.scrollY + element.getBoundingClientRect().top - offset), behavior: 'instant' });
  element.tabIndex = -1;
  element.focus({ preventScroll: true });
}
