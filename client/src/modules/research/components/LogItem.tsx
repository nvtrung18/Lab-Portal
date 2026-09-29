import { memo } from 'react';

import { useI18n } from '../../../shared/i18n';
import type { ResearchLog } from '../types';

interface LogItemProps {
  log: ResearchLog;
}

function formatDate(value: string | null | undefined, locale: 'vi' | 'en', fallback: string) {
  if (!value) {
    return fallback;
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(new Date(value));
}

function formatDateTime(value: string | null | undefined, locale: 'vi' | 'en', fallback: string) {
  if (!value) {
    return fallback;
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value));
}

function formatDuration(minutes: number, t: ReturnType<typeof useI18n>['t']) {
  if (!minutes) {
    return `0 ${t('research.log.minutes')}`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (!hours) {
    return `${remainingMinutes} ${t('research.log.minutes')}`;
  }
  if (!remainingMinutes) {
    return `${hours} ${t('research.log.hours')}`;
  }
  return `${hours} ${t('research.log.hours')} ${remainingMinutes} ${t('research.log.minutes')}`;
}

function resolveRoleLabel(log: ResearchLog, t: ReturnType<typeof useI18n>['t']) {
  if (log.logType === 'SYSTEM') {
    return t('research.log.system');
  }
  if (log.groupRole === 'LEADER') {
    return t('research.role.leader');
  }
  if (log.groupRole === 'MEMBER') {
    return t('research.role.member');
  }
  if (log.authorRole === 'LAB_MANAGER') {
    return t('research.role.manager');
  }
  return log.authorRole ?? t('common.notUpdated');
}

function Field({ label, value, fallback }: { label: string; value?: string | number | null; fallback: string }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase text-slate-500">{label}</dt>
      <dd className="mt-1 break-words text-sm text-slate-800">{value || fallback}</dd>
    </div>
  );
}

function LogItemComponent({ log }: LogItemProps) {
  const { locale, t } = useI18n();
  const fallback = t('common.notUpdated');
  return (
    <article className="relative pl-7">
      <span className="absolute left-0 top-1.5 h-3 w-3 rounded-full border-2 border-white bg-slate-900 shadow ring-2 ring-slate-300" />
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                {log.logType === 'SYSTEM' ? t('research.log.system') : t('research.log.manual')}
              </span>
              <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 ring-1 ring-blue-100">
                {formatDate(log.workDate, locale, fallback)}
              </span>
            </div>
            <h4 className="mt-3 break-words text-base font-semibold text-slate-950">{log.content}</h4>
          </div>
          <p className="shrink-0 text-sm text-slate-500">{formatDateTime(log.createdAt, locale, fallback)}</p>
        </div>

        <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field fallback={fallback} label={t('research.log.workDate')} value={formatDate(log.workDate, locale, fallback)} />
          <Field fallback={fallback} label={t('research.log.author')} value={log.authorName} />
          <Field fallback={fallback} label={t('research.log.authorRole')} value={resolveRoleLabel(log, t)} />
          <Field fallback={fallback} label={t('research.group.name')} value={log.groupName} />
          <Field fallback={fallback} label={t('research.log.milestone')} value={log.milestoneTitle} />
          <Field fallback={fallback} label={t('research.log.relatedTask')} value={log.taskTitle} />
          <Field fallback={fallback} label={t('research.log.duration')} value={formatDuration(log.durationMinutes, t)} />
        </dl>

        <dl className="mt-5 space-y-4">
          <Field fallback={fallback} label={t('research.log.content')} value={log.content} />
          <Field fallback={fallback} label={t('research.log.result')} value={log.result} />
          <Field fallback={fallback} label={t('research.log.problem')} value={log.problem} />
          <Field fallback={fallback} label={t('research.log.nextPlan')} value={log.nextPlan} />
          <div>
            <dt className="text-xs font-semibold uppercase text-slate-500">{t('research.log.evidence')}</dt>
            <dd className="mt-1 text-sm">
              {log.evidenceLink ? (
                <a
                  className="inline-flex min-h-11 max-w-full items-center break-all font-medium text-blue-700 underline underline-offset-2"
                  href={log.evidenceLink}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {log.evidenceLink}
                </a>
              ) : (
                <span className="text-slate-800">{fallback}</span>
              )}
            </dd>
          </div>
          <Field fallback={fallback} label={t('research.log.createdAt')} value={formatDateTime(log.createdAt, locale, fallback)} />
        </dl>
      </div>
    </article>
  );
}

export const LogItem = memo(LogItemComponent);
