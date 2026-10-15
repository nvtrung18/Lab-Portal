import type { WebsiteContent, WebsiteSection } from './website-content';

const productTitles = new Set([
  'hệ thống iot quan trắc khí gây mùi trong trang trại chăn nuôi',
  'hệ thống mô phỏng trang trại chăn nuôi lợn digital twin',
]);

/** Input must have passed the normalizer, including migration of legacy inline sections. */
export function productTransferCandidates(content: WebsiteContent): WebsiteSection[] {
  const selected = new Set(content.pages?.filter(p => p.template === 'news').flatMap(p => p.sectionIds ?? []));
  return (content.contentLibrary ?? []).filter(section => selected.has(section.id)
    && Object.values(section.title).some(title => productTitles.has(title.normalize('NFC').trim().toLowerCase())));
}

export function transferProductSections(content: WebsiteContent, sectionIds: string[]): WebsiteContent {
  if (!content.pages?.some(page => page.template === 'products')) throw new Error('Products page is missing.');
  const allowed = new Set(productTransferCandidates(content).map(section => section.id));
  const selected = new Set(sectionIds);
  if ([...selected].some(id => !allowed.has(id))) throw new Error('The selected News sections have changed.');
  return { ...content, pages: content.pages.map(page => {
    if (page.template !== 'news' && page.template !== 'products') return page;
    const ids = page.template === 'news' ? (page.sectionIds ?? []).filter(id => !selected.has(id))
      : [...new Set([...(page.sectionIds ?? []), ...selected])];
    return { ...page, sectionIds: ids, sections: ids.flatMap(id => {
      const section = content.contentLibrary?.find(item => item.id === id);
      return section ? [section] : [];
    }) };
  }) };
}
