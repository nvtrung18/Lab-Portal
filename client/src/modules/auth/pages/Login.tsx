import { zodResolver } from '@hookform/resolvers/zod';
import { AxiosError } from 'axios';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';

import { loginAPI } from '../api';
import {
  AUTH_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
} from '../../../shared/api';
import type { Response } from '../../../shared/types';

const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập email')
    .email('Email không đúng định dạng'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

function getErrorMessage(error: unknown) {
  if (error instanceof AxiosError) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? 'Email hoặc mật khẩu không chính xác';
  }

  return 'Không thể đăng nhập. Vui lòng thử lại sau.';
}

export function LoginPage() {
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

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

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null);

    try {
      const auth = await loginAPI(values);
      const accessToken = auth.accessToken ?? auth.token;

      if (!accessToken) {
        setServerError('Phản hồi đăng nhập không có access token.');
        return;
      }

      localStorage.setItem(AUTH_TOKEN_KEY, accessToken);

      if (auth.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
      }

      navigate('/', { replace: true });
    } catch (error) {
      setServerError(getErrorMessage(error));
    }
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
      <div>
        <h1 className="text-2xl font-semibold text-slate-950">Đăng nhập</h1>
        <p className="mt-2 text-sm text-slate-600">
          Sử dụng tài khoản Lab Portal để tiếp tục.
        </p>
      </div>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label
            className="block text-sm font-medium text-slate-700"
            htmlFor="email"
          >
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
            placeholder="admin@labportal.com"
            disabled={isSubmitting}
            {...register('email')}
          />
          {errors.email ? (
            <p className="mt-2 text-sm text-red-600">{errors.email.message}</p>
          ) : null}
        </div>

        <div>
          <label
            className="block text-sm font-medium text-slate-700"
            htmlFor="password"
          >
            Mật khẩu
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
            placeholder="Nhập mật khẩu"
            disabled={isSubmitting}
            {...register('password')}
          />
          {errors.password ? (
            <p className="mt-2 text-sm text-red-600">
              {errors.password.message}
            </p>
          ) : null}
        </div>

        {serverError ? (
          <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </div>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting}
          className="flex w-full items-center justify-center rounded-md bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        Chưa có tài khoản?{' '}
        <Link className="font-medium text-slate-950 hover:underline" to="/register">
          Đăng ký
        </Link>
      </p>
    </div>
  );
}
