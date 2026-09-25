import { useState } from 'react';

import { useI18n } from '../../../shared/i18n';
import { useCreateProject, useProjectsByGroup } from '../hooks';
import type { ResearchGroup } from '../types';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';
import { CreateProjectModal } from './CreateProjectModal';

interface ProjectPageProps {
  group: ResearchGroup | null;
  canCreate: boolean;
}

export function ProjectPage({ group, canCreate }: ProjectPageProps) {
  const { locale, t } = useI18n();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { data: projects = [], isError, isLoading, refetch } = useProjectsByGroup(group?.id);
  const createProject = useCreateProject(group?.id, group?.labId, group?.topicId);

  if (!group) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
        {t('research.project.chooseGroupToView')}
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('research.project.title')}</h3>
        </div>
        {canCreate ? (
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
            type="button"
            onClick={() => setIsCreateOpen(true)}
          >
            {t('research.project.createTitle')}
          </button>
        ) : null}
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-600">{t('common.loading')}</p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {t('common.loadError')}
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      ) : !projects.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          {t('common.empty')}
        </div>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          {projects.map((project) => (
            <article key={project.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-3">
                <h4 className="text-base font-semibold text-slate-950">
                  {project.code ? `${project.code} - ` : ''}{project.title}
                </h4>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
                  {formatProjectStatus(project.status, locale)}
                </span>
              </div>
              <p className="mt-3 text-sm text-slate-600">
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
                  <dt className="font-semibold text-slate-700">{t('research.project.createdBy')}</dt>
                  <dd className="mt-1 text-slate-600">{project.createdByName ?? t('common.notUpdated')}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}

      <CreateProjectModal
        group={group}
        isOpen={isCreateOpen}
        isSubmitting={createProject.isPending}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={(payload) => createProject.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
      />
    </section>
  );
}
