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
  primary: 'app-button app-button-primary focus-visible:ring-teal-500',
  secondary: 'app-button app-button-secondary focus-visible:ring-slate-400',
  outline:
    'app-button app-button-outline border focus-visible:ring-teal-500',
  ghost: 'app-button app-button-ghost focus-visible:ring-slate-400',
  danger: 'app-button app-button-danger focus-visible:ring-rose-500',
  success: 'app-button app-button-success focus-visible:ring-emerald-500',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-8 min-h-[32px] px-3 py-1 text-xs font-semibold rounded-md whitespace-nowrap',
  md: 'h-10 min-h-[40px] px-4 py-2 text-sm font-semibold rounded-lg whitespace-nowrap',
  lg: 'h-11 min-h-[44px] px-6 py-2.5 text-sm font-semibold rounded-lg whitespace-nowrap',
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
        'inline-flex shrink-0 items-center justify-center gap-1.5 text-center leading-none whitespace-nowrap select-none transition-all duration-150',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed disabled:opacity-50 active:scale-[0.98]',
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
