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
  if (role === 'LEADER') return translate('research.role.leader', locale);
  if (role === 'LAB_MANAGER') return translate('layout.managerRole', locale);
  if (role === 'DEPUTY') return translate('lab.role.deputy', locale);
  if (role === 'SECRETARY') return translate('lab.role.secretary', locale);
  if (role === 'MEMBER') return translate('lab.role.member', locale);
  if (role === 'STUDENT') return translate('layout.studentRole', locale);
  return role || translate('common.notUpdated', locale);
}

function formatJoinedAt(value: string | undefined | null, locale: Locale, fallback: string) {
  if (!value) return fallback;
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', { dateStyle: 'medium' }).format(date);
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
      <section className="app-surface-elevated rounded-lg border border-slate-200 p-4 shadow-sm sm:p-6" aria-label={t('common.loading')}>
        <div className="h-6 w-32 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-24 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  if (!activeMemberships.length) {
    return (
      <section className="app-surface-elevated rounded-xl border border-slate-200 p-4 text-sm text-slate-600 shadow-sm dark:border-slate-800 dark:text-slate-300 sm:p-6">
        {t('myLab.noMembership')}
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="app-surface-elevated rounded-xl border border-slate-200 p-4 shadow-sm dark:border-slate-800 sm:p-6">
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('myLab.title')}</h1>
      </div>

      <div className="app-surface-elevated rounded-xl border border-slate-200 p-4 shadow-sm dark:border-slate-800 sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-slate-950 dark:text-white">{t('myLab.selectedLab')}</h2>
          </div>

          {activeMemberships.length > 1 ? (
            <select
              aria-label={t('myLab.selectedLab')}
              className="w-full min-w-0 rounded-xl border border-slate-300 bg-white px-3 py-2 text-base text-slate-950 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white sm:w-72"
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
            <span className="min-w-0 break-words rounded-xl bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              {activeMembership ? getMembershipLabName(activeMembership) : t('myLab.notSelected')}
            </span>
          )}
        </div>
      </div>

      <div className="app-surface-elevated rounded-xl border border-slate-200 p-4 shadow-sm dark:border-slate-800 sm:p-5">
        <h2 className="text-sm font-semibold text-slate-950 dark:text-white">{t('myLab.info')}</h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-slate-500 dark:text-slate-400">{t('myLab.name')}</dt>
            <dd className="mt-1 break-words text-sm font-medium text-slate-950 dark:text-slate-100">
              {activeLab?.labName ??
                (activeMembership ? getMembershipLabName(activeMembership) : t('common.notUpdated'))}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500 dark:text-slate-400">{t('lab.manager')}</dt>
            <dd className="mt-1 break-words text-sm font-medium text-slate-950 dark:text-slate-100">
              {activeLab?.manager?.fullName || activeLab?.manager?.email || t('common.notUpdated')}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500 dark:text-slate-400">{t('myLab.memberRole')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {formatMembershipRole(activeMembership?.role, locale)}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500 dark:text-slate-400">{t('myLab.joinedAt')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950 dark:text-slate-100">
              {formatJoinedAt(activeMembership?.joinedAt ?? activeMembership?.createdAt, locale, t('common.notUpdated'))}
            </dd>
          </div>
        </dl>
      </div>

      <div>
        <div className="mb-4">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-white">{t('myLab.slots')}</h2>
        </div>
        <SlotList labId={activeLabId} mode="student" readonly />
      </div>

      <MyCleaningTasks />
    </section>
  );
}
