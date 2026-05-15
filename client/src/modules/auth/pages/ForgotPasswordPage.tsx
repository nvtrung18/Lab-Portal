import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useState } from 'react';

const forgotPasswordSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập email')
    .email('Email không đúng định dạng'),
});

type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export function ForgotPasswordPage() {
  const [message, setMessage] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: '',
    },
  });

  const onSubmit = async () => {
    setMessage(
      'Chức năng quên mật khẩu sẽ được hoàn thiện sau. Vui lòng liên hệ quản trị viên để được hỗ trợ.',
    );
  };

  return (
    <section className="w-full rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-950">Quên mật khẩu?</h1>
        <p className="mt-2 text-sm text-slate-600">
          Nhập email tài khoản để nhận hướng dẫn hỗ trợ.
        </p>
      </div>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label className="block text-sm font-medium text-slate-700" htmlFor="email">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
            placeholder="student@gmail.com"
            disabled={isSubmitting}
            {...register('email')}
          />
          {errors.email ? (
            <p className="mt-2 text-sm text-red-600">{errors.email.message}</p>
          ) : null}
        </div>

        {message ? (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
            {message}
          </div>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting}
          className="flex w-full items-center justify-center rounded-md bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {isSubmitting ? 'Đang gửi...' : 'Gửi yêu cầu'}
        </button>
      </form>

      <div className="mt-6 text-center text-sm text-slate-600">
        <Link className="font-semibold text-slate-950 hover:underline" to="/login">
          Quay lại đăng nhập
        </Link>
      </div>
    </section>
  );
}
