import { forwardRef } from 'react';
import type { ButtonHTMLAttributes } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'outline' | 'ghost' | 'success';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  loadingText?: string;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'app-button app-button-primary shadow-sm focus-visible:ring-slate-500',
  secondary: 'app-button app-button-secondary focus-visible:ring-slate-400',
  outline:
    'app-button app-button-outline border shadow-sm focus-visible:ring-slate-400',
  ghost: 'app-button app-button-ghost focus-visible:ring-slate-400',
  danger: 'app-button app-button-danger shadow-sm focus-visible:ring-red-500',
  success: 'app-button app-button-success shadow-sm focus-visible:ring-emerald-500',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'min-h-11 w-32 px-3 py-2 text-sm',
  md: 'min-h-11 w-40 px-4 py-2 text-sm',
  lg: 'min-h-12 w-48 px-5 py-3 text-base',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    children,
    className = '',
    disabled,
    loading = false,
    loadingText,
    size = 'md',
    type = 'button',
    variant = 'primary',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      aria-busy={loading || undefined}
      className={[
        'inline-flex shrink-0 items-center justify-center gap-2 rounded-lg font-semibold leading-none transition-colors duration-200',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed disabled:opacity-60',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        className,
      ].join(' ')}
      disabled={disabled || loading}
      type={type}
      {...props}
    >
      {loading && loadingText ? loadingText : children}
    </button>
  );
});
