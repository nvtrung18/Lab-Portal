import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button, toast } from '../../../shared/components';
import { translate, translateBackendMessage, useI18n, type Locale } from '../../../shared/i18n';
import type { Response } from '../../../shared/types';
import type { UserMembershipResponse } from '../api';
import { useCurrentUser, useUpdateProfile } from '../hooks';
import { FaceProfilePage } from '../../face/pages';

function createProfileSchema(locale: Locale) {
  return z.object({
    fullName: z.string().trim().min(1, translate('validation.required', locale)).max(100, translate('validation.fullNameMax', locale)),
    phone: z
      .string()
      .trim()
      .regex(/^[+]?[0-9]{10,15}$/, translate('validation.phone', locale))
      .or(z.literal('')),
    avatarUrl: z
      .string()
      .trim()
      .url(translate('validation.avatarUrl', locale))
      .or(z.literal('')),
  });
}

type ProfileFormValues = z.infer<ReturnType<typeof createProfileSchema>>;

function getApiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return translateBackendMessage(response?.message) ?? fallback;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}

function getLabName(membership: UserMembershipResponse, locale: Locale) {
  return membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? `${translate('lab.shortName', locale)} #${membership.labId ?? membership.lab?.id ?? membership.id ?? ''}`;
}

function formatRole(role: string, locale: Locale) {
  const normalizedRole = role.replace(/^ROLE_/, '');
  return normalizedRole === 'LAB_MANAGER'
    ? translate('layout.managerRole', locale)
    : normalizedRole === 'STUDENT'
      ? translate('layout.studentRole', locale)
      : normalizedRole === 'ADMIN'
        ? translate('layout.adminRole', locale)
        : normalizedRole;
}

export function ProfilePage() {
  const { locale, t } = useI18n();
  const { data: profile, isLoading, isError, error } = useCurrentUser();
  const updateProfileMutation = useUpdateProfile();
  const [isEditing, setIsEditing] = useState(false);
  const [formMessage, setFormMessage] = useState<string | null>(null);

  const canEditAvatarUrl = profile ? Object.prototype.hasOwnProperty.call(profile, 'avatarUrl') : false;
  const displayName = profile?.fullName || profile?.username || profile?.email || t('common.user');
  const activeMemberships = useMemo(
    () => profile?.memberships?.filter((membership) => membership.status?.toUpperCase() === 'ACTIVE') ?? [],
    [profile?.memberships],
  );
  const managedLab = profile?.managedLab;
  const managedLabName = managedLab?.name ?? managedLab?.labName ?? null;
  const managedLabId = managedLab?.id ?? profile?.managedLabId ?? null;
  const managedLabEntries = useMemo(
    () => [
      ...(managedLabName ? [{ key: `managed-${managedLabId ?? managedLabName}`, name: managedLabName, status: t('profile.managing') }] : []),
      ...activeMemberships
        .filter((membership) => {
          const membershipId = membership.labId ?? membership.lab?.id ?? membership.id;
          return !(managedLabId && membershipId === managedLabId) && getLabName(membership, locale) !== managedLabName;
        })
        .map((membership, index) => ({
          key: `${membership.labId ?? membership.lab?.id ?? membership.id ?? index}-${membership.status}`,
          name: getLabName(membership, locale),
          status: t('profile.active'),
        })),
    ],
    [activeMemberships, locale, managedLabId, managedLabName, t],
  );
  const profileSchema = useMemo(() => createProfileSchema(locale), [locale]);

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
      avatarUrl: '',
    },
  });

  useEffect(() => {
    if (!profile) {
      return;
    }

    reset({
      fullName: profile.fullName ?? '',
      phone: profile.phone ?? '',
      avatarUrl: profile.avatarUrl ?? '',
    });
  }, [profile, reset]);

  const onSubmit = async (values: ProfileFormValues) => {
    setFormMessage(null);

    try {
      await updateProfileMutation.mutateAsync({
        fullName: values.fullName.trim(),
        phone: values.phone.trim() || null,
        ...(canEditAvatarUrl ? { avatarUrl: values.avatarUrl.trim() || null } : {}),
      });
      setIsEditing(false);
      setFormMessage(t('profile.updateSuccess'));
      toast.success(t('profile.updateSuccess'));
    } catch (mutationError) {
      const message = getApiErrorMessage(mutationError, t('profile.updateFailed'));
      setFormMessage(message);
      toast.error(message);
    }
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-40 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
        </div>
      </section>
    );
  }

  if (isError || !profile) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        {t('profile.loadFailed')} {getApiErrorMessage(error, '')}
      </section>
    );
  }

  const isStudent = profile.roles.some((role) => role.replace(/^ROLE_/, '') === 'STUDENT');

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <div className="flex flex-col gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
        <div className="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1">
          {profile.avatarUrl ? (
            <img
              alt={displayName}
              className="h-16 w-16 rounded-full border border-slate-200 object-cover"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-900 text-xl font-semibold text-white">
              {displayName.charAt(0).toUpperCase()}
            </div>
          )}
          <div className="min-w-0">
            <h1 className="break-words text-xl font-semibold text-slate-950 dark:text-white">{displayName}</h1>
            <p className="mt-1 hidden break-all text-sm text-slate-600 dark:text-slate-300 sm:block">{profile.email}</p>
            <p className="mt-1 text-xs font-medium tracking-normal text-slate-500 dark:text-slate-400">{profile.roles.map((role) => formatRole(role, locale)).join(', ')}</p>
          </div>
          <p className="w-full break-all text-sm text-slate-600 dark:text-slate-300 sm:hidden">{profile.email}</p>
        </div>

        {!isEditing ? (
          <Button
            onClick={() => {
              setFormMessage(null);
              setIsEditing(true);
            }}
          >
            {t('profile.edit')}
          </Button>
        ) : null}
      </div>

      {formMessage ? (
        <div
          className={[
            'mt-5 rounded-md border px-3 py-2 text-sm',
            updateProfileMutation.isError
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200',
          ].join(' ')}
        >
          {formMessage}
        </div>
      ) : null}

      {!isEditing ? (
        <div className="mt-6 space-y-8">
          <dl className="grid gap-5 sm:grid-cols-2">
            <div>
              <dt className="text-sm font-medium text-slate-500">{t('profile.fullName')}</dt>
              <dd className="mt-1 text-sm text-slate-950 dark:text-slate-100">{profile.fullName}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-slate-500">{t('common.email')}</dt>
              <dd className="mt-1 break-all text-sm text-slate-950 dark:text-slate-100">{profile.email}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-slate-500">{t('profile.phone')}</dt>
              <dd className="mt-1 text-sm text-slate-950 dark:text-slate-100">{profile.phone || t('common.notUpdated')}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-slate-500">{t('profile.roles')}</dt>
              <dd className="mt-1 text-sm text-slate-950 dark:text-slate-100">{profile.roles.map((role) => formatRole(role, locale)).join(', ')}</dd>
            </div>
          </dl>

          <div>
            <h3 className="text-sm font-semibold text-slate-950 dark:text-white">{t('profile.labInfo')}</h3>
            {managedLabEntries.length ? (
              <ul className="mt-3 divide-y divide-slate-200 rounded-md border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                {managedLabEntries.map((lab) => (
                  <li
                    className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm"
                    key={lab.key}
                  >
                    <span className="min-w-0 break-words font-medium text-slate-950 dark:text-slate-100">{lab.name}</span>
                    <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">
                      {lab.status}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{t('profile.noLabs')}</p>
            )}
          </div>
        </div>
      ) : (
        <form className="mt-6 max-w-xl space-y-5" onSubmit={handleSubmit(onSubmit)}>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="fullName">
              {t('profile.fullName')}
            </label>
            <input
              id="fullName"
              className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              disabled={updateProfileMutation.isPending}
              {...register('fullName')}
            />
            {errors.fullName ? <p className="mt-2 text-sm text-red-600">{errors.fullName.message}</p> : null}
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="phone">
              {t('profile.phone')}
            </label>
            <input
              id="phone"
              className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              disabled={updateProfileMutation.isPending}
              placeholder="+84901234567"
              {...register('phone')}
            />
            {errors.phone ? <p className="mt-2 text-sm text-red-600">{errors.phone.message}</p> : null}
          </div>

          {canEditAvatarUrl ? (
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="avatarUrl">
                {t('profile.avatarUrl')}
              </label>
              <input
                id="avatarUrl"
                className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                disabled={updateProfileMutation.isPending}
                placeholder="https://example.com/avatar.jpg"
                {...register('avatarUrl')}
              />
              {errors.avatarUrl ? <p className="mt-2 text-sm text-red-600">{errors.avatarUrl.message}</p> : null}
            </div>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <Button
              disabled={updateProfileMutation.isPending || !isDirty}
              loading={updateProfileMutation.isPending}
              loadingText={t('profile.saving')}
              type="submit"
            >
              {t('common.save')}
            </Button>
            <Button
              disabled={updateProfileMutation.isPending}
              variant="outline"
              onClick={() => {
                reset({
                  fullName: profile.fullName ?? '',
                  phone: profile.phone ?? '',
                  avatarUrl: profile.avatarUrl ?? '',
                });
                setFormMessage(null);
                setIsEditing(false);
              }}
            >
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      )}
      </section>
      {isStudent ? <FaceProfilePage embedded /> : null}
    </div>
  );
}
