import { Search } from 'lucide-react';
import { useMemo, useState } from 'react';

import { getActiveMemberships, getMembershipLabId } from '../../../shared/utils/membership';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { useUserApplications } from '../../application/hooks';
import { useCurrentUser } from '../../user/hooks';
import { ApplyModal } from '../components';
import type { LabResponse } from '../api';
import { useLabs } from '../hooks';
import { isLabActive } from '../utils/labStatus';

function statusClassName(status: LabResponse['status']) {
  if (status === 'AVAILABLE') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950/60 dark:text-emerald-200 dark:ring-emerald-900';
  }

  if (status === 'MAINTENANCE') {
    return 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/60 dark:text-amber-200 dark:ring-amber-900';
  }

  return 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:ring-slate-700';
}

function formatLabStatus(status: LabResponse['status'], locale: Locale) {
  return status === 'AVAILABLE' || status === 'ACTIVE'
    ? translate('lab.status.active', locale)
    : status === 'MAINTENANCE'
      ? translate('lab.status.maintenance', locale)
      : translate('lab.status.inactive', locale);
}

function getApplyState(lab: LabResponse, applicationStatus: string | undefined, locale: Locale) {
  const status = applicationStatus ?? lab.applicationStatus;

  if (status === 'PENDING') {
    return { disabled: true, label: translate('lab.application.pending', locale) };
  }

  if (status === 'APPROVED') {
    return { disabled: false, label: translate('lab.application.resubmit', locale) };
  }

  if (status === 'REJECTED') {
    return { disabled: false, label: translate('lab.application.resubmit', locale) };
  }

  if (!isLabActive(lab)) {
    return { disabled: true, label: translate('lab.application.closed', locale) };
  }

  return { disabled: false, label: translate('lab.application.apply', locale) };
}

export function LabList() {
  const { locale, t } = useI18n();
  const { data: labs = [], isLoading, isError } = useLabs();
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const { data: userApplications = [], isLoading: isLoadingApplications } =
    useUserApplications(currentUser?.id);
  const [selectedLabId, setSelectedLabId] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  const activeMembershipLabIds = useMemo(() => {
    return new Set(
      getActiveMemberships(currentUser)
        .map(getMembershipLabId)
        .filter((labId): labId is number => Boolean(labId)),
    );
  }, [currentUser]);

  const applicationsByLabId = useMemo(() => {
    const latestApplications = new Map<number, string>();
    const sortedApplications = [...userApplications].sort(
      (first, second) =>
        new Date(second.createdAt ?? second.updatedAt).getTime() -
        new Date(first.createdAt ?? first.updatedAt).getTime(),
    );

    sortedApplications.forEach((application) => {
      if (!latestApplications.has(application.labId)) {
        latestApplications.set(application.labId, application.status);
      }
    });

    return latestApplications;
  }, [userApplications]);

  const availableLabs = useMemo(() => {
    return labs.filter((lab) => {
      return isLabActive(lab) && !activeMembershipLabIds.has(lab.id);
    });
  }, [activeMembershipLabIds, labs]);

  const labsForApply = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLocaleLowerCase(locale);
    if (!normalizedSearch) return availableLabs;
    return availableLabs.filter((lab) => [lab.labName, lab.department, lab.location]
      .some((value) => value?.toLocaleLowerCase(locale).includes(normalizedSearch)));
  }, [availableLabs, locale, searchTerm]);

  const selectedLab = useMemo(
    () => availableLabs.find((lab) => lab.id === selectedLabId),
    [availableLabs, selectedLabId],
  );

  if (isLoading || isLoadingUser || isLoadingApplications) {
    return (
      <section className="space-y-4">
        <div className="h-7 w-28 animate-pulse rounded bg-slate-200" />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <div
              key={index}
              className="h-48 animate-pulse rounded-lg border border-slate-200 bg-white"
            />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        {t('lab.list.loadFailed')}
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-7xl">
      <div className="mb-5 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
        <h1 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">{t('lab.list.title')}</h1>
        </div>
          <label className="relative block w-full lg:max-w-sm" htmlFor="lab-search">
            <span className="sr-only">{t('lab.list.searchLabel')}</span>
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 bg-white pl-10 pr-3 text-base text-slate-950 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              id="lab-search"
              placeholder={t('lab.list.searchPlaceholder')}
              type="search"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
          </label>
        </div>
        <p className="mt-4 text-sm tabular-nums text-slate-500 dark:text-slate-400" aria-live="polite">
          {t('lab.list.count', undefined, { shown: labsForApply.length, total: availableLabs.length })}
        </p>
      </div>

      {labsForApply.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          {searchTerm.trim() ? t('lab.list.noSearchResults') : t('lab.list.noAvailableLabs')}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {labsForApply.map((lab) => {
            const applyState = getApplyState(lab, applicationsByLabId.get(lab.id), locale);

            return (
              <article
                key={lab.id}
                className="flex h-full flex-col rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 transition-shadow hover:shadow-md dark:bg-slate-900 dark:ring-slate-800"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="break-words text-base font-semibold text-slate-950 dark:text-white">
                      {lab.labName}
                    </h3>
                    <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
                      {lab.department || t('lab.notAssignedDepartment')}
                    </p>
                  </div>
                  <span
                    className={[
                      'rounded-full px-2 py-1 text-xs font-semibold ring-1',
                      statusClassName(lab.status),
                    ].join(' ')}
                  >
                    {formatLabStatus(lab.status, locale)}
                  </span>
                </div>

                {lab.description ? <p className="mt-4 line-clamp-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{lab.description}</p> : null}

                <dl className="mt-4 grid flex-1 grid-cols-1 min-[380px]:grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-slate-500">{t('lab.manager')}</dt>
                    <dd className="break-words font-medium text-slate-950 dark:text-white">
                      {lab.manager?.fullName || lab.manager?.email || t('lab.notAssigned')}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">{t('lab.capacity')}</dt>
                    <dd className="font-medium text-slate-950 dark:text-white">
                      {lab.capacity ?? t('common.notUpdated')}
                    </dd>
                  </div>
                  <div className="min-[380px]:col-span-2">
                    <dt className="text-slate-500">{t('lab.location')}</dt>
                    <dd className="break-words font-medium text-slate-950 dark:text-white">
                      {lab.location || t('common.notUpdated')}
                    </dd>
                  </div>
                </dl>

                <button
                  type="button"
                  className="app-button app-button-primary mt-5 min-h-11 w-full rounded-md px-4 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={applyState.disabled}
                  onClick={() => setSelectedLabId(lab.id)}
                >
                  {applyState.label}
                </button>
              </article>
            );
          })}
        </div>
      )}

      <ApplyModal
        labId={selectedLabId}
        labName={selectedLab?.labName}
        labStatus={selectedLab?.status}
        onClose={() => setSelectedLabId(null)}
      />
    </section>
  );
}
