import axios from 'axios';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';

import { registerAPI, sendRegisterCodeAPI, verifyRegisterCodeAPI } from '../api';
import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
import { VALIDATION_MESSAGES } from '../../../shared/utils';

type RegisterStep = 'email' | 'otp' | 'profile';

const steps: Array<{ key: RegisterStep; label: string }> = [
  { key: 'email', label: 'Email' },
  { key: 'otp', label: 'Mã xác nhận' },
  { key: 'profile', label: 'Tài khoản' },
];

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? response?.errors?.[0] ?? 'Không thể đăng ký tài khoản.';
  }

  return 'Không thể đăng ký tài khoản. Vui lòng thử lại sau.';
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function RegisterPage() {
  const navigate = useNavigate();
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
      setError('Vui lòng nhập mã xác nhận gồm 6 số.');
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
      setError('Phiên xác thực email đã hết hạn. Vui lòng gửi lại mã xác nhận.');
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
      setError('Mật khẩu nhập lại không khớp.');
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
      navigate('/login', { replace: true });
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="min-w-0 w-full rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <h1 className="text-2xl font-semibold text-slate-950">Đăng ký tài khoản</h1>
      <p className="mt-2 break-words text-sm text-slate-600">
        Tài khoản mới mặc định là STUDENT và cần xác thực email trước khi tạo.
      </p>

      <div className="my-6 grid w-full grid-cols-3 items-center gap-2 text-center">
        {steps.map((item, index) => (
          <div
            key={item.key}
            className={[
              'flex min-w-0 items-center justify-center break-words text-xs font-medium text-slate-400 sm:text-sm',
              index === currentStepIndex ? 'font-bold text-slate-950' : '',
              index < currentStepIndex ? 'text-emerald-700' : '',
            ].join(' ')}
          >
            {index + 1}. {item.label}
          </div>
        ))}
      </div>

      {step === 'email' ? (
        <form className="space-y-5" onSubmit={handleSendCode}>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
            placeholder="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText="Đang gửi..."
            size="lg"
            type="submit"
          >
            Gửi mã xác nhận
          </Button>
        </form>
      ) : null}

      {step === 'otp' ? (
        <form className="space-y-5" onSubmit={handleVerifyCode}>
          <p className="break-words rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700">
            Mã xác nhận đã được gửi tới:{' '}
            <span className="font-semibold text-slate-950">{normalizedEmail}</span>
          </p>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
            inputMode="numeric"
            maxLength={6}
            placeholder="Mã xác nhận 6 số"
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
          />
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText="Đang xác thực..."
            size="lg"
            type="submit"
          >
            Xác thực email
          </Button>
          <Button
            className="w-full"
            disabled={isSubmitting}
            onClick={() => setStep('email')}
            size="lg"
            variant="outline"
          >
            Đổi email
          </Button>
        </form>
      ) : null}

      {step === 'profile' ? (
        <form className="space-y-5" onSubmit={handleCompleteRegister}>
          <p className="break-words rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
            Email đã được xác thực:{' '}
            <span className="font-semibold text-emerald-900">{normalizedEmail}</span>
          </p>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
            placeholder="Họ và tên"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
          />
          <div className="relative">
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 pr-16 text-sm outline-none focus:border-slate-900"
              placeholder="Mật khẩu"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300"
              disabled={isSubmitting}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              onClick={() => setShowPassword((value) => !value)}
            >
              {showPassword ? (
                <EyeOff size={18} />
              ) : (
                <Eye size={18} />
              )}
            </button>
          </div>
          <div className="relative">
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 pr-16 text-sm outline-none focus:border-slate-900"
              placeholder="Nhập lại mật khẩu"
              type={showConfirmPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300"
              disabled={isSubmitting}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              onClick={() => setShowPassword((value) => !value)}
            >
              {showPassword ? (
                <EyeOff size={18} />
              ) : (
                <Eye size={18} />
              )}
            </button>
          </div>
          <Button
            className="w-full"
            loading={isSubmitting}
            loadingText="Đang đăng ký..."
            size="lg"
            type="submit"
          >
            Đăng ký
          </Button>
        </form>
      ) : null}

      {error ? (
        <div className="mt-5 break-words rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      ) : null}
      {message ? (
        <div className="mt-5 break-words rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          {message}
        </div>
      ) : null}

      <div className="mt-6 text-center text-sm text-slate-600">
        Đã có tài khoản?{' '}
        <Link className="font-semibold text-slate-950 hover:underline" to="/login">
          Đăng nhập
        </Link>
      </div>
    </section>
  );
}
