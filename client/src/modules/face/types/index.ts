export type FaceConsentStatus = 'GRANTED' | 'WITHDRAWN' | 'DELETE_REQUESTED' | 'DELETED';
export type FaceProfileStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';

export interface FaceConsent {
  userId: number;
  status: FaceConsentStatus;
  changedAt: string;
}

export interface FaceProfile {
  userId: number;
  status: FaceProfileStatus;
  embeddingModel: string;
  updatedAt: string;
}

export interface FaceImageRequest {
  imageBase64: string;
  contentType: 'image/jpeg' | 'image/png';
  livenessRequired: boolean;
}

export interface FaceCheckinResult {
  bookingId: number;
  checkedIn: boolean;
  result: string;
  confidenceScore: number | null;
  livenessScore: number | null;
  failureReason: string | null;
  checkedInAt: string | null;
}
