// Standalone Vite browser harness: /tests/website-search.browser.html (not included in production build).
import fixture from './fixtures/website-editorial-search.json';
import { editorialSearchDocuments, prepareEditorialBody, recordSearchDocument } from '../src/modules/website/editorial-search';
import { filterSearch } from '../../contracts/website-search';
import type { WebsiteSection, LabData } from '../../contracts/website-content';
const section: WebsiteSection = { id: 'fixture', title: fixture.title, description: { vi: '', en: '' }, eyebrow: { vi: '', en: '' },
  body: [], bodyHtml: fixture.html, image: '', presentation: 'standard', linkLabel: { vi: '', en: '' }, linkUrl: '' };
let failures = 0;
function check(name: string, run: () => void) {
  const li = document.createElement('li');
  try { run(); li.textContent = `PASS: ${name}`; } catch (error) { failures++; li.textContent = `FAIL: ${name}: ${error}`; }
  document.querySelector('#results')!.append(li);
}
function assert(condition: unknown) { if (!condition) throw new Error('Assertion failed'); }
for (const locale of ['vi', 'en'] as const) {
  check(`${locale}: explicit items, nested list and continuation paragraphs`, () => {
    const prepared = prepareEditorialBody(section, locale);
    const items = prepared.entries.filter(item => item.kind === 'list-item');
    assert(items.length === 3); assert(items.every(item => item.anchor.includes(`-${locale}-`))); assert(items[0].number === 7); assert(items[1].number === 8);
    assert(items[1].urls?.includes('https://doi.org/10.1000/example')); assert(items[1].text.includes(locale === 'vi' ? 'Phụ lục' : 'Appendix'));
    const root = document.createElement('div'); root.innerHTML = prepared.html;
    assert(root.querySelectorAll('li').length === 5); assert(root.querySelector('ol')!.getAttribute('start') === '7');
    assert(root.querySelector('img')!.getAttribute('alt') === (locale === 'vi' ? 'Sơ đồ' : 'Diagram'));
    assert(root.querySelector('a')!.getAttribute('href') === 'https://doi.org/10.1000/example');
    for (const entry of prepared.entries) assert(root.querySelector(`[id="${entry.anchor}"]`)?.textContent);
  });
  check(`${locale}: exact item search, no bibliography guessing, no CMS mutation`, () => {
    const before = JSON.stringify(section);
    const docs = editorialSearchDocuments([section], locale);
    assert(filterSearch(docs, { query: 'IEEE Access 2026', filters: {} }).length === 1);
    assert(filterSearch(docs, { query: 'QG20.55', filters: {} }).length === 1);
    assert(filterSearch(docs, { query: '10.1000/example', filters: {} }).length === 1);
    assert(docs.every(doc => Object.keys(doc.metadata).length === 0));
    assert(docs.some(doc => doc.value.kind === 'passage' && doc.value.text.includes(locale === 'vi' ? 'Hai bài' : 'Two papers')));
    assert(JSON.stringify(section) === before);
    assert(editorialSearchDocuments([{ ...section, visible: false }], locale).length === 0);
  });
}
check('Explicit empty HTML, legacy plain text and missing language content', () => {
  const plain = { ...section, bodyHtml: null, body: [{ vi: 'Dòng 1\n<b>literal</b>', en: '' }] };
  const prepared = prepareEditorialBody(plain, 'vi'); assert(prepared.entries.length === 1); assert(prepared.html.includes('&lt;b&gt;literal&lt;/b&gt;'));
  assert(prepareEditorialBody({ ...plain, bodyHtml: { vi: '', en: '' } }, 'vi').entries.length === 0);
  assert(prepareEditorialBody({ ...section, bodyHtml: { vi: '<ol start="invalid"><li><p>Fallback number</p></li></ol>', en: '' } }, 'vi').entries[0].number === 1);
  assert(editorialSearchDocuments([{ ...section, bodyHtml: { vi: fixture.html.vi, en: '' } }], 'en').length === 0);
});
check('Unsafe HTML never becomes active while indexing or decorating', () => {
  const unsafe = { ...section, bodyHtml: { vi: '<p onclick="alert(1)">Safe<script>alert(1)</script><a href="javascript:alert(1)">link</a></p>', en: '' } };
  const prepared = prepareEditorialBody(unsafe, 'vi'); assert(!/onclick|<script|javascript:/.test(prepared.html));
});
check('Structured metadata, author IDs/names, year, project code and rich bodies', () => {
  const data = { research: [], projects: [], news: [], publications: [], members: [{ id: 'author-a', title: { vi: 'Nguyễn Đình An', en: 'Nguyen Dinh An' } }] } as unknown as LabData;
  const record = { id: 'QG20.55', title: { vi: 'Cloud', en: 'Cloud' }, description: { vi: '', en: '' }, status: 'Ongoing', area: '', category: { vi: '', en: '' }, period: '', objectives: [], bodyHtml: { vi: '<p>Nội dung <strong>rich</strong></p>', en: '' } } as LabData['projects'][number];
  const doc = recordSearchDocument(record, data, 'vi'); assert(doc.metadata.status === 'Ongoing'); assert(doc.text.includes('QG20.55')); assert(doc.text.includes('Nội dung rich'));
  const publication = { id: 'paper', title: { vi: 'Cloud', en: 'Cloud' }, description: { vi: '', en: '' }, year: 2026, type: 'Journal', authors: ['author-a', 'external-author'], area: '', venue: { vi: 'IEEE Access', en: '' }, resources: [] } as LabData['publications'][number];
  const paper = recordSearchDocument(publication, data, 'vi'); assert(paper.metadata.year === '2026'); assert(paper.text.includes('Nguyễn Đình An')); assert(paper.text.includes('external-author'));
});
document.querySelector('h1')!.textContent = failures ? `${failures} failed` : 'All 7 browser checks passed';
