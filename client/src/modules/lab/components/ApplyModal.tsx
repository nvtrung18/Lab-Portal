import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useApplyLab } from '../hooks';

const applySchema = z.object({
  cvUrl: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập link CV')
    .url('Link CV không hợp lệ'),
});

type ApplyFormValues = z.infer<typeof applySchema>;

interface ApplyModalProps {
  labId: number | null;
  labName?: string;
  onClose: () => void;
}

export function ApplyModal({ labId, labName, onClose }: ApplyModalProps) {
  const applyMutation = useApplyLab();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ApplyFormValues>({
    resolver: zodResolver(applySchema),
    defaultValues: {
      cvUrl: '',
    },
  });

  useEffect(() => {
    if (labId) {
      reset({ cvUrl: '' });
    }
  }, [labId, reset]);

  if (!labId) {
    return null;
  }

  const onSubmit = async (values: ApplyFormValues) => {
    await applyMutation.mutateAsync({ labId, cvUrl: values.cvUrl });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/40 px-4">
      <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Nộp CV</h3>
            <p className="mt-1 text-sm text-slate-600">
              {labName ? `Ứng tuyển vào ${labName}` : 'Ứng tuyển vào lab'}
            </p>
          </div>
          <button
            type="button"
            className="rounded-md px-2 py-1 text-sm text-slate-500 hover:bg-slate-100"
            disabled={applyMutation.isPending}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>

        <form className="mt-6 space-y-5" onSubmit={handleSubmit(onSubmit)}>
          <div>
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="cvUrl"
            >
              Link CV
            </label>
            <input
              id="cvUrl"
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="https://example.com/my-cv.pdf"
              disabled={applyMutation.isPending}
              {...register('cvUrl')}
            />
            {errors.cvUrl ? (
              <p className="mt-2 text-sm text-red-600">{errors.cvUrl.message}</p>
            ) : null}
          </div>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
              disabled={applyMutation.isPending}
              onClick={onClose}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
              disabled={applyMutation.isPending}
            >
              {applyMutation.isPending ? 'Đang nộp...' : 'Nộp CV'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
