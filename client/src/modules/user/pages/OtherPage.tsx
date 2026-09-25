import { useEffect, useMemo, useState } from 'react';

import { MyCleaningTasks, SlotList } from '../../booking/components';
import {
  getActiveMemberships,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { useI18n, type Locale, translate } from '../../../shared/i18n';
import { useLab } from '../../lab/hooks';
import { useCurrentUser } from '../hooks';

const ACTIVE_LAB_STORAGE_KEY = 'student.activeLabId';

function formatMembershipRole(role: string | undefined | null, locale: Locale) {
  return role === 'LEADER'
    ? translate('research.role.leader', locale)
    : role === 'LAB_MANAGER'
      ? translate('layout.managerRole', locale)
      : translate('layout.studentRole', locale);
}

export function OtherPage() {
  const { locale, t } = useI18n();
  const { data: currentUser, isLoading } = useCurrentUser();
  const activeMemberships = useMemo(
    () => getActiveMemberships(currentUser),
    [currentUser],
  );
  const activeMembershipLabIds = useMemo(
    () =>
      activeMemberships
        .map(getMembershipLabId)
        .filter((labId): labId is number => Boolean(labId)),
    [activeMemberships],
  );
  const [activeLabId, setActiveLabId] = useState<number | null>(null);

  useEffect(() => {
    if (!activeMembershipLabIds.length) {
      setActiveLabId(null);
      return;
    }

    const storedLabId = Number(localStorage.getItem(ACTIVE_LAB_STORAGE_KEY));
    const nextLabId = activeMembershipLabIds.includes(storedLabId)
      ? storedLabId
      : activeMembershipLabIds[0];

    setActiveLabId(nextLabId);
  }, [activeMembershipLabIds]);

  useEffect(() => {
    if (activeLabId) {
      localStorage.setItem(ACTIVE_LAB_STORAGE_KEY, String(activeLabId));
    }
  }, [activeLabId]);

  const activeMembership = activeMemberships.find(
    (membership) => getMembershipLabId(membership) === activeLabId,
  );
  const { data: activeLab } = useLab(activeLabId);

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-32 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-24 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  if (!activeMemberships.length) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
        {t('myLab.noMembership')}
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('myLab.title')}</h1>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-sm font-semibold text-slate-950 dark:text-white">{t('myLab.selectedLab')}</h3>
          </div>

          {activeMemberships.length > 1 ? (
            <select
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 sm:w-72"
              value={activeLabId ?? ''}
              onChange={(event) => setActiveLabId(Number(event.target.value))}
            >
              {activeMemberships.map((membership) => {
                const labId = getMembershipLabId(membership);
                return (
                  <option key={labId} value={labId ?? ''}>
                    {getMembershipLabName(membership)}
                  </option>
                );
              })}
            </select>
          ) : (
            <span className="rounded-md bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
              {activeMembership ? getMembershipLabName(activeMembership) : t('myLab.notSelected')}
            </span>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-950 dark:text-white">{t('myLab.info')}</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-slate-500">{t('myLab.name')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {activeLab?.labName ??
                (activeMembership ? getMembershipLabName(activeMembership) : t('common.notUpdated'))}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">{t('lab.manager')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {activeLab?.manager?.fullName || activeLab?.manager?.email || t('common.notUpdated')}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">{t('myLab.memberRole')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {formatMembershipRole(activeMembership?.role, locale)}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">{t('myLab.joinedAt')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {activeMembership?.joinedAt ?? activeMembership?.createdAt ?? t('common.notUpdated')}
            </dd>
          </div>
        </dl>
      </div>

      <div>
        <div className="mb-4">
          <h3 className="text-lg font-semibold text-slate-950 dark:text-white">{t('myLab.slots')}</h3>
        </div>
        <SlotList labId={activeLabId} mode="student" readonly />
      </div>

      <MyCleaningTasks />
    </section>
  );
}
