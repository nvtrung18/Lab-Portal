import { X } from 'lucide-react';
import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

import { useI18n } from '../i18n';
import { Button } from './Button';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';

interface ModalProps {
  id?: string;
  placement?: 'dialog' | 'drawer';
  closeOnBackdrop?: boolean;
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

let openModalCount = 0;
let originalBodyOverflow = '';
let backgroundRoot: HTMLElement | null = null;
let originalRootInert = false;

export function Modal({
  id,
  placement = 'dialog',
  closeOnBackdrop = false,
  backdropBlur = false,
  centered = true,
  children,
  closeDisabled = false,
  closeLabel,
  closeOnEscape = true,
  footer,
  isOpen = true,
  onClose,
  subtitle,
  title,
  size = 'md',
}: ModalProps) {
  const { t } = useI18n();
  const resolvedCloseLabel = closeLabel ?? t('common.close');
  const titleId = useId();
  const dialogRef = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  const closeDisabledRef = useRef(closeDisabled);
  const closeOnEscapeRef = useRef(closeOnEscape);
  onCloseRef.current = onClose;
  closeDisabledRef.current = closeDisabled;
  closeOnEscapeRef.current = closeOnEscape;

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (openModalCount === 0) {
      originalBodyOverflow = document.body.style.overflow;
      backgroundRoot = document.getElementById('root');
      originalRootInert = backgroundRoot?.inert ?? false;
      if (backgroundRoot) backgroundRoot.inert = true;
      document.body.style.overflow = 'hidden';
    }
    openModalCount += 1;
    const focusableSelector = [
      'button:not(:disabled)',
      'a[href]',
      'input:not(:disabled):not([type="hidden"])',
      'select:not(:disabled)',
      'textarea:not(:disabled)',
      '[contenteditable="true"]',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',');
    const getControls = () => Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(focusableSelector) ?? [])
      .filter(control => control.getClientRects().length > 0 && !control.matches(':disabled') && !control.closest('[inert], [hidden], [aria-hidden="true"]'));
    const focusFirstControl = window.requestAnimationFrame(() => {
      (getControls()[0] ?? dialogRef.current)?.focus();
    });
    const handleKeyDown = (event: KeyboardEvent) => {
      const dialogs = document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]');
      if (dialogs[dialogs.length - 1] !== dialogRef.current) return;
      if (event.key === 'Escape' && closeOnEscapeRef.current && !closeDisabledRef.current) {
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const controls = getControls();
      if (controls.length === 0) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialogRef.current.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !dialogRef.current.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFirstControl);
      window.removeEventListener('keydown', handleKeyDown);
      openModalCount -= 1;
      if (openModalCount === 0) {
        document.body.style.overflow = originalBodyOverflow;
        if (backgroundRoot) backgroundRoot.inert = originalRootInert;
        backgroundRoot = null;
      }
      if (previousFocus?.isConnected && !previousFocus.closest('[inert]')) previousFocus.focus();
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || placement !== 'drawer') return;
    const desktop = window.matchMedia('(min-width: 1024px)');
    const closeOnDesktop = () => { if (desktop.matches) onCloseRef.current(); };
    closeOnDesktop();
    desktop.addEventListener('change', closeOnDesktop);
    return () => desktop.removeEventListener('change', closeOnDesktop);
  }, [isOpen, placement]);

  if (!isOpen) {
    return null;
  }

  return createPortal(
    <div className={`app-shell app-modal-layer fixed inset-0 z-modal flex overscroll-contain ${placement === 'drawer' ? 'items-stretch justify-start lg:hidden' : `justify-center overflow-y-auto p-3 sm:p-6 ${centered ? 'items-center' : 'items-start'}`} ${backdropBlur ? 'backdrop-blur-sm' : ''}`}
      onClick={event => { if (closeOnBackdrop && event.target === event.currentTarget && !closeDisabled) onClose(); }}>
      <section
        id={id}
        ref={dialogRef}
        aria-labelledby={titleId}
        aria-modal="true"
        className={`app-dialog flex min-w-0 flex-col overflow-hidden border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-900 ${placement === 'drawer' ? 'app-sidebar app-navigation-dialog h-dvh w-72 max-w-[85vw]' : `max-h-[calc(100dvh-1.5rem)] w-full rounded-xl sm:max-h-[calc(100dvh-3rem)] ${SIZE_CLASSES[size]}`}`}
        role="dialog"
        tabIndex={-1}
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-800 sm:px-6 sm:py-4">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold text-slate-950 dark:text-white" id={titleId}>
              {title}
            </h3>
            {subtitle ? <div className="mt-1 text-sm text-slate-600 dark:text-slate-300">{subtitle}</div> : null}
          </div>
          <Button aria-label={resolvedCloseLabel} className="h-11 w-11 min-w-11 shrink-0 p-0" disabled={closeDisabled} onClick={onClose} size="sm" variant="ghost">
            <X aria-hidden="true" className="h-5 w-5" />
            <span className="sr-only">{resolvedCloseLabel}</span>
          </Button>
        </header>
        <div className="app-dialog-body min-h-0 min-w-0 flex-1 overscroll-contain overflow-y-auto px-4 py-4 sm:px-6 sm:py-5">
          {children}
        </div>
        {footer ? (
          <footer className="flex shrink-0 flex-col-reverse justify-end gap-2 border-t border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-950 sm:flex-row sm:px-6 sm:py-4">
            {footer}
          </footer>
        ) : null}
      </section>
    </div>, document.body,
  );
}
