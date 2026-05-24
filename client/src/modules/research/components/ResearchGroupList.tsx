import { useState } from 'react';

import {
  useCreateResearchGroup,
  useResearchEligibleStudents,
  useResearchGroupsByProject,
} from '../hooks';
import type { ResearchProject } from '../types';
import { formatDate, formatGroupStatus, getStatusClass } from '../utils';
import { CreateResearchGroupModal } from './CreateResearchGroupModal';

interface ResearchGroupListProps {
  project: ResearchProject;
  canCreate: boolean;
}

export function ResearchGroupList({ project, canCreate }: ResearchGroupListProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { data: groups = [], isError, isLoading, refetch } = useResearchGroupsByProject(project.id);
  const { data: students = [], isLoading: isLoadingStudents } = useResearchEligibleStudents(project.labId);
  const createGroup = useCreateResearchGroup(project.id, project.labId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Nhom nghien cuu</h3>
          <p className="mt-1 text-sm text-slate-600">Danh sach nhom thuoc de tai dang chon.</p>
        </div>
        {canCreate ? (
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
            type="button"
            onClick={() => setIsCreateOpen(true)}
          >
            Tao nhom nghien cuu
          </button>
        ) : null}
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-600">Dang tai danh sach nhom nghien cuu...</p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Khong the tai danh sach nhom nghien cuu.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tai lai
          </button>
        </div>
      ) : !groups.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Chua co nhom nghien cuu nao.
        </div>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {groups.map((group) => (
            <article key={group.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h4 className="text-base font-semibold text-slate-950">{group.name}</h4>
                  <p className="mt-1 text-sm text-slate-600">{group.objective || 'Chua cap nhat muc tieu nhom.'}</p>
                </div>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                  {formatGroupStatus(group.status)}
                </span>
              </div>
              <p className="mt-3 text-sm text-slate-600">{group.plan || 'Chua cap nhat ke hoach thuc hien.'}</p>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                <div>
                  <dt className="font-semibold text-slate-700">Thanh vien</dt>
                  <dd className="mt-1 text-slate-600">{group.memberCount ?? group.members?.length ?? 0}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Truong nhom</dt>
                  <dd className="mt-1 text-slate-600">{group.createdByName ?? 'Chua cap nhat'}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Ngay tao</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(group.createdAt)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}

      <CreateResearchGroupModal
        isOpen={isCreateOpen}
        project={project}
        students={students}
        isLoadingStudents={isLoadingStudents}
        isSubmitting={createGroup.isPending}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={(payload) => createGroup.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
      />
    </section>
  );
}
