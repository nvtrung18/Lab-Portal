import { normalizeWebsitePages } from '../../../../contracts/website-pages';
import seed from '../../../../contracts/website-demo-content.json';
import scholarSeed from '../../../../contracts/website-scholar-members.json';
import type { Member, WebsiteContent, WebsitePage } from '../../../../contracts/website-content';
import { LAB_BRAND } from '../../shared/constants';

export type { EditableWebsiteContent } from '../../../../contracts/website-pages';

export function withPages(content: WebsiteContent) {
  return normalizeWebsitePages(content, seed.content.pages as WebsitePage[]);
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
    return existing ? { ...existing, title: structuredClone(source.title), role: source.role,
      scholarProfile: existing.scholarProfile ?? structuredClone(source.scholarProfile) }
      : structuredClone(source);
  });
  return { ...content, data: { ...content.data,
    members: [...first, ...content.data.members.filter(member => !selected.has(member.id))] } };
}

const initialContent = structuredClone(seed.content) as WebsiteContent;
initialContent.profile.name = LAB_BRAND.name;
initialContent.profile.shortName = LAB_BRAND.shortName;
initialContent.profile.summary = { ...LAB_BRAND.summary };
initialContent.profile.email = '';
initialContent.profile.coordinates = '';
initialContent.profile.locationLabel = '';
initialContent.profile.showDemoNotice = false;
initialContent.data = { research: [], members: [], publications: [], projects: [], news: [] };
const peoplePage = initialContent.pages?.find(page => page.template === 'people');
if (peoplePage) peoplePage.description = {
  en: 'Meet ALELA\'s steering members, core members, doctoral researchers and research partners.',
  vi: 'Đội ngũ ALELA gồm thành viên chỉ đạo, thành viên cốt lõi, nghiên cứu sinh và các thành viên đối tác.',
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
