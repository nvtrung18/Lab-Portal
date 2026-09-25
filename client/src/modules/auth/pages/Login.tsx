import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { Eye, EyeOff, FlaskConical } from 'lucide-react';

import { googleLoginAPI, loginAPI, type LoginResult } from '../api';
import { GoogleSignInButton } from '../components';
import { getPrimaryRedirectPath, useAuth } from '../hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
import { translate, useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';


const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, VALIDATION_MESSAGES.required)
    .email(VALIDATION_MESSAGES.email),
  password: z.string().min(1, VALIDATION_MESSAGES.required),
});

type LoginFormValues = z.infer<typeof loginSchema>;

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? translate('auth.invalidCredentials');
  }

  if (error instanceof Error) {
    return error.message;
  }

  return translate('auth.loginFailed');
}

export function getSafeReturnPath(returnUrl: string | null, fallback: string) {
  if (
    !returnUrl
    || !returnUrl.startsWith('/')
    || returnUrl.startsWith('//')
    || returnUrl.includes('\\')
  ) {
    return fallback;
  }

  try {
    const parsed = new URL(returnUrl, 'https://lab-portal.invalid');
    return parsed.origin === 'https://lab-portal.invalid'
      ? `${parsed.pathname}${parsed.search}${parsed.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}

export function LoginPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { saveSession } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const completeLogin = async (result: LoginResult) => {
    queryClient.clear();
    const { user, profile } = await saveSession(result.token);
    queryClient.setQueryData(USER_ME_QUERY_KEY, profile);
    const returnUrl = searchParams.get('returnUrl');
    navigate(getSafeReturnPath(returnUrl, getPrimaryRedirectPath(user.roles)), { replace: true });
  };

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null);

    try {
      await completeLogin(await loginAPI(values));
    } catch (error) {
      setServerError(getErrorMessage(error));
    }
  };

  const onGoogleCredential = async (credential: string) => {
    setServerError(null);
    setIsGoogleSubmitting(true);
    try {
      await completeLogin(await googleLoginAPI({ credential }));
    } catch (error) {
      setServerError(getErrorMessage(error));
    } finally {
      setIsGoogleSubmitting(false);
    }
  };

  return (
    <section className="app-surface-elevated min-w-0 w-full rounded-2xl p-5 sm:p-8">
      <div className="mb-7 flex items-center gap-3">
        <span className="app-icon-badge flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl">
          <FlaskConical aria-hidden="true" className="h-6 w-6" />
        </span>
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-600 dark:text-slate-300">{t('common.appTitle')}</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 dark:text-white">{t('auth.loginTitle')}</h1>
        </div>
      </div>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label
            className="block text-sm font-medium text-slate-700 dark:text-slate-200"
            htmlFor="email"
          >
            {t('auth.email')}
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
            placeholder="admin@labportal.com"
            disabled={isSubmitting}
            {...register('email')}
          />
          {errors.email ? (
            <p className="mt-2 text-sm text-red-600">{errors.email.message}</p>
          ) : null}
        </div>

        <div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <label
              className="block text-sm font-medium text-slate-700 dark:text-slate-200"
              htmlFor="password"
            >
              {t('auth.password')}
            </label>
            <Link className="shrink-0 text-sm font-medium text-slate-700 hover:underline dark:text-slate-300" to="/forgot-password">
              {t('auth.forgotPassword')}
            </Link>
          </div>
          <div className="relative mt-2">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              className="block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 pr-14 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              placeholder={t('auth.password')}
              disabled={isSubmitting}
              {...register('password')}
            />
            <button
              type="button"
              className="app-button-ghost absolute right-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-xl p-0 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300 dark:text-slate-300 dark:hover:text-white"
              disabled={isSubmitting}
              aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
              onClick={() => setShowPassword((value) => !value)}
            >
              {showPassword ? (
                <EyeOff size={18} />
              ) : (
                <Eye size={18} />
              )}
            </button>
          </div>
          {errors.password ? (
            <p className="mt-2 text-sm text-red-600">
              {errors.password.message}
            </p>
          ) : null}
        </div>

        {serverError ? (
          <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
            {serverError}
          </div>
        ) : null}

        <Button
          className="w-full"
          loading={isSubmitting}
          loadingText={t('auth.loggingIn')}
          size="lg"
          type="submit"
        >
          {t('auth.login')}
        </Button>
      </form>

      <div className="my-6 flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-slate-200" />
        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">{t('auth.or')}</span>
        <span className="h-px flex-1 bg-slate-200" />
      </div>
      <GoogleSignInButton
        disabled={isSubmitting || isGoogleSubmitting}
        onCredential={onGoogleCredential}
      />

      <div className="mt-6 text-center text-sm text-slate-600 dark:text-slate-300">
        {t('auth.noAccount')}{' '}
        <Link className="font-semibold text-slate-950 hover:underline dark:text-white" to="/register">
          {t('auth.register')}
        </Link>
      </div>
    </section>
  );
}
