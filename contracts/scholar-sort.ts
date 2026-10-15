import type { ScholarPublication } from './website-content';

export type ScholarSort = { column: 'year' | 'citations'; direction: 'ascending' | 'descending' };
export const defaultScholarSort: ScholarSort = { column: 'year', direction: 'descending' };

export function toggleScholarSort(current: ScholarSort, column: ScholarSort['column']): ScholarSort {
  return { column, direction: current.column === column && current.direction === 'descending' ? 'ascending' : 'descending' };
}

/** Unknown values stay last in either direction. Source order breaks ties. Never mutate the CMS array. */
export function sortScholarPublications(publications: ScholarPublication[], sort: ScholarSort): ScholarPublication[] {
  return publications.map((publication, index) => ({ publication, index })).sort((a, b) => {
    const av = a.publication[sort.column];
    const bv = b.publication[sort.column];
    const missingA = av == null || !Number.isFinite(av);
    const missingB = bv == null || !Number.isFinite(bv);
    if (missingA || missingB) return Number(missingA) - Number(missingB) || a.index - b.index;
    return (sort.direction === 'descending' ? bv - av : av - bv) || a.index - b.index;
  }).map(item => item.publication);
}
