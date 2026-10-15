import { X } from 'lucide-react';
import { useEffect, useId, useRef, type KeyboardEventHandler, type ReactNode } from 'react';
import { useLab } from '../lab-provider';

export function Dialog({ open, onOpenChange, title, description, children, className = '', onKeyDown }: {
  open: boolean; onOpenChange: (open: boolean) => void; title: string; description?: string; children: ReactNode;
  className?: string; onKeyDown?: KeyboardEventHandler<HTMLDialogElement>;
}) {
  const { l } = useLab();
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (open && dialog && !dialog.open) dialog.showModal();
    if (!open && dialog?.open) dialog.close();
  }, [open]);
  return <dialog ref={ref} className={`dialog-content ${className}`} onKeyDown={onKeyDown} aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined}
    onCancel={event => { event.preventDefault(); onOpenChange(false); }}>
    <h2 id={titleId} className="dialog-title">{title}</h2>
    {description && <p id={descriptionId} className="dialog-description">{description}</p>}
    {children}
    <button className="dialog-close" aria-label={l('Close dialog', 'Đóng hộp thoại')} onClick={() => onOpenChange(false)}><X size={20} /></button>
  </dialog>;
}
