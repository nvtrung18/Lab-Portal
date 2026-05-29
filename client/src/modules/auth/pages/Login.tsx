import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';

import { loginAPI } from '../api';
import { PasswordVisibilityIcon } from '../components/PasswordVisibilityIcon';
import { getPrimaryRedirectPath, useAuth } from '../hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
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
    return response?.message ?? 'Email hoặc mật khẩu không chính xác';
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Không thể đăng nhập. Vui lòng thử lại sau.';
}

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { saveSession } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

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
      queryClient.clear();
      const result = await loginAPI(values);
      const { user, profile } = await saveSession(result.token);
      queryClient.setQueryData(USER_ME_QUERY_KEY, profile);
      console.log('[Auth] User profile loaded after login:', {
        id: user.id,
        email: user.email,
        roles: user.roles,
      });
      const returnUrl = searchParams.get('returnUrl');
      navigate(returnUrl || getPrimaryRedirectPath(user.roles), { replace: true });
    } catch (error) {
      setServerError(getErrorMessage(error));
    }
  };

  return (
    <section className="min-w-0 w-full rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-950">Đăng nhập</h1>
        <p className="mt-2 break-words text-sm text-slate-600">
          Đăng nhập để vào đúng không gian làm việc theo vai trò.
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
          <div className="flex flex-wrap items-center justify-between gap-2">
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="password"
            >
              Mật khẩu
            </label>
            <Link className="shrink-0 text-sm font-medium text-slate-700 hover:underline" to="/forgot-password">
              Quên mật khẩu?
            </Link>
          </div>
          <div className="relative mt-2">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              className="block w-full rounded-md border border-slate-300 px-3 py-2 pr-12 text-sm text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="Nhập mật khẩu"
              disabled={isSubmitting}
              {...register('password')}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-600 hover:text-slate-950 disabled:cursor-not-allowed disabled:text-slate-300"
              disabled={isSubmitting}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              onClick={() => setShowPassword((value) => !value)}
            >
              <PasswordVisibilityIcon visible={showPassword} />
            </button>
          </div>
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

        <Button
          className="w-full"
          loading={isSubmitting}
          loadingText="Đang đăng nhập..."
          size="lg"
          type="submit"
        >
          Đăng nhập
        </Button>
      </form>

      <div className="mt-6 text-center text-sm text-slate-600">
        Chưa có tài khoản?{' '}
        <Link className="font-semibold text-slate-950 hover:underline" to="/register">
          Đăng ký tài khoản
        </Link>
      </div>
    </section>
  );
}
