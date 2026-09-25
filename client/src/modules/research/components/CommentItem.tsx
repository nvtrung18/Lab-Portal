import { useI18n } from '../../../shared/i18n';
import type { ResearchReportComment } from '../types';

interface CommentItemProps {
  comment: ResearchReportComment;
  currentUserId?: number | null;
}

type RoleTone = 'manager' | 'leader' | 'member';

const ROLE_TONE_CLASSES: Record<RoleTone, string> = {
  manager: 'border-amber-200 bg-amber-50 text-amber-800',
  leader: 'border-blue-200 bg-blue-50 text-blue-800',
  member: 'border-slate-200 bg-slate-100 text-slate-700',
};

export function CommentItem({ comment, currentUserId }: CommentItemProps) {
  const { locale, t } = useI18n();
  const isMine = currentUserId != null && comment.authorId === currentUserId;
  const role = getCommentRole(comment, t);

  return (
    <article
      className={[
        'rounded-md border p-3 text-sm',
        isMine ? 'ml-auto max-w-[92%] border-slate-300 bg-slate-50' : 'border-slate-200 bg-white',
      ].join(' ')}
    >
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="break-words font-semibold text-slate-950">
              {comment.authorName || comment.authorEmail || t('common.unknownUser')}
            </p>
            {isMine ? (
              <span className="rounded-full bg-slate-900 px-2 py-0.5 text-xs font-semibold text-white">
                {t('research.you')}
              </span>
            ) : null}
          </div>
          {comment.authorEmail ? (
            <p className="mt-0.5 break-all text-xs text-slate-500">{comment.authorEmail}</p>
          ) : null}
        </div>

        <div className="flex shrink-0 flex-col items-end gap-1">
          <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${ROLE_TONE_CLASSES[role.tone]}`}>
            {role.label}
          </span>
          <time className="text-xs text-slate-500" dateTime={comment.createdAt}>
            {formatCommentTime(comment.createdAt, locale)}
          </time>
        </div>
      </header>

      <p className="mt-3 whitespace-pre-wrap break-words leading-6 text-slate-700">{comment.content}</p>
    </article>
  );
}

function getCommentRole(comment: ResearchReportComment, t: ReturnType<typeof useI18n>['t']): { label: string; tone: RoleTone } {
  if (comment.authorRole === 'LAB_MANAGER') {
    return { label: t('research.role.manager'), tone: 'manager' };
  }
  if (comment.groupRole === 'LEADER') {
    return { label: t('research.role.leader'), tone: 'leader' };
  }
  return { label: t('research.role.member'), tone: 'member' };
}

function formatCommentTime(value: string, locale: 'vi' | 'en') {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}
