import axios from 'axios';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Eye, EyeOff, FlaskConical } from 'lucide-react';

import { googleLoginAPI, loginAPI, registerAPI, sendRegisterCodeAPI, verifyRegisterCodeAPI } from '../api';
import { GoogleSignInButton } from '../components';
import { getPostAuthRedirectPath, useAuth } from '../hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
import { translate, useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';

type RegisterStep = 'email' | 'otp' | 'profile';

const steps: Array<{ key: RegisterStep; labelKey: string }> = [
  { key: 'email', labelKey: 'auth.email' },
  { key: 'otp', labelKey: 'auth.verificationCode' },
  { key: 'profile', labelKey: 'auth.account' },
];

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? response?.errors?.[0] ?? translate('auth.registerFailed');
  }

  return translate('auth.registerFailed');
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function RegisterPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnUrl = searchParams.get('returnUrl');
  const queryClient = useQueryClient();
  const { saveSession } = useAuth();
  const [step, setStep] = useState<RegisterStep>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [verificationToken, setVerificationToken] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);

  const normalizedEmail = email.trim().toLowerCase();
  const currentStepIndex = steps.findIndex((item) => item.key === step);

  const completeAuthentication = async (token: string) => {
    queryClient.clear();
    const { user, profile } = await saveSession(token);
    queryClient.setQueryData(USER_ME_QUERY_KEY, profile);
    navigate(getPostAuthRedirectPath(user, returnUrl), { replace: true });
  };

  const handleSendCode = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!normalizedEmail) {
      setError(VALIDATION_MESSAGES.required);
      return;
    }
    if (!isValidEmail(normalizedEmail)) {
      setError(VALIDATION_MESSAGES.email);
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await sendRegisterCodeAPI({ email: normalizedEmail });
      setEmail(result.email || normalizedEmail);
      setMessage(result.message);
      setStep('otp');
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleVerifyCode = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!/^\d{6}$/.test(code.trim())) {
      setError(t('auth.codeSixDigits'));
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await verifyRegisterCodeAPI({
        email: normalizedEmail,
        code: code.trim(),
      });
      setVerificationToken(result.verificationToken);
      setMessage(result.message);
      setStep('profile');
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCompleteRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!verificationToken) {
      setError(t('auth.emailSessionExpired'));
      setStep('email');
      return;
    }
    if (!fullName.trim()) {
      setError(VALIDATION_MESSAGES.required);
      return;
    }
    if (password.length < 8) {
      setError(VALIDATION_MESSAGES.password);
      return;
    }
    if (password !== confirmPassword) {
      setError(t('auth.passwordMismatch'));
      return;
    }

    setIsSubmitting(true);
    try {
      await registerAPI({
        email: normalizedEmail,
        verificationToken,
        fullName: fullName.trim(),
        password,
      });
      const loginResult = await loginAPI({ email: normalizedEmail, password });
      await completeAuthentication(loginResult.token);
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGoogleCredential = async (credential: string) => {
    setError('');
    setMessage('');
    setIsGoogleSubmitting(true);
    try {
      const result = await googleLoginAPI({ credential });
      await completeAuthentication(result.token);
    } catch (submitError) {
      setError(getErrorMessage(submitError));
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
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 dark:text-white">{t('auth.registerTitle')}</h1>
        </div>
      </div>
      <p className="mb-6 text-sm text-slate-600 dark:text-slate-300">{t('auth.registerDescription')}</p>

      <div>
        <GoogleSignInButton
          disabled={isSubmitting || isGoogleSubmitting}
          onCredential={handleGoogleCredential}
          text="signup_with"
        />
      </div>
      <div className="my-6 flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">{t('auth.orRegisterByEmail')}</span>
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
      </div>

      <div className="mb-6 grid w-full grid-cols-3 items-center gap-2 text-center">
        {steps.map((item, index) => (
          <div
            key={item.key}
            className={[
              'flex min-w-0 items-center justify-center break-words text-xs font-medium text-slate-400 sm:text-sm',
              index === currentStepIndex ? 'font-bold text-slate-950 dark:text-white' : '',
              index < currentStepIndex ? 'text-emerald-700 dark:text-emerald-400' : '',
            ].join(' ')}
          >
            {index + 1}. {t(item.labelKey)}
          </div>
        ))}
      </div>

      {step === 'email' ? (
        <form className="space-y-5" onSubmit={handleSendCode}>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="register-email">{t('auth.email')}</label>
            <input
              autoComplete="email"
              className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              id="register-email"
              placeholder={t('auth.email')}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText={t('auth.sending')}
            size="lg"
            type="submit"
          >
            {t('auth.sendVerificationCode')}
          </Button>
        </form>
      ) : null}

      {step === 'otp' ? (
        <form className="space-y-5" onSubmit={handleVerifyCode}>
          <p className="break-words rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:bg-slate-950 dark:text-slate-300">
            {t('auth.codeSentTo')}{' '}
            <span className="font-semibold text-slate-950 dark:text-white">{normalizedEmail}</span>
          </p>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="register-code">{t('auth.verificationCode')}</label>
            <input
              autoComplete="one-time-code"
              className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              id="register-code"
              inputMode="numeric"
              maxLength={6}
              placeholder={t('auth.codeSixDigits')}
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
            />
          </div>
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText={t('auth.verifying')}
            size="lg"
            type="submit"
          >
            {t('auth.verifyEmail')}
          </Button>
          <Button
            className="w-full"
            disabled={isSubmitting}
            onClick={() => setStep('email')}
            size="lg"
            variant="outline"
          >
            {t('auth.changeEmail')}
          </Button>
        </form>
      ) : null}

      {step === 'profile' ? (
        <form className="space-y-5" onSubmit={handleCompleteRegister}>
          <p className="break-words rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-200">
            {t('auth.emailVerified')}{' '}
            <span className="font-semibold text-emerald-900 dark:text-emerald-100">{normalizedEmail}</span>
          </p>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="register-name">{t('auth.fullName')}</label>
            <input
              autoComplete="name"
              className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              id="register-name"
              placeholder={t('auth.fullName')}
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="register-password">{t('auth.password')}</label>
            <div className="relative mt-2">
              <input
                autoComplete="new-password"
                className="block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 pr-14 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
                id="register-password"
                placeholder={t('auth.password')}
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
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
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="register-confirm-password">{t('auth.confirmPassword')}</label>
            <div className="relative mt-2">
              <input
                autoComplete="new-password"
                className="block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 pr-14 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
                id="register-confirm-password"
                placeholder={t('auth.confirmPassword')}
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
              <button
                type="button"
                className="app-button-ghost absolute right-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-xl p-0 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300 dark:text-slate-300 dark:hover:text-white"
                disabled={isSubmitting}
                aria-label={showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                onClick={() => setShowConfirmPassword((value) => !value)}
              >
                {showConfirmPassword ? (
                  <EyeOff size={18} />
                ) : (
                  <Eye size={18} />
                )}
              </button>
            </div>
          </div>
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText={t('auth.registering')}
            size="lg"
            type="submit"
          >
            {t('auth.register')}
          </Button>
        </form>
      ) : null}

      {error ? (
        <div className="mt-5 break-words rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
          {error}
        </div>
      ) : null}
      {message ? (
        <div className="mt-5 break-words rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200" role="status">
          {message}
        </div>
      ) : null}

      <div className="mt-6 text-center text-sm text-slate-600 dark:text-slate-300">
        {t('auth.alreadyAccount')}{' '}
        <Link className="font-semibold text-slate-950 hover:underline dark:text-white"
          to={`/login${returnUrl ? `?returnUrl=${encodeURIComponent(returnUrl)}` : ''}`}>
          {t('auth.signIn')}
        </Link>
      </div>
    </section>
  );
}
