import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useAddReportComment, useReportComments } from '../hooks';
import { CommentInput } from './CommentInput';
import { ReportCommentItem } from './ReportCommentItem';

interface ReportDiscussionPanelProps {
  reportId: number;
  canComment: boolean;
}

export function ReportDiscussionPanel({ reportId, canComment }: ReportDiscussionPanelProps) {
  const { t } = useI18n();
  const { data: comments = [], isError, isLoading, refetch } = useReportComments(reportId);
  const addComment = useAddReportComment(reportId);

  return (
    <section className="mt-4 rounded-md border border-slate-200 bg-white p-4" aria-label={t('research.report.discussionLabel')}>
      <h6 className="text-sm font-semibold text-slate-800">{t('research.report.discussionTitle')}</h6>
      {isLoading ? (
        <LoadingState className="mt-3">{t('research.report.commentsLoading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-3" onRetry={() => refetch()}>{t('research.report.commentsLoadFailed')}</ErrorState>
      ) : !comments.length ? (
        <EmptyState className="mt-3">{t('research.report.noComments')}</EmptyState>
      ) : (
        <div className="mt-3 max-h-64 space-y-2 overflow-y-auto">
          {comments.map((comment) => <ReportCommentItem comment={comment} key={comment.id} />)}
        </div>
      )}
      {canComment ? (
        <CommentInput
          isSubmitting={addComment.isPending}
          onSubmit={(content, onSuccess) => addComment.mutate(content, { onSuccess })}
        />
      ) : null}
    </section>
  );
}
