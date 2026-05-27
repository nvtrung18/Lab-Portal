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
  const isMine = currentUserId != null && comment.authorId === currentUserId;
  const role = getCommentRole(comment);

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
              {comment.authorName || comment.authorEmail || 'Người dùng'}
            </p>
            {isMine ? (
              <span className="rounded-full bg-slate-900 px-2 py-0.5 text-xs font-semibold text-white">
                Bạn
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
            {formatCommentTime(comment.createdAt)}
          </time>
        </div>
      </header>

      <p className="mt-3 whitespace-pre-wrap break-words leading-6 text-slate-700">{comment.content}</p>
    </article>
  );
}

function getCommentRole(comment: ResearchReportComment): { label: string; tone: RoleTone } {
  if (comment.authorRole === 'LAB_MANAGER') {
    return { label: 'Quản lý PTN', tone: 'manager' };
  }
  if (comment.groupRole === 'LEADER') {
    return { label: 'Trưởng nhóm', tone: 'leader' };
  }
  return { label: 'Thành viên', tone: 'member' };
}

function formatCommentTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}
