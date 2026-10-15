import type { LocalizedText, WebsiteContent, WebsiteHero, WebsitePage } from './website-content';

interface HeroBrand { name: string; slogan: string; introduction: LocalizedText }

/** Match the legacy hardcoded Hero, including its existing copy and route overrides. */
export function defaultHomeHero(brand: HeroBrand, copy: WebsiteContent['copy'] = {}, pages: WebsitePage[] = []): WebsiteHero {
  const text = (en: string, vi: string) => ({ ...(copy[en] ?? { en, vi }) });
  const path = (locale: 'vi' | 'en', template: 'research' | 'about') => {
    const slug = pages.find(page => page.template === template)?.slug ?? template;
    return `/${locale}${slug ? `/${slug}` : ''}`;
  };
  return {
    title: { vi: brand.slogan, en: brand.slogan },
    eyebrow: { vi: brand.name, en: brand.name },
    description: { ...brand.introduction },
    primaryCta: { label: text('Explore our research', 'Khám phá nghiên cứu'), url: { vi: path('vi', 'research'), en: path('en', 'research') } },
    secondaryCta: { label: text('Meet the laboratory', 'Tìm hiểu phòng lab'), url: { vi: path('vi', 'about'), en: path('en', 'about') } },
    backgroundImage: '/images/earth.jpg',
    backgroundAlt: text('Earth at night viewed from orbit', 'Trái Đất về đêm nhìn từ quỹ đạo'),
    footerText: text('CURIOSITY. COLLABORATION. DISCOVERY.', 'TÒ MÒ. HỢP TÁC. KHÁM PHÁ.'),
  };
}

export function resolveHomeHero(hero: WebsiteHero | null | undefined, brand: HeroBrand, copy: WebsiteContent['copy'] = {}, pages: WebsitePage[] = []): WebsiteHero {
  return hero == null ? defaultHomeHero(brand, copy, pages) : structuredClone(hero);
}

export function withHomeHero(content: WebsiteContent, pageId: string, hero: WebsiteHero): WebsiteContent {
  if (!content.pages?.some(page => page.id === pageId && page.template === 'home')) throw new Error('HOMEPAGE_REQUIRED');
  return { ...content, pages: content.pages.map(page => page.id === pageId ? { ...page, hero: structuredClone(hero) } : page) };
}

/** Same HTTP(S), internal-path and anchor policy as Java PAGE_LINK_URL. */
export function isHeroUrl(value: string) {
  return /^(?:$|\/(?!\/)[^\s]*|#[^\s]*|https?:\/\/[^\s]+)$/.test(value);
}
