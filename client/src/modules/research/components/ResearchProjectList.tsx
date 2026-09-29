import { useState } from 'react';
import { Link } from 'react-router-dom';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
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
  const { locale, t } = useI18n();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<ResearchProject | null>(null);
  const managerProjects = useResearchProjectsByLab(mode === 'manager' ? labId : null);
  const studentProjects = useStudentResearchProjectsByLab(mode === 'student' ? labId : null);
  const { data: projects = [], isError, isLoading, refetch } =
    mode === 'student' ? studentProjects : managerProjects;
  const createProject = useCreateResearchProject(labId);
  const updateProject = useUpdateResearchProject(labId);

  return (
    <section className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('research.project.listTitle')}</h3>
        </div>
        {canCreate ? (
          <Button onClick={() => setIsCreateOpen(true)}>
            {t('research.project.createTitle')}
          </Button>
        ) : null}
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.project.listLoading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {t('research.project.listLoadFailed')}
        </ErrorState>
      ) : !projects.length ? (
        <EmptyState className="mt-5">
          {mode === 'student' ? t('research.project.studentEmpty') : t('research.project.empty')}
        </EmptyState>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {projects.map((project) => (
            <article key={project.id} className="min-w-0 rounded-md border border-slate-200 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h4 className="min-w-0 break-words text-base font-semibold text-slate-950">
                  {project.code ? `${project.code} - ` : ''}
                  {project.title}
                </h4>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
                  {formatProjectStatus(project.status, locale)}
                </span>
              </div>
              <p className="mt-2 break-words text-sm font-medium text-slate-700">
                {project.researchDirection ?? t('research.project.noDirection')}
              </p>
              <p className="mt-3 break-words text-sm text-slate-600">
                {project.objective || project.description || t('research.group.noObjective')}
              </p>
              <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="font-semibold text-slate-700">{t('research.project.startDateLabel')}</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(project.startDate, locale)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('research.project.expectedEndDateLabel')}</dt>
                  <dd className="mt-1 text-slate-600">{formatDate(project.expectedEndDate ?? project.endDate, locale)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('research.project.priority')}</dt>
                  <dd className="mt-1 text-slate-600">{formatPriority(project.priority, locale)}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('research.project.manager')}</dt>
                  <dd className="mt-1 text-slate-600">{project.managerName ?? t('common.notUpdated')}</dd>
                </div>
              </dl>
              <div className="mt-4 flex flex-wrap gap-2">
                <Link
                  className="inline-flex min-h-11 items-center rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                  to={`/app/research/projects/${project.id}`}
                >
                  {t('common.viewDetails')}
                </Link>
                {mode === 'manager' ? (
                  <Button onClick={() => setEditingProject(project)} size="sm" variant="outline">
                    {t('research.project.edit')}
                  </Button>
                ) : null}
              </div>
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
