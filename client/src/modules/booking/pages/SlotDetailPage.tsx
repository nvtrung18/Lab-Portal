import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ConfirmDialog, LoadingState } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCreatePenalty, useSlotPenalties } from '../../penalty/hooks';
import { formatPenaltyType } from '../../penalty/utils';
import { useCurrentUser } from '../../user/hooks';
import { CancelSlotModal, PenaltyCreateModal } from '../components';
import type { BookingResponse } from '../api';
import { useCompleteSlot, useReviewBooking, useSlot, useSlotRegistrations } from '../hooks';
import { getBookingStatusLabel, isUsableSlot } from '../utils';

function formatDateTime(value: string | undefined, locale: Locale) {
  if (!value) {
    return translate('common.notUpdated', locale);
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return translate('common.notUpdated', locale);
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function canCreatePenalty(booking: BookingResponse) {
  return ['APPROVED', 'CHECKED_IN', 'CANCELLED_BY_STUDENT', 'NO_SHOW', 'COMPLETED'].includes(booking.status);
}

export function SlotDetailPage() {
  const { locale, t } = useI18n();
  const { slotId } = useParams();
  const numericSlotId = Number(slotId);
  const [cancelSlotId, setCancelSlotId] = useState<number | null>(null);
  const [selectedBooking, setSelectedBooking] = useState<BookingResponse | null>(null);
  const [pendingConfirmation, setPendingConfirmation] = useState<
    | { type: 'REVIEW'; bookingId: number; decision: 'APPROVE' | 'REJECT' }
    | { type: 'COMPLETE' }
    | null
  >(null);
  const { data: currentUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: slot, isLoading: isLoadingSlot } = useSlot(numericSlotId);
  const { data: registrations = [], isLoading, isError, refetch } = useSlotRegistrations(numericSlotId);
  const { data: slotPenalties = [] } = useSlotPenalties(numericSlotId);
  const reviewBooking = useReviewBooking(managedLabId, numericSlotId);
  const createPenalty = useCreatePenalty(numericSlotId);
  const completeSlot = useCompleteSlot(managedLabId, numericSlotId);
  const canMutateSlot = Boolean(slot && isUsableSlot(slot));
  const now = Date.now();
  const startTime = slot ? new Date(slot.startTime).getTime() : Number.NaN;
  const endTime = slot ? new Date(slot.endTime).getTime() : Number.NaN;
  const terminalSlot = Boolean(slot && ['CANCELLED', 'CLOSED', 'INACTIVE', 'ARCHIVED'].includes(slot.status));
  const canCancelSlot = Boolean(slot && canMutateSlot && !terminalSlot && now < startTime);
  const canCompleteSlot = Boolean(slot && !terminalSlot && startTime <= now && now < endTime);

  const counts = useMemo(
    () => ({
      pending: registrations.filter((booking) => booking.status === 'PENDING_APPROVAL').length,
      approved: registrations.filter((booking) => booking.status === 'APPROVED').length,
    }),
    [registrations],
  );

  const penaltiesByBookingId = useMemo(() => {
    const grouped = new Map<number, typeof slotPenalties>();
    slotPenalties.forEach((penalty) => {
      if (!penalty.bookingId) {
        return;
      }
      grouped.set(penalty.bookingId, [...(grouped.get(penalty.bookingId) ?? []), penalty]);
    });
    return grouped;
  }, [slotPenalties]);

  const handleReview = (bookingId: number, decision: 'APPROVE' | 'REJECT') => {
    setPendingConfirmation({ type: 'REVIEW', bookingId, decision });
  };

  const confirmPendingAction = () => {
    if (pendingConfirmation?.type === 'REVIEW') {
      reviewBooking.mutate(
        { bookingId: pendingConfirmation.bookingId, decision: pendingConfirmation.decision },
        { onSuccess: () => setPendingConfirmation(null) },
      );
    } else if (pendingConfirmation?.type === 'COMPLETE' && slot) {
      completeSlot.mutate(slot.id, { onSuccess: () => setPendingConfirmation(null) });
    }
  };

  const handleCreatePenalty = (payload: {
    userId: number;
    slotId: number;
    bookingId: number;
    type: string;
    point: number;
    reason: string;
  }) => {
    createPenalty.mutate(payload, {
      onSuccess: () => setSelectedBooking(null),
    });
  };

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-slate-600 hover:text-slate-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" to="/app/lab-slots">
          {t('common.backToList')}
        </Link>
        <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
          <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('slotDetail.title')}</h1>
          </div>
          {canCancelSlot && slot ? (
            <button
              type="button"
              className="min-h-11 w-full rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 sm:w-auto"
              onClick={() => setCancelSlotId(slot.id)}
            >
              {t('slot.cancel')}
            </button>
          ) : canCompleteSlot && slot ? (
            <button
              type="button"
              className="min-h-11 w-full rounded-md bg-red-700 px-3 py-2 text-sm font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-60 sm:w-auto"
              disabled={completeSlot.isPending}
              onClick={() => setPendingConfirmation({ type: 'COMPLETE' })}
            >
              {completeSlot.isPending ? t('slotDetail.completing') : t('slotDetail.complete')}
            </button>
          ) : null}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        {isLoadingSlot ? (
          <LoadingState />
        ) : slot ? (
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt className="text-sm text-slate-500">{t('slot.time')}</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950 dark:text-slate-100">
                {formatDateTime(slot.startTime, locale)} - {formatDateTime(slot.endTime, locale)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">{t('slot.capacity')}</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950 dark:text-slate-100">{slot.capacity}</dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">{t('slotDetail.pending')}</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950 dark:text-slate-100">{counts.pending}</dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">{t('slot.approved')}</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950 dark:text-slate-100">{counts.approved}</dd>
            </div>
          </dl>
        ) : (
          <p className="text-sm text-slate-600">{t('slotDetail.notFound')}</p>
        )}
      </div>

      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-5 py-4">
          <h3 className="text-lg font-semibold text-slate-950 dark:text-white">{t('slotDetail.registrations')}</h3>
        </div>
        {isLoading ? (
          <div className="p-5 text-sm text-slate-600">{t('slotDetail.loadingRegistrations')}</div>
        ) : isError ? (
          <div className="p-5 text-sm text-red-700">
            {t('slotDetail.loadRegistrationsFailed')}
            <button className="ml-3 min-h-11 font-semibold underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500" type="button" onClick={() => refetch()}>
              {t('common.retry')}
            </button>
          </div>
        ) : registrations.length ? (
          <div className="max-w-full overscroll-x-contain overflow-x-auto">
            <table className="w-full min-w-[720px] divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold">{t('slotDetail.student')}</th>
                  <th className="px-5 py-3 font-semibold">{t('common.email')}</th>
                  <th className="px-5 py-3 font-semibold">{t('slotDetail.registrationTime')}</th>
                  <th className="px-5 py-3 font-semibold">{t('slot.statusLabel')}</th>
                  <th className="px-5 py-3 font-semibold">{t('slotDetail.recordedPenalties')}</th>
                  <th className="px-5 py-3 font-semibold">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {registrations.map((booking) => {
                  const penalties = penaltiesByBookingId.get(booking.id) ?? [];

                  return (
                    <tr key={booking.id}>
                      <td className="px-5 py-3 font-medium text-slate-950">
                        {booking.studentName || t('common.notUpdated')}
                      </td>
                      <td className="break-all px-5 py-3 text-slate-600">{booking.studentEmail || t('common.notUpdated')}</td>
                      <td className="px-5 py-3 text-slate-600">{formatDateTime(booking.createdAt, locale)}</td>
                      <td className="px-5 py-3 text-slate-700">{getBookingStatusLabel(booking.status, locale)}</td>
                      <td className="px-5 py-3">
                        {penalties.length ? (
                          <div className="flex flex-wrap gap-1.5">
                            {penalties.map((penalty) => (
                              <span
                                key={penalty.id}
                                className="rounded-full bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700 ring-1 ring-amber-200"
                                title={penalty.reason}
                              >
                                {formatPenaltyType(penalty.type, locale)}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-slate-500">{t('common.none')}</span>
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex flex-wrap gap-2">
                          {booking.status === 'PENDING_APPROVAL' && canMutateSlot ? (
                            <>
                              <button
                                type="button"
                                className="min-h-11 rounded-md bg-emerald-600 px-3 py-2 text-sm font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
                                disabled={reviewBooking.isPending}
                                onClick={() => handleReview(booking.id, 'APPROVE')}
                              >
                                {t('common.approve')}
                              </button>
                              <button
                                type="button"
                                className="min-h-11 rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                                disabled={reviewBooking.isPending}
                                onClick={() => handleReview(booking.id, 'REJECT')}
                              >
                                {t('common.reject')}
                              </button>
                            </>
                          ) : null}

                          {canMutateSlot && canCreatePenalty(booking) ? (
                            <button
                              type="button"
                              className="min-h-11 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
                              onClick={() => setSelectedBooking(booking)}
                            >
                              {t('slotDetail.recordPenalty')}
                            </button>
                          ) : null}

                          {(!canMutateSlot || (booking.status !== 'PENDING_APPROVAL' && !canCreatePenalty(booking))) ? (
                            <span className="text-slate-500">{t('common.noActions')}</span>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-5 text-sm text-slate-600">{t('slotDetail.noRegistrations')}</div>
        )}
      </div>

      {managedLabId ? (
        <CancelSlotModal
          labId={managedLabId}
          slotId={cancelSlotId}
          isOpen={Boolean(cancelSlotId)}
          onClose={() => setCancelSlotId(null)}
        />
      ) : null}

      <PenaltyCreateModal
        booking={selectedBooking}
        slot={slot}
        isOpen={Boolean(selectedBooking)}
        isSubmitting={createPenalty.isPending}
        onClose={() => setSelectedBooking(null)}
        onSubmit={handleCreatePenalty}
      />
      <ConfirmDialog
        confirmLabel={pendingConfirmation?.type === 'COMPLETE'
          ? t('slotDetail.complete')
          : pendingConfirmation?.decision === 'APPROVE' ? t('common.approve') : t('common.reject')}
        isOpen={pendingConfirmation !== null}
        isPending={reviewBooking.isPending || completeSlot.isPending}
        message={pendingConfirmation?.type === 'COMPLETE'
          ? t('slotDetail.confirmComplete')
          : pendingConfirmation?.decision === 'APPROVE'
            ? t('slotDetail.confirmApprove')
            : t('slotDetail.confirmReject')}
        onCancel={() => setPendingConfirmation(null)}
        onConfirm={confirmPendingAction}
        title={pendingConfirmation?.type === 'COMPLETE'
          ? t('slotDetail.confirmCompleteTitle')
          : pendingConfirmation?.decision === 'APPROVE' ? t('slotDetail.confirmApproveTitle') : t('slotDetail.confirmRejectTitle')}
        variant={pendingConfirmation?.type === 'REVIEW' && pendingConfirmation.decision === 'APPROVE'
          ? 'success'
          : 'danger'}
      />
    </section>
  );
}
