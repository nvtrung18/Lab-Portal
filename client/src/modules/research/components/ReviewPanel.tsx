import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
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
  const { t } = useI18n();
  const [content, setContent] = useState('');
  const [submitStatus, setSubmitStatus] = useState<SubmitStatus>('idle');
  const [validationError, setValidationError] = useState<string | null>(null);
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
      setValidationError(null);
      setSubmitStatus('success');
      await queryClient.invalidateQueries({ queryKey });
      queueMicrotask(() => scrollToLatestComment());
    },
    onError: () => {
      setSubmitStatus('error');
      toast.error(t('research.report.commentSendFailed'));
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
    if (!trimmedContent) {
      setValidationError(t('validation.comment'));
      return;
    }
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
    <section className="mt-4 border-t border-slate-200 pt-4" aria-label={t('research.report.discussionTitle')}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h6 className="text-sm font-semibold text-slate-900">{t('research.report.discussionTitle')}</h6>
        <span className="text-xs font-medium text-slate-500">{comments.length} {t('research.report.commentsCount')}</span>
      </div>

      {isLoading ? (
        <LoadingState className="mt-3">{t('research.report.commentsLoading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-3" onRetry={() => refetch()}>
          {t('research.report.commentsLoadFailed')}
        </ErrorState>
      ) : !comments.length ? (
        <EmptyState className="mt-3">{t('research.report.noCommentsForReport')}</EmptyState>
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
            {t('research.report.commentInput')}
          </label>
          <textarea
            className="min-h-24 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id={contentId}
            maxLength={2000}
            placeholder={t('research.report.commentPlaceholder')}
            value={content}
            onChange={(event) => {
              setContent(event.target.value);
              setValidationError(null);
              if (submitStatus !== 'idle') {
                setSubmitStatus('idle');
              }
            }}
            onKeyDown={handleKeyDown}
          />
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="space-y-1">
              <p className="text-xs text-slate-500">{trimmedContent.length}/2000</p>
              {validationError ? <p className="text-xs font-medium text-red-700">{validationError}</p> : null}
              {addComment.isPending ? <p className="text-xs font-medium text-slate-600">{t('research.report.sendingComment')}</p> : null}
              {submitStatus === 'success' ? (
                <p className="text-xs font-medium text-emerald-700">{t('research.report.commentSent')}</p>
              ) : null}
              {submitStatus === 'error' ? (
                <p className="text-xs font-medium text-red-700">{t('research.report.commentSendFailed')}</p>
              ) : null}
            </div>
            <Button disabled={!canSubmit} loading={addComment.isPending} loadingText={t('common.processing')} size="sm" type="submit">
              {t('research.report.sendComment')}
            </Button>
          </div>
        </form>
      ) : null}
    </section>
  );
}
