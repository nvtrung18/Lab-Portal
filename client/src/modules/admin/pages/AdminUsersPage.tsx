import { useMemo, useState } from 'react';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useAdminLabs, useAdminUsers, useBanUser, useUnbanUser, useUpdateUserRoles } from '../hooks';

const roleOptions = ['', STUDENT, LAB_MANAGER];
const statusOptions = ['', 'ACTIVE', 'BANNED'];

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('vi-VN').format(new Date(value)) : 'N/A';
}

function isBanned(status: string) {
  return ['BANNED', 'DISABLED', 'SUSPENDED', 'INACTIVE'].includes(status.toUpperCase());
}

export function AdminUsersPage() {
  const { data: users = [], isLoading, isError } = useAdminUsers();
  const { data: labs = [] } = useAdminLabs();
  const updateRolesMutation = useUpdateUserRoles();
  const banMutation = useBanUser();
  const unbanMutation = useUnbanUser();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [roleDrafts, setRoleDrafts] = useState<Record<number, string>>({});

  const managedLabByUserId = useMemo(() => {
    return new Map(
      labs
        .filter((lab) => lab.manager?.id)
        .map((lab) => [lab.manager?.id as number, lab.labName]),
    );
  }, [labs]);

  const filteredUsers = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return users.filter((user) => {
      // Backend should also omit ADMIN from this management endpoint. This FE
      // filter keeps the single system admin out of all user-management actions.
      if (user.roles.includes('ADMIN')) {
        return false;
      }

      const matchesSearch =
        !keyword ||
        user.fullName?.toLowerCase().includes(keyword) ||
        user.email.toLowerCase().includes(keyword);
      const matchesRole = !roleFilter || user.roles.includes(roleFilter);
      const normalizedStatus = user.status.toUpperCase();
      const matchesStatus =
        !statusFilter ||
        normalizedStatus === statusFilter ||
        (statusFilter === 'BANNED' && isBanned(user.status));

      return matchesSearch && matchesRole && matchesStatus;
    });
  }, [roleFilter, search, statusFilter, users]);

  const handleChangeRole = (userId: number, currentRoles: string[]) => {
    const nextRole = roleDrafts[userId] ?? currentRoles[0] ?? STUDENT;
    if (
      currentRoles.includes(LAB_MANAGER) &&
      nextRole === STUDENT &&
      managedLabByUserId.has(userId)
    ) {
      window.alert('Vui lòng gỡ manager khỏi lab trước khi đổi role.');
      return;
    }
    void updateRolesMutation.mutateAsync({ userId, roles: [nextRole] });
  };

  const handleBanUser = (userId: number, roles: string[]) => {
    if (roles.includes(LAB_MANAGER) && managedLabByUserId.has(userId)) {
      window.alert('Manager đang quản lý lab, vui lòng gỡ khỏi lab trước khi ban.');
      return;
    }
    void banMutation.mutateAsync(userId);
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <div className="h-6 w-32 animate-pulse rounded bg-slate-700" />
        <div className="mt-6 space-y-3">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="h-10 animate-pulse rounded bg-slate-800" />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-900 bg-slate-900 p-6 text-sm text-red-300 shadow-sm">
        Không thể tải danh sách user.
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">Users</h2>
          <p className="mt-1 text-sm text-slate-400">
            Quản lý student, lab manager và trạng thái tài khoản.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <input
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
            placeholder="Search name/email"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <select
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value)}
          >
            {roleOptions.map((role) => (
              <option key={role || 'all'} value={role}>
                {role || 'All roles'}
              </option>
            ))}
          </select>
          <select
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            {statusOptions.map((status) => (
              <option key={status || 'all'} value={status}>
                {status || 'All status'}
              </option>
            ))}
          </select>
        </div>
      </div>

      {filteredUsers.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-700 p-8 text-center text-sm text-slate-400">
          Không có user phù hợp.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-800 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-400">
                <th className="px-3 py-3">Full name</th>
                <th className="px-3 py-3">Email</th>
                <th className="px-3 py-3">Roles</th>
                <th className="px-3 py-3">Status</th>
                <th className="px-3 py-3">Managed Lab</th>
                <th className="px-3 py-3">Created At</th>
                <th className="px-3 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {filteredUsers.map((user) => {
                const disabled =
                  updateRolesMutation.isPending ||
                  banMutation.isPending ||
                  unbanMutation.isPending;

                return (
                  <tr key={user.id}>
                    <td className="px-3 py-4 text-slate-100">
                      {user.fullName || user.username || 'N/A'}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{user.email}</td>
                    <td className="px-3 py-4">
                      <select
                        className="rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-xs text-white outline-none focus:border-white"
                        value={roleDrafts[user.id] ?? user.roles[0] ?? STUDENT}
                        disabled={disabled}
                        onChange={(event) =>
                          setRoleDrafts((drafts) => ({ ...drafts, [user.id]: event.target.value }))
                        }
                      >
                        {[STUDENT, LAB_MANAGER].map((role) => (
                          <option key={role} value={role}>
                            {role}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-3 py-4">
                      <span className="rounded-full bg-slate-800 px-2 py-1 text-xs font-semibold text-slate-200">
                        {isBanned(user.status) ? 'BANNED' : user.status}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-slate-300">
                      {user.roles.includes(LAB_MANAGER) ? managedLabByUserId.get(user.id) ?? 'Chưa gán' : '-'}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{formatDate(user.createdAt)}</td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          className="rounded-md border border-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-100 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:text-slate-500"
                          disabled={disabled}
                          onClick={() => handleChangeRole(user.id, user.roles)}
                        >
                          Change Role
                        </button>
                        {isBanned(user.status) ? (
                          <button
                            type="button"
                            className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-900"
                            disabled={disabled}
                            onClick={() => void unbanMutation.mutateAsync(user.id)}
                          >
                            Unban
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-900"
                            disabled={disabled}
                            onClick={() => handleBanUser(user.id, user.roles)}
                          >
                            Ban
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
