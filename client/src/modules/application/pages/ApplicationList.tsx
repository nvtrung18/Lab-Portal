import { useState } from 'react';

import { resolveApiAssetUrl } from '../../../shared/api';
import { ConfirmDialog } from '../../../shared/components';
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
  const { data: applications = [], isLoading, isError } = useApplications(managedLabId);
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
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        {t('application.loadFailed')}
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between gap-4">
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
        <div className="mt-6 max-w-full overscroll-x-contain overflow-x-auto">
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
                    <td className="px-3 py-4 text-slate-700">
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
                              className="whitespace-nowrap font-medium text-slate-950 underline-offset-2 hover:underline"
                              href={application.cvUrl}
                              rel="noreferrer"
                              target="_blank"
                            >
                              {t('application.viewCvUrl')}
                            </a>
                          ) : null}
                          {application.cvFileUrl ? (
                            <a
                              className="whitespace-nowrap font-medium text-slate-950 underline-offset-2 hover:underline"
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
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            disabled={disableActions}
                            className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300"
                            onClick={() => handleReview(application.id, 'APPROVED')}
                          >
                            {isProcessing ? t('common.processing') : t('common.approve')}
                          </button>
                          <button
                            type="button"
                            disabled={disableActions}
                            className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
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
