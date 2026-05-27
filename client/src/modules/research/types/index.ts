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
  | 'APPROVED'
  | 'REJECTED';

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
  createdAt?: string | null;
  updatedAt?: string | null;
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

export type ManagerReportDecision = 'APPROVE' | 'REQUEST_REVISION' | 'REJECT';

export interface CreateMilestonePayload {
  projectId: number;
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
