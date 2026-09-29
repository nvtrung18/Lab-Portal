import { useState } from 'react';

import { resolveApiAssetUrl } from '../../../shared/api';
import { ConfirmDialog, ErrorState } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import type { ApplicationStatus } from '../api';
import { useApplications, useReviewApplication } from '../hooks';

function statusClassName(status: ApplicationStatus) {
  if (status === 'APPROVED') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }

  if (status === 'REJECTED') {
    return 'bg-red-50 text-red-700 ring-red-200';
  }

  return 'bg-amber-50 text-amber-700 ring-amber-200';
}

function formatStatus(status: ApplicationStatus, locale: Locale) {
  return status === 'APPROVED'
    ? translate('application.status.approved', locale)
    : status === 'REJECTED'
      ? translate('application.status.rejected', locale)
      : translate('application.status.pending', locale);
}

function formatDate(value: string, locale: Locale) {
  if (!value) {
    return translate('common.notUpdated', locale);
  }

  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function ApplicationList() {
  const { locale, t } = useI18n();
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const { data: applications = [], isLoading, isError, refetch } = useApplications(managedLabId);
  const reviewMutation = useReviewApplication(managedLabId);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [pendingReview, setPendingReview] = useState<{
    appId: number;
    status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>;
  } | null>(null);

  const handleReview = (
    appId: number,
    status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>,
  ) => {
    setPendingReview({ appId, status });
  };

  const confirmReview = async () => {
    if (!pendingReview) return;
    setProcessingId(pendingReview.appId);
    try {
      await reviewMutation.mutateAsync(pendingReview);
      setPendingReview(null);
    } finally {
      setProcessingId(null);
    }
  };

  if (isLoadingUser || isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-48 animate-pulse rounded bg-slate-200" />
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
        {t('application.unassignedLab')}
      </section>
    );
  }

  if (isError) {
    return (
      <ErrorState onRetry={() => void refetch()}>{t('application.loadFailed')}</ErrorState>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2 sm:gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('application.title')}</h1>
        </div>
        <span className="shrink-0 text-sm text-slate-500">
          {applications.length} {t('application.count')}
        </span>
      </div>

      {applications.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-300 p-8 text-center text-sm text-slate-600">
          {t('application.empty')}
        </div>
      ) : (
        <>
        <div className="mt-6 space-y-3 md:hidden">
          {applications.map((application) => {
            const isProcessing = reviewMutation.isPending && processingId === application.id;
            return (
              <article className="min-w-0 rounded-lg border border-slate-200 p-4" key={application.id}>
                <h2 className="break-words font-semibold text-slate-950">
                  {application.applicantName || `${t('common.user')} #${application.userId}`}
                </h2>
                <p className="mt-1 break-all text-sm text-slate-600">{application.applicantEmail}</p>
                <dl className="mt-4 space-y-3 text-sm">
                  <div><dt className="text-slate-500">{t('lab.label')}</dt><dd className="mt-1 break-words text-slate-900">{application.labName}</dd></div>
                  <div>
                    <dt className="text-slate-500">{t('application.cv')}</dt>
                    <dd className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
                      {application.cvUrl ? <a className="inline-flex min-h-11 items-center font-medium underline underline-offset-2" href={application.cvUrl} rel="noreferrer" target="_blank">{t('application.viewCvUrl')}</a> : null}
                      {application.cvFileUrl ? <a className="inline-flex min-h-11 items-center font-medium underline underline-offset-2" href={resolveApiAssetUrl(application.cvFileUrl)} rel="noreferrer" target="_blank" title={application.cvFileName ?? t('application.cvFile')}>{t('application.downloadCv')}</a> : null}
                      {!application.cvUrl && !application.cvFileUrl ? <span className="text-slate-400">—</span> : null}
                    </dd>
                  </div>
                  <div><dt className="text-slate-500">{t('application.status')}</dt><dd className="mt-1"><span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ring-1 ${statusClassName(application.status)}`}>{formatStatus(application.status, locale)}</span></dd></div>
                  <div><dt className="text-slate-500">{t('application.createdAt')}</dt><dd className="mt-1 text-slate-900">{formatDate(application.createdAt, locale)}</dd></div>
                </dl>
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  {application.status === 'PENDING' ? (
                    <>
                      <button className="min-h-11 rounded-md bg-emerald-600 px-3 py-2 text-sm font-semibold text-white disabled:bg-emerald-300" disabled={reviewMutation.isPending} type="button" onClick={() => handleReview(application.id, 'APPROVED')}>{isProcessing ? t('common.processing') : t('common.approve')}</button>
                      <button className="min-h-11 rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white disabled:bg-red-300" disabled={reviewMutation.isPending} type="button" onClick={() => handleReview(application.id, 'REJECTED')}>{t('common.reject')}</button>
                    </>
                  ) : <span className="text-xs text-slate-500">{t('common.processed')}</span>}
                </div>
              </article>
            );
          })}
        </div>
        <div className="mt-6 hidden max-w-full overscroll-x-contain overflow-x-auto md:block">
          <table className="w-full min-w-[760px] divide-y divide-slate-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-500">
                <th className="px-3 py-3">{t('application.applicant')}</th>
                <th className="px-3 py-3">{t('common.email')}</th>
                <th className="px-3 py-3">{t('lab.label')}</th>
                <th className="px-3 py-3">{t('application.cv')}</th>
                <th className="px-3 py-3">{t('application.status')}</th>
                <th className="px-3 py-3">{t('application.createdAt')}</th>
                <th className="px-3 py-3 text-right">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {applications.map((application) => {
                const isProcessing =
                  reviewMutation.isPending && processingId === application.id;
                const disableActions = reviewMutation.isPending;

                return (
                  <tr key={application.id}>
                    <td className="px-3 py-4 text-slate-700">
                      <div className="font-medium text-slate-950">
                        {application.applicantName || `${t('common.user')} #${application.userId}`}
                      </div>
                    </td>
                    <td className="break-all px-3 py-4 text-slate-700">
                      {application.applicantEmail}
                    </td>
                    <td className="px-3 py-4 text-slate-700">
                      {application.labName}
                    </td>
                    <td className="px-3 py-4">
                      {application.cvUrl || application.cvFileUrl ? (
                        <div className="flex flex-col gap-1">
                          {application.cvUrl ? (
                            <a
                              className="inline-flex min-h-11 items-center whitespace-nowrap font-medium text-slate-950 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500"
                              href={application.cvUrl}
                              rel="noreferrer"
                              target="_blank"
                            >
                              {t('application.viewCvUrl')}
                            </a>
                          ) : null}
                          {application.cvFileUrl ? (
                            <a
                              className="inline-flex min-h-11 items-center whitespace-nowrap font-medium text-slate-950 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500"
                              href={resolveApiAssetUrl(application.cvFileUrl)}
                              rel="noreferrer"
                              target="_blank"
                              title={application.cvFileName ?? t('application.cvFile')}
                            >
                              {t('application.downloadCv')}
                            </a>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="px-3 py-4">
                      <span
                        className={[
                          'inline-flex rounded-full px-2 py-1 text-xs font-semibold ring-1',
                          statusClassName(application.status),
                        ].join(' ')}
                      >
                        {formatStatus(application.status, locale)}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-slate-700">
                      {formatDate(application.createdAt, locale)}
                    </td>
                    <td className="px-3 py-4 text-right">
                      {application.status === 'PENDING' ? (
                        <div className="flex flex-wrap justify-end gap-2">
                          <button
                            type="button"
                            disabled={disableActions}
                            className="min-h-11 rounded-md bg-emerald-600 px-3 py-2 text-sm font-semibold text-white transition hover:bg-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 disabled:cursor-not-allowed disabled:bg-emerald-300"
                            onClick={() => handleReview(application.id, 'APPROVED')}
                          >
                            {isProcessing ? t('common.processing') : t('common.approve')}
                          </button>
                          <button
                            type="button"
                            disabled={disableActions}
                            className="min-h-11 rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white transition hover:bg-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:cursor-not-allowed disabled:bg-red-300"
                            onClick={() => handleReview(application.id, 'REJECTED')}
                          >
                            {t('common.reject')}
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">{t('common.processed')}</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        </>
      )}
      <ConfirmDialog
        confirmLabel={pendingReview?.status === 'APPROVED' ? t('application.approve') : t('application.reject')}
        isOpen={pendingReview !== null}
        isPending={reviewMutation.isPending}
        message={pendingReview?.status === 'APPROVED'
          ? t('application.confirmApprove')
          : t('application.confirmReject')}
        onCancel={() => setPendingReview(null)}
        onConfirm={() => { void confirmReview(); }}
        title={pendingReview?.status === 'APPROVED' ? t('application.confirmApproveTitle') : t('application.confirmRejectTitle')}
        variant={pendingReview?.status === 'APPROVED' ? 'success' : 'danger'}
      />
    </section>
  );
}
