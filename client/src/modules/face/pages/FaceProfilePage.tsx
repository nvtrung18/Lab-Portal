import axios from 'axios';
import { Camera, Shield, Trash2, UserCheck } from 'lucide-react';
import { ChangeEvent, FormEvent, useState } from 'react';

import { getStoredRole } from '../../../shared/api';
import { Button, EmptyState, ErrorState } from '../../../shared/components';
import type { Response } from '../../../shared/types';
import { readFaceImage } from '../api';
import { useFaceProfile, useFaceProfileActions } from '../hooks';

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<Response<unknown>> | undefined;
    return body?.message ?? 'Không thể cập nhật hồ sơ khuôn mặt.';
  }
  return error instanceof Error ? error.message : 'Không thể cập nhật hồ sơ khuôn mặt.';
}

export function FaceProfilePage() {
  const isAdmin = getStoredRole()?.replace(/^ROLE_/, '') === 'ADMIN';
  const [targetInput, setTargetInput] = useState('');
  const targetUserId = isAdmin && Number(targetInput) > 0 ? Number(targetInput) : null;
  const enabled = !isAdmin || targetUserId !== null;
  const { consent, profile } = useFaceProfile(targetUserId, enabled);
  const actions = useFaceProfileActions(targetUserId);
  const [image, setImage] = useState<Awaited<ReturnType<typeof readFaceImage>> | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [reason, setReason] = useState('');
  const [livenessRequired, setLivenessRequired] = useState(true);
  const [localError, setLocalError] = useState('');

  const handleFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const result = await readFaceImage(file);
      setImage(result);
      setPreviewUrl(URL.createObjectURL(file));
      setLocalError('');
    } catch (error) {
      setLocalError(errorMessage(error));
    }
  };

  const handleSave = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!image) {
      setLocalError('Vui lòng chọn ảnh khuôn mặt rõ nét.');
      return;
    }
    setLocalError('');
    actions.save.mutate({ request: { ...image, livenessRequired }, update: Boolean(profile.data) });
  };

  const mutationError = actions.consent.error ?? actions.save.error ?? actions.remove.error;

  return (
    <section className="mx-auto max-w-5xl">
      <header className="mb-6">
        <p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500"><Shield aria-hidden="true" className="h-4 w-4" /> Sinh trắc học có kiểm soát</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-950 dark:text-white">Hồ sơ khuôn mặt</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300">Ảnh chỉ được gửi để tạo embedding. Spring lưu embedding đã mã hóa và quản lý đồng ý của người dùng.</p>
      </header>

      {isAdmin ? (
        <label className="mb-5 block max-w-sm text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="face-target-user">
          ID người dùng cần quản lý
          <input id="face-target-user" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base dark:border-slate-700 dark:bg-slate-900 dark:text-white" min={1} type="number" value={targetInput} onChange={(event) => setTargetInput(event.target.value)} />
        </label>
      ) : null}

      {!enabled ? <EmptyState>Nhập ID người dùng để xem trạng thái đồng ý và hồ sơ khuôn mặt.</EmptyState> : (
        <div className="grid gap-5 lg:grid-cols-2">
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="flex items-center gap-2 text-base font-semibold text-slate-950 dark:text-white"><UserCheck aria-hidden="true" className="h-5 w-5" /> Đồng ý xử lý dữ liệu</h2>
            {consent.isLoading ? <div className="mt-4 h-20 animate-pulse rounded bg-slate-100 dark:bg-slate-800" /> : consent.isError ? <ErrorState className="mt-4" onRetry={() => void consent.refetch()}>Không thể tải trạng thái đồng ý.</ErrorState> : (
              <div className="mt-4">
                <p className="text-sm text-slate-600 dark:text-slate-300">Trạng thái: <strong className="text-slate-950 dark:text-white">{consent.data?.status ?? 'CHƯA THIẾT LẬP'}</strong></p>
                <label className="mt-4 block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="face-consent-reason">Lý do thay đổi (không bắt buộc)</label>
                <textarea id="face-consent-reason" className="mt-2 min-h-20 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base dark:border-slate-700 dark:bg-slate-950 dark:text-white" maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} />
                <div className="mt-4 flex flex-wrap gap-2">
                  <Button disabled={consent.data?.status === 'GRANTED'} loading={actions.consent.isPending} variant="success" onClick={() => actions.consent.mutate({ status: 'GRANTED', reason })}>Cấp đồng ý</Button>
                  <Button disabled={consent.data?.status === 'WITHDRAWN'} loading={actions.consent.isPending} variant="outline" onClick={() => actions.consent.mutate({ status: 'WITHDRAWN', reason })}>Rút đồng ý</Button>
                </div>
              </div>
            )}
          </section>

          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="flex items-center gap-2 text-base font-semibold text-slate-950 dark:text-white"><Camera aria-hidden="true" className="h-5 w-5" /> Mẫu khuôn mặt</h2>
            {profile.isLoading ? <div className="mt-4 h-20 animate-pulse rounded bg-slate-100 dark:bg-slate-800" /> : profile.isError ? <ErrorState className="mt-4" onRetry={() => void profile.refetch()}>Không thể tải hồ sơ khuôn mặt.</ErrorState> : (
              <form className="mt-4" onSubmit={handleSave}>
                {profile.data ? <div className="mb-4 rounded-md bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-950 dark:text-slate-300"><p>Trạng thái: <strong>{profile.data.status}</strong></p><p className="mt-1">Model: {profile.data.embeddingModel}</p></div> : <p className="mb-4 text-sm text-slate-600 dark:text-slate-300">Chưa đăng ký hồ sơ khuôn mặt.</p>}
                {previewUrl ? <img alt="Ảnh khuôn mặt được chọn" className="mb-4 aspect-video w-full rounded-md border border-slate-200 object-cover dark:border-slate-700" src={previewUrl} /> : null}
                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="face-image">Ảnh JPEG hoặc PNG</label>
                <input id="face-image" accept="image/jpeg,image/png" capture="user" className="mt-2 block w-full text-sm text-slate-600 file:mr-3 file:min-h-11 file:rounded-md file:border-0 file:bg-slate-900 file:px-4 file:text-sm file:font-semibold file:text-white dark:text-slate-300 dark:file:bg-white dark:file:text-slate-950" type="file" onChange={(event) => void handleFile(event)} />
                <label className="mt-4 flex min-h-11 items-center gap-3 text-sm text-slate-700 dark:text-slate-200"><input checked={livenessRequired} className="h-5 w-5" type="checkbox" onChange={(event) => setLivenessRequired(event.target.checked)} /> Yêu cầu kiểm tra liveness khi đăng ký</label>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Button loading={actions.save.isPending} loadingText="Đang xử lý..." type="submit">{profile.data ? 'Cập nhật mẫu' : 'Đăng ký mẫu'}</Button>
                  {profile.data ? <Button aria-label="Xóa hồ sơ khuôn mặt" loading={actions.remove.isPending} variant="danger" onClick={() => { if (window.confirm('Xóa hồ sơ khuôn mặt hiện tại?')) actions.remove.mutate(); }}><Trash2 aria-hidden="true" className="h-4 w-4" /> Xóa hồ sơ</Button> : null}
                </div>
              </form>
            )}
          </section>
        </div>
      )}

      {localError || mutationError ? <p className="mt-5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">{localError || errorMessage(mutationError)}</p> : null}
    </section>
  );
}
