export type NotificationEventType =
  | 'TASK_ASSIGNED'
  | 'TASK_PROPOSAL_SUBMITTED'
  | 'TASK_PROPOSAL_REVIEWED'
  | 'REPORT_SUBMITTED'
  | 'REPORT_REVIEWED'
  | 'AI_ACTION_SUGGESTED'
  | 'AI_ACTION_REVIEWED'
  | 'FACE_CHECKIN_SUCCEEDED'
  | 'FACE_CHECKIN_FAILED';

export type NotificationTargetModule = 'RESEARCH' | 'AI' | 'FACE' | 'BOOKING' | 'SYSTEM';

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
