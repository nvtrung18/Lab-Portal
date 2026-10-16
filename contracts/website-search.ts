/** Read-only search helpers. Metadata is supplied explicitly, never inferred from prose. */
export type SearchFilter = 'year' | 'type' | 'author' | 'area' | 'status' | 'category';
export type SearchMetadata = Partial<Record<SearchFilter, string | string[]>>;
export interface SearchDocument<T> { id: string; text: string; metadata: SearchMetadata; value: T }
export type SearchFilters = Partial<Record<SearchFilter, string>>;
export interface SearchCriteria { query: string; filters: SearchFilters }
export const normalizeSearchText = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().replace(/\s+/g, ' ').trim();
export function matchesSearch(document: Pick<SearchDocument<unknown>, 'text' | 'metadata'>, criteria: SearchCriteria) {
  const text = normalizeSearchText(document.text);
  return normalizeSearchText(criteria.query).split(' ').filter(Boolean).every(term => text.includes(term))
    && Object.entries(criteria.filters).every(([key, expected]) => {
      if (!expected || expected === 'all') return true;
      const actual = document.metadata[key as SearchFilter];
      return Array.isArray(actual) ? actual.includes(String(expected)) : actual === expected;
    });
}
export function filterSearch<T>(documents: SearchDocument<T>[], criteria: SearchCriteria) {
  return documents.filter(document => matchesSearch(document, criteria));
}
export function searchFilterValues<T>(documents: SearchDocument<T>[], key: SearchFilter): string[] {
  return [...new Set(documents.flatMap(document => {
    const value = document.metadata[key]; return value == null ? [] : Array.isArray(value) ? value : [value];
  }).filter(Boolean))];
}
