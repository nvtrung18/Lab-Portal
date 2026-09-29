import { useMemo, useState } from 'react';

import { getStoredUser } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n, type Locale } from '../../../shared/i18n';
import { useAdminLabs, useAdminUsers, useBanUser, useUnbanUser, useUpdateUserRoles, useAssignableLabs } from '../hooks';

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
  return role === LAB_MANAGER ? t('layout.managerRole')
    : role === STUDENT ? t('layout.studentRole')
      : role;
}

function formatStatus(status: string, t: (key: string, fallback?: string) => string) {
  const upper = status.toUpperCase();
  if (upper === 'ACTIVE') return t('adminUsers.active');
  if (upper === 'DISABLED') return t('adminUsers.disabled');
  if (isBanned(status)) return t('adminUsers.banned');
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

  const confirmContent = getLocalizedConfirmModalContent(confirmAction, managedLabByUserId, t);

  return (
    <section className="min-w-0 rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-sm sm:p-6">
      <div className="flex flex-col gap-4 2xl:flex-row 2xl:items-center 2xl:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">{t('adminUsers.title')}</h2>
        </div>
        <div className="grid min-w-0 gap-3 sm:grid-cols-2 2xl:w-full 2xl:max-w-4xl 2xl:grid-cols-4">
          <input
            aria-label={t('adminUsers.searchPlaceholder')}
            className="min-h-11 min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-base text-white outline-none placeholder:text-slate-500 focus:border-white sm:text-sm"
            placeholder={t('adminUsers.searchPlaceholder')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <select
            aria-label={t('adminUsers.tableRole')}
            className="min-h-11 min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-base text-white outline-none focus:border-white sm:text-sm"
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value)}
          >
            {roleOptions.map((role) => (
              <option key={role || 'all'} value={role} className="bg-slate-900 text-slate-100">
                {role ? formatRole(role, t) : t('adminUsers.allRoles')}
              </option>
            ))}
          </select>
          <select
            aria-label={t('adminUsers.tableStatus')}
            className="min-h-11 min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-base text-white outline-none focus:border-white sm:text-sm"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            {statusOptions.map((status) => (
              <option key={status || 'all'} value={status} className="bg-slate-900 text-slate-100">
                {status ? formatStatus(status, t) : t('adminUsers.allStatuses')}
              </option>
            ))}
          </select>
          <select
            aria-label={t('adminUsers.tableLab')}
            className="min-h-11 min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-base text-white outline-none focus:border-white sm:text-sm"
            value={unassignedFilter}
            onChange={(event) => setUnassignedFilter(event.target.value)}
          >
            <option value="" className="bg-slate-900 text-slate-100">{t('adminUsers.allAssignments')}</option>
            <option value="UNASSIGNED" className="bg-slate-900 text-slate-100">{t('adminUsers.unassigned')}</option>
          </select>
        </div>
      </div>

      {filteredUsers.length === 0 ? (
        <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300" />
      ) : (
        <>
        <div className="mt-6 space-y-3 md:hidden">
          {filteredUsers.map((user) => {
            const disabled = isMutating;
            const isSelf = currentUser?.id === user.id;
            const userDisplayName = user.fullName || user.username || t('common.notUpdated');
            const managedLab = user.managedLabName ?? managedLabByUserId.get(user.id);

            return (
              <article className="min-w-0 rounded-lg border border-slate-700 bg-slate-950/30 p-4" key={user.id}>
                <h3 className="break-words font-semibold text-slate-100">{userDisplayName}</h3>
                <p className="mt-1 break-all text-sm text-slate-300">{user.email}</p>
                <dl className="mt-4 grid gap-x-3 gap-y-3 text-sm sm:grid-cols-2">
                  <div className="min-w-0">
                    <dt className="text-slate-400">{t('adminUsers.tableRole')}</dt>
                    <dd className="mt-1">
                      <select
                        aria-label={`${t('adminUsers.changeRole')}: ${userDisplayName}`}
                        className="min-h-11 w-full min-w-0 rounded-md border border-slate-700 bg-slate-950 px-2 text-base text-white outline-none focus:border-white"
                        disabled={disabled}
                        value={roleDrafts[user.id] ?? user.roles[0] ?? STUDENT}
                        onChange={(event) => setRoleDrafts((drafts) => ({ ...drafts, [user.id]: event.target.value }))}
                      >
                        {[STUDENT, LAB_MANAGER].map((role) => <option key={role} value={role}>{formatRole(role, t)}</option>)}
                      </select>
                    </dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-slate-400">{t('adminUsers.tableStatus')}</dt>
                    <dd className="mt-1 break-words font-semibold text-slate-100">{formatStatus(user.status, t)}</dd>
                  </div>
                  <div className="min-w-0 sm:col-span-2">
                    <dt className="text-slate-400">{t('adminUsers.tableLab')}</dt>
                    <dd className="mt-1 break-words text-slate-100">{user.roles.includes(LAB_MANAGER) ? managedLab || t('adminUsers.unassigned') : '—'}</dd>
                  </div>
                  <div className="min-w-0 sm:col-span-2">
                    <dt className="text-slate-400">{t('adminUsers.tableCreated')}</dt>
                    <dd className="mt-1 text-slate-100">{formatDate(user.createdAt, locale, t)}</dd>
                  </div>
                </dl>
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  <Button className="w-full" size="sm" variant="outline" disabled={disabled} onClick={() => handleChangeRole(user.id, userDisplayName, user.roles)}>
                    {t('adminUsers.changeRole')}
                  </Button>
                  {isBanned(user.status) ? (
                    <Button className="w-full" size="sm" variant="success" disabled={disabled} onClick={() => handleUnbanUser(user.id, userDisplayName)}>
                      {t('adminUsers.unlock')}
                    </Button>
                  ) : (
                    <Button className="w-full" size="sm" variant="danger" disabled={disabled || isSelf} title={isSelf ? t('adminUsers.selfLockDenied') : undefined} onClick={() => handleBanUser(user.id, userDisplayName, user.roles)}>
                      {t('adminUsers.lock')}
                    </Button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
        <div aria-label={t('adminUsers.title')} className="mt-6 hidden max-w-full overscroll-x-contain overflow-x-auto md:block" role="region" tabIndex={0}>
          <table className="w-full min-w-[800px] divide-y divide-slate-800 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-400">
                <th className="px-3 py-3">{t('adminUsers.tableName')}</th>
                <th className="px-3 py-3">{t('adminUsers.tableEmail')}</th>
                <th className="px-3 py-3">{t('adminUsers.tableRole')}</th>
                <th className="px-3 py-3">{t('adminUsers.tableStatus')}</th>
                <th className="px-3 py-3">{t('adminUsers.tableLab')}</th>
                <th className="px-3 py-3">{t('adminUsers.tableCreated')}</th>
                <th className="px-3 py-3 text-right">{t('adminUsers.tableActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {filteredUsers.map((user) => {
                const disabled = isMutating;
                const isSelf = currentUser?.id === user.id;
                const userDisplayName = user.fullName || user.username || t('common.notUpdated');

                return (
                  <tr key={user.id} className="transition-colors hover:bg-slate-950/30">
                    <td className="px-3 py-4 text-slate-100">
                      {userDisplayName}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{user.email}</td>
                    <td className="px-3 py-4">
                      <select
                        aria-label={`${t('adminUsers.changeRole')}: ${userDisplayName}`}
                        className="min-h-11 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-white outline-none focus:border-white"
                        value={roleDrafts[user.id] ?? user.roles[0] ?? STUDENT}
                        disabled={disabled}
                        onChange={(event) =>
                          setRoleDrafts((drafts) => ({ ...drafts, [user.id]: event.target.value }))
                        }
                      >
                        {[STUDENT, LAB_MANAGER].map((role) => (
                          <option key={role} value={role} className="bg-slate-900 text-slate-100">
                            {formatRole(role, t)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-3 py-4">
                      <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusBadgeClass(user.status)}`}>
                        {formatStatus(user.status, t)}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-slate-300">
                      {user.roles.includes(LAB_MANAGER) ? (
                        (user.managedLabName ?? managedLabByUserId.get(user.id)) ? (
                          user.managedLabName ?? managedLabByUserId.get(user.id)
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-amber-950 px-2 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
                            {t('adminUsers.unassigned')}
                          </span>
                        )
                      ) : (
                        '-'
                      )}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{formatDate(user.createdAt, locale, t)}</td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
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
    </section>
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
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-md overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <h3 className="text-lg font-semibold text-white">{title}</h3>
        <p className="mt-3 text-sm text-slate-300 whitespace-pre-line">{message}</p>
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
      </div>
    </div>
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
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-lg overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <h3 className="text-lg font-semibold text-white">{t('adminUsers.assignManager')}</h3>
        
        <div className="mt-4 min-w-0 space-y-2 break-words text-sm text-slate-300">
          <div>
            <span className="text-slate-400">{t('adminUsers.user')}</span>{' '}
            <strong className="text-slate-100">{userName}</strong> ({userEmail})
          </div>
          <div>
            <span className="text-slate-400">{t('adminUsers.newRole')}</span>{' '}
            <span className="inline-flex items-center rounded-md bg-amber-950 px-2.5 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
              {t('layout.managerRole')}
            </span>
          </div>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-300" htmlFor="assignLabSelect">
            {t('adminUsers.chooseLab')}
          </label>
          
          {isLoadingLabs ? (
            <div className="mt-2 py-4 text-center text-sm text-slate-400">
              {t('adminUsers.loadingLabs')}
            </div>
          ) : assignableLabs.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-700 bg-slate-950/50 p-4 text-center">
              <p className="text-sm text-slate-400 font-medium">{t('adminUsers.noAssignableLabs')}</p>
            </div>
          ) : (
            <select
              id="assignLabSelect"
              className="mt-2 min-h-11 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-base text-white outline-none focus:border-white sm:text-sm"
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
      </div>
    </div>
  );
}
