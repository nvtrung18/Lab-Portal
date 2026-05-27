import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useAddReportComment, useReportComments } from '../hooks';
import { CommentInput } from './CommentInput';
import { ReportCommentItem } from './ReportCommentItem';

interface ReportDiscussionPanelProps {
  reportId: number;
  canComment: boolean;
}

export function ReportDiscussionPanel({ reportId, canComment }: ReportDiscussionPanelProps) {
  const { data: comments = [], isError, isLoading, refetch } = useReportComments(reportId);
  const addComment = useAddReportComment(reportId);

  return (
    <section className="mt-4 rounded-md border border-slate-200 bg-white p-4" aria-label="Cửa sổ góp ý báo cáo">
      <h6 className="text-sm font-semibold text-slate-800">Cửa sổ góp ý</h6>
      {isLoading ? (
        <LoadingState className="mt-3">Đang tải góp ý...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-3" onRetry={() => refetch()}>Không thể tải góp ý.</ErrorState>
      ) : !comments.length ? (
        <EmptyState className="mt-3">Không có góp ý nào.</EmptyState>
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
