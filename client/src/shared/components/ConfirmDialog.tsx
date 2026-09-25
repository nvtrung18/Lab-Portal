import type { ReactNode } from 'react';

import { useI18n } from '../i18n';
import { Button } from './Button';
import { Modal } from './Modal';

interface ConfirmDialogProps {
  cancelLabel?: string;
  confirmLabel?: string;
  isOpen: boolean;
  isPending?: boolean;
  message: ReactNode;
  onCancel: () => void;
  onConfirm: () => void;
  title: ReactNode;
  variant?: 'danger' | 'primary' | 'success';
}

export function ConfirmDialog({
  cancelLabel,
  confirmLabel,
  isOpen,
  isPending = false,
  message,
  onCancel,
  onConfirm,
  title,
  variant = 'danger',
}: ConfirmDialogProps) {
  const { t } = useI18n();

  return (
    <Modal
      centered
      closeDisabled={isPending}
      closeOnEscape
      footer={(
        <>
          <Button disabled={isPending} onClick={onCancel} variant="secondary">
            {cancelLabel ?? t('common.cancel')}
          </Button>
          <Button loading={isPending} onClick={onConfirm} variant={variant}>
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </>
      )}
      isOpen={isOpen}
      onClose={onCancel}
      size="sm"
      title={title}
    >
      <div className="text-sm leading-6 text-slate-600 dark:text-slate-300">{message}</div>
    </Modal>
  );
}
