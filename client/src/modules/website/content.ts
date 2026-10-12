import seed from '../../../../contracts/website-demo-content.json';
import scholarSeed from '../../../../contracts/website-scholar-members.json';
import type { Member, WebsiteContent, WebsitePage, WebsiteSection } from '../../../../contracts/website-content';

export type EditableWebsiteContent = WebsiteContent & { pages: WebsitePage[]; contentLibrary: WebsiteSection[] };

export function withPages(content: WebsiteContent): EditableWebsiteContent {
  const storedPages = content.pages ?? [];
  const used = new Set<WebsitePage>();
  const pages: WebsitePage[] = (structuredClone(seed.content.pages) as WebsitePage[]).map(template => {
    const stored = storedPages.find(page => !used.has(page) && page.id === template.id)
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
    } else if (content.pages != null) {
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

export const scholarMembers = scholarSeed as Member[];

export function withScholarMembers(content: WebsiteContent): WebsiteContent {
  const selected = new Set<string>();
  const first = scholarMembers.map(source => {
    const existing = content.data.members.find(member => !selected.has(member.id) && (
      member.id === source.id
      || (member.scholarProfile && source.scholarProfile
        && member.scholarProfile.url.match(/[?&]user=([^&#]+)/)?.[1]
          === source.scholarProfile.url.match(/[?&]user=([^&#]+)/)?.[1])
      || member.title.en.toLowerCase() === source.title.en.toLowerCase()
      || member.title.vi.toLowerCase() === source.title.vi.toLowerCase()
    ));
    if (existing) selected.add(existing.id);
    return existing ? { ...existing, scholarProfile: existing.scholarProfile ?? structuredClone(source.scholarProfile) }
      : structuredClone(source);
  });
  return { ...content, data: { ...content.data,
    members: [...first, ...content.data.members.filter(member => !selected.has(member.id))] } };
}

const initialContent = structuredClone(seed.content) as WebsiteContent;
const peoplePage = initialContent.pages?.find(page => page.template === 'people');
if (peoplePage) peoplePage.description = {
  en: 'Meet our faculty and researchers, explore their research interests and academic profiles.',
  vi: 'Gặp gỡ giảng viên và các nhà nghiên cứu, tìm hiểu lĩnh vực nghiên cứu và hồ sơ học thuật.',
};
export const demoContent = withPages(withScholarMembers(initialContent));

export function createPage(): WebsitePage {
  return { id: crypto.randomUUID(), slug: '', title: { vi: '', en: '' },
    description: { vi: '', en: '' }, navigationTitle: { vi: '', en: '' },
    template: 'custom', visible: true, showInNavigation: true, sections: [] };
}

export function createSection(): WebsitePage['sections'][number] {
  return { id: crypto.randomUUID(), title: { vi: '', en: '' }, description: { vi: '', en: '' },
    eyebrow: { vi: '', en: '' }, body: [], image: '', presentation: 'standard',
    linkLabel: { vi: '', en: '' }, linkUrl: '', visible: true };
}
