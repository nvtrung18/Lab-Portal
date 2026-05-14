import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useProfile, useUpdateProfile } from '../hooks';
import type { Response } from '../../../shared/types';

const profileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(2, 'Họ tên phải có ít nhất 2 ký tự')
    .max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z
    .string()
    .trim()
    .regex(/^[+]?[0-9]{10,15}$/, 'Số điện thoại phải có 10-15 chữ số')
    .or(z.literal('')),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

function getMutationErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? 'Không thể cập nhật hồ sơ.';
  }

  return 'Không thể cập nhật hồ sơ. Vui lòng thử lại.';
}

export function ProfilePage() {
  const { data: profile, isLoading, isError, error } = useProfile();
  const updateProfileMutation = useUpdateProfile();
  const [isEditing, setIsEditing] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const fallbackName = useMemo(() => {
    return profile?.fullName || profile?.username || profile?.email || 'User';
  }, [profile]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: '',
      phone: '',
    },
  });

  useEffect(() => {
    if (profile) {
      reset({
        fullName: profile.fullName ?? '',
        phone: profile.phone ?? '',
      });
    }
  }, [profile, reset]);

  const onSubmit = async (values: ProfileFormValues) => {
    setStatusMessage(null);

    try {
      await updateProfileMutation.mutateAsync({
        fullName: values.fullName,
        phone: values.phone || null,
      });
      setStatusMessage('Cập nhật hồ sơ thành công.');
      setIsEditing(false);
    } catch (mutationError) {
      setStatusMessage(getMutationErrorMessage(mutationError));
    }
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-40 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 space-y-3">
          <div className="h-4 w-72 animate-pulse rounded bg-slate-200" />
          <div className="h-4 w-56 animate-pulse rounded bg-slate-200" />
          <div className="h-4 w-64 animate-pulse rounded bg-slate-200" />
        </div>
      </section>
    );
  }

  if (isError || !profile) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-red-700 shadow-sm">
        Không thể tải hồ sơ người dùng.
        {error instanceof Error ? ` ${error.message}` : null}
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-col gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-slate-900 text-lg font-semibold text-white">
            {fallbackName.charAt(0).toUpperCase()}
          </div>
          <div>
            <h2 className="text-xl font-semibold text-slate-950">
              {fallbackName}
            </h2>
            <p className="mt-1 text-sm text-slate-600">{profile.email}</p>
          </div>
        </div>

        {!isEditing ? (
          <button
            type="button"
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800"
            onClick={() => {
              setStatusMessage(null);
              setIsEditing(true);
            }}
          >
            Chỉnh sửa
          </button>
        ) : null}
      </div>

      {statusMessage ? (
        <div
          className={[
            'mt-5 rounded-md border px-3 py-2 text-sm',
            updateProfileMutation.isError
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700',
          ].join(' ')}
        >
          {statusMessage}
        </div>
      ) : null}

      {!isEditing ? (
        <dl className="mt-6 grid gap-5 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-slate-500">Họ tên</dt>
            <dd className="mt-1 text-sm text-slate-950">{profile.fullName}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Username</dt>
            <dd className="mt-1 text-sm text-slate-950">{profile.username}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Số điện thoại</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {profile.phone || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Vai trò</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {profile.roles.join(', ')}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Trạng thái</dt>
            <dd className="mt-1 text-sm text-slate-950">{profile.status}</dd>
          </div>
        </dl>
      ) : (
        <form className="mt-6 max-w-xl space-y-5" onSubmit={handleSubmit(onSubmit)}>
          <div>
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="fullName"
            >
              Họ tên
            </label>
            <input
              id="fullName"
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              disabled={updateProfileMutation.isPending}
              {...register('fullName')}
            />
            {errors.fullName ? (
              <p className="mt-2 text-sm text-red-600">
                {errors.fullName.message}
              </p>
            ) : null}
          </div>

          <div>
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="phone"
            >
              Số điện thoại
            </label>
            <input
              id="phone"
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              disabled={updateProfileMutation.isPending}
              placeholder="+84901234567"
              {...register('phone')}
            />
            {errors.phone ? (
              <p className="mt-2 text-sm text-red-600">
                {errors.phone.message}
              </p>
            ) : null}
          </div>

          <div className="flex gap-3">
            <button
              type="submit"
              disabled={updateProfileMutation.isPending || !isDirty}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
            >
              {updateProfileMutation.isPending ? 'Đang lưu...' : 'Lưu'}
            </button>
            <button
              type="button"
              disabled={updateProfileMutation.isPending}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
              onClick={() => {
                reset({
                  fullName: profile.fullName ?? '',
                  phone: profile.phone ?? '',
                });
                setStatusMessage(null);
                setIsEditing(false);
              }}
            >
              Hủy
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
