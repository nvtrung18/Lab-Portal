import type { WebsiteContent, WebsitePage } from './website-content';

export type EditableWebsiteContent = WebsiteContent & { pages: WebsitePage[]; contentLibrary: NonNullable<WebsiteContent['contentLibrary']> };

/** Add the new fixed tab without changing any stored section or publishing data. */
export function websitePageTemplates(templates: WebsitePage[]): WebsitePage[] {
  const pages = structuredClone(templates).filter(page => page.template !== 'products');
  const beforeNews = pages.findIndex(page => page.template === 'news');
  pages.splice(beforeNews < 0 ? pages.length : beforeNews, 0, {
    id: 'products', slug: 'products', template: 'products', visible: true, showInNavigation: true,
    navigationTitle: { vi: 'Sản phẩm', en: 'Products' },
    title: { vi: 'Sản phẩm nghiên cứu', en: 'Research products' },
    description: { vi: '', en: '' }, sections: [],
  });
  return pages;
}

export function normalizeWebsitePages(content: WebsiteContent, templates: WebsitePage[]): EditableWebsiteContent {
  const storedPages = content.pages ?? [];
  const used = new Set<WebsitePage>();
  const pages: WebsitePage[] = websitePageTemplates(templates).map(template => {
    const stored = storedPages.find(page => !used.has(page) && page.id === template.id && (template.template !== 'products' || page.template === 'products'))
      ?? storedPages.find(page => !used.has(page) && page.template === template.template)
      ?? storedPages.find(page => !used.has(page) && page.slug === template.slug);
    const page = stored ? { ...stored } : template;
    if (!stored) {
      // Existing pages may already use a restored page's identifier or URL.
      for (const field of ['id', 'slug'] as const) {
        const value = page[field];
        const occupied = new Set(storedPages.map(item => item[field]));
        let suffix = 2;
        while (occupied.has(page[field])) page[field] = `${value || 'home'}-${suffix++}`;
      }
    }
    if (stored) {
      used.add(stored);
    } else if (content.pages != null && template.template !== 'products') {
      // Restore the fixed page structure without repopulating removed content.
      page.title = { en: '', vi: '' };
      page.description = { en: '', vi: '' };
    } else {
      // Compatibility with content saved before separate page headings existed.
      page.title = content.copy[page.title.en] ?? page.title;
      page.description = content.copy[page.description.en] ?? page.description;
      if (page.template === 'home') {
        const first = content.copy['Intelligent systems.'];
        const second = content.copy['Meaningful impact.'];
        if (first && second) page.title = { en: `${first.en}\n${second.en}`, vi: `${first.vi}\n${second.vi}` };
      }
    }
    const originalDescription = templates.find(item => item.template === template.template)?.description;
    if (originalDescription) {
      page.description = {
        en: page.description.en === originalDescription.en ? '' : page.description.en,
        vi: page.description.vi === originalDescription.vi ? '' : page.description.vi,
      };
    }
    return { ...page, template: template.template, navigationTitle: template.navigationTitle,
      showInNavigation: template.showInNavigation, visible: true };
  });
  // Keep previously saved additional pages accessible, outside the fixed menu.
  for (const page of storedPages) {
    if (!used.has(page)) pages.push({ ...page, showInNavigation: false });
  }
  const contentLibrary = [...(content.contentLibrary ?? [])];
  const libraryById = new Map(contentLibrary.map(item => [item.id, item]));
  const migratedPages = pages.map(page => {
    const legacyIds: string[] = [];
    if (content.contentLibrary == null) {
      for (const section of page.sections) {
        let id = section.id;
        let suffix = 2;
        const existing = libraryById.get(id);
        // Published responses may repeat one shared section across pages.
        if (!existing || JSON.stringify(existing) !== JSON.stringify(section)) {
          while (libraryById.has(id)) id = `${section.id.slice(0, 70)}-${suffix++}`;
          const entry = { ...section, id };
          libraryById.set(id, entry);
          contentLibrary.push(entry);
        }
        if (section.visible !== false) legacyIds.push(id);
      }
    } else {
      legacyIds.push(...page.sections.filter(item => item.visible !== false).map(item => item.id));
    }
    return { ...page, sectionIds: page.sectionIds ?? legacyIds };
  });
  return { ...content, contentLibrary, pages: migratedPages.map(page => ({
    ...page, sections: page.sectionIds.flatMap(id => {
      const item = libraryById.get(id);
      return item ? [item] : [];
    }),
  })) };
}
