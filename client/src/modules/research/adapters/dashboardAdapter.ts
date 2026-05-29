import type {
  DashboardStats,
  RawGroupProgress,
  RawMilestoneProgress,
  RawProjectDashboardStats,
  RawStudentAttendance,
} from '../types/dashboard';

const MILESTONE_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: 'Chưa bắt đầu',
  PLANNED: 'Đã lên kế hoạch',
  IN_PROGRESS: 'Đang thực hiện',
  WAITING_REVIEW: 'Chờ đánh giá',
  COMPLETED: 'Hoàn thành',
  OVERDUE: 'Quá hạn',
  DELAYED: 'Chậm tiến độ',
  CANCELLED: 'Đã hủy',
};

export function adaptProjectDashboardStats(raw?: RawProjectDashboardStats | null): DashboardStats {
  const overview = raw?.overview ?? {};
  const taskByStatus = raw?.taskByStatus ?? raw?.task_by_status ?? {};

  return {
    projectId: toNumber(raw?.projectId ?? raw?.project_id),
    projectTitle: toStringValue(raw?.projectTitle ?? raw?.project_title),
    scope: toScope(raw?.scope),
    scopeLabel: toStringValue(raw?.scopeLabel ?? raw?.scope_label) || getDefaultScopeLabel(toScope(raw?.scope)),
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

export function getMilestoneStatusLabel(status?: string | null): string {
  if (!status) {
    return 'Không xác định';
  }
  return MILESTONE_STATUS_LABELS[status] ?? status;
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
    statusLabel: getMilestoneStatusLabel(raw.status),
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

function getDefaultScopeLabel(scope: DashboardStats['scope']) {
  if (scope === 'GROUP') {
    return 'Tổng quan nhóm của tôi';
  }
  if (scope === 'ME') {
    return 'Tổng quan cá nhân';
  }
  return 'Tổng quan đề tài';
}
