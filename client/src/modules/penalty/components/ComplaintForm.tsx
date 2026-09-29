import { useState } from 'react';

import { useI18n } from '../../../shared/i18n';

interface ComplaintFormProps {
  isSubmitting: boolean;
  onCancel: () => void;
  onSubmit: (content: string) => void;
}

export function ComplaintForm({ isSubmitting, onCancel, onSubmit }: ComplaintFormProps) {
  const { t } = useI18n();
  const [content, setContent] = useState('');
  const trimmedContent = content.trim();
  const validationMessage =
    trimmedContent.length > 0 && trimmedContent.length < 10
      ? t('penalty.complaint.minLength')
      : trimmedContent.length > 1000
        ? t('penalty.complaint.maxLength')
        : '';
  const canSubmit = trimmedContent.length >= 10 && trimmedContent.length <= 1000 && !isSubmitting;

  return (
    <form
      className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (canSubmit) {
          onSubmit(trimmedContent);
        }
      }}
    >
      <label className="block text-sm font-semibold text-slate-800" htmlFor="complaint-content">
        {t('penalty.complaintContentLabel')}
      </label>
      <textarea
        id="complaint-content"
        className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 bg-white px-3 py-2 text-base text-slate-800 outline-none transition focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        maxLength={1000}
        placeholder={t('penalty.complaint.placeholder')}
        value={content}
        onChange={(event) => setContent(event.target.value)}
      />
      <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className={validationMessage ? 'text-sm text-red-600' : 'text-sm text-slate-500'}>
          {validationMessage || t('penalty.complaint.characterCount', undefined, { count: trimmedContent.length })}
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            className="min-h-11 flex-1 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 sm:flex-none"
            disabled={isSubmitting}
            type="button"
            onClick={onCancel}
          >
            {t('common.cancel')}
          </button>
          <button
            className="min-h-11 flex-1 rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 disabled:cursor-not-allowed disabled:opacity-60 sm:flex-none"
            disabled={!canSubmit}
            type="submit"
          >
            {isSubmitting ? t('penalty.complaint.submitting') : t('penalty.complaint.submit')}
          </button>
        </div>
      </div>
    </form>
  );
}
