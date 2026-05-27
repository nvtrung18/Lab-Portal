import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { addReportComment, getReportComments } from '../api';
import type { ResearchReportComment } from '../types';
import { CommentItem } from './CommentItem';

interface ReviewPanelProps {
  reportId: number;
  canComment: boolean;
  currentUserId?: number | null;
}

type SubmitStatus = 'idle' | 'success' | 'error';

export function ReviewPanel({ reportId, canComment, currentUserId }: ReviewPanelProps) {
  const [content, setContent] = useState('');
  const [submitStatus, setSubmitStatus] = useState<SubmitStatus>('idle');
  const contentId = useId();
  const endRef = useRef<HTMLDivElement | null>(null);
  const queryClient = useQueryClient();
  const queryKey = queryKeys.research.reportComments(reportId);

  const {
    data: comments = [],
    isError,
    isLoading,
    refetch,
  } = useQuery({
    queryKey,
    queryFn: () => getReportComments(reportId),
    refetchInterval: 15000,
    refetchOnWindowFocus: true,
    staleTime: 15000,
  });

  const addComment = useMutation({
    mutationFn: (nextContent: string) => addReportComment(reportId, nextContent),
    onSuccess: async (createdComment) => {
      queryClient.setQueryData<ResearchReportComment[]>(queryKey, (current = []) => {
        if (current.some((comment) => comment.id === createdComment.id)) {
          return current;
        }
        return [...current, createdComment].sort((first, second) =>
          first.createdAt.localeCompare(second.createdAt),
        );
      });
      setContent('');
      setSubmitStatus('success');
      await queryClient.invalidateQueries({ queryKey });
      queueMicrotask(() => scrollToLatestComment());
    },
    onError: () => {
      setSubmitStatus('error');
      toast.error('Không thể gửi góp ý. Vui lòng thử lại.');
    },
  });

  const trimmedContent = content.trim();
  const canSubmit = canComment && trimmedContent.length >= 2 && !addComment.isPending;

  useEffect(() => {
    if (comments.length > 0) {
      scrollToLatestComment();
    }
  }, [comments.length]);

  function scrollToLatestComment() {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    submitComment();
  }

  function submitComment() {
    if (!canSubmit) {
      return;
    }
    addComment.mutate(trimmedContent);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      submitComment();
    }
  }

  return (
    <section className="mt-4 border-t border-slate-200 pt-4" aria-label="Cửa sổ góp ý">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h6 className="text-sm font-semibold text-slate-900">Cửa sổ góp ý</h6>
        <span className="text-xs font-medium text-slate-500">{comments.length} góp ý</span>
      </div>

      {isLoading ? (
        <LoadingState className="mt-3">Đang tải góp ý...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-3" onRetry={() => refetch()}>
          Không thể tải góp ý.
        </ErrorState>
      ) : !comments.length ? (
        <EmptyState className="mt-3">Chưa có góp ý nào cho báo cáo này.</EmptyState>
      ) : (
        <div className="mt-3 max-h-80 space-y-3 overflow-y-auto pr-1">
          {comments.map((comment) => (
            <CommentItem comment={comment} currentUserId={currentUserId} key={comment.id} />
          ))}
          <div ref={endRef} />
        </div>
      )}

      {canComment ? (
        <form className="mt-4 space-y-2" onSubmit={handleSubmit}>
          <label className="sr-only" htmlFor={contentId}>
            Nhập nội dung góp ý hoặc phản hồi
          </label>
          <textarea
            className="min-h-24 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id={contentId}
            maxLength={2000}
            placeholder="Nhập nội dung góp ý hoặc phản hồi..."
            value={content}
            onChange={(event) => {
              setContent(event.target.value);
              if (submitStatus !== 'idle') {
                setSubmitStatus('idle');
              }
            }}
            onKeyDown={handleKeyDown}
          />
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="space-y-1">
              <p className="text-xs text-slate-500">{trimmedContent.length}/2000</p>
              {addComment.isPending ? <p className="text-xs font-medium text-slate-600">Đang gửi góp ý...</p> : null}
              {submitStatus === 'success' ? (
                <p className="text-xs font-medium text-emerald-700">Đã gửi góp ý.</p>
              ) : null}
              {submitStatus === 'error' ? (
                <p className="text-xs font-medium text-red-700">Không thể gửi góp ý. Vui lòng thử lại.</p>
              ) : null}
            </div>
            <Button disabled={!canSubmit} loading={addComment.isPending} loadingText="Đang gửi..." size="sm" type="submit">
              Gửi góp ý
            </Button>
          </div>
        </form>
      ) : null}
    </section>
  );
}
