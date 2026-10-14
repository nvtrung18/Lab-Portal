import { useMemo, useState } from 'react';

import { Button, ConfirmDialog, EmptyState, ErrorState, LoadingState, Modal, ResponsiveTable } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import type { LabMemberRole } from '../api';
import { useLabMembers, useRemoveLabMember, useUpdateLabMemberRole } from '../hooks';

const memberRoles: LabMemberRole[] = ['MEMBER', 'DEPUTY', 'SECRETARY'];

function isLabMemberRole(role: string): role is LabMemberRole {
  return role === 'MEMBER' || role === 'DEPUTY' || role === 'SECRETARY';
}

function statusClassName(status: string) {
  if (status.toUpperCase() === 'ACTIVE') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-950/60 dark:text-emerald-300 dark:ring-emerald-500/30';
  }

  return 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700';
}

function formatDate(value: string | undefined, locale: Locale) {
  return value ? new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', { dateStyle: 'short' }).format(new Date(value)) : translate('common.notUpdated', locale);
}

function formatRole(role: string | undefined, locale: Locale) {
  if (role === 'MEMBER') return translate('lab.role.member', locale);
  if (role === 'DEPUTY') return translate('lab.role.deputy', locale);
  if (role === 'SECRETARY') return translate('lab.role.secretary', locale);
  if (role === 'LAB_MANAGER') return translate('layout.managerRole', locale);
  if (role === 'STUDENT') return translate('layout.studentRole', locale);
  return role || translate('common.notUpdated', locale);
}

function formatStatus(status: string, locale: Locale) {
  return status.toUpperCase() === 'ACTIVE' ? translate('lab.status.active', locale) : translate('lab.memberInactive', locale);
}

export function LabMembersPage() {
  const { locale, t } = useI18n();
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: members = [], isLoading, isError } = useLabMembers(managedLabId);
  const removeMemberMutation = useRemoveLabMember();
  const updateRoleMutation = useUpdateLabMemberRole();
  const [search, setSearch] = useState('');
  const [roleDrafts, setRoleDrafts] = useState<Record<number, LabMemberRole>>({});
  const [memberPendingRemove, setMemberPendingRemove] = useState<(typeof members)[number] | null>(null);
  const [selectedMemberDetail, setSelectedMemberDetail] = useState<(typeof members)[number] | null>(null);

  const handleRemoveMember = (member: (typeof members)[number]) => {
    if (!managedLabId) {
      return;
    }
    setMemberPendingRemove(member);
  };

  const confirmRemoveMember = () => {
    if (!managedLabId || !memberPendingRemove) return;
    removeMemberMutation.mutate(
      { labId: managedLabId, userId: memberPendingRemove.userId },
      { onSuccess: () => setMemberPendingRemove(null) },
    );
  };

  const saveMemberRole = (userId: number, currentRole: string) => {
    const role = roleDrafts[userId];
    if (!managedLabId || !role || role === currentRole) return;

    updateRoleMutation.mutate({ labId: managedLabId, userId, role }, {
      onSuccess: () => setRoleDrafts((drafts) => {
        const next = { ...drafts };
        delete next[userId];
        return next;
      }),
    });
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
        <LoadingState />
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        {t('layout.unassignedLab')}
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 shadow-sm">
        <ErrorState />
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('lab.membersTitle')}</h1>
        </div>
        <input
          aria-label={t('lab.membersSearch')}
          className="min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 sm:w-72"
          placeholder={t('lab.membersSearch')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {filteredMembers.length === 0 ? (
        <EmptyState className="mt-6">{t('lab.membersEmpty')}</EmptyState>
      ) : (
        <>
        <div className="mt-6 space-y-3 md:hidden">
          {filteredMembers.map((member) => {
            const roleDraft = roleDrafts[member.userId] ?? (isLabMemberRole(member.role) ? member.role : '');
            return (
              <article className="min-w-0 rounded-lg border border-slate-200 p-4" key={member.id}>
                <h2 className="break-words font-semibold text-slate-950">{member.fullName || t('common.notUpdated')}</h2>
                <p className="mt-1 break-all text-sm text-slate-600">{member.email}</p>
                <dl className="mt-4 space-y-3 text-sm">
                  <div>
                    <dt className="text-slate-500">{t('lab.changeMemberRole')}</dt>
                    <dd className="mt-1">
                      {member.canReceiveLabRole ? (
                        <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                          <select
                            aria-label={`${t('lab.changeMemberRole')}: ${member.fullName || member.email}`}
                            className="min-h-11 w-full min-w-0 rounded-md border border-slate-300 bg-white px-2 text-base text-slate-950 focus:border-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10"
                            disabled={updateRoleMutation.isPending}
                            value={roleDraft}
                            onChange={(event) => {
                              const nextRole = event.target.value;
                              if (isLabMemberRole(nextRole)) setRoleDrafts((drafts) => ({ ...drafts, [member.userId]: nextRole }));
                            }}
                          >
                            {!isLabMemberRole(member.role) ? <option value="" disabled>{formatRole(member.role, locale)}</option> : null}
                            {memberRoles.map((role) => <option key={role} value={role}>{formatRole(role, locale)}</option>)}
                          </select>
                          <Button className="w-full" disabled={updateRoleMutation.isPending || !roleDraft || roleDraft === member.role} loading={updateRoleMutation.isPending && updateRoleMutation.variables?.userId === member.userId} loadingText={t('common.saving')} size="sm" variant="outline" onClick={() => saveMemberRole(member.userId, member.role)}>{t('common.save')}</Button>
                        </div>
                      ) : <span className="text-slate-900">{formatRole(member.role, locale)}</span>}
                    </dd>
                  </div>
                  <div><dt className="text-slate-500">{t('slot.statusLabel')}</dt><dd className="mt-1"><span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ring-1 ${statusClassName(member.status)}`}>{formatStatus(member.status, locale)}</span></dd></div>
                  <div><dt className="text-slate-500">{t('myLab.joinedAt')}</dt><dd className="mt-1 text-slate-900">{formatDate(member.joinedAt, locale)}</dd></div>
                </dl>
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  <Button className="w-full" size="sm" variant="outline" onClick={() => setSelectedMemberDetail(member)}>{t('common.viewDetails')}</Button>
                  <Button className="w-full" disabled={updateRoleMutation.isPending} loading={removeMemberMutation.isPending} loadingText={t('lab.removingMember')} size="sm" variant="danger" onClick={() => handleRemoveMember(member)}>{t('lab.removeMember')}</Button>
                </div>
              </article>
            );
          })}
        </div>
        <ResponsiveTable className="mt-6 hidden md:block">
          <table className="w-full min-w-[840px] divide-y divide-slate-200 text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50/80 text-left text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
                <th className="px-3 py-3">{t('profile.fullName')}</th>
                <th className="px-3 py-3">{t('common.email')}</th>
                <th className="px-3 py-3">{t('lab.changeMemberRole')}</th>
                <th className="px-3 py-3">{t('slot.statusLabel')}</th>
                <th className="px-3 py-3">{t('myLab.joinedAt')}</th>
                <th className="px-3 py-3 text-right">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredMembers.map((member) => {
                const roleDraft = roleDrafts[member.userId] ?? (isLabMemberRole(member.role) ? member.role : '');
                return <tr key={member.id}>
                  <td className="px-3 py-4 font-medium text-slate-950">
                    {member.fullName || t('common.notUpdated')}
                  </td>
                  <td className="break-all px-3 py-4 text-slate-700">{member.email}</td>
                  <td className="px-3 py-4 text-slate-700">
                    {member.canReceiveLabRole ? (
                      <div className="flex flex-wrap items-center gap-2">
                        <select
                          aria-label={`${t('lab.changeMemberRole')}: ${member.fullName || member.email}`}
                          className="min-h-11 min-w-32 rounded-md border border-slate-300 bg-white px-2 text-base text-slate-950 focus:border-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10"
                          disabled={updateRoleMutation.isPending}
                          value={roleDraft}
                          onChange={(event) => {
                            const nextRole = event.target.value;
                            if (isLabMemberRole(nextRole)) {
                              setRoleDrafts((drafts) => ({ ...drafts, [member.userId]: nextRole }));
                            }
                          }}
                        >
                          {!isLabMemberRole(member.role) ? (
                            <option value="" disabled>{formatRole(member.role, locale)}</option>
                          ) : null}
                          {memberRoles.map((role) => (
                            <option key={role} value={role}>{formatRole(role, locale)}</option>
                          ))}
                        </select>
                        <Button
                          aria-label={`${t('common.save')}: ${member.fullName || member.email}`}
                          disabled={updateRoleMutation.isPending || !roleDraft || roleDraft === member.role}
                          loading={updateRoleMutation.isPending && updateRoleMutation.variables?.userId === member.userId}
                          loadingText={t('common.saving')}
                          size="sm"
                          variant="outline"
                          onClick={() => saveMemberRole(member.userId, member.role)}
                        >
                          {t('common.save')}
                        </Button>
                      </div>
                    ) : formatRole(member.role, locale)}
                  </td>
                  <td className="px-3 py-4">
                    <span
                      className={[
                        'inline-flex shrink-0 items-center whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1',
                        statusClassName(member.status),
                      ].join(' ')}
                    >
                      {formatStatus(member.status, locale)}
                    </span>
                  </td>
                  <td className="px-3 py-4 text-slate-700">{formatDate(member.joinedAt, locale)}</td>
                  <td className="px-3 py-4 text-right">
                    <div className="flex justify-end gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setSelectedMemberDetail(member)}
                    >
                      {t('common.viewDetails')}
                    </Button>
                    <Button
                      disabled={updateRoleMutation.isPending}
                      loading={removeMemberMutation.isPending}
                      loadingText={t('lab.removingMember')}
                      size="sm"
                      variant="danger"
                      onClick={() => handleRemoveMember(member)}
                    >
                      {t('lab.removeMember')}
                    </Button>
                    </div>
                  </td>
                </tr>;
              })}
            </tbody>
          </table>
        </ResponsiveTable>
        </>
      )}
      <ConfirmDialog
        confirmLabel={t('lab.removeMember')}
        isOpen={memberPendingRemove !== null}
        isPending={removeMemberMutation.isPending}
        message={t('lab.removeMemberDescription')}
        onCancel={() => setMemberPendingRemove(null)}
        onConfirm={confirmRemoveMember}
        title={t('lab.removeMemberTitle')}
      />
      <Modal
        centered
        closeOnEscape
        isOpen={selectedMemberDetail !== null}
        onClose={() => setSelectedMemberDetail(null)}
        size="sm"
        title={t('lab.memberDetails')}
      >
        <dl className="space-y-3 text-sm">
          <div><dt className="text-slate-500">{t('profile.fullName')}</dt><dd className="font-semibold text-slate-950 dark:text-slate-100">{selectedMemberDetail?.fullName || t('common.notUpdated')}</dd></div>
          <div><dt className="text-slate-500">{t('common.email')}</dt><dd className="break-all text-slate-800">{selectedMemberDetail?.email}</dd></div>
        </dl>
      </Modal>
    </section>
  );
}
