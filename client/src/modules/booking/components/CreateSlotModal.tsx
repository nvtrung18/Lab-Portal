import { type FormEvent, useMemo, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import { useCreateSlot } from '../hooks';

interface CreateSlotModalProps {
  labId: number;
  isOpen: boolean;
  onClose: () => void;
}

interface FormState {
  date: string;
  startTime: string;
  endTime: string;
  capacity: string;
  status: string;
}

const initialForm: FormState = {
  date: '',
  startTime: '',
  endTime: '',
  capacity: '',
  status: 'AVAILABLE',
};

function toLocalDateTime(date: string, time: string) {
  return `${date}T${time}:00`;
}

function toApiDateTime(date: string, time: string) {
  return new Date(toLocalDateTime(date, time)).toISOString();
}

export function CreateSlotModal({ labId, isOpen, onClose }: CreateSlotModalProps) {
  const [form, setForm] = useState<FormState>(initialForm);
  const [error, setError] = useState('');
  const createSlot = useCreateSlot();

  const isSubmitting = createSlot.isPending;
  const capacity = Number(form.capacity);
  const validationError = useMemo(() => {
    if (!form.date) {
      return VALIDATION_MESSAGES.required;
    }
    if (!form.startTime) {
      return VALIDATION_MESSAGES.required;
    }
    if (!form.endTime) {
      return VALIDATION_MESSAGES.required;
    }
    if (!Number.isFinite(capacity) || capacity <= 0) {
      return 'Sức chứa phải lớn hơn 0.';
    }

    const start = new Date(toLocalDateTime(form.date, form.startTime));
    const end = new Date(toLocalDateTime(form.date, form.endTime));
    if (start >= end) {
      return VALIDATION_MESSAGES.dateRange;
    }

    return '';
  }, [capacity, form.date, form.endTime, form.startTime]);

  if (!isOpen) {
    return null;
  }

  const updateField = (field: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setError('');
  };

  const handleClose = () => {
    if (isSubmitting) {
      return;
    }
    setForm(initialForm);
    setError('');
    onClose();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      await createSlot.mutateAsync({
        labId,
        startTime: toApiDateTime(form.date, form.startTime),
        endTime: toApiDateTime(form.date, form.endTime),
        capacity,
        status: form.status,
      });
      setForm(initialForm);
      setError('');
      onClose();
    } catch {
      setError('Không thể tạo khung giờ sử dụng. Vui lòng kiểm tra lại thông tin.');
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Modal
        footer={(
          <>
            <Button disabled={isSubmitting} onClick={handleClose} variant="outline">
              Hủy
            </Button>
            <Button loading={isSubmitting} loadingText="Đang tạo..." type="submit">
              Tạo khung giờ
            </Button>
          </>
        )}
        onClose={handleClose}
        subtitle="Khung giờ sẽ được tạo cho PTN bạn đang quản lý."
        title="Tạo khung giờ sử dụng"
      >
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-slate-700" htmlFor="slot-date">
              Ngày
            </label>
            <input
              id="slot-date"
              type="date"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.date}
              onChange={(event) => updateField('date', event.target.value)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-start">
                Giờ bắt đầu
              </label>
              <input
                id="slot-start"
                type="time"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.startTime}
                onChange={(event) => updateField('startTime', event.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-end">
                Giờ kết thúc
              </label>
              <input
                id="slot-end"
                type="time"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.endTime}
                onChange={(event) => updateField('endTime', event.target.value)}
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-capacity">
                Sức chứa
              </label>
              <input
                id="slot-capacity"
                type="number"
                min="1"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.capacity}
                onChange={(event) => updateField('capacity', event.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-status">
                Trạng thái
              </label>
              <select
                id="slot-status"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.status}
                onChange={(event) => updateField('status', event.target.value)}
              >
                <option value="AVAILABLE">Còn chỗ</option>
                <option value="CLOSED">Đã đóng</option>
                <option value="MAINTENANCE">Bảo trì</option>
              </select>
            </div>
          </div>

          {error ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          ) : null}

        </div>
      </Modal>
    </form>
  );
}
