import { useMemo, useState } from 'react';

import { getStoredUser } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, Modal } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useAdminLabs, useAdminUsers, useBanUser, useUnbanUser, useUpdateUserRoles, useAssignableLabs } from '../hooks';
import type { LabResponse } from '../../lab/api';
import type { AssignableLab } from '../api';

const roleOptions = ['', STUDENT, LAB_MANAGER];
const statusOptions = ['', 'ACTIVE', 'BANNED', 'DISABLED'];

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
  const upper = status.toUpperCase();
  if (upper === 'ACTIVE') return 'Đang hoạt động';
  if (upper === 'DISABLED') return 'Bị vô hiệu hóa';
  if (isBanned(status)) return 'Đã khóa';
  return status;
}

function statusBadgeClass(status: string) {
  const upper = status.toUpperCase();
  if (upper === 'ACTIVE') return 'bg-emerald-950 text-emerald-300 ring-1 ring-emerald-800';
  if (upper === 'DISABLED') return 'bg-amber-950 text-amber-300 ring-1 ring-amber-800';
  if (isBanned(status)) return 'bg-red-950 text-red-300 ring-1 ring-red-800';
  return 'bg-slate-800 text-slate-300 ring-1 ring-slate-700';
}

type ConfirmAction =
  | { type: 'ban'; userId: number; userName: string }
  | { type: 'unban'; userId: number; userName: string }
  | { type: 'changeRole'; userId: number; userName: string; newRole: string };

export function AdminUsersPage() {
  const { data: users = [], isLoading, isError, refetch } = useAdminUsers();
  const { data: labs = [] } = useAdminLabs();
  const updateRolesMutation = useUpdateUserRoles();
  const banMutation = useBanUser();
  const unbanMutation = useUnbanUser();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [unassignedFilter, setUnassignedFilter] = useState('');
  const [roleDrafts, setRoleDrafts] = useState<Record<number, string>>({});
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [assignState, setAssignState] = useState<{
    userId: number;
    userName: string;
    userEmail: string;
    currentRoles: string[];
  } | null>(null);

  const currentUser = getStoredUser();

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
      const matchesUnassigned =
        !unassignedFilter ||
        (unassignedFilter === 'UNASSIGNED' &&
          user.roles.includes(LAB_MANAGER) &&
          !user.managedLabId);

      return matchesSearch && matchesRole && matchesStatus && matchesUnassigned;
    });
  }, [roleFilter, search, statusFilter, unassignedFilter, users]);

  const handleChangeRole = (userId: number, userName: string, currentRoles: string[]) => {
    const nextRole = roleDrafts[userId] ?? currentRoles[0] ?? STUDENT;

    if (nextRole === LAB_MANAGER) {
      const u = users.find((user) => user.id === userId);
      setAssignState({
        userId,
        userName,
        userEmail: u?.email || '',
        currentRoles,
      });
      return;
    }

    setConfirmAction({ type: 'changeRole', userId, userName, newRole: nextRole });
  };

  const handleBanUser = (userId: number, userName: string, roles: string[]) => {
    if (currentUser?.id === userId) {
      return;
    }
    if (roles.includes(LAB_MANAGER) && managedLabByUserId.has(userId)) {
      setConfirmAction(null);
      return;
    }
    setConfirmAction({ type: 'ban', userId, userName });
  };

  const handleUnbanUser = (userId: number, userName: string) => {
    setConfirmAction({ type: 'unban', userId, userName });
  };

  const executeConfirmedAction = async () => {
    if (!confirmAction) return;

    try {
      if (confirmAction.type === 'ban') {
        await banMutation.mutateAsync(confirmAction.userId);
      } else if (confirmAction.type === 'unban') {
        await unbanMutation.mutateAsync(confirmAction.userId);
      } else if (confirmAction.type === 'changeRole') {
        await updateRolesMutation.mutateAsync({
          userId: confirmAction.userId,
          role: confirmAction.newRole,
        });
      }
    } finally {
      setConfirmAction(null);
    }
  };

  const handleAssignLabManagerConfirm = async (labId: number) => {
    if (!assignState) return;
    try {
      await updateRolesMutation.mutateAsync({
        userId: assignState.userId,
        role: LAB_MANAGER,
        labId: labId,
      });
    } finally {
      setAssignState(null);
    }
  };

  const isMutating =
    updateRolesMutation.isPending || banMutation.isPending || unbanMutation.isPending;

  const getConfirmModalContent = () => {
    if (!confirmAction) return { title: '', message: '', danger: false };
    switch (confirmAction.type) {
      case 'ban':
        return {
          title: 'Xác nhận khóa tài khoản',
          message: `Bạn có chắc muốn khóa tài khoản "${confirmAction.userName}"? Người dùng sẽ không thể đăng nhập sau khi bị khóa.`,
          danger: true,
        };
      case 'unban':
        return {
          title: 'Xác nhận mở khóa tài khoản',
          message: `Bạn có chắc muốn mở khóa tài khoản "${confirmAction.userName}"?`,
          danger: false,
        };
      case 'changeRole':
        const managesLab = managedLabByUserId.get(confirmAction.userId);
        if (confirmAction.newRole === STUDENT && managesLab) {
          return {
            title: 'Xác nhận chuyển về sinh viên',
            message: `Người dùng này đang quản lý PTN ${managesLab}. Nếu chuyển về sinh viên, PTN này sẽ tạm thời chưa có quản lý.`,
            danger: true,
            confirmText: 'Xác nhận chuyển vai trò',
          };
        }
        const warningSuffix = (confirmAction.newRole !== LAB_MANAGER && managesLab)
          ? `\n\nLưu ý: Người dùng này đang quản lý PTN "${managesLab}". Khi đổi vai trò về ${formatRole(confirmAction.newRole)}, hệ thống sẽ tự động gỡ gán quyền quản lý khỏi PTN này.`
          : '';
        return {
          title: 'Xác nhận đổi vai trò',
          message: `Bạn có chắc muốn đổi vai trò của "${confirmAction.userName}" thành ${formatRole(confirmAction.newRole)}?${warningSuffix}`,
          danger: confirmAction.newRole !== LAB_MANAGER && !!managesLab,
        };
    }
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <LoadingState className="text-slate-300" />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  const confirmContent = getConfirmModalContent();

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">Người dùng</h2>
          <p className="mt-1 text-sm text-slate-400">
            Quản lý sinh viên, quản lý PTN và trạng thái tài khoản.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-4">
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
              <option key={role || 'all'} value={role} className="bg-slate-900 text-slate-100">
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
              <option key={status || 'all'} value={status} className="bg-slate-900 text-slate-100">
                {status ? formatStatus(status) : 'Tất cả trạng thái'}
              </option>
            ))}
          </select>
          <select
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
            value={unassignedFilter}
            onChange={(event) => setUnassignedFilter(event.target.value)}
          >
            <option value="" className="bg-slate-900 text-slate-100">Tất cả gán PTN</option>
            <option value="UNASSIGNED" className="bg-slate-900 text-slate-100">Chưa gán PTN</option>
          </select>
        </div>
      </div>

      {filteredUsers.length === 0 ? (
        <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300" />
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
                const disabled = isMutating;
                const isSelf = currentUser?.id === user.id;
                const userDisplayName = user.fullName || user.username || 'Chưa cập nhật';

                return (
                  <tr key={user.id} className="transition-colors hover:bg-slate-950/30">
                    <td className="px-3 py-4 text-slate-100">
                      {userDisplayName}
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
                          <option key={role} value={role} className="bg-slate-900 text-slate-100">
                            {formatRole(role)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-3 py-4">
                      <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusBadgeClass(user.status)}`}>
                        {formatStatus(user.status)}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-slate-300">
                      {user.roles.includes(LAB_MANAGER) ? (
                        (user.managedLabName ?? managedLabByUserId.get(user.id)) ? (
                          user.managedLabName ?? managedLabByUserId.get(user.id)
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-amber-950 px-2 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
                            Chưa gán PTN
                          </span>
                        )
                      ) : (
                        '-'
                      )}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{formatDate(user.createdAt)}</td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={disabled}
                          onClick={() => handleChangeRole(user.id, userDisplayName, user.roles)}
                        >
                          Đổi vai trò
                        </Button>
                        {isBanned(user.status) ? (
                          <Button
                            size="sm"
                            variant="success"
                            disabled={disabled}
                            onClick={() => handleUnbanUser(user.id, userDisplayName)}
                          >
                            Mở khóa
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={disabled || isSelf}
                            title={isSelf ? 'Không thể tự khóa tài khoản của mình' : undefined}
                            onClick={() => handleBanUser(user.id, userDisplayName, user.roles)}
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

      {confirmAction ? (
        <ConfirmModal
          danger={confirmContent.danger}
          isLoading={isMutating}
          message={confirmContent.message}
          title={confirmContent.title}
          confirmText={confirmContent.confirmText}
          onClose={() => setConfirmAction(null)}
          onConfirm={() => void executeConfirmedAction()}
        />
      ) : null}

      {assignState ? (
        <RoleAssignmentModal
          userId={assignState.userId}
          userName={assignState.userName}
          userEmail={assignState.userEmail}
          isLoading={isMutating}
          onClose={() => setAssignState(null)}
          onConfirm={(labId) => void handleAssignLabManagerConfirm(labId)}
        />
      ) : null}
    </section>
  );
}

interface ConfirmModalProps {
  danger: boolean;
  isLoading: boolean;
  message: string;
  title: string;
  confirmText?: string;
  onClose: () => void;
  onConfirm: () => void;
}

function ConfirmModal({ danger, isLoading, message, title, confirmText, onClose, onConfirm }: ConfirmModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="w-full max-w-md rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-white">{title}</h3>
        <p className="mt-3 text-sm text-slate-300 whitespace-pre-line">{message}</p>
        <div className="mt-6 flex justify-end gap-3">
          <Button
            size="sm"
            variant="outline"
            disabled={isLoading}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            size="sm"
            variant={danger ? 'danger' : 'primary'}
            disabled={isLoading}
            loading={isLoading}
            loadingText="Đang xử lý..."
            onClick={onConfirm}
          >
            {confirmText || 'Xác nhận'}
          </Button>
        </div>
      </div>
    </div>
  );
}

interface RoleAssignmentModalProps {
  userId: number;
  userName: string;
  userEmail: string;
  isLoading: boolean;
  onClose: () => void;
  onConfirm: (labId: number) => void;
}

function RoleAssignmentModal({
  userId,
  userName,
  userEmail,
  isLoading,
  onClose,
  onConfirm,
}: RoleAssignmentModalProps) {
  const { data: assignableLabs = [], isLoading: isLoadingLabs } = useAssignableLabs();
  const [selectedLabId, setSelectedLabId] = useState<string>('');

  const handleConfirm = () => {
    if (!selectedLabId) return;
    onConfirm(Number(selectedLabId));
  };

  const isConfirmDisabled = !selectedLabId || isLoading || isLoadingLabs;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="w-full max-w-lg rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-white">Cấp quyền quản lý PTN</h3>
        
        <div className="mt-4 space-y-2 text-sm text-slate-300">
          <div>
            <span className="text-slate-400">Người dùng:</span>{' '}
            <strong className="text-slate-100">{userName}</strong> ({userEmail})
          </div>
          <div>
            <span className="text-slate-400">Vai trò mới:</span>{' '}
            <span className="inline-flex items-center rounded-md bg-amber-950 px-2.5 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
              Quản lý PTN
            </span>
          </div>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-300" htmlFor="assignLabSelect">
            Chọn Phòng Thí Nghiệm quản lý
          </label>
          
          {isLoadingLabs ? (
            <div className="mt-2 py-4 text-center text-sm text-slate-400">
              Đang tải danh sách PTN...
            </div>
          ) : assignableLabs.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-700 bg-slate-950/50 p-4 text-center">
              <p className="text-sm text-slate-400 font-medium">Không có PTN nào có thể gán quản lý.</p>
              <p className="mt-1.5 text-xs text-slate-500">
                Vui lòng tạo PTN mới hoặc bỏ gán manager khỏi PTN hiện có.
              </p>
            </div>
          ) : (
            <select
              id="assignLabSelect"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={selectedLabId}
              disabled={isLoading}
              onChange={(e) => setSelectedLabId(e.target.value)}
            >
              <option value="">-- Chọn PTN --</option>
              {assignableLabs.map((lab) => (
                <option key={lab.id} value={lab.id}>
                  {lab.name} {lab.department ? `(${lab.department})` : ''}
                </option>
              ))}
            </select>
          )}
        </div>

        <p className="mt-4 text-xs text-slate-400">
          * User này sẽ có quyền quản lý PTN được chọn.
        </p>

        <div className="mt-6 flex justify-end gap-3">
          <Button
            size="sm"
            variant="outline"
            disabled={isLoading}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            size="sm"
            variant="primary"
            disabled={isConfirmDisabled}
            loading={isLoading}
            loadingText="Đang cấp quyền..."
            onClick={handleConfirm}
          >
            Xác nhận cấp quyền
          </Button>
        </div>
      </div>
    </div>
  );
}
