import type { LocalizedText, WebsiteContent, WebsiteOurStory } from './website-content';

/** Match the existing About section and its copy overrides without populating saved JSON. */
export function defaultAboutStory(brand: { name: string; introduction: LocalizedText }, copy: WebsiteContent['copy'] = {}): WebsiteOurStory {
  const text = (en: string, vi: string) => ({ ...(copy[en] ?? { en, vi }) });
  return {
    eyebrow: text('OUR STORY', 'CÂU CHUYỆN'),
    title: { vi: brand.name, en: brand.name },
    description: { ...brand.introduction },
    ctaLabel: text('Meet the ALELA team', 'Gặp gỡ đội ngũ ALELA'),
  };
}

export function resolveAboutStory(story: WebsiteOurStory | null | undefined, brand: { name: string; introduction: LocalizedText }, copy: WebsiteContent['copy'] = {}): WebsiteOurStory {
  return story == null ? defaultAboutStory(brand, copy) : structuredClone(story);
}

export function withAboutStory(content: WebsiteContent, pageId: string, story: WebsiteOurStory): WebsiteContent {
  if (!content.pages?.some(page => page.id === pageId && page.template === 'about')) throw new Error('ABOUT_PAGE_REQUIRED');
  return { ...content, pages: content.pages.map(page => page.id === pageId ? { ...page, ourStory: structuredClone(story) } : page) };
}
