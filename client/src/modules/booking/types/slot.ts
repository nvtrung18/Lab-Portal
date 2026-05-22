export interface LabSlot {
  id: number;
  labId: number;
  labName?: string;
  startTime: string;
  endTime: string;
  capacity: number;
  bookedCount: number;
  hasBookedCount: boolean;
  remainingCapacity: number | null;
  status: string;
  statusLabel: string;
}
