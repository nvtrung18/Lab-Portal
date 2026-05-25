import { useState } from 'react';

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
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
            type="button"
            onClick={() => setIsCreateOpen(true)}
          >
            Tạo nhóm nghiên cứu
          </button>
        ) : null}
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-600">Đang tải danh sách nhóm nghiên cứu...</p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Không thể tải danh sách nhóm nghiên cứu.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tải lại
          </button>
        </div>
      ) : !groups.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Đề tài này chưa có nhóm nghiên cứu nào.
        </div>
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
                <button
                  className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                  type="button"
                  onClick={() => setDetailGroupId(group.id)}
                >
                  Xem chi tiết
                </button>
                {canCreate ? (
                  <button
                    className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                    onClick={() => setEditingGroup(group)}
                    type="button"
                  >
                    Sửa thông tin
                  </button>
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
