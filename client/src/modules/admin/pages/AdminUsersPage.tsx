import { useMemo, useState } from 'react';

import { Button } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useAdminLabs, useAdminUsers, useBanUser, useUnbanUser, useUpdateUserRoles } from '../hooks';

const roleOptions = ['', STUDENT, LAB_MANAGER];
const statusOptions = ['', 'ACTIVE', 'BANNED'];

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('vi-VN').format(new Date(value)) : 'Chưa cập nhật';
}

function isBanned(status: string) {
  return ['BANNED', 'DISABLED', 'SUSPENDED', 'INACTIVE'].includes(status.toUpperCase());
}

function formatRole(role: string) {
  return role === LAB_MANAGER ? 'Quản lý PTN' : role === STUDENT ? 'Sinh viên' : role;
}

function formatStatus(status: string) {
  return isBanned(status) ? 'Đã khóa' : status === 'ACTIVE' ? 'Đang hoạt động' : status;
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
      if (user.roles.includes('ADMIN') || user.status.toUpperCase() === 'PENDING_VERIFICATION') {
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
      window.alert('Vui lòng gỡ quản lý khỏi PTN trước khi đổi vai trò.');
      return;
    }
    void updateRolesMutation.mutateAsync({ userId, roles: [nextRole] });
  };

  const handleBanUser = (userId: number, roles: string[]) => {
    if (roles.includes(LAB_MANAGER) && managedLabByUserId.has(userId)) {
      window.alert('Quản lý đang phụ trách PTN, vui lòng gỡ khỏi PTN trước khi khóa tài khoản.');
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
        Không thể tải danh sách người dùng.
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">Người dùng</h2>
          <p className="mt-1 text-sm text-slate-400">
            Quản lý sinh viên, quản lý PTN và trạng thái tài khoản.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <input
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
            placeholder="Tìm theo tên hoặc email"
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
                {role ? formatRole(role) : 'Tất cả vai trò'}
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
                {status ? formatStatus(status) : 'Tất cả trạng thái'}
              </option>
            ))}
          </select>
        </div>
      </div>

      {filteredUsers.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-700 p-8 text-center text-sm text-slate-400">
          Không có người dùng phù hợp.
        </div>
      ) : (
        <div className="mt-6 max-w-full overscroll-x-contain overflow-x-auto">
          <table className="w-full min-w-[800px] divide-y divide-slate-800 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-400">
                <th className="px-3 py-3">Họ tên</th>
                <th className="px-3 py-3">Email</th>
                <th className="px-3 py-3">Vai trò</th>
                <th className="px-3 py-3">Trạng thái</th>
                <th className="px-3 py-3">PTN quản lý</th>
                <th className="px-3 py-3">Ngày tạo</th>
                <th className="px-3 py-3 text-right">Thao tác</th>
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
                      {user.fullName || user.username || 'Chưa cập nhật'}
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
                            {formatRole(role)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-3 py-4">
                      <span className="rounded-full bg-slate-800 px-2 py-1 text-xs font-semibold text-slate-200">
                        {formatStatus(user.status)}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-slate-300">
                      {user.roles.includes(LAB_MANAGER) ? managedLabByUserId.get(user.id) ?? 'Chưa gán' : '-'}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{formatDate(user.createdAt)}</td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
                        <Button
                          className="border-slate-700 bg-transparent text-slate-100 hover:bg-slate-800"
                          size="sm"
                          variant="outline"
                          disabled={disabled}
                          onClick={() => handleChangeRole(user.id, user.roles)}
                        >
                          Đổi vai trò
                        </Button>
                        {isBanned(user.status) ? (
                          <Button
                            className="bg-emerald-600 hover:bg-emerald-700"
                            size="sm"
                            disabled={disabled}
                            onClick={() => void unbanMutation.mutateAsync(user.id)}
                          >
                            Mở khóa
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={disabled}
                            onClick={() => handleBanUser(user.id, user.roles)}
                          >
                            Khóa tài khoản
                          </Button>
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
