import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { scholarAuthorId } from '../../contracts/scholar-author-id.ts';
import { defaultScholarSort, sortScholarPublications, toggleScholarSort } from '../../contracts/scholar-sort.ts';
import { normalizeWebsitePages } from '../../contracts/website-pages.ts';
import { productTransferCandidates, transferProductSections } from '../../contracts/website-product-transfer.ts';
import type { ScholarPublication, WebsiteContent, WebsitePage, WebsiteSection } from '../../contracts/website-content.ts';
import { memberRoleGroups, matchesMemberRole } from '../src/modules/website/member-roles.ts';
import { appendGalleryImages, articleImages, MAX_GALLERY_IMAGES, MAX_WEBSITE_IMAGE_BYTES, moveGalleryImage, promoteGalleryImage, validWebsiteImageFile } from '../../contracts/website-gallery.ts';
import type { GalleryImage } from '../../contracts/website-content.ts';

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
