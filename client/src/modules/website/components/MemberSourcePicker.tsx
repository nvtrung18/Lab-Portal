import { useState } from 'react';
import type { Member } from '../../../../../contracts/website-content';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { WebsiteMemberSource } from '../api';
import { inputClass } from './EditorFields';

const PAGE_SIZE = 20;

export function MemberSourcePicker({ users, members, target, loading, failed, refreshing, onRetry, onSelect }: {
  users: WebsiteMemberSource[];
  members: Member[];
  target: Member | null;
  loading: boolean;
  failed: boolean;
  refreshing: boolean;
  onRetry: () => void;
  onSelect: (user: WebsiteMemberSource) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const needle = search.trim().toLocaleLowerCase();
  const matches = users.filter(user => `${user.fullName ?? ''} ${user.username} ${user.email}`.toLocaleLowerCase().includes(needle));
  const pageCount = Math.max(1, Math.ceil(matches.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const linkedUsers = new Set(members.filter(member => member.id !== target?.id).flatMap(member => member.userId == null ? [] : [member.userId]));
  const statusLabels: Record<WebsiteMemberSource['status'], string> = {
    ACTIVE: l('Hoạt động', 'Active'), PENDING_VERIFICATION: l('Chưa xác minh', 'Pending verification'),
    INACTIVE: l('Không hoạt động', 'Inactive'), SUSPENDED: l('Bị khóa', 'Suspended'),
  };
  return <div className="space-y-4">
    <p className="text-sm text-slate-500">{l('Chọn một tài khoản để tạo hoặc liên kết hồ sơ Thành viên. Nội dung hiển thị được chỉnh riêng; email tài khoản không tự đưa lên website.', 'Select an account to create or link a member profile. Public content is edited separately; account email is not automatically published.')}</p>
    <div className="flex flex-wrap items-center gap-3">
      <input type="search" aria-label={l('Tìm người dùng', 'Find users')} className={`${inputClass} flex-1`} value={search} placeholder={l('Tên, tên đăng nhập hoặc email…', 'Name, username or email…')} onChange={event => { setSearch(event.target.value); setPage(0); }} />
      <Button size="sm" variant="outline" disabled={refreshing} onClick={onRetry}>{l('Tải lại danh sách', 'Reload users')}</Button>
    </div>
    {loading && <p role="status" className="py-6 text-sm text-slate-500">{l('Đang tải người dùng…', 'Loading users…')}</p>}
    {failed && <p role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">{l('Không tải được danh sách người dùng. Bấm tải lại để thử lại.', 'Users could not be loaded. Reload the list to retry.')}</p>}
    {!loading && !failed && <>
      <p className="text-xs text-slate-500">{matches.length} {l('người dùng', 'users')}</p>
      <div className="divide-y divide-slate-200 dark:divide-slate-800">
        {matches.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE).map(user => {
          const linked = linkedUsers.has(user.id);
          const current = target?.userId === user.id;
          return <article key={user.id} className="flex flex-wrap items-center justify-between gap-3 py-4">
            <div className="min-w-0 flex-1">
              <h3 className="break-words text-sm font-semibold">{user.fullName || user.username}</h3>
              <p className="mt-1 break-words text-xs text-slate-500">@{user.username} · {user.email}</p>
              <span className="mt-1 inline-block text-xs text-slate-500">{statusLabels[user.status]}</span>
            </div>
            <Button size="sm" variant="outline" disabled={linked || current} onClick={() => onSelect(user)}>{linked ? l('Đã có hồ sơ', 'Profile exists') : current ? l('Đang liên kết', 'Currently linked') : l('Chọn người này', 'Select user')}</Button>
          </article>;
        })}
      </div>
      {!matches.length && <p role="status" className="py-8 text-center text-sm text-slate-500">{l('Không có người dùng phù hợp.', 'No matching users.')}</p>}
      {pageCount > 1 && <nav aria-label={l('Trang danh sách người dùng', 'User list pages')} className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-800">
        <Button size="sm" variant="outline" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>{l('Trang trước', 'Previous')}</Button>
        <span className="text-sm">{currentPage + 1} / {pageCount}</span>
        <Button size="sm" variant="outline" disabled={currentPage + 1 >= pageCount} onClick={() => setPage(currentPage + 1)}>{l('Trang sau', 'Next')}</Button>
      </nav>}
    </>}
  </div>;
}
