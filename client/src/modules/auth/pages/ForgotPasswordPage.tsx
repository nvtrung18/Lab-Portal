import axios from 'axios';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, FlaskConical } from 'lucide-react';

import {
  resetPasswordAPI,
  sendForgotPasswordCodeAPI,
  verifyForgotPasswordCodeAPI,
} from '../api';
import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
import { translate, useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';

type ForgotPasswordStep = 'email' | 'otp' | 'password';

const steps: Array<{ key: ForgotPasswordStep; labelKey: string }> = [
  { key: 'email', labelKey: 'auth.email' },
  { key: 'otp', labelKey: 'auth.verificationCode' },
  { key: 'password', labelKey: 'auth.newPassword' },
];

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return (
      response?.message ??
      response?.errors?.[0] ??
      translate('auth.forgotCodeFailed')
    );
  }

  return translate('auth.forgotCodeFailed');
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function ForgotPasswordPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [step, setStep] = useState<ForgotPasswordStep>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const normalizedEmail = email.trim().toLowerCase();
  const currentStepIndex = steps.findIndex((item) => item.key === step);

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
      const result = await sendForgotPasswordCodeAPI({ email: normalizedEmail });
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
      const result = await verifyForgotPasswordCodeAPI({
        email: normalizedEmail,
        code: code.trim(),
      });
      setResetToken(result.resetToken);
      setMessage(result.message);
      setStep('password');
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetPassword = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!resetToken) {
      setError(t('auth.resetSessionExpired'));
      setStep('email');
      return;
    }
    if (newPassword.length < 8) {
      setError(VALIDATION_MESSAGES.password);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t('auth.passwordMismatch'));
      return;
    }

    setIsSubmitting(true);
    try {
      await resetPasswordAPI({
        email: normalizedEmail,
        resetToken,
        newPassword,
      });
      navigate('/login', { replace: true });
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
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
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 dark:text-white">{t('auth.forgotTitle')}</h1>
        </div>
      </div>
      <p className="mb-6 text-sm text-slate-600 dark:text-slate-300">{t('auth.forgotDescription')}</p>

      <div className="my-6 grid w-full grid-cols-3 items-center gap-2 text-center">
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
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="forgot-email">{t('auth.email')}</label>
            <input
              autoComplete="email"
              className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              id="forgot-email"
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
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="forgot-code">{t('auth.verificationCode')}</label>
            <input
              autoComplete="one-time-code"
              className="mt-2 block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              id="forgot-code"
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
            {t('auth.verifyCode')}
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

      {step === 'password' ? (
        <form className="space-y-5" onSubmit={handleResetPassword}>
          <p className="break-words rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-200">
            {t('auth.validCodeFor')}{' '}
            <span className="font-semibold text-emerald-900 dark:text-emerald-100">{normalizedEmail}</span>
          </p>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="forgot-new-password">{t('auth.newPassword')}</label>
            <div className="relative mt-2">
              <input
                autoComplete="new-password"
                className="block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 pr-14 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
                id="forgot-new-password"
                placeholder={t('auth.newPassword')}
                type={showPassword ? 'text' : 'password'}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
              <button
                aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                className="app-button-ghost absolute right-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-xl p-0 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300 dark:text-slate-300 dark:hover:text-white"
                onClick={() => setShowPassword((value) => !value)}
                type="button"
              >
                {showPassword ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="forgot-confirm-password">{t('auth.confirmPassword')}</label>
            <div className="relative mt-2">
              <input
                autoComplete="new-password"
                className="block min-h-12 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 pr-14 text-base text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
                id="forgot-confirm-password"
                placeholder={t('auth.confirmPassword')}
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
              <button
                aria-label={showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                className="app-button-ghost absolute right-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-xl p-0 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300 dark:text-slate-300 dark:hover:text-white"
                onClick={() => setShowConfirmPassword((value) => !value)}
                type="button"
              >
                {showConfirmPassword ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
              </button>
            </div>
          </div>
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText={t('auth.updating')}
            size="lg"
            type="submit"
          >
            {t('auth.resetPassword')}
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
        <Link className="font-semibold text-slate-950 hover:underline dark:text-white" to="/login">
          {t('auth.backToLogin')}
        </Link>
      </div>
    </section>
  );
}
