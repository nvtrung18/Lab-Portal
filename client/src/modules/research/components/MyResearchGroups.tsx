import { useNavigate } from 'react-router-dom';

import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useMyResearchGroups } from '../hooks';
import type { ResearchGroup } from '../types';
import { formatGroupStatus, getStatusClass } from '../utils';

interface MyResearchGroupsProps {
  labId: number;
  currentUserId?: number | null;
}

export function MyResearchGroups({ labId, currentUserId }: MyResearchGroupsProps) {
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  const { data: groups = [], isError, isLoading, refetch } = useMyResearchGroups(labId);

  function getProjectName(group: ResearchGroup) {
    if (group.projectTitle) {
      return group.projectCode ? `${group.projectCode} - ${group.projectTitle}` : group.projectTitle;
    }
    return t('research.group.projectMissing');
  }

  function handleViewGroup(group: ResearchGroup) {
    if (!group.projectId) {
      toast.error(t('research.group.notLinked'));
      return;
    }
    navigate(`/app/research/projects/${group.projectId}/groups/${group.id}`);
  }

  return (
    <section className="min-w-0 space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-xl font-medium text-slate-950">{t('research.group.myGroupsTitle')}</h2>
      </div>

      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-56 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      ) : isError ? (
        <ErrorState onRetry={() => refetch()}>
          {t('research.group.myGroupsLoadFailed')}
        </ErrorState>
      ) : groups.length === 0 ? (
        <EmptyState>
          {t('research.group.noMyGroups')}
        </EmptyState>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          {groups.map((group) => {
            const myRole = group.myRole ?? group.members?.find((member) => member.userId === currentUserId)?.role ?? null;
            const isLeader = myRole === 'LEADER';

            return (
              <article
                key={group.id}
                className="flex min-w-0 flex-col justify-between rounded-lg border border-slate-200 bg-white p-4 shadow-sm hover:shadow-md transition duration-200 sm:p-5"
              >
                <div className="space-y-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <h3 className="min-w-0 flex-1 break-words font-medium text-slate-950 text-base leading-snug line-clamp-2" title={group.name}>
                      {group.name}
                    </h3>
                    <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ring-1 ${getStatusClass(group.status)}`}>
                      {formatGroupStatus(group.status, locale)}
                    </span>
                  </div>

                  <div className="space-y-1">
                    <span className="block text-[11px] font-semibold text-slate-500 tracking-normal dark:text-slate-400">
                      {t('research.group.projectLabelShort')}
                    </span>
                    <p className="min-w-0 break-words text-sm font-medium text-slate-800 line-clamp-2 leading-relaxed">
                      {getProjectName(group)}
                    </p>
                  </div>

                  <div className="grid grid-cols-1 gap-4 border-t border-slate-100 pt-4 text-xs min-[380px]:grid-cols-2">
                    <div>
                      <span className="block font-semibold text-slate-500">{t('research.group.myRoleLabel')}</span>
                      <span className={`inline-flex mt-1 rounded-full px-2 py-0.5 font-medium ${
                        isLeader ? 'bg-blue-50 text-blue-700 ring-1 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-1 ring-slate-100'
                      }`}>
                        {isLeader ? t('research.role.leader') : t('research.role.member')}
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">{t('research.group.leader')}</span>
                      <span className="block mt-1 break-words text-slate-800 font-medium" title={group.leaderName || t('common.notAssigned')}>
                        {group.leaderName || t('common.notAssigned')}
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">{t('research.group.membersLabel')}</span>
                      <span className="block mt-1 text-slate-800 font-medium">
                        {group.memberCount ?? group.members?.length ?? 0} {t('research.group.people')}
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">{t('research.group.topicRecruiting')}</span>
                      <span className="block mt-1 break-words text-slate-800 font-medium" title={group.topicName || t('common.notUpdated')}>
                        {group.topicName || t('common.notUpdated')}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-5 pt-4 border-t border-slate-100">
                  <Button
                    onClick={() => handleViewGroup(group)}
                    size="sm"
                    className="w-full flex justify-center text-sm font-semibold py-2"
                  >
                    {t('research.group.viewDetails')}
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
