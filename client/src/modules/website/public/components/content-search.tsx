import { useLayoutEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowUpRight } from 'lucide-react';
import { filterSearch, searchFilterValues, type SearchFilter, type SearchFilters } from '../../../../../../contracts/website-search';
import { editorialSearchDocuments, recordSearchDocument, scrollToEditorialTarget, type SearchCollection, type SearchRecord } from '../../editorial-search';
import { roleVi, useLab } from './lab-provider';
import { DynamicSections } from './dynamic-sections';
import { SearchInput } from './search-input';
import { Select } from './ui/select';

const filtersFor: Record<SearchCollection, SearchFilter[]> = {
  publications: ['year', 'type', 'author', 'area'], projects: ['status', 'area'], news: ['category'], research: [],
};
const labels: Record<SearchFilter, [string, string]> = {
  year: ['Year', 'Năm'], type: ['Type', 'Loại'], author: ['Author', 'Tác giả'],
  area: ['Research area', 'Lĩnh vực nghiên cứu'], status: ['Project status', 'Trạng thái đề tài'], category: ['News category', 'Nhóm tin tức'],
};
export function ContentSearch<T extends SearchRecord>({ kind, records, renderRecords }: {
  kind: SearchCollection; records: T[]; renderRecords: (items: T[]) => ReactNode;
}) {
  const { page, data, locale, l, text, loadError } = useLab();
  const { hash, pathname, search } = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<SearchFilters>({});
  const editorials = useMemo(() => editorialSearchDocuments(page?.sections ?? [], locale), [page?.sections, locale]);
  const structured = useMemo(() => records.filter(item => item.visible !== false).map(item => recordSearchDocument(item, data, locale)), [records, data, locale]);
  const available = filtersFor[kind].map(key => ({ key, values: searchFilterValues(structured, key) })).filter(item => item.values.length);
  // A locale/publication refresh cannot leave a filter selected whose metadata no longer exists.
  const activeFilters: SearchFilters = Object.fromEntries(available.map(({ key, values }) => [key, values.includes(String(filters[key])) ? filters[key] : 'all']));
  const criteria = { query, filters: activeFilters };
  const metadataActive = Object.values(activeFilters).some(value => value && value !== 'all');
  const searching = Boolean(query.trim()) || metadataActive;
  const matchedEditorials = filterSearch(editorials, criteria);
  const matchedRecords = filterSearch(structured, criteria);
  const sourceTarget = editorials.find(item => `#${item.value.anchor}` === hash)?.value;
  const total = editorials.length + structured.length;
  const shown = matchedEditorials.length + matchedRecords.length;
  const clearSource = (returnToResults = false) => {
    if (hash) navigate({ pathname, search, hash: '' }, { replace: true, preventScrollReset: true });
    if (returnToResults) requestAnimationFrame(() => {
      const element = document.getElementById('content-search');
      if (element) scrollToEditorialTarget(element);
    });
  };
  const reset = () => { setQuery(''); setFilters({}); clearSource(); };
  useLayoutEffect(() => {
    if (!sourceTarget) return;
    const element = document.getElementById(sourceTarget.anchor);
    if (element) scrollToEditorialTarget(element);
  }, [sourceTarget, locale]);
  const placeholders: Record<SearchCollection, string> = {
    publications: l('Search titles, authors, years, venues, DOI…', 'Tìm tiêu đề, tác giả, năm, nơi công bố, DOI…'),
    projects: l('Search project names, codes or content…', 'Tìm tên, mã đề tài hoặc nội dung…'),
    research: l('Search research areas…', 'Tìm lĩnh vực nghiên cứu…'), news: l('Search news and events…', 'Tìm tin và sự kiện…'),
  };
  return <>
    <section className="section content-search" id="content-search" aria-label={l('Search published content', 'Tìm nội dung đã xuất bản')}>
      <div className="container">
        <div className={`filter-panel${available.length ? '' : ' search-only'}`}>
          <SearchInput value={query} onChange={value => { setQuery(value); clearSource(); }} placeholder={placeholders[kind]} />
          <div className="filter-selects">
            {available.map(({ key, values }) => <Select key={key} label={l(...labels[key])} value={String(activeFilters[key] ?? 'all')}
              onValueChange={value => { setFilters(current => ({ ...current, [key]: value })); clearSource(); }} options={[
                { value: 'all', label: `${l('All', 'Tất cả')} — ${l(...labels[key])}` },
                ...[...values].sort((a, b) => key === 'year' ? Number(b) - Number(a) : a.localeCompare(b)).map(value => ({ value,
                  label: key === 'author' ? text(data.members.find(item => item.id === value)?.title ?? { vi: value, en: value }) || value
                    : key === 'area' ? text(data.research.find(item => item.id === value)?.title ?? { vi: value, en: value }) || value
                    : l(value, roleVi[value] ?? value) })),
              ]} />)}
            {searching && <button type="button" onClick={reset} className="clear-filters">{l('Reset', 'Đặt lại')}</button>}
          </div>
        </div>
        <div className="results-header" role="status">
          <span><strong>{shown}</strong> {l(shown === 1 ? 'result' : 'results', 'kết quả')}
            {matchedRecords.length > 0 && <> · {matchedRecords.length} {l(matchedRecords.length === 1 ? 'record' : 'records', 'bản ghi')}</>}
            {matchedEditorials.length > 0 && <> · {matchedEditorials.length} {l(matchedEditorials.length === 1 ? 'list item / text passage' : 'list items / text passages', 'mục danh sách / đoạn nội dung')}</>}
          </span>
        </div>
        {editorials.length > 0 && <p className="content-search-note">{l('Search includes the original text. List items and text passages are not verified individual research records.', 'Tìm kiếm bao gồm văn bản gốc. Mục danh sách và đoạn nội dung chưa được phân loại thành các bản ghi học thuật riêng.')}</p>}
        {metadataActive && editorials.length > 0 && <p className="content-search-note">{l('Active filters include only records with the selected classification. Reset filters to search all text.', 'Bộ lọc đang bật chỉ tính các bản ghi có thông tin phân loại tương ứng. Đặt lại bộ lọc để tìm toàn bộ văn bản.')}</p>}
        {!shown && <div className="empty-state" role="status">
          <h3>{loadError ? l('Content unavailable', 'Chưa tải được nội dung') : total ? l('No matching results', 'Không có kết quả phù hợp') : l('No searchable text in this language yet', 'Chưa có nội dung văn bản để tìm ở ngôn ngữ này')}</h3>
          <p>{loadError ? l('Please reload the page to retry.', 'Vui lòng tải lại trang để thử lại.') : total ? l('Try another keyword or reset filters.', 'Thử từ khóa khác hoặc đặt lại bộ lọc.') : l('Published content will appear here when available.', 'Nội dung đã xuất bản sẽ hiển thị tại đây khi được bổ sung.')}</p>
          {searching && <button type="button" className="text-link" onClick={reset}>{l('Clear all filters', 'Xóa tất cả bộ lọc')}</button>}
        </div>}
        {searching && <>
          {matchedRecords.length > 0 && renderRecords(matchedRecords.map(item => item.value))}
          {matchedEditorials.length > 0 && <div className="editorial-search-results">
            {matchedEditorials.map(({ id, value: entry }) => <article className="editorial-search-hit" key={id}>
              <p className="editorial-search-context">{entry.sectionTitle} · {entry.kind === 'list-item' ? l('List item', 'Mục danh sách') : l('Text passage', 'Đoạn nội dung')}{entry.number != null && ` ${entry.number}`}</p>
              <p className="editorial-search-text">{entry.text}</p>
              <a href={`#${entry.anchor}`} className="text-link" onClick={event => { event.preventDefault(); navigate({ pathname, search, hash: `#${entry.anchor}` }, { preventScrollReset: true }); }}>
                {l('View in original content', 'Xem trong nội dung gốc')}<ArrowUpRight size={16} />
              </a>
            </article>)}
          </div>}
        </>}
        {sourceTarget && <button type="button" className="text-link search-source-back" onClick={() => clearSource(true)}>{searching ? l('Back to results', 'Quay lại kết quả') : l('Show all content', 'Hiển thị toàn bộ nội dung')}</button>}
      </div>
    </section>
    {(!searching || sourceTarget) && <DynamicSections key={sourceTarget?.anchor ?? 'all'} sectionId={sourceTarget?.sectionId} reveal={Boolean(sourceTarget)} />}
    {!searching && structured.length > 0 && <section className="section"><div className="container">{renderRecords(structured.map(item => item.value))}</div></section>}
  </>;
}
