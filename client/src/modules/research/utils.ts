import axios from 'axios';

import { getStoredLocale, translate, translateBackendMessage, type Locale } from '../../shared/i18n';
import type { GroupStatus, MilestoneStatus, ProjectStatus, ResearchPriority, TopicStatus } from './types';

interface ApiErrorMessageContext {
  fallback?: string;
  forbidden?: string;
  notFound?: string;
}

export function getApiErrorMessage(error: unknown, context: string | ApiErrorMessageContext = {}) {
  const options = typeof context === 'string' ? { fallback: context } : context;
  const locale = getStoredLocale();

  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return translate('common.networkError', locale);
    }

    const status = error.response.status;
    const data = error.response.data as { message?: string; errors?: string[] } | undefined;
    const apiMessage = translateBackendMessage(data?.message ?? data?.errors?.[0], locale);
    if (status === 401) {
      return translate('common.sessionExpired', locale);
    }
    if (status === 403) {
      return options.forbidden ?? translate('research.forbidden', locale);
    }
    if (status === 404) {
      return options.notFound ?? translate('common.notFound', locale);
    }
    if (apiMessage && (status === 400 || status === 409)) {
      return apiMessage;
    }
    if (status >= 500) {
      return translate('common.serverFailure', locale);
    }
  }

  return options.fallback ?? translate('common.loadError', locale);
}

export function formatTopicStatus(status?: TopicStatus | null, locale: Locale = getStoredLocale()) {
  const key = ({
    RECRUITING: 'research.topic.recruiting',
    ONGOING: 'research.topic.ongoing',
    PAUSED: 'research.topic.paused',
    COMPLETED: 'research.topic.completed',
  } as Record<string, string>)[status ?? ''];
  return status ? translate(key ?? '', locale, status) : translate('common.notUpdated', locale);
}

export function formatGroupStatus(status?: GroupStatus | null, locale: Locale = getStoredLocale()) {
  const key = ({
    ACTIVE: 'research.group.active',
    PAUSED: 'research.group.paused',
    COMPLETED: 'research.group.completed',
    ARCHIVED: 'research.group.archived',
  } as Record<string, string>)[status ?? ''];
  return status ? translate(key ?? '', locale, status) : translate('common.notUpdated', locale);
}

export function formatProjectStatus(status?: ProjectStatus | null, locale: Locale = getStoredLocale()) {
  const key = ({
    DRAFT: 'research.project.new',
    PLANNED: 'research.project.new',
    ONGOING: 'research.project.ongoing',
    WAITING_REVIEW: 'research.project.waitingReview',
    COMPLETED: 'research.project.completed',
    ARCHIVED: 'research.project.archived',
    CANCELLED: 'research.project.cancelled',
  } as Record<string, string>)[status ?? ''];
  return status ? translate(key ?? '', locale, status) : translate('common.notUpdated', locale);
}

export function formatPriority(priority?: ResearchPriority | null, locale: Locale = getStoredLocale()) {
  const key = ({
    HIGH: 'research.priority.high',
    MEDIUM: 'research.priority.medium',
    LOW: 'research.priority.low',
  } as Record<string, string>)[priority ?? ''];
  return priority ? translate(key ?? '', locale, priority) : translate('common.notUpdated', locale);
}

export function formatGroupRole(role?: 'LEADER' | 'MEMBER' | null, locale: Locale = getStoredLocale()) {
  const key = ({
    LEADER: 'research.role.leader',
    MEMBER: 'research.role.member',
  } as Record<string, string>)[role ?? ''];
  return role ? translate(key ?? '', locale, role) : translate('common.notUpdated', locale);
}

export function formatMilestoneStatus(status?: MilestoneStatus | null, locale: Locale = getStoredLocale()) {
  const key = ({
    NOT_STARTED: 'research.milestone.notStarted',
    IN_PROGRESS: 'research.milestone.inProgress',
    WAITING_REVIEW: 'research.milestone.waitingReview',
    COMPLETED: 'research.milestone.completed',
    OVERDUE: 'research.milestone.overdue',
    CANCELLED: 'research.milestone.cancelled',
  } as Record<string, string>)[status ?? ''];
  return status ? translate(key ?? '', locale, status) : translate('common.notUpdated', locale);
}

export function isMilestoneOverdue(deadline?: string | null, status?: MilestoneStatus | null) {
  if (!deadline || status === 'COMPLETED' || status === 'CANCELLED') {
    return false;
  }
  const now = new Date();
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
  return deadline < today;
}

export function getStatusClass(status?: string | null) {
  switch (status) {
    case 'ACTIVE':
    case 'ONGOING':
    case 'IN_PROGRESS':
    case 'DOING':
    case 'RECRUITING':
      return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
    case 'COMPLETED':
    case 'DONE':
      return 'bg-blue-50 text-blue-700 ring-blue-200';
    case 'PAUSED':
    case 'WAITING_REVIEW':
      return 'bg-amber-50 text-amber-700 ring-amber-200';
    case 'NEEDS_REVISION':
      return 'bg-orange-50 text-orange-700 ring-orange-200';
    case 'OVERDUE':
      return 'bg-red-50 text-red-700 ring-red-200';
    case 'ARCHIVED':
    case 'CANCELLED':
    case 'NOT_STARTED':
    case 'TODO':
      return 'bg-slate-100 text-slate-600 ring-slate-200';
    default:
      return 'bg-slate-100 text-slate-600 ring-slate-200';
  }
}

export function formatDate(value?: string | null, locale: Locale = getStoredLocale()) {
  if (!value) {
    return translate('common.notUpdated', locale);
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(new Date(value));
}

export function formatReportSubmitterName(report: { submittedByName?: string | null }, locale: Locale = getStoredLocale()) {
  return report.submittedByName || translate('research.unknownSubmitter', locale);
}
