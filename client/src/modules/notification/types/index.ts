export type NotificationEventType =
  | 'TASK_ASSIGNED'
  | 'TASK_STATUS_CHANGED'
  | 'REPORT_SUBMITTED'
  | 'REPORT_REVIEWED'
  | 'PROPOSAL_SUBMITTED'
  | 'PROPOSAL_APPROVED'
  | 'PROPOSAL_REJECTED'
  | 'AI_ACTION_STATUS_CHANGED'
  | 'FACE_CHECKIN_SUCCEEDED'
  | 'FACE_CHECKIN_FAILED'
  | 'BOOKING_REQUESTED'
  | 'BOOKING_APPROVED'
  | 'BOOKING_REJECTED'
  | 'BOOKING_CANCELLED'
  | 'BOOKING_SESSION_COMPLETED'
  | 'QR_CHECKIN_REQUESTED'
  | 'QR_CHECKIN_REVIEWED';

export type NotificationTargetModule = 'TASK' | 'REPORT' | 'PROPOSAL' | 'AI' | 'FACE' | 'BOOKING';

export type RealtimeEventType = 'CONNECTED' | 'NOTIFICATION_CREATED';

export interface RealtimeEvent {
  eventId: string;
  type: RealtimeEventType;
  notificationEventType: NotificationEventType | null;
  title: string | null;
  message: string | null;
  targetModule: NotificationTargetModule | null;
  targetId: number | null;
  occurredAt: string;
}

export interface NotificationItem {
  id: number;
  eventType: NotificationEventType;
  title: string;
  message: string;
  targetModule: NotificationTargetModule;
  targetId: number | null;
  assistantKey: 'ADMIN_ASSISTANT' | 'LAB_ASSISTANT' | 'RESEARCH_ASSISTANT' | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}
