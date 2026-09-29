import type {
  DashboardStats,
  RawGroupProgress,
  RawMilestoneProgress,
  RawProjectDashboardStats,
  RawStudentAttendance,
} from '../types/dashboard';
import { getStoredLocale, translate, type Locale } from '../../../shared/i18n';

const MILESTONE_STATUS_KEYS: Record<string, string> = {
  NOT_STARTED: 'research.milestone.notStarted',
  PLANNED: 'research.milestone.planned',
  IN_PROGRESS: 'research.milestone.inProgress',
  WAITING_REVIEW: 'research.milestone.waitingReview',
  COMPLETED: 'research.milestone.completed',
  OVERDUE: 'research.milestone.overdue',
  DELAYED: 'research.milestone.delayed',
  CANCELLED: 'research.milestone.cancelled',
};

export function adaptProjectDashboardStats(raw?: RawProjectDashboardStats | null): DashboardStats {
  const locale = getStoredLocale();
  const overview = raw?.overview ?? {};
  const taskByStatus = raw?.taskByStatus ?? raw?.task_by_status ?? {};
  const scope = toScope(raw?.scope);

  return {
    projectId: toNumber(raw?.projectId ?? raw?.project_id),
    projectTitle: toStringValue(raw?.projectTitle ?? raw?.project_title),
    scope,
    scopeLabel: getDefaultScopeLabel(scope, locale),
    scopeGroupId: toNullableNumber(raw?.scopeGroupId ?? raw?.scope_group_id),
    cards: {
      memberCount: toNumber(overview.memberCount ?? overview.member_count),
      milestoneCount: toNumber(overview.milestoneCount ?? overview.milestone_count),
      completedMilestoneCount: toNumber(overview.completedMilestoneCount ?? overview.completed_milestone_count),
      taskCount: toNumber(overview.taskCount ?? overview.task_count),
      taskCompletionRate: toNumber(overview.taskCompletionRate ?? overview.task_completion_rate),
      reportCount: toNumber(overview.reportCount ?? overview.report_count),
      approvedReportCount: toNumber(overview.approvedReportCount ?? overview.approved_report_count),
      productCount: toNumber(overview.productCount ?? overview.product_count),
      averageEvaluationScore: toNullableNumber(overview.averageEvaluationScore ?? overview.average_evaluation_score),
      overdueTaskCount: toNumber(overview.overdueTaskCount ?? overview.overdue_task_count),
    },
    taskProgress: {
      todo: toNumber(taskByStatus.TODO),
      doing: toNumber(taskByStatus.DOING ?? taskByStatus.IN_PROGRESS),
      waitingReview: toNumber(taskByStatus.WAITING_REVIEW ?? taskByStatus.REVIEW),
      needsRevision: toNumber(taskByStatus.NEEDS_REVISION),
      done: toNumber(taskByStatus.DONE),
      overdue: toNumber(taskByStatus.OVERDUE),
    },
    milestoneProgress: toArray(raw?.milestoneProgress ?? raw?.milestone_progress).map(adaptMilestoneProgress),
    groupProgress: toArray(raw?.groupProgress ?? raw?.group_progress).map(adaptGroupProgress),
  };
}

export function getMilestoneStatusLabel(status?: string | null, locale: Locale = getStoredLocale()): string {
  if (!status) {
    return translate('common.unknown', locale);
  }
  return translate(MILESTONE_STATUS_KEYS[status] ?? '', locale, status);
}

function adaptStudentAttendance(raw: RawStudentAttendance) {
  return {
    studentId: toNumber(raw.studentId ?? raw.student_id),
    studentName: toStringValue(raw.studentName ?? raw.student_name),
    attendanceCount: toNumber(raw.attendanceCount ?? raw.attendance_count),
    expectedAttendanceCount: toNumber(raw.expectedAttendanceCount ?? raw.expected_attendance_count),
    attendanceRate: toNumber(raw.attendanceRate ?? raw.attendance_rate),
  };
}

function adaptMilestoneProgress(raw: RawMilestoneProgress) {
  return {
    milestoneId: toNumber(raw.milestoneId ?? raw.milestone_id),
    title: toStringValue(raw.title),
    progressPercent: toNumber(raw.progressPercent ?? raw.progress_percent),
    status: raw.status ?? null,
  };
}

function adaptGroupProgress(raw: RawGroupProgress) {
  return {
    groupId: toNumber(raw.groupId ?? raw.group_id),
    groupName: toStringValue(raw.groupName ?? raw.group_name),
    memberCount: toNumber(raw.memberCount ?? raw.member_count),
    taskCompletionRate: toNumber(raw.taskCompletionRate ?? raw.task_completion_rate),
    reportCount: toNumber(raw.reportCount ?? raw.report_count),
    productCount: toNumber(raw.productCount ?? raw.product_count),
    averageEvaluationScore: toNullableNumber(raw.averageEvaluationScore ?? raw.average_evaluation_score),
  };
}

function toArray<T>(value?: T[] | null): T[] {
  return Array.isArray(value) ? value : [];
}

function toNumber(value: unknown): number {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function toNullableNumber(value: unknown): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function toStringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function toScope(value: unknown): DashboardStats['scope'] {
  return value === 'GROUP' || value === 'ME' || value === 'PROJECT' ? value : 'PROJECT';
}

function getDefaultScopeLabel(scope: DashboardStats['scope'], locale: Locale) {
  if (scope === 'GROUP') {
    return translate('research.dashboard.scopeGroup', locale);
  }
  if (scope === 'ME') {
    return translate('research.dashboard.scopeMe', locale);
  }
  return translate('research.dashboard.scopeProject', locale);
}
