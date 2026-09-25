import { ErrorState, LoadingState, Modal, ResponsiveTable } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useResearchGroup } from '../hooks';
import { formatDate, formatGroupRole, formatGroupStatus, getStatusClass } from '../utils';

interface ResearchGroupDetailModalProps {
  groupId: number | null;
  onClose: () => void;
}

export function ResearchGroupDetailModal({ groupId, onClose }: ResearchGroupDetailModalProps) {
  const { locale, t } = useI18n();
  const { data: group, isError, isLoading, refetch } = useResearchGroup(groupId);

  if (!groupId) {
    return null;
  }

  const projectName = group?.projectTitle
    ? group.projectCode
      ? `${group.projectCode} - ${group.projectTitle}`
      : group.projectTitle
    : t('common.notUpdated');

  return (
    <Modal onClose={onClose} size="xl" title={t('research.group.detailTitle')}>
        {isLoading ? (
          <LoadingState>{t('research.group.detailLoading')}</LoadingState>
        ) : isError || !group ? (
          <ErrorState onRetry={() => refetch()}>
            {t('research.group.detailLoadFailed')}
          </ErrorState>
        ) : (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h4 className="text-base font-semibold text-slate-950">{group.name}</h4>
                <p className="mt-1 text-sm text-slate-600">{t('research.group.project')}: {projectName}</p>
              </div>
              <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                {formatGroupStatus(group.status)}
              </span>
            </div>

            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Detail label={t('research.group.objective')} value={group.objective ?? t('common.notUpdated')} />
              <Detail label={t('research.group.plan')} value={group.plan ?? t('common.notUpdated')} />
              <Detail label={t('research.group.leader')} value={group.leaderName ?? t('common.notUpdated')} />
              <Detail label={t('research.group.createdAt')} value={formatDate(group.createdAt, locale)} />
              <Detail label={t('research.group.createdBy')} value={group.managerName ?? group.createdByName ?? t('common.notUpdated')} />
              <Detail label={t('research.group.statusLabel')} value={formatGroupStatus(group.status, locale)} />
            </dl>

            <div>
              <h4 className="text-sm font-semibold text-slate-950">{t('research.group.members')}</h4>
              {!group.members?.length ? (
                <p className="mt-3 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                  {t('research.group.noMembers')}
                </p>
              ) : (
                <ResponsiveTable className="mt-3">
                  <table className="w-full min-w-[520px] divide-y divide-slate-200 text-sm">
                    <thead className="bg-slate-50">
                      <tr className="text-left font-semibold text-slate-700">
                        <th className="px-3 py-3">{t('research.group.member')}</th>
                        <th className="px-3 py-3">{t('research.group.role')}</th>
                        <th className="px-3 py-3">{t('research.group.joinedAt')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {group.members.map((member) => (
                        <tr key={member.id}>
                          <td className="px-3 py-3">
                            <p className="font-medium text-slate-950">{member.fullName ?? member.email ?? `${t('research.group.student')} #${member.userId}`}</p>
                            <p className="mt-1 text-xs text-slate-500">{member.email}</p>
                          </td>
                          <td className="px-3 py-3 text-slate-600">{formatGroupRole(member.role, locale)}</td>
                          <td className="px-3 py-3 text-slate-600">{formatDate(member.joinedAt, locale)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </ResponsiveTable>
              )}
            </div>
          </div>
        )}
    </Modal>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 whitespace-pre-wrap text-slate-600">{value}</dd>
    </div>
  );
}
