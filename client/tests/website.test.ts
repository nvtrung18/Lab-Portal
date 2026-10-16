import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { scholarAuthorId } from '../../contracts/scholar-author-id.ts';
import { defaultScholarSort, sortScholarPublications, toggleScholarSort } from '../../contracts/scholar-sort.ts';
import { normalizeWebsitePages } from '../../contracts/website-pages.ts';
import { productTransferCandidates, transferProductSections } from '../../contracts/website-product-transfer.ts';
import type { ScholarPublication, WebsiteContent, WebsitePage, WebsiteSection } from '../../contracts/website-content.ts';
import { memberRoleGroups, matchesMemberRole, partitionMemberDisplayGroups } from '../src/modules/website/member-roles.ts';
import { appendGalleryImages, articleImages, MAX_GALLERY_IMAGES, MAX_WEBSITE_IMAGE_BYTES, moveGalleryImage, promoteGalleryImage, validWebsiteImageFile } from '../../contracts/website-gallery.ts';
import type { GalleryImage } from '../../contracts/website-content.ts';
import { defaultHomeHero, isHeroUrl, resolveHomeHero, withHomeHero } from '../../contracts/website-hero.ts';
import { LAB_BRAND } from '../src/shared/constants/lab-brand.ts';
import { defaultAboutStory, resolveAboutStory, withAboutStory } from '../../contracts/website-story.ts';
import { paragraphsToHtml } from '../../contracts/website-rich-text.ts';

const seed = JSON.parse(readFileSync(new URL('../../contracts/website-demo-content.json', import.meta.url), 'utf8')).content as WebsiteContent;
const templates = seed.pages as WebsitePage[];
const section: WebsiteSection = {
  id: 'product-iot', title: { vi: 'Hệ thống IoT quan trắc khí gây mùi trong trang trại chăn nuôi', en: 'Odor monitoring' },
  description: { vi: 'Mô tả', en: 'Summary' }, eyebrow: { vi: '', en: '' }, body: [],
  bodyHtml: { vi: '<p><strong>Nội dung</strong><img src="/api/uploads/website/a.png" alt="ảnh"><a href="https://example.org">Link</a></p>', en: '<p>English text</p>' },
  image: '/api/uploads/website/hero.png', presentation: 'split', linkLabel: { vi: 'Xem', en: 'Read' }, linkUrl: 'https://example.org',
};
const paper = (title: string, year: number | null, citations: number | null): ScholarPublication => ({ title, year, citations, authors: '', venue: '', url: 'https://example.org/' + title });

const galleryImage = (id: string): GalleryImage => ({ id, src: `/api/uploads/website/${id}.png`, alt: { vi: `Ảnh ${id}`, en: `Image ${id}` }, caption: { vi: `Chú thích ${id}`, en: `Caption ${id}` } });
test('Missing/null Our Story exactly matches the previous independent About defaults and copy overrides', () => {
  const fallback = defaultAboutStory(LAB_BRAND);
  assert.deepEqual(resolveAboutStory(undefined, LAB_BRAND), fallback);
  assert.deepEqual(resolveAboutStory(null, LAB_BRAND), fallback);
  assert.deepEqual(fallback.description, LAB_BRAND.introduction);
  assert.deepEqual(fallback.title, { vi: LAB_BRAND.name, en: LAB_BRAND.name });
  const overridden = defaultAboutStory(LAB_BRAND, { 'OUR STORY': { vi: 'Chuyện đã biên tập', en: '' }, 'Meet the ALELA team': { vi: 'Đội ngũ', en: 'Team' } });
  assert.equal(overridden.eyebrow.en, ''); assert.equal(overridden.eyebrow.vi, 'Chuyện đã biên tập');
  assert.equal(overridden.ctaLabel.vi, 'Đội ngũ');
});
test('Plain descriptions enter the rich editor as escaped literal text with all VI/EN line breaks intact', () => {
  const plain = { vi: 'A & B\n<b>không phải HTML</b>\r\n"trích dẫn"', en: "<script>alert('text')</script>\nNext line" };
  assert.deepEqual(paragraphsToHtml([plain]), {
    vi: '<p>A &amp; B<br>&lt;b&gt;không phải HTML&lt;/b&gt;<br>&quot;trích dẫn&quot;</p>',
    en: '<p>&lt;script&gt;alert(&#39;text&#39;)&lt;/script&gt;<br>Next line</p>',
  });
  assert.deepEqual(paragraphsToHtml([{ vi: '', en: '' }]), { vi: '<p></p>', en: '<p></p>' });
});
test('Editing Our Story and Hero independently preserves each other, all sections and unrelated collections', () => {
  const input = normalizeWebsitePages(structuredClone(seed), templates);
  const before = structuredClone(input);
  const about = input.pages.find(page => page.template === 'about')!;
  const home = input.pages.find(page => page.template === 'home')!;
  const story = defaultAboutStory(LAB_BRAND);
  story.descriptionHtml = { vi: '<p><strong>Câu chuyện</strong></p><p><em>Nghiên cứu</em><br>Tiếp tục <a href="/vi/people">đội ngũ</a></p>', en: '<p><strong>Our story</strong></p><p><em>Research</em><br>Next <a href="https://example.org">Team</a></p>' };
  const withStory = withAboutStory(input, about.id, story);
  const hero = defaultHomeHero(LAB_BRAND);
  hero.descriptionHtml = { vi: '<p>Hero <strong>VI</strong></p>', en: '<p>Hero <em>EN</em></p>' };
  const result = withHomeHero(withStory, home.id, hero);
  assert.deepEqual(result.pages!.find(page => page.id === about.id), { ...about, ourStory: story });
  assert.deepEqual(result.pages!.find(page => page.id === home.id), { ...home, hero });
  const updatedStory = { ...story, title: { vi: 'Mới', en: 'New' } };
  const changed = withAboutStory(result, about.id, updatedStory);
  assert.deepEqual(changed.pages!.find(page => page.id === home.id)!.hero, hero);
  assert.deepEqual(changed.data, input.data); assert.deepEqual(changed.copy, input.copy);
  assert.deepEqual(changed.contentLibrary, input.contentLibrary);
  assert.deepEqual(input, before);
  story.title.vi = 'Unsaved mutation'; assert.notEqual(result.pages!.find(page => page.id === about.id)!.ourStory!.title.vi, story.title.vi);
  assert.throws(() => withAboutStory(input, home.id, story), /ABOUT_PAGE_REQUIRED/);
});
test('Our Story rich HTML survives legacy normalization, preview/save JSON and public projection without changing plain text', () => {
  const input = normalizeWebsitePages(structuredClone(seed), templates);
  const about = input.pages.find(page => page.template === 'about')!;
  const story = defaultAboutStory(LAB_BRAND);
  story.descriptionHtml = { vi: '<p><strong>VI</strong><br><em>Dòng mới</em></p><p><a href="#team">Nhóm</a></p>', en: '<p><strong>EN</strong></p><p><a href="/en/people">Team</a></p>' };
  const saved = normalizeWebsitePages(JSON.parse(JSON.stringify(withAboutStory(input, about.id, story))), templates);
  const projected = { ...saved, contentLibrary: undefined, pages: saved.pages.map(page => ({ ...page, sectionIds: undefined })) };
  assert.deepEqual(normalizeWebsitePages(projected, templates).pages.find(page => page.id === about.id)!.ourStory, story);
  assert.deepEqual(story.description, LAB_BRAND.introduction);
  assert.equal(input.pages.find(page => page.id === about.id)!.ourStory, undefined);
});
test('Explicitly cleared rich content and one edited language do not restore defaults or overwrite the other language', () => {
  const story = defaultAboutStory(LAB_BRAND);
  story.descriptionHtml = { vi: '', en: '<p>English only</p>' };
  story.ctaLabel.vi = '';
  const resolved = resolveAboutStory(story, LAB_BRAND);
  assert.equal(resolved.descriptionHtml!.vi, ''); assert.equal(resolved.ctaLabel.vi, '');
  assert.equal(resolved.descriptionHtml!.en, '<p>English only</p>');
  resolved.descriptionHtml!.en = 'Unsaved'; assert.equal(story.descriptionHtml.en, '<p>English only</p>');
  const hero = defaultHomeHero(LAB_BRAND); hero.descriptionHtml = { vi: '', en: '<p>Hero</p>' };
  assert.deepEqual(resolveHomeHero(hero, LAB_BRAND).descriptionHtml, hero.descriptionHtml);
});
test('Absent/null Hero uses the exact existing ALELA brand, Earth image and bilingual CTA defaults', () => {
  const fallback = defaultHomeHero(LAB_BRAND);
  assert.deepEqual(resolveHomeHero(undefined, LAB_BRAND), fallback);
  assert.deepEqual(resolveHomeHero(null, LAB_BRAND), fallback);
  assert.equal(fallback.title.vi, LAB_BRAND.slogan);
  assert.equal(fallback.title.en, LAB_BRAND.slogan);
  assert.equal(fallback.eyebrow.vi, LAB_BRAND.name);
  assert.deepEqual(fallback.description, LAB_BRAND.introduction);
  assert.equal(fallback.backgroundImage, '/images/earth.jpg');
  assert.deepEqual(fallback.primaryCta.url, { vi: '/vi/research', en: '/en/research' });
  assert.deepEqual(fallback.secondaryCta.url, { vi: '/vi/about', en: '/en/about' });
});
test('Legacy Hero fallback keeps copy overrides (including intentional blanks), media source and renamed routes', () => {
  const content = normalizeWebsitePages(structuredClone(seed), templates);
  content.copy['Explore our research'] = { vi: 'Nút đã biên tập', en: '' };
  content.copy['Earth at night viewed from orbit'] = { vi: 'Ảnh cũ', en: 'Old image' };
  content.pages.find(page => page.template === 'research')!.slug = 'custom-research';
  const before = structuredClone(content);
  const fallback = defaultHomeHero(LAB_BRAND, content.copy, content.pages);
  assert.deepEqual(fallback.primaryCta.label, { vi: 'Nút đã biên tập', en: '' });
  assert.deepEqual(fallback.primaryCta.url, { vi: '/vi/custom-research', en: '/en/custom-research' });
  assert.equal(fallback.backgroundAlt.vi, 'Ảnh cũ');
  assert.equal(fallback.backgroundImage, '/images/earth.jpg');
  assert.deepEqual(content, before);
});
test('Applying a Hero changes only the selected Homepage and preserves CMS sections/gallery/copy', () => {
  const content = normalizeWebsitePages(structuredClone(seed), templates);
  content.contentLibrary.push({ ...structuredClone(section), gallery: [galleryImage('retained')] });
  const before = structuredClone(content);
  const hero = defaultHomeHero(LAB_BRAND);
  hero.title = { vi: 'Tiêu đề VI', en: 'English title' };
  hero.primaryCta.url = { vi: '#research', en: 'https://example.org/english' };
  const home = content.pages.find(page => page.template === 'home')!;
  const updated = withHomeHero(content, home.id, hero);
  assert.deepEqual(updated.pages!.find(page => page.id === home.id), { ...home, hero });
  assert.deepEqual(updated.pages!.filter(page => page.id !== home.id), content.pages.filter(page => page.id !== home.id));
  assert.deepEqual(updated.contentLibrary, content.contentLibrary); assert.deepEqual(updated.data, content.data);
  assert.deepEqual(content, before);
  assert.throws(() => withHomeHero(content, content.pages.find(page => page.template === 'news')!.id, hero), /HOMEPAGE_REQUIRED/);
});
test('Configured Hero preserves explicit empty text, independent language URLs and background clearing', () => {
  const hero = defaultHomeHero(LAB_BRAND);
  hero.title.vi = '';
  hero.primaryCta.label.vi = ''; hero.primaryCta.url.vi = '';
  hero.backgroundImage = '';
  assert.deepEqual(resolveHomeHero(hero, LAB_BRAND), hero);
  const resolved = resolveHomeHero(hero, LAB_BRAND);
  resolved.title.en = 'Unsaved edit';
  assert.equal(hero.title.en, LAB_BRAND.slogan);
});
test('Hero survives normalized legacy headings, save/preview JSON and public sections-only round-trip', () => {
  const input = normalizeWebsitePages(structuredClone(seed), templates);
  const home = input.pages.find(page => page.template === 'home')!;
  const hero = defaultHomeHero(LAB_BRAND);
  hero.backgroundImage = '/api/uploads/website/hero.png';
  hero.primaryCta.url = { vi: '/vi/products?from=hero#intro', en: 'https://example.org/products' };
  const updated = withHomeHero(input, home.id, hero);
  const saved = normalizeWebsitePages(JSON.parse(JSON.stringify(updated)), templates);
  assert.deepEqual(saved.pages.find(page => page.id === home.id)!.hero, hero);
  const projected = { ...saved, contentLibrary: undefined, pages: saved.pages.map(page => ({ ...page, sectionIds: undefined })) };
  assert.deepEqual(normalizeWebsitePages(projected, templates).pages.find(page => page.id === home.id)!.hero, hero);
  assert.equal(input.pages.find(page => page.id === home.id)!.hero, undefined);
});
test('Hero CTA policy supports localized paths, anchors and HTTP(S), rejects executable/protocol-relative URLs', () => {
  for (const value of ['', '/vi/products?from=hero#demo', '/en/research', '#research', 'https://example.org/a', 'http://example.org']) assert.equal(isHeroUrl(value), true, value);
  for (const value of ['javascript:alert(1)', '//example.org', 'data:text/html,x', 'file:///etc/passwd', 'products', 'https://example.org/a b']) assert.equal(isHeroUrl(value), false, value);
});
test('Gallery reads 0/1/many images and absent/null legacy fields without modifying the cover', () => {
  assert.deepEqual(articleImages({ image: '' }), []);
  const legacy = { image: '/images/legacy.jpg' };
  assert.equal(articleImages(legacy).length, 1);
  assert.equal(articleImages({ ...legacy, gallery: null })[0].src, legacy.image);
  assert.deepEqual(legacy, { image: '/images/legacy.jpg' });
  assert.deepEqual(articleImages({ ...legacy, gallery: [galleryImage('a'), galleryImage('b')] }).map(image => image.src), [legacy.image, galleryImage('a').src, galleryImage('b').src]);
});
test('Multi-upload assigns a missing cover once and retains upload order and VI/EN metadata', () => {
  const input = { image: '', gallery: [] };
  const uploaded = [galleryImage('a'), galleryImage('b'), galleryImage('c')];
  const result = appendGalleryImages(input, uploaded);
  assert.equal(result.image, uploaded[0].src);
  assert.deepEqual(result.imageAlt, uploaded[0].alt);
  assert.deepEqual(result.imageCaption, uploaded[0].caption);
  assert.deepEqual(result.gallery, uploaded.slice(1));
  assert.equal(uploaded.length, 3); assert.deepEqual(input, { image: '', gallery: [] });
});
test('Changing cover swaps the old cover back into the gallery without losing captions or content', () => {
  const cover = galleryImage('old');
  const value = { ...section, image: cover.src, imageAlt: cover.alt, imageCaption: cover.caption, gallery: [galleryImage('a'), galleryImage('b')] };
  const before = structuredClone(value);
  const result = promoteGalleryImage(value, 'b');
  assert.equal(result.image, galleryImage('b').src);
  assert.deepEqual(result.imageAlt, galleryImage('b').alt);
  assert.deepEqual(result.imageCaption, galleryImage('b').caption);
  assert.equal(result.gallery.length, 2);
  assert.deepEqual(result.gallery[1], { ...cover, id: 'b' });
  assert.deepEqual(result.bodyHtml, value.bodyHtml);
  assert.deepEqual(value, before);
  assert.equal(promoteGalleryImage(value, 'missing'), value);
  const noCover = promoteGalleryImage({ image: '', gallery: [galleryImage('a')] }, 'a');
  assert.equal(noCover.image, galleryImage('a').src); assert.deepEqual(noCover.gallery, []);
});
test('Gallery reordering preserves stable IDs, metadata, input arrays and boundary order', () => {
  const input = [galleryImage('a'), galleryImage('b'), galleryImage('c')];
  const before = structuredClone(input);
  assert.deepEqual(moveGalleryImage(input, 'b', -1), [input[1], input[0], input[2]]);
  assert.deepEqual(moveGalleryImage(input, 'b', 1), [input[0], input[2], input[1]]);
  assert.deepEqual(moveGalleryImage(input, 'a', -1), input);
  assert.deepEqual(moveGalleryImage(input, 'c', 1), input);
  assert.deepEqual(input, before);
});
test('Gallery limit rejects excess images without truncating existing data; cover is separate', () => {
  const images = Array.from({ length: MAX_GALLERY_IMAGES }, (_, i) => galleryImage(`image-${i}`));
  const full = { image: '/cover.png', gallery: images };
  assert.throws(() => appendGalleryImages(full, [galleryImage('extra')]), /GALLERY_LIMIT/);
  assert.equal(full.gallery.length, 15);
  assert.equal(appendGalleryImages({ image: '' }, [galleryImage('cover'), ...images]).gallery!.length, 15);
  assert.equal(promoteGalleryImage(full, images[0].id).gallery.length, 15);
});
test('Image selection validates actual type, nonempty data and the existing inclusive 5MB limit', () => {
  for (const type of ['image/png', 'image/jpeg', 'image/webp']) assert.equal(validWebsiteImageFile({ type, size: MAX_WEBSITE_IMAGE_BYTES }), true);
  for (const file of [{ type: 'image/svg+xml', size: 100 }, { type: 'image/png', size: 0 }, { type: 'image/webp', size: MAX_WEBSITE_IMAGE_BYTES + 1 }]) assert.equal(validWebsiteImageFile(file), false);
});
test('Gallery survives library normalization, Products transfer and JSON preview/save round-trips', () => {
  const input = normalizeWebsitePages(structuredClone(seed), templates);
  const entry = { ...structuredClone(section), gallery: [galleryImage('one'), galleryImage('two')], imageAlt: galleryImage('cover').alt, imageCaption: galleryImage('cover').caption };
  input.contentLibrary = [entry];
  input.pages.find(page => page.template === 'news')!.sectionIds = [entry.id];
  const moved = normalizeWebsitePages(transferProductSections(normalizeWebsitePages(input, templates), [entry.id]), templates);
  const restored = normalizeWebsitePages(JSON.parse(JSON.stringify(moved)), templates);
  assert.deepEqual(restored.contentLibrary[0], entry);
  assert.deepEqual(restored.pages.find(page => page.template === 'products')!.sections, [entry]);
  assert.deepEqual(restored.pages.find(page => page.template === 'news')!.sections, []);
});

test('Master\'s tier follows PhD, remains visible when empty, and is available to the CMS role selector', () => {
  const roles = memberRoleGroups.map(group => group.role);
  assert.equal(roles.indexOf('MSc student'), roles.indexOf('PhD student') + 1);
  assert(roles.indexOf('MSc student') < roles.indexOf('Guest'));
  const masters = memberRoleGroups.find(group => group.role === 'MSc student')!;
  assert.equal(masters.showWhenEmpty, true);
  assert.equal(masters.label.vi, 'ThS');
  assert.equal(masters.title.en, "Master's Students");
});
test('Academic role filtering keeps doctoral members with ThS titles in PhD and preserves existing data', () => {
  const members = [
    { title: 'ThS. Doctoral researcher', role: 'PhD student' },
    { title: 'Graduate researcher', role: 'MSc student' },
    { title: 'ThS. Guest', role: 'Guest' },
    { title: 'Faculty member', role: 'Faculty' },
  ];
  const before = structuredClone(members);
  assert.deepEqual(members.filter(member => matchesMemberRole(member.role, 'MSc student')).map(member => member.title), ['Graduate researcher']);
  assert.deepEqual(members.filter(member => matchesMemberRole(member.role, 'PhD student')).map(member => member.title), ['ThS. Doctoral researcher']);
  assert.deepEqual(members.filter(member => matchesMemberRole(member.role, 'other')).map(member => member.title), ['Faculty member']);
  assert.deepEqual(members, before);
  const guest = memberRoleGroups.find(group => group.role === 'Guest')!;
  assert.deepEqual(guest.label, { vi: 'Khách mời', en: 'Guests' });
  assert.equal(matchesMemberRole('Guest', 'Guest'), true);
  assert.equal(matchesMemberRole('Guest', 'MSc student'), false);
});

test('Scholar ID parser supports URL encoding and fragments and rejects ambiguous/foreign URLs', () => {
  assert.equal(scholarAuthorId('https://scholar.google.com/citations?hl=vi&user=abc%2Ddef#articles'), 'abc-def');
  assert.equal(scholarAuthorId('http://scholar.google.com/citations?user=abc'), 'abc');
  assert.equal(scholarAuthorId('https://scholar.google.com/citations?user=abc&user=other'), null);
  assert.equal(scholarAuthorId('https://scholar.google.com.attacker.example/citations?user=abc'), null);
});

test('Scholar defaults to newest year; ties stable; null last; original array preserved', () => {
  const input = [paper('unknown', null, null), paper('old', 2020, 9), paper('new-a', 2026, 0), paper('new-b', 2026, 4)];
  const before = structuredClone(input);
  assert.deepEqual(sortScholarPublications(input, defaultScholarSort).map(p => p.title), ['new-a', 'new-b', 'old', 'unknown']);
  assert.deepEqual(sortScholarPublications(input, toggleScholarSort(defaultScholarSort, 'year')).map(p => p.title), ['old', 'new-a', 'new-b', 'unknown']);
  assert.deepEqual(input, before);
});
test('Citation header starts descending and toggles; zero is known, missing/NaN last', () => {
  const input = [paper('missing', 2025, null), paper('a', 2025, 8), paper('b', 2025, 8), paper('zero', 2025, 0), paper('nan', null, NaN)];
  const descending = toggleScholarSort(defaultScholarSort, 'citations');
  assert.equal(descending.direction, 'descending');
  assert.deepEqual(sortScholarPublications(input, descending).map(p => p.title), ['a', 'b', 'zero', 'missing', 'nan']);
  assert.deepEqual(sortScholarPublications(input, toggleScholarSort(descending, 'citations')).map(p => p.title), ['zero', 'a', 'b', 'missing', 'nan']);
});
test('Legacy CMS gets Products between Projects and News, without moving or duplicating sections', () => {
  const legacy = structuredClone(seed);
  legacy.pages!.find(p => p.template === 'news')!.sections = [structuredClone(section)];
  const before = structuredClone(legacy);
  const normalized = normalizeWebsitePages(legacy, templates);
  const nav = normalized.pages.filter(p => p.showInNavigation).map(p => p.template);
  assert.deepEqual(nav.slice(nav.indexOf('projects'), nav.indexOf('news') + 1), ['projects', 'products', 'news']);
  assert.equal(normalized.pages.find(p => p.template === 'products')!.slug, 'products');
  assert.deepEqual(normalized.pages.find(p => p.template === 'news')!.sectionIds, [section.id]);
  assert.deepEqual(normalized.pages.find(p => p.template === 'products')!.sectionIds, []);
  assert.deepEqual(normalized.contentLibrary.find(p => p.id === section.id), section);
  assert.deepEqual(legacy, before);
  assert.deepEqual(normalizeWebsitePages(normalized, templates), normalized);
});
test('Explicit move only changes selected memberships; HTML/images/VI/EN/source and unrelated News survive', () => {
  const input = normalizeWebsitePages(structuredClone(seed), templates);
  const unrelated = { ...structuredClone(section), id: 'real-news', title: { vi: 'Tin khác', en: 'Other news' } };
  input.contentLibrary = [structuredClone(section), unrelated];
  input.pages.find(p => p.template === 'news')!.sectionIds = [section.id, unrelated.id];
  const normalized = normalizeWebsitePages(input, templates);
  const before = structuredClone(normalized);
  assert.deepEqual(productTransferCandidates(normalized).map(s => s.id), [section.id]);
  const moved = transferProductSections(normalized, [section.id, section.id]);
  assert.deepEqual(moved.pages!.find(p => p.template === 'news')!.sectionIds, [unrelated.id]);
  assert.deepEqual(moved.pages!.find(p => p.template === 'products')!.sectionIds, [section.id]);
  assert.deepEqual(moved.contentLibrary, before.contentLibrary);
  assert.deepEqual(normalized, before);
  assert.equal(productTransferCandidates(moved).length, 0);
  assert.throws(() => transferProductSections(normalized, ['real-news']));
});
test('Existing Products draft and public-resolved sections survive normalization and preview round-trip', () => {
  const normalized = normalizeWebsitePages(structuredClone(seed), templates);
  normalized.contentLibrary = [structuredClone(section)];
  normalized.pages.find(p => p.template === 'products')!.sectionIds = [section.id];
  const saved = normalizeWebsitePages(JSON.parse(JSON.stringify(normalized)), templates);
  assert.deepEqual(saved.pages.find(p => p.template === 'products')!.sections, [section]);
  const publicContent = { ...saved, contentLibrary: undefined, pages: saved.pages.map(p => ({ ...p, sectionIds: undefined })) };
  assert.deepEqual(normalizeWebsitePages(publicContent, templates).pages.find(p => p.template === 'products')!.sections, [section]);
});
test('Colliding IDs/slugs and hidden library content are not overwritten or silently selected', () => {
  const input = structuredClone(seed);
  input.pages!.push({ ...structuredClone(templates[0]), id: 'products', slug: 'extra', template: 'custom', sections: [] });
  input.contentLibrary = [{ ...structuredClone(section), visible: false }];
  const normalized = normalizeWebsitePages(input, templates);
  assert.equal(new Set(normalized.pages.map(p => p.id)).size, normalized.pages.length);
  assert(normalized.pages.some(p => p.id === 'products' && p.slug === 'extra' && p.template === 'custom'));
  assert.equal(normalized.pages.find(p => p.template === 'products')!.slug, 'products');
  assert.deepEqual(normalized.contentLibrary, input.contentLibrary);
  assert.deepEqual(normalized.pages.find(p => p.template === 'products')!.sectionIds, []);
});

test('Published editorial list passages are searchable by author/year/venue/DOI/code without inferred metadata', async () => {
  const { filterSearch } = await import('../../contracts/website-search.ts');
  const docs = [
    { id: 'li-7', text: 'A. T. Tran L. M. Pham ProMESh IEEE Access 2026 doi: 10.1109/ACCESS.2026.3656429', metadata: {}, value: 'li-7' },
    { id: 'li-8', text: 'Nguyễn Đình An nội dung nhiều đoạn DOI 10.1000/example', metadata: {}, value: 'li-8' },
    { id: 'project', text: 'Điện toán đám mây QG20.55', metadata: {}, value: 'project' },
  ];
  for (const query of ['promesh', 'IEEE Access 2026', 'L. M. Pham', '10.1109/ACCESS.2026.3656429']) assert.deepEqual(filterSearch(docs, { query, filters: {} }).map(doc => doc.id), ['li-7']);
  assert.deepEqual(filterSearch(docs, { query: 'nguyen dinh an', filters: {} }).map(doc => doc.id), ['li-8']);
  assert.deepEqual(filterSearch(docs, { query: 'QG20.55', filters: {} }).map(doc => doc.id), ['project']);
  assert.equal(filterSearch(docs, { query: '', filters: { year: '2026' } }).length, 0);
});
test('Mixed search applies only explicit metadata and preserves source order and inputs', async () => {
  const { filterSearch, searchFilterValues } = await import('../../contracts/website-search.ts');
  const docs = [
    { id: 'editorial', text: 'Cloud IEEE 2026', metadata: {}, value: 1 },
    { id: 'record-a', text: 'Cloud IEEE 2026', metadata: { year: '2026', author: ['a', 'b'], type: 'Journal' }, value: 2 },
    { id: 'record-b', text: 'Cloud 2025', metadata: { year: '2025', author: ['b'], type: 'Conference' }, value: 3 },
  ];
  const original = structuredClone(docs);
  assert.deepEqual(filterSearch(docs, { query: '  CLOUD  ', filters: {} }).map(doc => doc.id), ['editorial', 'record-a', 'record-b']);
  assert.deepEqual(filterSearch(docs, { query: 'cloud', filters: { author: 'b', year: '2026', type: 'Journal' } }).map(doc => doc.id), ['record-a']);
  assert.deepEqual(searchFilterValues(docs, 'year'), ['2026', '2025']);
  assert.deepEqual(searchFilterValues(docs, 'author'), ['a', 'b']);
  assert.deepEqual(searchFilterValues(docs, 'status'), []);
  assert.deepEqual(docs, original);
});
test('Unknown project status, empty metadata and no-match queries are not given fabricated classifications', async () => {
  const { filterSearch, searchFilterValues } = await import('../../contracts/website-search.ts');
  const docs = [{ id: 'free-text', text: 'Đề tài QG20.55 hoàn thành năm 2026', metadata: {}, value: 1 }];
  assert.equal(filterSearch(docs, { query: '', filters: {} }).length, 1);
  assert.equal(filterSearch(docs, { query: 'unavailable', filters: {} }).length, 0);
  assert.equal(filterSearch(docs, { query: '', filters: { status: 'Completed' } }).length, 0);
  assert.deepEqual(searchFilterValues(docs, 'status'), []);
  assert.deepEqual(filterSearch([], { query: '', filters: {} }), []);
});


test('People display keeps guests independent and the academic chain ends at MSc without losing legacy roles', () => {
  const groups = [...memberRoleGroups, { role: 'other' }];
  const before = structuredClone(groups);
  const display = partitionMemberDisplayGroups(groups);
  assert.deepEqual(display.hierarchy.map(group => group.role), ['Steering Member', 'Core Member', 'PhD student', 'MSc student']);
  assert.deepEqual(display.collaborators.map(group => group.role), ['Guest']);
  assert.deepEqual(display.additional.map(group => group.role), ['Undergraduate', 'other']);
  assert.equal(display.hierarchy.length + display.additional.length + display.collaborators.length, groups.length);
  assert.deepEqual(groups, before);
  const guestsOnly = partitionMemberDisplayGroups(groups.filter(group => group.role === 'Guest'));
  assert.deepEqual(guestsOnly.hierarchy, []);
  assert.deepEqual(guestsOnly.additional, []);
  assert.equal(guestsOnly.collaborators.length, 1);
  const doctoralOnly = partitionMemberDisplayGroups(groups.filter(group => group.role === 'PhD student'));
  assert.equal(doctoralOnly.hierarchy.length, 1);
  assert.deepEqual(doctoralOnly.collaborators, []);
  assert.deepEqual(partitionMemberDisplayGroups([]), { hierarchy: [], additional: [], collaborators: [] });
});
