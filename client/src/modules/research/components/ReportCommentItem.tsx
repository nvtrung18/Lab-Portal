import { useI18n } from '../../../shared/i18n';
import type { ResearchReportComment } from '../types';

interface ReportCommentItemProps {
  comment: ResearchReportComment;
}

export function ReportCommentItem({ comment }: ReportCommentItemProps) {
  const { locale, t } = useI18n();
  const roleLabel = comment.authorRole === 'LAB_MANAGER'
    ? t('research.role.manager')
    : comment.groupRole === 'LEADER'
      ? t('research.role.leader')
      : t('research.role.member');

  return (
    <article className="rounded-md bg-slate-50 p-3 text-sm text-slate-700">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-slate-900">{comment.authorName || comment.authorEmail || t('common.unknownUser')}</p>
          {comment.authorName && comment.authorEmail ? (
            <p className="text-xs text-slate-500">{comment.authorEmail}</p>
          ) : null}
        </div>
        <div className="flex flex-col items-end gap-1">
          <span className="rounded-full bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700">
            {roleLabel}
          </span>
          <time className="text-xs text-slate-500" dateTime={comment.createdAt}>
            {formatCommentTime(comment.createdAt, locale)}
          </time>
        </div>
      </header>
      <p className="mt-3 whitespace-pre-wrap">{comment.content}</p>
    </article>
  );
}

function formatCommentTime(value: string, locale: 'vi' | 'en') {
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
