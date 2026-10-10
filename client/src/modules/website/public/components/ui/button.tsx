import { cloneElement, isValidElement, type ComponentProps } from 'react';
import { cn } from '../../lib/utils';

const variants = { default: 'button-primary', outline: 'button-outline', ghost: 'button-ghost', light: 'button-light', destructive: 'button-destructive' };
const sizes = { default: '', sm: 'button-sm', icon: 'button-icon' };
export function Button({ className, variant = 'default', size = 'default', asChild = false, children, ...props }:
  ComponentProps<'button'> & { variant?: keyof typeof variants; size?: keyof typeof sizes; asChild?: boolean }) {
  const resolved = cn('button', variants[variant], sizes[size], className);
  if (asChild && isValidElement<{ className?: string }>(children)) {
    return cloneElement(children, { ...props, className: cn(resolved, children.props.className) });
  }
  return <button {...props} className={resolved}>{children}</button>;
}
