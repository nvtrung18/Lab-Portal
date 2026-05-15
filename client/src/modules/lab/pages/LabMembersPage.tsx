import { useMemo, useState } from 'react';

import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { useLabMembers, useRemoveLabMember } from '../hooks';

function statusClassName(status: string) {
  if (status.toUpperCase() === 'ACTIVE') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }

  return 'bg-slate-100 text-slate-600 ring-slate-200';
}

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' }).format(new Date(value)) : 'N/A';
}

export function LabMembersPage() {
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const { data: members = [], isLoading, isError } = useLabMembers(managedLabId);
  const removeMemberMutation = useRemoveLabMember();
  const [search, setSearch] = useState('');

  const handleRemoveMember = (member: (typeof members)[number]) => {
    if (!managedLabId) {
      return;
    }

    const confirmed = window.confirm(
      'Bạn có chắc muốn xóa thành viên này khỏi lab không? Tài khoản của user vẫn được giữ lại, chỉ xóa tư cách thành viên trong lab.',
    );

    if (!confirmed) {
      return;
    }

    removeMemberMutation.mutate({ labId: managedLabId, userId: member.userId });
  };

  const filteredMembers = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return members.filter((member) => {
      const isActive = member.status.toUpperCase() === 'ACTIVE';
      const matchesSearch =
        !keyword ||
        member.fullName?.toLowerCase().includes(keyword) ||
        member.email.toLowerCase().includes(keyword);
      return isActive && matchesSearch;
    });
  }, [members, search]);

  if (isLoadingUser || isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-44 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-10 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        Bạn chưa được gán quản lý lab nào.
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải danh sách thành viên lab.
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-slate-950">Lab Members</h2>
          <p className="mt-1 text-sm text-slate-600">
            Danh sách thành viên thuộc lab bạn quản lý
            {managedLabName ? `: ${managedLabName}` : ''}.
          </p>
        </div>
        <input
          className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
          placeholder="Search name/email"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {filteredMembers.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-300 p-8 text-center text-sm text-slate-600">
          Chưa có thành viên ACTIVE trong lab này.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-500">
                <th className="px-3 py-3">Full name</th>
                <th className="px-3 py-3">Email</th>
                <th className="px-3 py-3">Role</th>
                <th className="px-3 py-3">Status</th>
                <th className="px-3 py-3">Joined at</th>
                <th className="px-3 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredMembers.map((member) => (
                <tr key={member.id}>
                  <td className="px-3 py-4 font-medium text-slate-950">
                    {member.fullName || 'N/A'}
                  </td>
                  <td className="px-3 py-4 text-slate-700">{member.email}</td>
                  <td className="px-3 py-4 text-slate-700">{member.role || 'MEMBER'}</td>
                  <td className="px-3 py-4">
                    <span
                      className={[
                        'inline-flex rounded-full px-2 py-1 text-xs font-semibold ring-1',
                        statusClassName(member.status),
                      ].join(' ')}
                    >
                      {member.status}
                    </span>
                  </td>
                  <td className="px-3 py-4 text-slate-700">{formatDate(member.joinedAt)}</td>
                  <td className="px-3 py-4 text-right">
                    <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
                      onClick={() => window.alert(`${member.fullName || member.email}\n${member.email}`)}
                    >
                      View detail
                    </button>
                    <button
                      type="button"
                      className="rounded-md border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-700 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                      disabled={removeMemberMutation.isPending}
                      onClick={() => handleRemoveMember(member)}
                    >
                      {removeMemberMutation.isPending ? 'Removing...' : 'Remove'}
                    </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
