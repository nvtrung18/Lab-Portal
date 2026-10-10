import { useState } from 'react';
import type { ResearchArea } from '../../../../../contracts/website-content';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { LabResponse } from '../../lab/api';
import { inputClass } from './EditorFields';

const PAGE_SIZE = 20;

export function ResearchSourcePicker({ labs, research, target, loading, failed, refreshing, onRetry, onSelect }: {
  labs: LabResponse[];
  research: ResearchArea[];
  target: ResearchArea | null;
  loading: boolean;
  failed: boolean;
  refreshing: boolean;
  onRetry: () => void;
  onSelect: (lab: LabResponse) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const needle = search.trim().toLocaleLowerCase();
  const matches = labs.filter(lab => `${lab.labName} ${lab.description ?? ''} ${lab.department ?? ''}`.toLocaleLowerCase().includes(needle))
    .sort((left, right) => left.id - right.id);
  const pageCount = Math.max(1, Math.ceil(matches.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const linkedLabs = new Set(research.filter(item => item.id !== target?.id).flatMap(item => item.labId == null ? [] : [item.labId]));
  const statusLabels: Record<LabResponse['status'], string> = {
    AVAILABLE: l('Hoạt động', 'Available'), ACTIVE: l('Hoạt động', 'Active'),
    MAINTENANCE: l('Bảo trì', 'Maintenance'), INACTIVE: l('Không hoạt động', 'Inactive'),
    ARCHIVED: l('Lưu trữ', 'Archived'), CLOSED: l('Đã đóng', 'Closed'),
  };
  return <div className="space-y-4">
    <p className="text-sm text-slate-500">{l('Chọn nhóm nghiên cứu để tạo hoặc liên kết nội dung Hướng nghiên cứu. Tên và mô tả được điền khi tạo mới; bạn có thể chỉnh riêng nội dung công khai trước khi xuất bản.', 'Select a research group to create or link a research direction. Its name and description fill the new record; public content can be edited before publication.')}</p>
    <div className="flex flex-wrap items-center gap-3">
      <input type="search" aria-label={l('Tìm nhóm nghiên cứu', 'Find research groups')} className={`${inputClass} flex-1`} value={search} placeholder={l('Tên nhóm, mô tả hoặc đơn vị…', 'Group name, description or department…')} onChange={event => { setSearch(event.target.value); setPage(0); }} />
      <Button size="sm" variant="outline" disabled={refreshing} onClick={onRetry}>{l('Tải lại danh sách', 'Reload groups')}</Button>
    </div>
    {loading && <p role="status" className="py-6 text-sm text-slate-500">{l('Đang tải nhóm nghiên cứu…', 'Loading research groups…')}</p>}
    {failed && <p role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">{l('Không tải được danh sách nhóm nghiên cứu. Bấm tải lại để thử lại.', 'Research groups could not be loaded. Reload the list to retry.')}</p>}
    {!loading && !failed && <>
      <p className="text-xs text-slate-500">{matches.length} {l('nhóm nghiên cứu', 'research groups')}</p>
      <div className="divide-y divide-slate-200 dark:divide-slate-800">
        {matches.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE).map(lab => {
          const linked = linkedLabs.has(lab.id);
          const current = target?.labId === lab.id;
          return <article key={lab.id} className="flex flex-wrap items-center justify-between gap-3 py-4">
            <div className="min-w-0 flex-1">
              <h3 className="break-words text-sm font-semibold">{lab.labName}</h3>
              {lab.description && <p className="mt-1 line-clamp-2 break-words text-xs text-slate-500">{lab.description}</p>}
              <span className="mt-1 inline-block text-xs text-slate-500">{lab.department ? `${lab.department} · ` : ''}{statusLabels[lab.status]}</span>
            </div>
            <Button size="sm" variant="outline" disabled={linked || current} onClick={() => onSelect(lab)}>{linked ? l('Đã có nội dung', 'Content exists') : current ? l('Đang liên kết', 'Currently linked') : l('Chọn nhóm này', 'Select group')}</Button>
          </article>;
        })}
      </div>
      {!matches.length && <p role="status" className="py-8 text-center text-sm text-slate-500">{l('Không có nhóm nghiên cứu phù hợp.', 'No matching research groups.')}</p>}
      {pageCount > 1 && <nav aria-label={l('Trang danh sách nhóm nghiên cứu', 'Research group list pages')} className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
        <Button size="sm" variant="outline" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>{l('Trang trước', 'Previous')}</Button>
        <span className="text-sm">{currentPage + 1} / {pageCount}</span>
        <Button size="sm" variant="outline" disabled={currentPage + 1 >= pageCount} onClick={() => setPage(currentPage + 1)}>{l('Trang sau', 'Next')}</Button>
      </nav>}
    </>}
  </div>;
}
