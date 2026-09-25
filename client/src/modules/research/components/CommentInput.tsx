import { useId, useState } from 'react';

import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';

interface CommentInputProps {
  isSubmitting: boolean;
  onSubmit: (content: string, onSuccess: () => void) => void;
}

export function CommentInput({ isSubmitting, onSubmit }: CommentInputProps) {
  const { t } = useI18n();
  const [content, setContent] = useState('');
  const [error, setError] = useState<string | null>(null);
  const contentId = useId();

  function handleSubmit() {
    const value = content.trim();
    if (!value) {
      setError(t('validation.comment'));
      return;
    }
    onSubmit(value, () => {
      setContent('');
      setError(null);
    });
  }

  return (
    <div className="mt-3 space-y-2">
      <label className="block text-sm font-semibold text-slate-700" htmlFor={contentId}>
        {t('research.comment.inputLabel')}
      </label>
      <textarea
        className="min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        id={contentId}
        maxLength={5000}
        placeholder={t('research.comment.inputPlaceholder')}
        value={content}
        onChange={(event) => {
          setContent(event.target.value);
          setError(null);
        }}
      />
      {error ? <p className="text-xs font-medium text-red-700">{error}</p> : null}
      <Button disabled={isSubmitting} loading={isSubmitting} onClick={handleSubmit} size="sm">
        {t('research.comment.submit')}
      </Button>
    </div>
  );
}
