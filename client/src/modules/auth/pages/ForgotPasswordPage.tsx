import axios from 'axios';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

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
    <section className="min-w-0 w-full rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-8">
      <h1 className="text-2xl font-semibold text-slate-950 dark:text-white">{t('auth.forgotTitle')}</h1>

      <div className="my-6 grid w-full grid-cols-3 items-center gap-2 text-center">
        {steps.map((item, index) => (
          <div
            key={item.key}
            className={[
              'flex min-w-0 items-center justify-center break-words text-xs font-medium text-slate-400 sm:text-sm',
              index === currentStepIndex ? 'font-bold text-slate-950 dark:text-white' : '',
              index < currentStepIndex ? 'text-emerald-700' : '',
            ].join(' ')}
          >
            {index + 1}. {t(item.labelKey)}
          </div>
        ))}
      </div>

      {step === 'email' ? (
        <form className="space-y-5" onSubmit={handleSendCode}>
          <input
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
            placeholder={t('auth.email')}
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
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
          <p className="break-words rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:bg-slate-950 dark:text-slate-300">
            {t('auth.codeSentTo')}{' '}
            <span className="font-semibold text-slate-950 dark:text-white">{normalizedEmail}</span>
          </p>
          <input
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
            inputMode="numeric"
            maxLength={6}
            placeholder={t('auth.codeSixDigits')}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
          />
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
          <p className="break-words rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-200">
            {t('auth.validCodeFor')}{' '}
            <span className="font-semibold text-emerald-900">{normalizedEmail}</span>
          </p>
          <div className="relative">
            <input
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 pr-16 text-sm text-slate-950 outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              placeholder={t('auth.newPassword')}
              type={showPassword ? 'text' : 'password'}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <button
              aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
              className="absolute inset-y-0 right-0 flex w-14 items-center justify-center text-xs font-semibold text-slate-600 dark:text-slate-300"
              onClick={() => setShowPassword((value) => !value)}
              type="button"
            >
              {showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
            </button>
          </div>
          <div className="relative">
            <input
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 pr-16 text-sm text-slate-950 outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:focus:border-white"
              placeholder={t('auth.confirmPassword')}
              type={showConfirmPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
            <button
              aria-label={showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')}
              className="absolute inset-y-0 right-0 flex w-14 items-center justify-center text-xs font-semibold text-slate-600 dark:text-slate-300"
              onClick={() => setShowConfirmPassword((value) => !value)}
              type="button"
            >
              {showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')}
            </button>
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
        <div className="mt-5 break-words rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
          {error}
        </div>
      ) : null}
      {message ? (
        <div className="mt-5 break-words rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200">
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
