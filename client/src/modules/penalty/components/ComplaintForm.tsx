import { useState } from 'react';

interface ComplaintFormProps {
  isSubmitting: boolean;
  onCancel: () => void;
  onSubmit: (content: string) => void;
}

export function ComplaintForm({ isSubmitting, onCancel, onSubmit }: ComplaintFormProps) {
  const [content, setContent] = useState('');
  const trimmedContent = content.trim();
  const validationMessage =
    trimmedContent.length > 0 && trimmedContent.length < 10
      ? 'Nội dung khiếu nại cần tối thiểu 10 ký tự.'
      : trimmedContent.length > 1000
        ? 'Nội dung khiếu nại không vượt quá 1000 ký tự.'
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
        Nội dung khiếu nại
      </label>
      <textarea
        id="complaint-content"
        className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        maxLength={1000}
        placeholder="Nhập nội dung khiếu nại hoặc lý do cần xem xét lại vi phạm..."
        value={content}
        onChange={(event) => setContent(event.target.value)}
      />
      <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className={validationMessage ? 'text-sm text-red-600' : 'text-sm text-slate-500'}>
          {validationMessage || `${trimmedContent.length}/1000 ký tự`}
        </p>
        <div className="flex gap-2">
          <button
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100"
            disabled={isSubmitting}
            type="button"
            onClick={onCancel}
          >
            Hủy
          </button>
          <button
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={!canSubmit}
            type="submit"
          >
            {isSubmitting ? 'Đang gửi...' : 'Gửi khiếu nại'}
          </button>
        </div>
      </div>
    </form>
  );
}
