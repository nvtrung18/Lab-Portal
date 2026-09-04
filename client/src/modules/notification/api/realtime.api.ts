import { API_BASE_URL, getAuthToken } from '../../../shared/api';
import type { NotificationEventType, RealtimeEvent } from '../types';

const NOTIFICATION_EVENT_TYPES = new Set<NotificationEventType>([
  'TASK_ASSIGNED',
  'TASK_STATUS_CHANGED',
  'REPORT_SUBMITTED',
  'REPORT_REVIEWED',
  'PROPOSAL_SUBMITTED',
  'PROPOSAL_APPROVED',
  'PROPOSAL_REJECTED',
  'AI_ACTION_STATUS_CHANGED',
  'FACE_CHECKIN_SUCCEEDED',
  'FACE_CHECKIN_FAILED',
  'BOOKING_REQUESTED',
  'BOOKING_APPROVED',
  'BOOKING_REJECTED',
  'BOOKING_CANCELLED',
  'BOOKING_SESSION_COMPLETED',
  'BOOKING_NO_SHOW',
  'BOOKING_CHECKED_IN',
  'PENALTY_CREATED',
  'QR_CHECKIN_REQUESTED',
  'QR_CHECKIN_REVIEWED',
]);

function isRealtimeEvent(value: unknown): value is RealtimeEvent {
  if (!value || typeof value !== 'object') return false;
  const event = value as Partial<RealtimeEvent>;
  return (
    typeof event.eventId === 'string' &&
    (event.type === 'CONNECTED' || event.type === 'NOTIFICATION_CREATED') &&
    (event.notificationEventType === null ||
      (typeof event.notificationEventType === 'string' &&
        NOTIFICATION_EVENT_TYPES.has(event.notificationEventType as NotificationEventType))) &&
    (event.title === null || typeof event.title === 'string') &&
    (event.message === null || typeof event.message === 'string') &&
    (event.targetId === null || typeof event.targetId === 'number') &&
    typeof event.occurredAt === 'string'
  );
}

function parseEventBlock(block: string): RealtimeEvent | null {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (!data) return null;

  try {
    const parsed: unknown = JSON.parse(data);
    return isRealtimeEvent(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export async function streamRealtimeEvents(
  signal: AbortSignal,
  onEvent: (event: RealtimeEvent) => void,
) {
  const token = getAuthToken();
  if (!token) throw new Error('Authentication is required for realtime events.');

  const response = await fetch(`${API_BASE_URL}/api/notifications/stream`, {
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`,
    },
    signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`Realtime stream failed with status ${response.status}.`);
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  while (!signal.aborted) {
    const { value, done } = await reader.read();
    if (done) return;
    buffer += value.replace(/\r\n/g, '\n');

    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const event = parseEventBlock(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
      if (event) onEvent(event);
      boundary = buffer.indexOf('\n\n');
    }
  }
}
