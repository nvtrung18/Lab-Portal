export type {
  DashboardStats,
  RawGroupProgress,
  RawMilestoneProgress,
  RawProjectDashboardStats,
  RawStudentAttendance,
} from './dashboard';

export type {
  InstantString,
  LocalDateString,
  ProjectTaskBoardResponse,
  TaskApiResponse,
  TaskBacklogPageResponse,
  TaskBoardColumnResponse,
  TaskPriority,
  TaskResponse,
  TaskStatus,
  TaskType,
  TaskProposalStatus,
  TaskProposal,
  TaskProposalReview,
  TaskProposalListItem,
  TaskProposalPageResponse,
} from './task';

export type TopicStatus = 'RECRUITING' | 'ONGOING' | 'PAUSED' | 'COMPLETED';

export type GroupStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';

export type ProjectStatus = 'DRAFT' | 'PLANNED' | 'ONGOING' | 'WAITING_REVIEW' | 'COMPLETED' | 'ARCHIVED' | 'CANCELLED';

export type ResearchPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export type MilestoneStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING_REVIEW' | 'COMPLETED' | 'OVERDUE' | 'CANCELLED';

export type ResearchTaskStatus =
  | 'TODO'
  | 'DOING'
  | 'WAITING_REVIEW'
  | 'NEEDS_REVISION'
  | 'DONE'
  | 'OVERDUE'
  | 'CANCELLED';

export type TaskColumn = 'TODO' | 'DOING' | 'WAITING_REVIEW' | 'NEEDS_REVISION' | 'DONE';

export type ResearchReportStatus =
  | 'SUBMITTED'
  | 'LEADER_REVIEWED'
  | 'NEEDS_REVISION'
  | 'LEADER_REJECTED'
  | 'APPROVED'
  | 'MANAGER_REJECTED';

export type LeaderReportDecision = 'ACCEPT' | 'REQUEST_REVISION' | 'REJECT';

export type ResearchProductType =
  | 'FINAL_REPORT'
  | 'SLIDE'
  | 'SOURCE_CODE'
  | 'DATASET'
  | 'DEMO_VIDEO'
  | 'PAPER'
  | 'SOFTWARE_DEMO'
  | 'OTHER';

export type ResearchProductStatus = 'SUBMITTED' | 'ACCEPTED' | 'NEEDS_REVISION' | 'REJECTED';

export type ResearchLogType = 'MANUAL' | 'SYSTEM';

export type ResearchLogVisibility = 'PRIVATE' | 'GROUP' | 'PROJECT';

export type ResearchGroupRole = 'LEADER' | 'MEMBER';

export interface ResearchLog {
  id: number;
  projectId: number;
  groupId?: number | null;
  groupName?: string | null;
  milestoneId?: number | null;
  milestoneTitle?: string | null;
  taskId?: number | null;
  taskTitle?: string | null;
  authorId: number;
  authorName?: string | null;
  authorRole?: string | null;
  groupRole?: ResearchGroupRole | null;
  logType: ResearchLogType;
  workDate: string;
  durationMinutes: number;
  content: string;
  result?: string | null;
  problem?: string | null;
  nextPlan?: string | null;
  evidenceLink?: string | null;
  visibility: ResearchLogVisibility;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RawResearchLog {
  id: number;
  projectId?: number;
  project_id?: number;
  groupId?: number | null;
  group_id?: number | null;
  groupName?: string | null;
  group_name?: string | null;
  milestoneId?: number | null;
  milestone_id?: number | null;
  milestoneTitle?: string | null;
  milestone_title?: string | null;
  taskId?: number | null;
  task_id?: number | null;
  taskTitle?: string | null;
  task_title?: string | null;
  authorId?: number;
  author_id?: number;
  authorName?: string | null;
  author_name?: string | null;
  authorRole?: string | null;
  author_role?: string | null;
  groupRole?: ResearchGroupRole | null;
  group_role?: ResearchGroupRole | null;
  logType?: ResearchLogType;
  log_type?: ResearchLogType;
  workDate?: string;
  work_date?: string;
  durationMinutes?: number;
  duration_minutes?: number;
  content?: string;
  result?: string | null;
  problem?: string | null;
  nextPlan?: string | null;
  next_plan?: string | null;
  evidenceLink?: string | null;
  evidence_link?: string | null;
  visibility?: ResearchLogVisibility;
  createdAt?: string | null;
  created_at?: string | null;
  updatedAt?: string | null;
  updated_at?: string | null;
}

export interface CreateResearchLogPayload {
  projectId: number;
  groupId?: number | null;
  milestoneId?: number | null;
  taskId?: number | null;
  workDate: string;
  durationMinutes: number;
  content: string;
  result?: string;
  problem?: string;
  nextPlan?: string;
  evidenceLink?: string;
  visibility: ResearchLogVisibility;
}

export interface ResearchLogFilters {
  groupId?: number | null;
  milestoneId?: number | null;
  taskId?: number | null;
  authorId?: number | null;
  logType?: ResearchLogType | null;
}

export interface ResearchEvaluation {
  id: number;
  projectId: number;
  groupId?: number | null;
  groupName?: string | null;
  studentId: number;
  studentName?: string | null;
  evaluatorId?: number | null;
  evaluatorName?: string | null;
  contributionScore: number;
  taskScore: number;
  reportScore: number;
  productScore: number;
  attitudeScore: number;
  totalScore: number;
  lecturerComment?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RawResearchEvaluation {
  id: number;
  projectId?: number;
  project_id?: number;
  groupId?: number | null;
  group_id?: number | null;
  groupName?: string | null;
  group_name?: string | null;
  studentId?: number;
  student_id?: number;
  studentName?: string | null;
  student_name?: string | null;
  evaluatorId?: number | null;
  evaluator_id?: number | null;
  evaluatorName?: string | null;
  evaluator_name?: string | null;
  contributionScore?: number;
  contribution_score?: number;
  taskScore?: number;
  task_score?: number;
  reportScore?: number;
  report_score?: number;
  productScore?: number;
  product_score?: number;
  attitudeScore?: number;
  attitude_score?: number;
  totalScore?: number;
  total_score?: number;
  lecturerComment?: string | null;
  lecturer_comment?: string | null;
  createdAt?: string | null;
  created_at?: string | null;
  updatedAt?: string | null;
  updated_at?: string | null;
}

export interface SubmitEvaluationPayload {
  projectId: number;
  groupId?: number | null;
  studentId: number;
  contributionScore: number;
  taskScore: number;
  reportScore: number;
  productScore: number;
  attitudeScore: number;
  lecturerComment?: string;
}

export interface ResearchTopic {
  id: number;
  labId: number;
  name: string;
  description?: string | null;
  requirements?: string | null;
  references?: string | null;
  managerName?: string | null;
  createdByName?: string | null;
  status?: TopicStatus | null;
  groupCount?: number | null;
  createdAt?: string | null;
}

export interface ResearchGroup {
  id: number;
  labId: number;
  topicId?: number | null;
  projectId?: number | null;
  topicName?: string | null;
  projectTitle?: string | null;
  projectCode?: string | null;
  leaderName?: string | null;
  managerName?: string | null;
  name: string;
  description?: string | null;
  objective?: string | null;
  plan?: string | null;
  status?: GroupStatus | null;
  memberCount?: number | null;
  projectCount?: number | null;
  leaderId?: number | null;
  myRole?: 'LEADER' | 'MEMBER' | null;
  myGroupRole?: 'LEADER' | 'MEMBER' | null;
  createdByName?: string | null;
  createdAt?: string | null;
  members?: ResearchGroupMember[];
}

export interface ResearchGroupMember {
  id: number;
  groupId: number;
  userId: number;
  fullName?: string | null;
  email?: string | null;
  role: 'LEADER' | 'MEMBER';
  joinedAt?: string | null;
}

export interface ResearchEligibleStudent {
  id: number;
  userId: number;
  fullName?: string | null;
  email: string;
  labId: number;
  labName: string;
  role: string;
  status: string;
  joinedAt?: string | null;
}

export interface ResearchProject {
  id: number;
  labId?: number | null;
  groupId?: number | null;
  topicId?: number | null;
  code?: string | null;
  title: string;
  researchDirection?: string | null;
  description?: string | null;
  objective?: string | null;
  status?: ProjectStatus | null;
  managerName?: string | null;
  createdByName?: string | null;
  priority?: ResearchPriority | null;
  requiredProducts?: string | null;
  evaluationCriteria?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  expectedEndDate?: string | null;
  createdAt?: string | null;
}

export interface CreateResearchProjectPayload {
  labId: number;
  code?: string;
  title: string;
  researchDirection?: string;
  description?: string;
  objective?: string;
  startDate?: string;
  expectedEndDate?: string;
  priority?: ResearchPriority;
  requiredProducts?: string;
  evaluationCriteria?: string;
  status?: ProjectStatus;
}

export interface ResearchMilestone {
  id: number;
  projectId: number;
  groupId?: number | null;
  groupName?: string | null;
  projectTitle?: string | null;
  title: string;
  description?: string | null;
  assignedToStudentId?: number | null;
  assignedToStudentName?: string | null;
  deadline?: string | null;
  status?: MilestoneStatus | null;
  progressPercent: number;
  evidenceUrl?: string | null;
  managerComment?: string | null;
  myTaskCount?: number;
  myCompletedTaskCount?: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RawResearchTask {
  id: number;
  milestoneId: number;
  projectId?: number | null;
  title: string;
  description?: string | null;
  assignedToStudentId?: number | null;
  assignedToStudentName?: string | null;
  assignedToStudentEmail?: string | null;
  assigneeId?: number | null;
  deadline?: string | null;
  status?: ResearchTaskStatus | 'IN_PROGRESS' | 'REVIEW' | null;
  progressPercent?: number | null;
  milestoneTitle?: string | null;
  milestone_title?: string | null;
  latestReportStatus?: ResearchReportStatus | null;
  latest_report_status?: ResearchReportStatus | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ResearchTask {
  id: number;
  milestoneId: number;
  projectId?: number | null;
  title: string;
  description: string | null;
  assignedToStudentId: number | null;
  assigneeName: string | null;
  assigneeEmail: string | null;
  deadline: string | null;
  status: ResearchTaskStatus;
  statusLabel: string;
  column: TaskColumn;
  progressPercent: number;
  isOverdue: boolean;
  milestoneTitle?: string | null;
  latestReportStatus?: ResearchReportStatus | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ResearchReport {
  id: number;
  projectId: number;
  groupId?: number | null;
  milestoneId: number;
  taskId?: number | null;
  submittedById: number;
  submittedByName?: string | null;
  submittedByEmail?: string | null;
  groupName?: string | null;
  milestoneTitle?: string | null;
  taskTitle?: string | null;
  version: number;
  title: string;
  contentDone: string;
  result: string;
  difficulty: string;
  nextPlan: string;
  selfAssessment: string;
  fileUrl: string;
  fileName?: string | null;
  fileType?: string | null;
  fileSize?: number | null;
  evidenceLink?: string | null;
  status: ResearchReportStatus;
  leaderReviewedAt?: string | null;
  leaderComment?: string | null;
  managerReviewedAt?: string | null;
  managerComment?: string | null;
  commentCount?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  submittedByGroupRole?: string | null;
  isLatestVersion?: boolean | null;
}

export interface ResearchReportComment {
  id: number;
  reportId: number;
  authorId: number;
  authorName: string | null;
  authorEmail: string | null;
  authorRole: 'LAB_MANAGER' | 'STUDENT';
  groupRole: 'LEADER' | 'MEMBER' | null;
  content: string;
  createdAt: string;
}

export interface ResearchProduct {
  id: number;
  projectId: number;
  groupId?: number | null;
  submittedById?: number | null;
  submittedByName?: string | null;
  submittedByEmail?: string | null;
  productType: ResearchProductType;
  title: string;
  description?: string | null;
  fileUrl?: string | null;
  fileName?: string | null;
  fileType?: string | null;
  fileSize?: number | null;
  externalLink?: string | null;
  version: number;
  status: ResearchProductStatus;
  submittedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RawResearchProduct {
  id: number;
  projectId?: number;
  project_id?: number;
  groupId?: number | null;
  group_id?: number | null;
  submittedById?: number | null;
  submitted_by_id?: number | null;
  submittedByName?: string | null;
  submitted_by_name?: string | null;
  submittedByEmail?: string | null;
  submitted_by_email?: string | null;
  productType?: ResearchProductType;
  product_type?: ResearchProductType;
  title?: string;
  description?: string | null;
  fileUrl?: string | null;
  file_url?: string | null;
  fileName?: string | null;
  file_name?: string | null;
  fileType?: string | null;
  file_type?: string | null;
  fileSize?: number | null;
  file_size?: number | null;
  externalLink?: string | null;
  external_link?: string | null;
  version?: number;
  status?: ResearchProductStatus;
  submittedAt?: string | null;
  submitted_at?: string | null;
  createdAt?: string | null;
  created_at?: string | null;
  updatedAt?: string | null;
  updated_at?: string | null;
}

export interface SubmitProductPayload {
  projectId: number;
  groupId?: number | null;
  productType: ResearchProductType;
  title: string;
  description?: string;
  externalLink?: string;
  file?: File | null;
}

export interface SubmitReportPayload {
  milestoneId: number;
  taskId: number;
  title: string;
  contentDone: string;
  result: string;
  difficulty: string;
  nextPlan: string;
  selfAssessment: string;
  evidenceLink?: string;
  file: File;
}

export interface ReplaceReportPayload {
  title: string;
  contentDone: string;
  result: string;
  difficulty: string;
  nextPlan: string;
  selfAssessment: string;
  evidenceLink?: string;
  file?: File | null;
}

export type ManagerReportDecision = 'APPROVE' | 'REQUEST_REVISION' | 'REJECT';

export interface CreateMilestonePayload {
  projectId: number;
  groupId?: number | null;
  title: string;
  description?: string;
  assignedToStudentId?: number;
  deadline?: string;
  status?: MilestoneStatus;
  progressPercent: number;
  evidenceUrl?: string;
  managerComment?: string;
}

export interface UpdateMilestonePayload {
  title: string;
  description?: string;
  assignedToStudentId?: number;
  deadline?: string;
  status: MilestoneStatus;
  progressPercent: number;
  evidenceUrl?: string;
  managerComment?: string;
}

export interface CreateTopicPayload {
  labId: number;
  name: string;
  description?: string;
  requirements?: string;
  references?: string;
  status?: TopicStatus;
}

export interface CreateGroupPayload {
  labId: number;
  topicId: number;
  name: string;
  description?: string;
  objective?: string;
  plan?: string;
  status?: GroupStatus;
}

export interface CreateResearchGroupPayload {
  projectId: number;
  name: string;
  objective?: string;
  plan?: string;
  leaderStudentId: number;
  memberIds: number[];
}

export interface UpdateResearchGroupPayload {
  name: string;
  objective?: string;
  plan?: string;
  leaderStudentId: number;
  memberIds: number[];
  status: GroupStatus;
}

export interface CreateProjectPayload {
  groupId: number;
  code?: string;
  title: string;
  description?: string;
  objective?: string;
  startDate?: string;
  expectedEndDate?: string;
  priority?: ResearchPriority;
  requiredProducts?: string;
  evaluationCriteria?: string;
  status?: ProjectStatus;
}

export interface CreateTaskPayload {
  title: string;
  description?: string;
  assignedToStudentId: number;
  deadline?: string;
}
