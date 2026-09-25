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
    <article className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-base font-semibold text-slate-950">
            {penalty.labName ?? t('lab.label')}
          </h3>
          <p className="mt-1 text-sm text-slate-600">
            {t('common.recordedAt')}: {formatDateTime(penalty.createdAt, locale)}
          </p>
        </div>
        <Badge className={getPenaltyStatusClass(penalty.status)}>
          {formatPenaltyStatus(penalty.status)}
        </Badge>
      </div>

      <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="font-semibold text-slate-700">{t('penalty.typeLabel')}</dt>
          <dd className="mt-1 text-slate-600">{formatPenaltyType(penalty.type)}</dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">{t('penalty.points')}</dt>
          <dd className="mt-1 text-slate-600">{penalty.point ?? penalty.amount ?? t('common.notApplicable')}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="font-semibold text-slate-700">{t('penalty.reasonLabel')}</dt>
          <dd className="mt-1 whitespace-pre-wrap text-slate-600">{penalty.reason}</dd>
        </div>
      </dl>

      <div className="mt-5 border-t border-slate-200 pt-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-slate-800">{t('penalty.complaintContentLabel')}</p>
            <p className="mt-1 text-sm text-slate-600">
              {complaint ? t('penalty.complaint.infoSent') : t('penalty.complaint.none')}
            </p>
          </div>
          {complaint ? (
            <Badge className={getComplaintStatusClass(complaint.status)}>
              {formatComplaintStatus(complaint.status)}
            </Badge>
          ) : (
            <button
              className="w-fit rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={penalty.status !== 'ACTIVE'}
              type="button"
              onClick={() => setIsFormOpen(true)}
            >
              {t('penalty.complaint.submit')}
            </button>
          )}
        </div>

        {complaint ? (
          <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-4">
            <p className="whitespace-pre-wrap text-sm text-slate-700">{complaint.content}</p>
            <p className="mt-3 text-xs font-medium text-slate-500">
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
