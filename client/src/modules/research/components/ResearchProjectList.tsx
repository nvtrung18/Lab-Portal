import { useState } from 'react';
import { Link } from 'react-router-dom';

import {
  useCreateResearchProject,
  useResearchProjectsByLab,
  useStudentResearchProjectsByLab,
  useUpdateResearchProject,
} from '../hooks';
import type { ResearchProject } from '../types';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';
import { CreateResearchProjectModal } from './CreateResearchProjectModal';

interface ResearchProjectListProps {
  labId: number | null;
  canCreate: boolean;
  mode?: 'manager' | 'student';
}

export function ResearchProjectList({ labId, canCreate, mode = 'manager' }: ResearchProjectListProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<ResearchProject | null>(null);
  const managerProjects = useResearchProjectsByLab(mode === 'manager' ? labId : null);
  const studentProjects = useStudentResearchProjectsByLab(mode === 'student' ? labId : null);
  const { data: projects = [], isError, isLoading, refetch } =
    mode === 'student' ? studentProjects : managerProjects;
  const createProject = useCreateResearchProject(labId);
  const updateProject = useUpdateResearchProject(labId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Đề tài nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">
            {mode === 'student'
              ? 'Danh sách đề tài nghiên cứu thuộc PTN bạn đang tham gia.'
              : 'Danh sách đề tài nghiên cứu thuộc PTN đang quản lý.'}
          </p>
        </div>
        {canCreate ? (
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
            type="button"
            onClick={() => setIsCreateOpen(true)}
          >
            Tạo đề tài nghiên cứu
          </button>
        ) : null}
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-600">Đang tải danh sách đề tài nghiên cứu...</p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Không thể tải danh sách đề tài nghiên cứu.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tải lại
          </button>
        </div>
      ) : !projects.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          {mode === 'student' ? 'PTN này chưa có đề tài nghiên cứu nào.' : 'Chưa có đề tài nghiên cứu nào.'}
        </div>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {projects.map((project) => (
            <article key={project.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <h4 className="text-base font-semibold text-slate-950">
                  {project.code ? `${project.code} - ` : ''}
                  {project.title}
                </h4>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
                  {formatProjectStatus(project.status)}
                </span>
              </div>
              <p className="mt-2 text-sm font-medium text-slate-700">
                {project.researchDirection ?? 'Chưa cập nhật hướng nghiên cứu'}
              </p>
              <p className="mt-3 text-sm text-slate-600">
                {project.objective || project.description || 'Chưa cập nhật mục tiêu nghiên cứu.'}
              </p>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="font-semibold text-slate-700">Ngày bắt đầu</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(project.startDate)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Ngày kết thúc dự kiến</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(project.expectedEndDate ?? project.endDate)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Mức độ ưu tiên</dt>
                  <dd className="mt-1 text-slate-600">{formatPriority(project.priority)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Quản lý hướng dẫn</dt>
                  <dd className="mt-1 text-slate-600">{project.managerName ?? 'Chưa cập nhật'}</dd>
                </div>
              </dl>
              {mode === 'manager' ? (
                <div className="mt-4 flex flex-wrap gap-2">
                  <Link
                    className="inline-flex rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                    to={`/app/research/projects/${project.id}`}
                  >
                    Xem chi tiết
                  </Link>
                  <button
                    className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                    onClick={() => setEditingProject(project)}
                    type="button"
                  >
                    Sửa đề tài
                  </button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {canCreate ? (
        <>
          <CreateResearchProjectModal
            labId={labId}
            isOpen={isCreateOpen}
            isSubmitting={createProject.isPending}
            onClose={() => setIsCreateOpen(false)}
            onSubmit={(payload) => createProject.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
          />
          <CreateResearchProjectModal
            labId={labId}
            project={editingProject}
            isOpen={Boolean(editingProject)}
            isSubmitting={updateProject.isPending}
            onClose={() => setEditingProject(null)}
            onSubmit={(payload) => {
              if (!editingProject) {
                return;
              }
              updateProject.mutate(
                { projectId: editingProject.id, payload },
                { onSuccess: () => setEditingProject(null) },
              );
            }}
          />
        </>
      ) : null}
    </section>
  );
}
