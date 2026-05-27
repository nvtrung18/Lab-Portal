import { useId, useState } from 'react';

import { Button } from '../../../shared/components';

interface CommentInputProps {
  isSubmitting: boolean;
  onSubmit: (content: string, onSuccess: () => void) => void;
}

export function CommentInput({ isSubmitting, onSubmit }: CommentInputProps) {
  const [content, setContent] = useState('');
  const contentId = useId();

  function handleSubmit() {
    const value = content.trim();
    if (!value) {
      return;
    }
    onSubmit(value, () => setContent(''));
  }

  return (
    <div className="mt-3 space-y-2">
      <label className="block text-sm font-semibold text-slate-700" htmlFor={contentId}>
        Nhập nội dung trao đổi
      </label>
      <textarea
        className="min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        id={contentId}
        maxLength={5000}
        placeholder="Nhập nội dung trao đổi..."
        value={content}
        onChange={(event) => setContent(event.target.value)}
      />
      <Button disabled={!content.trim()} loading={isSubmitting} onClick={handleSubmit} size="sm">
        Gửi góp ý
      </Button>
    </div>
  );
}
