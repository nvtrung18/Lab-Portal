import { useMemo, useState } from 'react';

import { Button } from '../../../shared/components';
import type { UserProfileResponse } from '../../user/api/user.api';
import {
  useCreateResearchLog,
  useMilestonesByProject,
  useResearchGroupsByProject,
  useResearchLogs,
} from '../hooks';
import type { ResearchGroupRole, ResearchLog, ResearchLogFilters, ResearchLogType } from '../types';
import { CreateLogModal } from './CreateLogModal';
import { LogItem } from './LogItem';

interface LogPageProps {
  projectId: number;
  currentUser?: UserProfileResponse | null;
  role: string;
  groupRole?: ResearchGroupRole | null;
  groupId?: number | null;
}

type MemberView = 'mine' | 'group';

const logTypeLabels: Record<ResearchLogType, string> = {
  MANUAL: 'Nhật ký thủ công',
  SYSTEM: 'Sự kiện hệ thống',
};

function sortNewestFirst(logs: ResearchLog[]) {
  return [...logs].sort((left, right) => {
    const leftTime = left.createdAt ? new Date(left.createdAt).getTime() : 0;
    const rightTime = right.createdAt ? new Date(right.createdAt).getTime() : 0;
    return rightTime - leftTime;
  });
}

function uniqueById<T extends { id: number }>(items: T[]) {
  return Array.from(new Map(items.map((item) => [item.id, item])).values());
}

function toNumberOrNull(value: string) {
  return value ? Number(value) : null;
}

export function LogPage({ projectId, currentUser, role, groupRole, groupId }: LogPageProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [groupFilter, setGroupFilter] = useState('');
  const [milestoneFilter, setMilestoneFilter] = useState('');
  const [taskFilter, setTaskFilter] = useState('');
  const [authorFilter, setAuthorFilter] = useState('');
  const [logTypeFilter, setLogTypeFilter] = useState('');
  const [memberView, setMemberView] = useState<MemberView>('mine');

  const { data: groups = [] } = useResearchGroupsByProject(projectId);
  const { data: milestones = [] } = useMilestonesByProject(projectId);
  const createLog = useCreateResearchLog(projectId);
  const isManager = role === 'LAB_MANAGER';

  const selectedGroup = useMemo(() => {
    if (groupId) {
      return groups.find((group) => group.id === groupId) ?? null;
    }
    if (groupFilter) {
      return groups.find((group) => String(group.id) === groupFilter) ?? null;
    }
    return groups[0] ?? null;
  }, [groupFilter, groupId, groups]);

  const resolvedGroupRole = useMemo(() => {
    if (groupRole) {
      return groupRole;
    }
    if (selectedGroup?.myRole) {
      return selectedGroup.myRole;
    }
    return selectedGroup?.members?.find((member) => member.userId === currentUser?.id)?.role ?? null;
  }, [currentUser?.id, groupRole, selectedGroup]);

  const isLeader = resolvedGroupRole === 'LEADER';
  const isMember = !isManager && !isLeader;
  const resolvedGroupId = groupId ?? selectedGroup?.id ?? null;

  const queryFilters = useMemo<ResearchLogFilters>(() => ({
    groupId: isManager ? toNumberOrNull(groupFilter) : resolvedGroupId,
    milestoneId: toNumberOrNull(milestoneFilter),
    taskId: toNumberOrNull(taskFilter),
    authorId: toNumberOrNull(authorFilter),
    logType: logTypeFilter ? (logTypeFilter as ResearchLogType) : null,
  }), [authorFilter, groupFilter, isManager, logTypeFilter, milestoneFilter, resolvedGroupId, taskFilter]);

  const {
    data: logPages,
    fetchNextPage,
    hasNextPage,
    isError,
    isFetchingNextPage,
    isLoading,
    refetch,
  } = useResearchLogs(projectId, queryFilters);

  const logs = useMemo(() => logPages?.pages.flat() ?? [], [logPages]);
  const sortedLogs = useMemo(() => sortNewestFirst(logs), [logs]);
  const visibleLogs = useMemo(() => isMember
    ? sortedLogs.filter((log) => (
      memberView === 'mine'
        ? log.authorId === currentUser?.id
        : log.authorId !== currentUser?.id && log.visibility === 'GROUP'
    ))
    : sortedLogs, [currentUser?.id, isMember, memberView, sortedLogs]);

  const taskOptions = useMemo(() => uniqueById(
    logs
      .filter((log) => log.taskId && log.taskTitle)
      .map((log) => ({ id: log.taskId as number, title: log.taskTitle as string })),
  ), [logs]);
  const authorOptions = useMemo(() => uniqueById(
    logs
      .filter((log) => log.authorId && log.authorName)
      .map((log) => ({ id: log.authorId, name: log.authorName as string })),
  ), [logs]);
  const availableLogTypes = useMemo(() => Array.from(new Set(logs.map((log) => log.logType))), [logs]);
  const groupOptions = useMemo(
    () => isManager ? groups : groups.filter((group) => group.id === resolvedGroupId),
    [groups, isManager, resolvedGroupId],
  );
  const showAdvancedFilters = isManager || isLeader;

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Nhật ký nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">
            Theo dõi quá trình làm việc, kết quả và vấn đề phát sinh trong đề tài.
          </p>
        </div>
        <Button type="button" onClick={() => setIsCreateOpen(true)}>
          Ghi nhật ký nghiên cứu
        </Button>
      </div>

      {isMember ? (
        <div className="mt-5 inline-flex rounded-md border border-slate-200 bg-slate-50 p-1">
          <button
            className={`rounded px-3 py-1.5 text-sm font-semibold ${
              memberView === 'mine' ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-600'
            }`}
            type="button"
            onClick={() => setMemberView('mine')}
          >
            Nhật ký của tôi
          </button>
          <button
            className={`rounded px-3 py-1.5 text-sm font-semibold ${
              memberView === 'group' ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-600'
            }`}
            type="button"
            onClick={() => setMemberView('group')}
          >
            Nhật ký nhóm
          </button>
        </div>
      ) : null}

      {showAdvancedFilters ? (
        <div className="mt-5 grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2 lg:grid-cols-5">
          {isManager ? (
            <label className="block text-sm font-medium text-slate-700">
              Nhóm nghiên cứu
              <select
                className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                value={groupFilter}
                onChange={(event) => {
                  setGroupFilter(event.target.value);
                  setTaskFilter('');
                }}
              >
                <option value="">Tất cả nhóm</option>
                {groupOptions.map((group) => (
                  <option key={group.id} value={group.id}>{group.name}</option>
                ))}
              </select>
            </label>
          ) : null}

          <label className="block text-sm font-medium text-slate-700">
            Mốc nghiên cứu
            <select
              className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={milestoneFilter}
              onChange={(event) => {
                setMilestoneFilter(event.target.value);
                setTaskFilter('');
              }}
            >
              <option value="">Tất cả mốc</option>
              {milestones.map((milestone) => (
                <option key={milestone.id} value={milestone.id}>{milestone.title}</option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Nhiệm vụ
            <select
              className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={taskFilter}
              onChange={(event) => setTaskFilter(event.target.value)}
            >
              <option value="">Tất cả nhiệm vụ</option>
              {taskOptions.map((task) => (
                <option key={task.id} value={task.id}>{task.title}</option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Người ghi
            <select
              className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={authorFilter}
              onChange={(event) => setAuthorFilter(event.target.value)}
            >
              <option value="">Tất cả người ghi</option>
              {authorOptions.map((author) => (
                <option key={author.id} value={author.id}>{author.name}</option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Loại log
            <select
              className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={logTypeFilter}
              onChange={(event) => setLogTypeFilter(event.target.value)}
            >
              <option value="">Tất cả loại log</option>
              {availableLogTypes.map((logType) => (
                <option key={logType} value={logType}>{logTypeLabels[logType]}</option>
              ))}
            </select>
          </label>
        </div>
      ) : null}

      {isLoading ? (
        <p className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Đang tải nhật ký nghiên cứu...
        </p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Không thể tải nhật ký nghiên cứu.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tải lại
          </button>
        </div>
      ) : !visibleLogs.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Chưa có nhật ký nghiên cứu nào.
        </div>
      ) : (
        <>
          <div className="relative mt-6 space-y-5 before:absolute before:left-1.5 before:top-2 before:h-full before:w-px before:bg-slate-200">
            {visibleLogs.map((log) => (
              <LogItem key={log.id} log={log} />
            ))}
          </div>
          {hasNextPage ? (
            <div className="mt-6 flex justify-center">
              <Button
                loading={isFetchingNextPage}
                loadingText="Đang tải..."
                type="button"
                variant="outline"
                onClick={() => fetchNextPage()}
              >
                Tải thêm
              </Button>
            </div>
          ) : null}
        </>
      )}

      <CreateLogModal
        currentUser={currentUser}
        groupId={resolvedGroupId}
        groupRole={resolvedGroupRole}
        isOpen={isCreateOpen}
        isSubmitting={createLog.isPending}
        projectId={projectId}
        role={role}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={(payload) => {
          createLog.mutate(payload, {
            onSuccess: () => setIsCreateOpen(false),
          });
        }}
      />
    </section>
  );
}
