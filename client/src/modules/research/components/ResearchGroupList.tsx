import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import {
  useCreateResearchGroup,
  useResearchEligibleStudents,
  useResearchGroupsByProject,
  useUpdateResearchGroup,
} from '../hooks';
import type { ResearchGroup, ResearchProject } from '../types';
import { formatDate, formatGroupStatus, getStatusClass } from '../utils';
import { CreateResearchGroupModal } from './CreateResearchGroupModal';
import { EditResearchGroupModal } from './EditResearchGroupModal';
import { ResearchGroupDetailModal } from './ResearchGroupDetailModal';

interface ResearchGroupListProps {
  project: ResearchProject;
  canCreate: boolean;
}

export function ResearchGroupList({ project, canCreate }: ResearchGroupListProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [detailGroupId, setDetailGroupId] = useState<number | null>(null);
  const [editingGroup, setEditingGroup] = useState<ResearchGroup | null>(null);
  const { data: groups = [], isError, isLoading, refetch } = useResearchGroupsByProject(project.id);
  const { data: students = [], isLoading: isLoadingStudents } = useResearchEligibleStudents(
    canCreate ? project.labId : null,
  );
  const createGroup = useCreateResearchGroup(project.id, project.labId);
  const updateGroup = useUpdateResearchGroup(project.id, project.labId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Nhóm nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">Danh sách nhóm thuộc đề tài đang chọn.</p>
        </div>
        {canCreate ? (
          <Button onClick={() => setIsCreateOpen(true)}>
            Tạo nhóm nghiên cứu
          </Button>
        ) : null}
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải danh sách nhóm nghiên cứu...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách nhóm nghiên cứu.
        </ErrorState>
      ) : !groups.length ? (
        <EmptyState className="mt-5">
          Đề tài này chưa có nhóm nghiên cứu nào.
        </EmptyState>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {groups.map((group) => (
            <article key={group.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h4 className="text-base font-semibold text-slate-950">{group.name}</h4>
                  <p className="mt-1 text-sm text-slate-600">{group.objective || 'Chưa cập nhật mục tiêu nhóm.'}</p>
                </div>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                  {formatGroupStatus(group.status)}
                </span>
              </div>
              <p className="mt-3 text-sm text-slate-600">{group.plan || 'Chưa cập nhật kế hoạch thực hiện.'}</p>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                <div>
                  <dt className="font-semibold text-slate-700">Thành viên</dt>
                  <dd className="mt-1 text-slate-600">{group.memberCount ?? group.members?.length ?? 0}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Trưởng nhóm</dt>
                  <dd className="mt-1 text-slate-600">{group.leaderName ?? 'Chưa cập nhật'}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Ngày tạo</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(group.createdAt)}</dd>
                </div>
              </dl>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button onClick={() => setDetailGroupId(group.id)} size="sm" variant="outline">
                  Xem chi tiết
                </Button>
                {canCreate ? (
                  <Button onClick={() => setEditingGroup(group)} size="sm" variant="outline">
                    Sửa thông tin
                  </Button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      )}

      {canCreate ? (
        <>
          <CreateResearchGroupModal
            isOpen={isCreateOpen}
            project={project}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={createGroup.isPending}
            onClose={() => setIsCreateOpen(false)}
            onSubmit={(payload) => createGroup.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
          />
          <EditResearchGroupModal
            group={editingGroup}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={updateGroup.isPending}
            onClose={() => setEditingGroup(null)}
            onSubmit={(payload) => {
              if (!editingGroup) {
                return;
              }
              updateGroup.mutate(
                { groupId: editingGroup.id, payload },
                { onSuccess: () => setEditingGroup(null) },
              );
            }}
          />
        </>
      ) : null}

      <ResearchGroupDetailModal groupId={detailGroupId} onClose={() => setDetailGroupId(null)} />
    </section>
  );
}
