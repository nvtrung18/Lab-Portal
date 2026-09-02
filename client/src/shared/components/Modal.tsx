import { useEffect, useId } from 'react';
import type { ReactNode } from 'react';

import { Button } from './Button';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';

interface ModalProps {
  backdropBlur?: boolean;
  centered?: boolean;
  children: ReactNode;
  closeDisabled?: boolean;
  closeLabel?: string;
  closeOnEscape?: boolean;
  footer?: ReactNode;
  isOpen?: boolean;
  onClose: () => void;
  subtitle?: ReactNode;
  title: ReactNode;
  size?: ModalSize;
}

const SIZE_CLASSES: Record<ModalSize, string> = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-2xl',
  xl: 'max-w-3xl',
  '2xl': 'max-w-5xl',
  full: 'max-w-7xl',
};

export function Modal({
  backdropBlur = false,
  centered = false,
  children,
  closeDisabled = false,
  closeLabel = 'Đóng',
  closeOnEscape = false,
  footer,
  isOpen = true,
  onClose,
  subtitle,
  title,
  size = 'md',
}: ModalProps) {
  const titleId = useId();

  useEffect(() => {
    if (!isOpen || !closeOnEscape) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !closeDisabled) {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [closeDisabled, closeOnEscape, isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className={`fixed inset-0 z-50 flex justify-center overflow-y-auto overscroll-contain bg-slate-950/45 p-2 sm:px-4 sm:py-6 ${centered ? 'items-center' : 'items-start'} ${backdropBlur ? 'backdrop-blur-sm' : ''}`}>
      <section
        aria-labelledby={titleId}
        aria-modal="true"
        className={`flex max-h-[calc(100dvh-1rem)] min-w-0 w-full flex-col overflow-hidden rounded-lg bg-white shadow-xl sm:max-h-[90vh] ${SIZE_CLASSES[size]}`}
        role="dialog"
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-slate-100 px-4 py-3 sm:px-6 sm:py-4">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold text-slate-950" id={titleId}>
              {title}
            </h3>
            {subtitle ? <div className="mt-1 text-sm text-slate-600">{subtitle}</div> : null}
          </div>
          <Button aria-label={closeLabel} disabled={closeDisabled} onClick={onClose} size="sm" variant="ghost">
            {closeLabel}
          </Button>
        </header>
        <div className="min-h-0 min-w-0 flex-1 overscroll-contain overflow-y-auto px-4 py-4 sm:px-6 sm:py-5">
          {children}
        </div>
        {footer ? (
          <footer className="flex shrink-0 flex-col-reverse justify-end gap-2 border-t border-slate-100 bg-white px-4 py-3 sm:flex-row sm:px-6 sm:py-4">
            {footer}
          </footer>
        ) : null}
      </section>
    </div>
  );
}
