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
};

function toNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function normalizeStatus(
  rawStatus: string | undefined,
  capacity: number,
  bookedCount: number,
  endTime: string,
): string {
  const status = rawStatus?.toUpperCase() || 'AVAILABLE';
  const endDate = endTime ? new Date(endTime) : null;

  if (
    endDate &&
    !Number.isNaN(endDate.getTime()) &&
    endDate.getTime() < Date.now() &&
    !['CLOSED', 'CANCELLED', 'INACTIVE', 'MAINTENANCE'].includes(status)
  ) {
    return 'EXPIRED';
  }

  if (status === 'AVAILABLE' && capacity > 0 && bookedCount >= capacity) {
    return 'FULL';
  }

  return status;
}

export function normalizeSlot(slot: RawSlotResponse): LabSlot {
  const capacity = toNumber(slot.capacity);
  const bookedValue =
    slot.bookedCount ?? slot.booked_count ?? slot.currentBookings ?? slot.current_bookings;
  const hasBookedCount = typeof bookedValue === 'number' && Number.isFinite(bookedValue);
  const bookedCount = toNumber(bookedValue);
  const remainingValue = slot.remainingCapacity ?? slot.remaining_capacity;
  const remainingCapacity =
    typeof remainingValue === 'number' && Number.isFinite(remainingValue)
      ? remainingValue
      : capacity > 0 && hasBookedCount
        ? Math.max(capacity - bookedCount, 0)
        : null;
  const startTime = slot.startTime ?? slot.start_time ?? '';
  const endTime = slot.endTime ?? slot.end_time ?? '';
  const status = normalizeStatus(slot.status, capacity, bookedCount, endTime);

  return {
    id: toNumber(slot.id),
    labId: toNumber(slot.labId ?? slot.lab_id ?? slot.lab?.id),
    labName: slot.labName ?? slot.lab_name ?? slot.lab?.name ?? slot.lab?.labName,
    startTime,
    endTime,
    capacity,
    bookedCount,
    hasBookedCount,
    remainingCapacity,
    status,
    statusLabel: STATUS_LABELS[status] ?? status,
  };
}
