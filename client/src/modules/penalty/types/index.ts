export interface Complaint {
  id: number;
  userId: number;
  studentName?: string | null;
  studentEmail?: string | null;
  penaltyId?: number | null;
  labId?: number | null;
  labName?: string | null;
  bookingId?: number | null;
  penaltyReason?: string | null;
  content: string;
  status: string;
  resolutionNote?: string | null;
  resolvedAt?: string | null;
  createdAt?: string | null;
}

export interface Penalty {
  id: number;
  userId: number;
  labId?: number | null;
  labName?: string | null;
  bookingId?: number | null;
  slotId?: number | null;
  type?: string | null;
  reason: string;
  point?: number | null;
  amount?: number | string | null;
  status: string;
  createdAt?: string | null;
  complaint?: Complaint | null;
}

export interface CreateComplaintPayload {
  penaltyId: number;
  content: string;
}

export interface CreatePenaltyPayload {
  userId: number;
  slotId: number;
  bookingId?: number | null;
  type: string;
  point: number;
  reason: string;
}
