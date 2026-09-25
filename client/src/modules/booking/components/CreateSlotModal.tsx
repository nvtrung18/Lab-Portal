import { type FormEvent, useMemo, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
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

function todayInputValue() {
  const now = new Date();
  const localToday = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return localToday.toISOString().slice(0, 10);
}

const HOURS_24 = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));
const MINUTES = Array.from({ length: 60 }, (_, minute) => String(minute).padStart(2, '0'));

function Time24HourSelect({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const { t } = useI18n();
  const [hour = '', minute = ''] = value.split(':', 2);
  const selectClassName = 'min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10';

  return (
    <fieldset>
      <legend className="text-sm font-medium text-slate-700">{label}</legend>
      <div className="mt-1 grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <select
          aria-label={`${label} - ${t('booking.hour')}`}
          className={selectClassName}
          id={`${id}-hour`}
          value={hour}
          onChange={(event) => onChange(event.target.value ? `${event.target.value}:${minute || '00'}` : '')}
        >
          <option value="">{t('booking.hour')}</option>
          {HOURS_24.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
        <span aria-hidden="true" className="font-semibold text-slate-500">:</span>
        <select
          aria-label={`${label} - ${t('booking.minute')}`}
          className={selectClassName}
          id={`${id}-minute`}
          value={minute}
          onChange={(event) => onChange(event.target.value ? `${hour || '00'}:${event.target.value}` : '')}
        >
          <option value="">{t('booking.minute')}</option>
          {MINUTES.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
      </div>
    </fieldset>
  );
}

export function CreateSlotModal({ labId, isOpen, onClose }: CreateSlotModalProps) {
  const { locale, t } = useI18n();
  const [form, setForm] = useState<FormState>(initialForm);
  const [error, setError] = useState('');
  const createSlot = useCreateSlot();

  const isSubmitting = createSlot.isPending;
  const capacity = Number(form.capacity);
  const validationError = useMemo(() => {
    if (!form.date) {
      return t('validation.required');
    }
    if (!form.startTime) {
      return t('validation.required');
    }
    if (!form.endTime) {
      return t('validation.required');
    }
    if (!Number.isFinite(capacity) || capacity <= 0) {
      return t('booking.capacityPositive');
    }

    const start = new Date(toLocalDateTime(form.date, form.startTime));
    const end = new Date(toLocalDateTime(form.date, form.endTime));
    if (start >= end) {
      return t('validation.dateRange');
    }

    return '';
  }, [capacity, form.date, form.endTime, form.startTime, t]);

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
      // The mutation displays the server error through the shared toast.
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Modal
        backdropBlur
        centered
        footer={(
          <>
            <Button disabled={isSubmitting} onClick={handleClose} variant="outline">
              {t('common.cancel')}
            </Button>
            <Button loading={isSubmitting} loadingText={t('booking.creatingSlot')} type="submit">
              {t('booking.createSlot')}
            </Button>
          </>
        )}
        onClose={handleClose}
        size="lg"
        title={t('booking.createSlot')}
      >
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-slate-700" htmlFor="slot-date">
              {t('booking.date')}
            </label>
            <input
              id="slot-date"
              type="date"
              lang={locale === 'en' ? 'en-US' : 'vi-VN'}
              min={todayInputValue()}
              className="mt-1 min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.date}
              onChange={(event) => updateField('date', event.target.value)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Time24HourSelect id="slot-start" label={t('booking.startTime')} value={form.startTime} onChange={(value) => updateField('startTime', value)} />
            <Time24HourSelect id="slot-end" label={t('booking.endTime')} value={form.endTime} onChange={(value) => updateField('endTime', value)} />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-capacity">
                {t('booking.capacity')}
              </label>
              <input
                id="slot-capacity"
                type="number"
                min="1"
                className="mt-1 min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.capacity}
                onChange={(event) => updateField('capacity', event.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium text-slate-700" htmlFor="slot-status">
                {t('booking.slotStatus')}
              </label>
              <select
                id="slot-status"
                className="mt-1 min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.status}
                onChange={(event) => updateField('status', event.target.value)}
              >
                <option value="AVAILABLE">{t('booking.available')}</option>
                <option value="CLOSED">{t('booking.closed')}</option>
                <option value="MAINTENANCE">{t('booking.maintenance')}</option>
              </select>
            </div>
          </div>

          {error ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
              {error}
            </div>
          ) : null}

        </div>
      </Modal>
    </form>
  );
}
