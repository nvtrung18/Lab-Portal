import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { scholarAuthorId } from '../../contracts/scholar-author-id.ts';
import { defaultScholarSort, sortScholarPublications, toggleScholarSort } from '../../contracts/scholar-sort.ts';
import { normalizeWebsitePages } from '../../contracts/website-pages.ts';
import { productTransferCandidates, transferProductSections } from '../../contracts/website-product-transfer.ts';
import type { ScholarPublication, WebsiteContent, WebsitePage, WebsiteSection } from '../../contracts/website-content.ts';

const seed = JSON.parse(readFileSync(new URL('../../contracts/website-demo-content.json', import.meta.url), 'utf8')).content as WebsiteContent;
const templates = seed.pages as WebsitePage[];
const section: WebsiteSection = {
  id: 'product-iot', title: { vi: 'Hệ thống IoT quan trắc khí gây mùi trong trang trại chăn nuôi', en: 'Odor monitoring' },
  description: { vi: 'Mô tả', en: 'Summary' }, eyebrow: { vi: '', en: '' }, body: [],
  bodyHtml: { vi: '<p><strong>Nội dung</strong><img src="/api/uploads/website/a.png" alt="ảnh"><a href="https://example.org">Link</a></p>', en: '<p>English text</p>' },
  image: '/api/uploads/website/hero.png', presentation: 'split', linkLabel: { vi: 'Xem', en: 'Read' }, linkUrl: 'https://example.org',
};
const paper = (title: string, year: number | null, citations: number | null): ScholarPublication => ({ title, year, citations, authors: '', venue: '', url: 'https://example.org/' + title });

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
