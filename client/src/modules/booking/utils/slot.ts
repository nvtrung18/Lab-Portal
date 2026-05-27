import type { RawSlotResponse } from '../api';
import type { LabSlot } from '../types';

const STATUS_LABELS: Record<string, string> = {
  AVAILABLE: 'Còn chỗ',
  FULL: 'Hết chỗ',
  CLOSED: 'Đã đóng',
  INACTIVE: 'Tạm ngừng',
  MAINTENANCE: 'Bảo trì',
  EXPIRED: 'Đã qua',
  CANCELLED: 'Đã hủy',
  ARCHIVED: 'Đã lưu trữ',
};

const HIDDEN_SLOT_STATUSES = ['CANCELLED', 'INACTIVE', 'ARCHIVED'];

function toNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function normalizeStatus(
  rawStatus: string | undefined,
  capacity: number,
  approvedCount: number,
  endTime: string,
): string {
  const status = rawStatus?.toUpperCase() || 'AVAILABLE';
  const endDate = endTime ? new Date(endTime) : null;

  if (
    endDate &&
    !Number.isNaN(endDate.getTime()) &&
    endDate.getTime() < Date.now() &&
    !['CLOSED', 'CANCELLED', 'INACTIVE', 'MAINTENANCE', 'ARCHIVED'].includes(status)
  ) {
    return 'EXPIRED';
  }

  if (status === 'AVAILABLE' && capacity > 0 && approvedCount >= capacity) {
    return 'FULL';
  }

  return status;
}

export function normalizeSlot(slot: RawSlotResponse): LabSlot {
  const capacity = toNumber(slot.capacity);
  const approvedValue =
    slot.approvedCount ??
    slot.approved_count ??
    slot.bookedCount ??
    slot.booked_count ??
    slot.currentBookings ??
    slot.current_bookings;
  const checkedInValue = slot.checkedInCount ?? slot.checked_in_count;
  const pendingValue = slot.pendingCount ?? slot.pending_count;
  const hasBookedCount = typeof approvedValue === 'number' && Number.isFinite(approvedValue);
  const approvedCount = toNumber(approvedValue);
  const checkedInCount = toNumber(checkedInValue);
  const pendingCount = toNumber(pendingValue);
  const remainingValue = slot.remainingCapacity ?? slot.remaining_capacity;
  const remainingCapacity =
    typeof remainingValue === 'number' && Number.isFinite(remainingValue)
      ? remainingValue
      : capacity > 0 && hasBookedCount
        ? Math.max(capacity - approvedCount, 0)
        : null;
  const startTime = slot.startTime ?? slot.start_time ?? '';
  const endTime = slot.endTime ?? slot.end_time ?? '';
  const status = normalizeStatus(slot.status, capacity, approvedCount, endTime);

  return {
    id: toNumber(slot.id),
    labId: toNumber(slot.labId ?? slot.lab_id ?? slot.lab?.id),
    labName: slot.labName ?? slot.lab_name ?? slot.lab?.name ?? slot.lab?.labName,
    startTime,
    endTime,
    capacity,
    bookedCount: approvedCount,
    approvedCount,
    checkedInCount,
    pendingCount,
    hasBookedCount,
    remainingCapacity,
    status,
    statusLabel: STATUS_LABELS[status] ?? status,
  };
}

export function isUsableSlot(slot: { endTime?: string | null; status?: string | null; slotStatus?: string | null }): boolean {
  const endTime = new Date(slot.endTime ?? '').getTime();
  const status = (slot.slotStatus ?? slot.status ?? '').toUpperCase();

  return Number.isFinite(endTime) && endTime >= Date.now() && !HIDDEN_SLOT_STATUSES.includes(status);
}

export function isCurrentOrFutureSlot(slot: Pick<LabSlot, 'endTime'>): boolean {
  const endDate = new Date(slot.endTime);

  return !Number.isNaN(endDate.getTime()) && endDate.getTime() >= Date.now();
}
