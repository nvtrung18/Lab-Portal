import { useMemo, useState } from 'react';
import { Search, X } from 'lucide-react';

import { getStoredUser } from '../../../shared/api';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  Modal,
  type BadgeVariant,
} from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n, type Locale } from '../../../shared/i18n';
import {
  useAdminLabs,
  useAdminUsers,
  useAssignableLabs,
  useBanUser,
  useUnbanUser,
  useUpdateUserRoles,
} from '../hooks';

const roleOptions = ['', STUDENT, LAB_MANAGER];
const statusOptions = ['', 'ACTIVE', 'BANNED', 'DISABLED'];

function formatDate(value: string | undefined, locale: Locale, t: (key: string, fallback?: string) => string) {
  return value
    ? new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(new Date(value))
    : t('common.notUpdated');
}

function isBanned(status: string) {
  return ['BANNED', 'DISABLED', 'SUSPENDED', 'INACTIVE'].includes(status.toUpperCase());
}

function formatRole(role: string, t: (key: string, fallback?: string) => string) {
  return role === LAB_MANAGER
    ? t('layout.managerRole')
    : role === STUDENT
      ? t('layout.studentRole')
      : role;
}

function formatStatus(status: string, t: (key: string, fallback?: string) => string) {
  const upper = status.toUpperCase();
  if (upper === 'ACTIVE') return t('adminUsers.active');
  if (upper === 'DISABLED') return t('adminUsers.disabled');
  if (isBanned(status)) return t('adminUsers.banned');
  return status;
}

function statusBadgeVariant(status: string): BadgeVariant {
  const upper = status.toUpperCase();
  if (upper === 'ACTIVE') return 'success';
  if (upper === 'DISABLED') return 'warning';
  if (isBanned(status)) return 'danger';
  return 'neutral';
}

type ConfirmAction =
  | { type: 'ban'; userId: number; userName: string }
  | { type: 'unban'; userId: number; userName: string }
  | { type: 'changeRole'; userId: number; userName: string; newRole: string };

export function AdminUsersPage() {
  const { locale, t } = useI18n();
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

  if (isLoading) {
    return (
      <section className="rounded-xl border border-slate-200/90 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <LoadingState className="text-slate-600 dark:text-slate-300" />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-xl border border-slate-200/90 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  const confirmContent = getLocalizedConfirmModalContent(confirmAction, managedLabByUserId, t);
  const hasActiveFilters = Boolean(search || roleFilter || statusFilter || unassignedFilter);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
              {t('adminUsers.title')}
            </h1>
            <span className="inline-flex shrink-0 items-center rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-semibold text-teal-800 ring-1 ring-inset ring-teal-600/20 dark:bg-teal-950/60 dark:text-teal-300 dark:ring-teal-500/30">
              {filteredUsers.length} {locale === 'vi' ? 'người dùng' : 'users'}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {locale === 'vi'
              ? 'Quản lý tài khoản, vai trò và phân công phòng thí nghiệm cho người dùng hệ thống'
              : 'Manage accounts, roles, and laboratory assignments for system users'}
          </p>
        </div>
      </div>

      {/* Filter Toolbar Card */}
      <div className="rounded-xl border border-slate-200/90 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          {/* Search Input */}
          <div className="relative flex-1 min-w-[240px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
            <input
              aria-label={t('adminUsers.searchPlaceholder')}
              className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/50 pl-9 pr-8 text-sm text-slate-900 placeholder:text-slate-400 transition-all focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              placeholder={t('adminUsers.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>

          {/* Filters Row */}
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3 lg:flex lg:items-center">
            <select
              aria-label={t('adminUsers.tableRole')}
              className="h-10 min-w-[150px] rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-xs sm:text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              value={roleFilter}
              onChange={(event) => setRoleFilter(event.target.value)}
            >
              {roleOptions.map((role) => (
                <option key={role || 'all'} value={role}>
                  {role ? formatRole(role, t) : t('adminUsers.allRoles')}
                </option>
              ))}
            </select>

            <select
              aria-label={t('adminUsers.tableStatus')}
              className="h-10 min-w-[150px] rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-xs sm:text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              {statusOptions.map((status) => (
                <option key={status || 'all'} value={status}>
                  {status ? formatStatus(status, t) : t('adminUsers.allStatuses')}
                </option>
              ))}
            </select>

            <select
              aria-label={t('adminUsers.tableLab')}
              className="h-10 min-w-[170px] rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-xs sm:text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              value={unassignedFilter}
              onChange={(event) => setUnassignedFilter(event.target.value)}
            >
              <option value="">{t('adminUsers.allAssignments')}</option>
              <option value="UNASSIGNED">{t('adminUsers.unassigned')}</option>
            </select>

            {hasActiveFilters && (
              <Button
                size="sm"
                variant="ghost"
                className="h-10 text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-100"
                onClick={() => {
                  setSearch('');
                  setRoleFilter('');
                  setStatusFilter('');
                  setUnassignedFilter('');
                }}
              >
                {locale === 'vi' ? 'Đặt lại' : 'Reset'}
              </Button>
            )}
          </div>
        </div>
      </div>

      {filteredUsers.length === 0 ? (
        <EmptyState className="rounded-xl border border-dashed border-slate-300 bg-white dark:border-slate-800 dark:bg-slate-900/60" />
      ) : (
        <>
          {/* Mobile Card List (< md) */}
          <div className="space-y-3 md:hidden">
            {filteredUsers.map((user) => {
              const disabled = isMutating;
              const isSelf = currentUser?.id === user.id;
              const userDisplayName = user.fullName || user.username || t('common.notUpdated');
              const managedLab = user.managedLabName ?? managedLabByUserId.get(user.id);

              return (
                <article
                  key={user.id}
                  className="rounded-xl border border-slate-200/90 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-slate-800 dark:bg-slate-900/60"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200 ring-1 ring-slate-200 dark:ring-slate-700">
                        {userDisplayName.charAt(0).toUpperCase()}
                      </span>
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-slate-900 dark:text-slate-100">{userDisplayName}</h3>
                        <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user.email}</p>
                      </div>
                    </div>
                    <Badge variant={statusBadgeVariant(user.status)}>
                      {formatStatus(user.status, t)}
                    </Badge>
                  </div>

                  <dl className="mt-3.5 grid grid-cols-2 gap-3 border-t border-slate-100 pt-3 text-xs dark:border-slate-800/80">
                    <div>
                      <dt className="text-slate-400 dark:text-slate-500">{t('adminUsers.tableRole')}</dt>
                      <dd className="mt-1">
                        <select
                          aria-label={`${t('adminUsers.changeRole')}: ${userDisplayName}`}
                          className="h-8 w-full rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-800 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                          disabled={disabled}
                          value={roleDrafts[user.id] ?? user.roles[0] ?? STUDENT}
                          onChange={(event) =>
                            setRoleDrafts((drafts) => ({ ...drafts, [user.id]: event.target.value }))
                          }
                        >
                          {[STUDENT, LAB_MANAGER].map((role) => (
                            <option key={role} value={role}>
                              {formatRole(role, t)}
                            </option>
                          ))}
                        </select>
                      </dd>
                    </div>
                    <div>
                      <dt className="text-slate-400 dark:text-slate-500">{t('adminUsers.tableLab')}</dt>
                      <dd className="mt-1 font-medium text-slate-700 dark:text-slate-300 truncate">
                        {user.roles.includes(LAB_MANAGER) ? managedLab || t('adminUsers.unassigned') : '—'}
                      </dd>
                    </div>
                    <div className="col-span-2">
                      <dt className="text-slate-400 dark:text-slate-500">{t('adminUsers.tableCreated')}</dt>
                      <dd className="mt-0.5 text-slate-600 dark:text-slate-400">{formatDate(user.createdAt, locale, t)}</dd>
                    </div>
                  </dl>

                  <div className="mt-3.5 grid grid-cols-2 gap-2 border-t border-slate-100 pt-3 dark:border-slate-800/80">
                    <Button
                      size="sm"
                      variant="outline"
                      className="w-full justify-center"
                      disabled={disabled}
                      onClick={() => handleChangeRole(user.id, userDisplayName, user.roles)}
                    >
                      {t('adminUsers.changeRole')}
                    </Button>
                    {isBanned(user.status) ? (
                      <Button
                        size="sm"
                        variant="success"
                        className="w-full justify-center"
                        disabled={disabled}
                        onClick={() => handleUnbanUser(user.id, userDisplayName)}
                      >
                        {t('adminUsers.unlock')}
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="danger"
                        className="w-full justify-center"
                        disabled={disabled || isSelf}
                        title={isSelf ? t('adminUsers.selfLockDenied') : undefined}
                        onClick={() => handleBanUser(user.id, userDisplayName, user.roles)}
                      >
                        {t('adminUsers.lock')}
                      </Button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>

          {/* Desktop Table Container (>= md) */}
          <div
            aria-label={t('adminUsers.title')}
            className="app-table-frame hidden min-w-0 max-w-full overflow-x-auto rounded-xl border border-slate-200/90 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900/60 md:block"
            role="region"
            tabIndex={0}
          >
            <table className="w-full min-w-[960px] text-left text-sm border-collapse">
              <thead className="border-b border-slate-200/90 bg-slate-50/80 text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3.5 min-w-[200px] font-semibold">{t('adminUsers.tableName')}</th>
                  <th className="px-4 py-3.5 min-w-[220px] font-semibold">{t('adminUsers.tableEmail')}</th>
                  <th className="px-4 py-3.5 min-w-[170px] font-semibold">{t('adminUsers.tableRole')}</th>
                  <th className="px-4 py-3.5 min-w-[150px] font-semibold">{t('adminUsers.tableStatus')}</th>
                  <th className="px-4 py-3.5 min-w-[190px] font-semibold">{t('adminUsers.tableLab')}</th>
                  <th className="px-4 py-3.5 min-w-[120px] font-semibold">{t('adminUsers.tableCreated')}</th>
                  <th className="px-4 py-3.5 min-w-[220px] text-right font-semibold">{t('adminUsers.tableActions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70">
                {filteredUsers.map((user) => {
                  const disabled = isMutating;
                  const isSelf = currentUser?.id === user.id;
                  const userDisplayName = user.fullName || user.username || t('common.notUpdated');
                  const managedLab = user.managedLabName ?? managedLabByUserId.get(user.id);

                  return (
                    <tr
                      key={user.id}
                      className="transition-colors hover:bg-slate-50/80 dark:hover:bg-slate-800/40"
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-3">
                          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200 ring-1 ring-slate-200 dark:ring-slate-700">
                            {userDisplayName.charAt(0).toUpperCase()}
                          </span>
                          <span className="font-semibold text-slate-900 dark:text-slate-100 truncate max-w-[180px]" title={userDisplayName}>
                            {userDisplayName}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300 font-mono text-xs sm:text-sm">
                        <span className="truncate block max-w-[220px]" title={user.email}>{user.email}</span>
                      </td>
                      <td className="px-4 py-3.5">
                        <select
                          aria-label={`${t('adminUsers.changeRole')}: ${userDisplayName}`}
                          className="h-8.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-800 transition focus:border-teal-500 focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-teal-400"
                          value={roleDrafts[user.id] ?? user.roles[0] ?? STUDENT}
                          disabled={disabled}
                          onChange={(event) =>
                            setRoleDrafts((drafts) => ({ ...drafts, [user.id]: event.target.value }))
                          }
                        >
                          {[STUDENT, LAB_MANAGER].map((role) => (
                            <option key={role} value={role}>
                              {formatRole(role, t)}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-4 py-3.5">
                        <Badge variant={statusBadgeVariant(user.status)}>
                          {formatStatus(user.status, t)}
                        </Badge>
                      </td>
                      <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300">
                        {user.roles.includes(LAB_MANAGER) ? (
                          managedLab ? (
                            <span className="font-medium text-slate-900 dark:text-slate-100 truncate block max-w-[180px]" title={managedLab}>
                              {managedLab}
                            </span>
                          ) : (
                            <Badge variant="warning" dot={false}>
                              {t('adminUsers.unassigned')}
                            </Badge>
                          )
                        ) : (
                          <span className="text-slate-400 dark:text-slate-600">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {formatDate(user.createdAt, locale, t)}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <div className="inline-flex items-center justify-end gap-2 whitespace-nowrap">
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={disabled}
                            onClick={() => handleChangeRole(user.id, userDisplayName, user.roles)}
                          >
                            {t('adminUsers.changeRole')}
                          </Button>
                          {isBanned(user.status) ? (
                            <Button
                              size="sm"
                              variant="success"
                              disabled={disabled}
                              onClick={() => handleUnbanUser(user.id, userDisplayName)}
                            >
                              {t('adminUsers.unlock')}
                            </Button>
                          ) : (
                            <Button
                              size="sm"
                              variant="danger"
                              disabled={disabled || isSelf}
                              title={isSelf ? t('adminUsers.selfLockDenied') : undefined}
                              onClick={() => handleBanUser(user.id, userDisplayName, user.roles)}
                            >
                              {t('adminUsers.lock')}
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
        </>
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
          userName={assignState.userName}
          userEmail={assignState.userEmail}
          isLoading={isMutating}
          onClose={() => setAssignState(null)}
          onConfirm={(labId) => void handleAssignLabManagerConfirm(labId)}
        />
      ) : null}
    </div>
  );
}

function getLocalizedConfirmModalContent(
  confirmAction: ConfirmAction | null,
  managedLabByUserId: Map<number, string>,
  t: ReturnType<typeof useI18n>['t'],
) {
  if (!confirmAction) return { title: '', message: '', danger: false };

  const fill = (key: string, params: Record<string, string>) =>
    t(key).replace(/\{(\w+)\}/g, (_, name: string) => params[name] ?? `{${name}}`);

  switch (confirmAction.type) {
    case 'ban':
      return {
        title: t('adminUsers.confirmLockTitle'),
        message: fill('adminUsers.confirmLockMessage', { user: confirmAction.userName }),
        danger: true,
      };
    case 'unban':
      return {
        title: t('adminUsers.confirmUnlockTitle'),
        message: fill('adminUsers.confirmUnlockMessage', { user: confirmAction.userName }),
        danger: false,
      };
    case 'changeRole': {
      const managesLab = managedLabByUserId.get(confirmAction.userId);
      if (confirmAction.newRole === STUDENT && managesLab) {
        return {
          title: t('adminUsers.confirmStudentTitle'),
          message: fill('adminUsers.confirmStudentMessage', { lab: managesLab }),
          danger: true,
          confirmText: t('adminUsers.confirmRole'),
        };
      }
      const roleLabel = formatRole(confirmAction.newRole, t);
      const warningSuffix = confirmAction.newRole !== LAB_MANAGER && managesLab
        ? `\n\n${fill('adminUsers.confirmRoleWarning', { lab: managesLab, role: roleLabel })}`
        : '';
      return {
        title: t('adminUsers.confirmRoleTitle'),
        message: `${fill('adminUsers.confirmRoleMessage', { user: confirmAction.userName, role: roleLabel })}${warningSuffix}`,
        danger: confirmAction.newRole !== LAB_MANAGER && !!managesLab,
      };
    }
  }
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
  const { t } = useI18n();

  return (
    <Modal title={title} size="sm" closeDisabled={isLoading} onClose={onClose}>
      <p className="mt-3 text-sm text-slate-700 dark:text-slate-300 whitespace-pre-line">{message}</p>
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button
          size="sm"
          variant="outline"
          disabled={isLoading}
          onClick={onClose}
        >
          {t('common.cancel')}
        </Button>
        <Button
          size="sm"
          variant={danger ? 'danger' : 'primary'}
          disabled={isLoading}
          loading={isLoading}
          loadingText={t('common.loading')}
          onClick={onConfirm}
        >
          {confirmText || t('common.confirm')}
        </Button>
      </div>
    </Modal>
  );
}

interface RoleAssignmentModalProps {
  userName: string;
  userEmail: string;
  isLoading: boolean;
  onClose: () => void;
  onConfirm: (labId: number) => void;
}

function RoleAssignmentModal({
  userName,
  userEmail,
  isLoading,
  onClose,
  onConfirm,
}: RoleAssignmentModalProps) {
  const { t } = useI18n();
  const { data: assignableLabs = [], isLoading: isLoadingLabs } = useAssignableLabs();
  const [selectedLabId, setSelectedLabId] = useState<string>('');

  const handleConfirm = () => {
    if (!selectedLabId) return;
    onConfirm(Number(selectedLabId));
  };

  const isConfirmDisabled = !selectedLabId || isLoading || isLoadingLabs;

  return (
    <Modal title={t('adminUsers.assignManager')} size="md" closeDisabled={isLoading} onClose={onClose}>
      <div className="mt-4 min-w-0 space-y-2 break-words text-sm text-slate-700 dark:text-slate-300">
        <div>
          <span className="text-slate-500 dark:text-slate-400">{t('adminUsers.user')}</span>{' '}
          <strong className="text-slate-900 dark:text-slate-100">{userName}</strong> ({userEmail})
        </div>
        <div>
          <span className="text-slate-500 dark:text-slate-400">{t('adminUsers.newRole')}</span>{' '}
          <span className="inline-flex items-center rounded-md bg-amber-50 dark:bg-amber-950 px-2.5 py-0.5 text-xs font-medium text-amber-800 dark:text-amber-300 ring-1 ring-inset ring-amber-200 dark:ring-amber-800">
            {t('layout.managerRole')}
          </span>
        </div>
      </div>

      <div className="mt-6">
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300" htmlFor="assignLabSelect">
          {t('adminUsers.chooseLab')}
        </label>

        {isLoadingLabs ? (
          <div className="mt-2 py-4 text-center text-sm text-slate-500 dark:text-slate-400">
            {t('adminUsers.loadingLabs')}
          </div>
        ) : assignableLabs.length === 0 ? (
          <div className="mt-2 rounded-md border border-dashed border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950/50 p-4 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">{t('adminUsers.noAssignableLabs')}</p>
          </div>
        ) : (
          <select
            id="assignLabSelect"
            className="mt-2 min-h-10 w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-teal-500 sm:text-sm"
            value={selectedLabId}
            disabled={isLoading}
            onChange={(e) => setSelectedLabId(e.target.value)}
          >
            <option value="">{t('adminUsers.chooseLabOption')}</option>
            {assignableLabs.map((lab) => (
              <option key={lab.id} value={lab.id}>
                {lab.name} {lab.department ? `(${lab.department})` : ''}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button
          size="sm"
          variant="outline"
          disabled={isLoading}
          onClick={onClose}
        >
          {t('common.cancel')}
        </Button>
        <Button
          size="sm"
          variant="primary"
          disabled={isConfirmDisabled}
          loading={isLoading}
          loadingText={t('adminUsers.assignLoading')}
          onClick={handleConfirm}
        >
          {t('adminUsers.assignConfirm')}
        </Button>
      </div>
    </Modal>
  );
}
