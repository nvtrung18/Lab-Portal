export type DashboardStats = {
  projectId: number;
  projectTitle: string;
  scope: 'PROJECT' | 'GROUP' | 'ME';
  scopeLabel: string;
  scopeGroupId: number | null;
  cards: {
    memberCount: number;
    milestoneCount: number;
    completedMilestoneCount: number;
    taskCount: number;
    taskCompletionRate: number;
    reportCount: number;
    productCount: number;
    averageEvaluationScore: number | null;
    attendanceRate: number;
    overdueTaskCount: number;
  };
  taskProgress: {
    todo: number;
    doing: number;
    waitingReview: number;
    needsRevision: number;
    done: number;
    overdue: number;
  };
  attendance: {
    totalAttendanceCount: number;
    attendanceRate: number;
    byStudent: Array<{
      studentId: number;
      studentName: string;
      attendanceCount: number;
      expectedAttendanceCount: number;
      attendanceRate: number;
    }>;
  };
  milestoneProgress: Array<{
    milestoneId: number;
    title: string;
    progressPercent: number;
    statusLabel: string;
  }>;
  groupProgress: Array<{
    groupId: number;
    groupName: string;
    memberCount: number;
    taskCompletionRate: number;
    reportCount: number;
    productCount: number;
    averageEvaluationScore: number | null;
  }>;
};

export type RawProjectDashboardStats = {
  projectId?: number | null;
  project_id?: number | null;
  projectTitle?: string | null;
  project_title?: string | null;
  scope?: 'PROJECT' | 'GROUP' | 'ME' | string | null;
  scopeLabel?: string | null;
  scope_label?: string | null;
  scopeGroupId?: number | null;
  scope_group_id?: number | null;
  overview?: {
    memberCount?: number | null;
    member_count?: number | null;
    milestoneCount?: number | null;
    milestone_count?: number | null;
    completedMilestoneCount?: number | null;
    completed_milestone_count?: number | null;
    taskCount?: number | null;
    task_count?: number | null;
    taskCompletionRate?: number | null;
    task_completion_rate?: number | null;
    reportCount?: number | null;
    report_count?: number | null;
    productCount?: number | null;
    product_count?: number | null;
    averageEvaluationScore?: number | null;
    average_evaluation_score?: number | null;
    attendanceCount?: number | null;
    attendance_count?: number | null;
    attendanceRate?: number | null;
    attendance_rate?: number | null;
    overdueTaskCount?: number | null;
    overdue_task_count?: number | null;
  } | null;
  taskByStatus?: Record<string, number | null | undefined> | null;
  task_by_status?: Record<string, number | null | undefined> | null;
  attendanceByStudent?: RawStudentAttendance[] | null;
  attendance_by_student?: RawStudentAttendance[] | null;
  milestoneProgress?: RawMilestoneProgress[] | null;
  milestone_progress?: RawMilestoneProgress[] | null;
  groupProgress?: RawGroupProgress[] | null;
  group_progress?: RawGroupProgress[] | null;
};

export type RawStudentAttendance = {
  studentId?: number | null;
  student_id?: number | null;
  studentName?: string | null;
  student_name?: string | null;
  attendanceCount?: number | null;
  attendance_count?: number | null;
  expectedAttendanceCount?: number | null;
  expected_attendance_count?: number | null;
  attendanceRate?: number | null;
  attendance_rate?: number | null;
};

export type RawMilestoneProgress = {
  milestoneId?: number | null;
  milestone_id?: number | null;
  title?: string | null;
  progressPercent?: number | null;
  progress_percent?: number | null;
  status?: string | null;
};

export type RawGroupProgress = {
  groupId?: number | null;
  group_id?: number | null;
  groupName?: string | null;
  group_name?: string | null;
  memberCount?: number | null;
  member_count?: number | null;
  taskCompletionRate?: number | null;
  task_completion_rate?: number | null;
  reportCount?: number | null;
  report_count?: number | null;
  productCount?: number | null;
  product_count?: number | null;
  averageEvaluationScore?: number | null;
  average_evaluation_score?: number | null;
};
