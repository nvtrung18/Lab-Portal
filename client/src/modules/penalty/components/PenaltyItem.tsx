import { useState } from 'react';

import { useI18n } from '../../../shared/i18n';
import type { Penalty } from '../types';
import {
  formatComplaintStatus,
  formatDateTime,
  formatPenaltyStatus,
  formatPenaltyType,
  getComplaintStatusClass,
  getPenaltyStatusClass,
} from '../utils';
import { ComplaintForm } from './ComplaintForm';

interface PenaltyItemProps {
  penalty: Penalty;
  isSubmitting: boolean;
  onSubmitComplaint: (penaltyId: number, content: string) => void;
}

function Badge({ children, className }: { children: string; className: string }) {
  return (
    <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${className}`}>
      {children}
    </span>
  );
}

export function PenaltyItem({ penalty, isSubmitting, onSubmitComplaint }: PenaltyItemProps) {
  const { locale, t } = useI18n();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const complaint = penalty.complaint;

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-slate-950 dark:text-white">
            {penalty.labName ?? t('lab.label')}
          </h3>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {t('common.recordedAt')}: {formatDateTime(penalty.createdAt, locale)}
          </p>
        </div>
        <Badge className={getPenaltyStatusClass(penalty.status)}>
          {formatPenaltyStatus(penalty.status, locale)}
        </Badge>
      </div>

      <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="font-semibold text-slate-700 dark:text-slate-300">{t('penalty.typeLabel')}</dt>
          <dd className="mt-1 text-slate-600 dark:text-slate-400">{formatPenaltyType(penalty.type, locale)}</dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700 dark:text-slate-300">{t('penalty.points')}</dt>
          <dd className="mt-1 text-slate-600 dark:text-slate-400">{penalty.point ?? penalty.amount ?? t('common.notApplicable')}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="font-semibold text-slate-700 dark:text-slate-300">{t('penalty.reasonLabel')}</dt>
          <dd className="mt-1 whitespace-pre-wrap text-slate-600 dark:text-slate-400">{penalty.reason}</dd>
        </div>
      </dl>

      <div className="mt-5 border-t border-slate-200 pt-4 dark:border-slate-800">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-200">{t('penalty.complaintContentLabel')}</p>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              {complaint ? t('penalty.complaint.infoSent') : t('penalty.complaint.none')}
            </p>
          </div>
          {complaint ? (
            <Badge className={getComplaintStatusClass(complaint.status)}>
              {formatComplaintStatus(complaint.status, locale)}
            </Badge>
          ) : (
            <button
              className="min-h-11 w-full rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white sm:w-auto"
              disabled={penalty.status !== 'ACTIVE'}
              type="button"
              onClick={() => setIsFormOpen(true)}
            >
              {t('penalty.complaint.submit')}
            </button>
          )}
        </div>

        {complaint ? (
          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-800/60">
            <p className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">{complaint.content}</p>
            <p className="mt-3 text-xs font-medium text-slate-500 dark:text-slate-400">
              {t('penalty.complaint.sentAt')}: {formatDateTime(complaint.createdAt, locale)}
            </p>
          </div>
        ) : null}

        {isFormOpen && !complaint ? (
          <ComplaintForm
            isSubmitting={isSubmitting}
            onCancel={() => setIsFormOpen(false)}
            onSubmit={(content) => {
              onSubmitComplaint(penalty.id, content);
              setIsFormOpen(false);
            }}
          />
        ) : null}
      </div>
    </article>
  );
}
