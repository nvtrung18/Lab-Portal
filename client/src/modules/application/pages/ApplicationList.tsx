import { useState } from 'react';

import { resolveApiAssetUrl } from '../../../shared/api';
import { Badge, Button, ConfirmDialog, ErrorState } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import type { ApplicationStatus } from '../api';
import { useApplications, useReviewApplication } from '../hooks';

function statusBadgeVariant(status: ApplicationStatus): 'success' | 'danger' | 'warning' {
  if (status === 'APPROVED') {
    return 'success';
  }

  if (status === 'REJECTED') {
    return 'danger';
  }

  return 'warning';
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
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6 dark:border-slate-800 dark:bg-slate-900">
      <div className="flex flex-wrap items-center justify-between gap-2 sm:gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{t('application.title')}</h1>
        </div>
        <span className="shrink-0 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {applications.length} {t('application.count')}
        </span>
      </div>

      {applications.length === 0 ? (
        <div className="mt-6 rounded-lg border border-dashed border-slate-300 p-8 text-center text-sm text-slate-600 dark:border-slate-700 dark:text-slate-400">
          {t('application.empty')}
        </div>
      ) : (
        <>
        <div className="mt-6 space-y-3 md:hidden">
          {applications.map((application) => {
            const isProcessing = reviewMutation.isPending && processingId === application.id;
            return (
              <article className="min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-950/40" key={application.id}>
                <div className="flex items-start justify-between gap-2">
                  <h2 className="break-words font-semibold text-slate-900 dark:text-slate-100">
                    {application.applicantName || `${t('common.user')} #${application.userId}`}
                  </h2>
                  <Badge variant={statusBadgeVariant(application.status)}>
                    {formatStatus(application.status, locale)}
                  </Badge>
                </div>
                <p className="mt-1 break-all text-xs text-slate-500 dark:text-slate-400">{application.applicantEmail}</p>
                <dl className="mt-4 space-y-2 text-sm">
                  <div className="flex justify-between gap-2">
                    <dt className="text-slate-500 dark:text-slate-400">{t('lab.label')}:</dt>
                    <dd className="font-medium text-slate-900 dark:text-slate-200">{application.labName}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">{t('application.cv')}:</dt>
                    <dd className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
                      {application.cvUrl ? (
                        <a className="text-xs font-semibold text-teal-600 hover:underline dark:text-teal-400" href={application.cvUrl} rel="noreferrer" target="_blank">
                          {t('application.viewCvUrl')}
                        </a>
                      ) : null}
                      {application.cvFileUrl ? (
                        <a className="text-xs font-semibold text-teal-600 hover:underline dark:text-teal-400" href={resolveApiAssetUrl(application.cvFileUrl)} rel="noreferrer" target="_blank" title={application.cvFileName ?? t('application.cvFile')}>
                          {t('application.downloadCv')}
                        </a>
                      ) : null}
                      {!application.cvUrl && !application.cvFileUrl ? <span className="text-slate-400">—</span> : null}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
                    <dt>{t('application.createdAt')}:</dt>
                    <dd>{formatDate(application.createdAt, locale)}</dd>
                  </div>
                </dl>
                <div className="mt-4 flex flex-wrap gap-2">
                  {application.status === 'PENDING' ? (
                    <>
                      <Button
                        className="flex-1"
                        disabled={reviewMutation.isPending}
                        size="sm"
                        variant="primary"
                        onClick={() => handleReview(application.id, 'APPROVED')}
                      >
                        {isProcessing ? t('common.processing') : t('common.approve')}
                      </Button>
                      <Button
                        className="flex-1"
                        disabled={reviewMutation.isPending}
                        size="sm"
                        variant="danger"
                        onClick={() => handleReview(application.id, 'REJECTED')}
                      >
                        {t('common.reject')}
                      </Button>
                    </>
                  ) : <span className="text-xs text-slate-400">{t('common.processed')}</span>}
                </div>
              </article>
            );
          })}
        </div>
        <div className="mt-6 hidden max-w-full overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800 md:block">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm border-collapse">
              <thead className="border-b border-slate-200 bg-slate-50/75 text-xs font-semibold uppercase tracking-wider text-slate-600 dark:border-slate-800 dark:bg-slate-950/60 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3 min-w-[160px]">{t('application.applicant')}</th>
                  <th className="px-4 py-3 min-w-[180px]">{t('common.email')}</th>
                  <th className="px-4 py-3 min-w-[140px]">{t('lab.label')}</th>
                  <th className="px-4 py-3 min-w-[120px]">{t('application.cv')}</th>
                  <th className="px-4 py-3 min-w-[130px]">{t('application.status')}</th>
                  <th className="px-4 py-3 min-w-[130px]">{t('application.createdAt')}</th>
                  <th className="px-4 py-3 min-w-[160px] text-right">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/80">
                {applications.map((application) => {
                  const isProcessing =
                    reviewMutation.isPending && processingId === application.id;
                  const disableActions = reviewMutation.isPending;

                  return (
                    <tr className="transition-colors hover:bg-slate-50/60 dark:hover:bg-slate-800/40" key={application.id}>
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                        <div className="font-semibold text-slate-900 dark:text-slate-100">
                          {application.applicantName || `${t('common.user')} #${application.userId}`}
                        </div>
                      </td>
                      <td className="break-all px-4 py-3 text-xs text-slate-600 dark:text-slate-400">
                        {application.applicantEmail}
                      </td>
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                        {application.labName}
                      </td>
                      <td className="px-4 py-3">
                        {application.cvUrl || application.cvFileUrl ? (
                          <div className="flex flex-col gap-1">
                            {application.cvUrl ? (
                              <a
                                className="inline-flex items-center whitespace-nowrap text-xs font-semibold text-teal-600 hover:underline dark:text-teal-400"
                                href={application.cvUrl}
                                rel="noreferrer"
                                target="_blank"
                              >
                                {t('application.viewCvUrl')}
                              </a>
                            ) : null}
                            {application.cvFileUrl ? (
                              <a
                                className="inline-flex items-center whitespace-nowrap text-xs font-semibold text-teal-600 hover:underline dark:text-teal-400"
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
                      <td className="px-4 py-3">
                        <Badge variant={statusBadgeVariant(application.status)}>
                          {formatStatus(application.status, locale)}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-500 dark:text-slate-400">
                        {formatDate(application.createdAt, locale)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {application.status === 'PENDING' ? (
                          <div className="flex items-center justify-end gap-2">
                            <Button
                              disabled={disableActions}
                              size="sm"
                              variant="primary"
                              onClick={() => handleReview(application.id, 'APPROVED')}
                            >
                              {isProcessing ? t('common.processing') : t('common.approve')}
                            </Button>
                            <Button
                              disabled={disableActions}
                              size="sm"
                              variant="danger"
                              onClick={() => handleReview(application.id, 'REJECTED')}
                            >
                              {t('common.reject')}
                            </Button>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400 dark:text-slate-500">{t('common.processed')}</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
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
