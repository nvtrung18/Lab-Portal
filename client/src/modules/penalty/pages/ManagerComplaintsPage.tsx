import { useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState } from '../../../shared/components';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { ComplaintReviewModal } from '../components';
import { useManagerComplaints, useReviewComplaint } from '../hooks';
import type { Complaint } from '../types';
import {
  formatComplaintStatus,
  formatDateTime,
  formatPenaltyType,
  getComplaintStatusClass,
} from '../utils';
import { useCurrentUser } from '../../user/hooks';
import { useI18n } from '../../../shared/i18n';

export function ManagerComplaintsPage() {
  const { locale, t } = useI18n();
  const filters = [
    { value: 'ALL', label: t('common.all') },
    { value: 'PENDING', label: t('penalty.complaint.pending') },
    { value: 'APPROVED', label: t('penalty.complaint.approved') },
    { value: 'REJECTED', label: t('penalty.complaint.rejected') },
  ];
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [reviewState, setReviewState] = useState<{
    complaint: Complaint;
    decision: 'APPROVE' | 'REJECT';
  } | null>(null);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const { data: complaints = [], isLoading, isError, refetch } = useManagerComplaints(managedLabId);
  const reviewComplaint = useReviewComplaint(managedLabId);

  const filteredComplaints = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return complaints.filter((complaint) => {
      const matchesStatus = statusFilter === 'ALL' || complaint.status === statusFilter;
      const matchesSearch =
        !keyword ||
        [complaint.studentName, complaint.studentEmail, complaint.content]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword));
      return matchesStatus && matchesSearch;
    });
  }, [complaints, search, statusFilter]);

  if (isLoadingUser || isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-52 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-36 animate-pulse rounded bg-slate-100" />
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

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('penalty.complaintsTitle')}</h1>
        <p className="mt-3 text-sm font-medium text-slate-800">
          {t('lab.label')}: {managedLabName ?? `#${managedLabId}`}
        </p>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex max-w-full gap-2 overscroll-x-contain overflow-x-auto pb-1">
          {filters.map((filter) => (
            <Button
              key={filter.value}
              size="sm"
              variant={statusFilter === filter.value ? 'primary' : 'outline'}
              onClick={() => setStatusFilter(filter.value)}
            >
              {filter.label}
            </Button>
          ))}
        </div>
        <input
          aria-label={t('penalty.searchComplaints')}
          className="min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 lg:w-80"
          placeholder={t('penalty.searchComplaints')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      {isError ? (
        <ErrorState onRetry={() => refetch()}>
          {t('penalty.complaintsLoadFailed')}
        </ErrorState>
      ) : !filteredComplaints.length ? (
        <EmptyState>
          {t('penalty.noComplaints')}
        </EmptyState>
      ) : (
        <div className="space-y-4">
          {filteredComplaints.map((complaint) => (
            <article key={complaint.id} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-base font-semibold text-slate-950">
                    {complaint.studentName || complaint.studentEmail || `${t('penalty.complaint.student')} #${complaint.userId}`}
                  </h3>
                  <p className="mt-1 text-sm text-slate-600">{complaint.studentEmail}</p>
                  <p className="mt-1 text-sm text-slate-600">{t('lab.label')}: {complaint.labName ?? t('lab.shortName')}</p>
                </div>
                <span
                  className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getComplaintStatusClass(complaint.status)}`}
                >
                  {formatComplaintStatus(complaint.status)}
                </span>
              </div>

              <dl className="mt-4 grid gap-3 text-sm lg:grid-cols-2">
                <div>
                  <dt className="font-semibold text-slate-700">{t('penalty.typeLabel')}</dt>
                  <dd className="mt-1 text-slate-600">{formatPenaltyType(null)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('common.submittedAt')}</dt>
                    <dd className="mt-1 text-slate-600">{formatDateTime(complaint.createdAt, locale)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('penalty.reasonLabel')}</dt>
                  <dd className="mt-1 whitespace-pre-wrap text-slate-600">
                    {complaint.penaltyReason ?? t('common.notUpdated')}
                  </dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('penalty.complaintContentLabel')}</dt>
                  <dd className="mt-1 whitespace-pre-wrap text-slate-600">{complaint.content}</dd>
                </div>
              </dl>

              {complaint.resolutionNote ? (
                <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                  {t('penalty.resolutionNote')}: {complaint.resolutionNote}
                </div>
              ) : null}

              {complaint.status === 'PENDING' ? (
                <div className="mt-5 flex flex-wrap gap-2">
                  <Button onClick={() => setReviewState({ complaint, decision: 'APPROVE' })} size="sm">
                    {t('common.accept')}
                  </Button>
                  <Button onClick={() => setReviewState({ complaint, decision: 'REJECT' })} size="sm" variant="danger">
                    {t('common.reject')}
                  </Button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      <ComplaintReviewModal
        complaint={reviewState?.complaint ?? null}
        decision={reviewState?.decision ?? null}
        isSubmitting={reviewComplaint.isPending}
        onClose={() => setReviewState(null)}
        onSubmit={(note) => {
          if (!reviewState) {
            return;
          }
          reviewComplaint.mutate(
            {
              complaintId: reviewState.complaint.id,
              decision: reviewState.decision,
              note,
            },
            { onSuccess: () => setReviewState(null) },
          );
        }}
      />
    </section>
  );
}
