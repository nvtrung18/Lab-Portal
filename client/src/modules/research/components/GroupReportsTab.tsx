import { useState, useMemo } from 'react';
import axios from 'axios';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { downloadReportFile } from '../api';
import { useGroupReports, useMyGroupReports, useMyResearchTasks } from '../hooks';
import type { ResearchReport } from '../types';
import { formatDate, getApiErrorMessage, formatReportSubmitterName } from '../utils';
import { ReportStatusBadge } from './ReportStatusBadge';
import { LeaderReviewButton } from './LeaderReviewButton';
import { ManagerReviewActions } from './ManagerReviewActions';
import { ReportHistoryModal } from './ReportHistoryModal';
import { ReportUploadModal } from './ReportUploadModal';
import { ReportCommentsModal } from './ReportCommentsModal';

interface GroupReportsTabProps {
  groupId: number;
  projectId?: number | null;
  currentUserId?: number | null;
  role?: 'LAB_MANAGER' | 'GROUP_LEADER' | 'STUDENT_MEMBER';
  labId?: number | null;
  scope?: 'me' | 'group';
}

export function GroupReportsTab({
  groupId,
  projectId,
  currentUserId,
  role = 'GROUP_LEADER',
  labId,
  scope,
}: GroupReportsTabProps) {
  const { locale, t } = useI18n();
  const isLeader = role === 'GROUP_LEADER';
  const isMember = role === 'STUDENT_MEMBER';

  const [subTab, setSubTab] = useState<'me' | 'group'>(scope ?? (isMember ? 'me' : 'group'));

  const {
    data: myReports = [],
    isError: isMyError,
    error: myError,
    isLoading: isLoadingMy,
    refetch: refetchMy,
  } = useMyGroupReports(subTab === 'me' ? groupId : null);

  const {
    data: allReports = [],
    isError: isAllError,
    error: allError,
    isLoading: isLoadingAll,
    refetch: refetchAll,
  } = useGroupReports(subTab === 'group' ? groupId : null);

  const {
    data: myTasks = [],
    isLoading: isLoadingTasks,
  } = useMyResearchTasks(subTab === 'me' ? groupId : null);

  const reports = subTab === 'me' ? myReports : allReports;
  const isLoading = (subTab === 'me' ? isLoadingMy : isLoadingAll) || (subTab === 'me' ? isLoadingTasks : false);
  const isError = subTab === 'me' ? isMyError : isAllError;
  const error = subTab === 'me' ? myError : allError;
  const refetch = subTab === 'me' ? refetchMy : refetchAll;

  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  const [historyModalConfig, setHistoryModalConfig] = useState<{
    isOpen: boolean;
    taskId: number;
    taskTitle: string;
  } | null>(null);

  const [commentsModalConfig, setCommentsModalConfig] = useState<{
    isOpen: boolean;
    reportId: number;
    taskTitle: string;
    version: number;
  } | null>(null);

  const [uploadModalConfig, setUploadModalConfig] = useState<{
    isOpen: boolean;
    report: ResearchReport;
  } | null>(null);

  const latestReports = useMemo(() => {
    const map = new Map<string, ResearchReport>();
    reports.forEach((r) => {
      if (!r.taskId) return;
      const key = `${r.taskId}_${r.submittedById}`;
      const existing = map.get(key);
      if (!existing || r.version > existing.version) {
        map.set(key, r);
      }
    });
    return Array.from(map.values()).sort((a, b) => {
      return (b.createdAt ?? '').localeCompare(a.createdAt ?? '');
    });
  }, [reports]);

  async function handleDownload(report: ResearchReport) {
    setDownloadingId(report.id);
    try {
      const file = await downloadReportFile(report.id);
      const fileUrl = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = fileUrl;
      link.download = report.fileName || `bao-cao-v${report.version}`;
      link.click();
      URL.revokeObjectURL(fileUrl);
    } catch {
      toast.error(t('common.serverFailure'));
    } finally {
      setDownloadingId(null);
    }
  }

  function getSubmitterLabel(report: ResearchReport) {
    if (report.submittedById === currentUserId) {
      return t('research.reports.mine');
    }
    return formatReportSubmitterName(report, locale);
  }

  // Resolve the active report tab title.
  const displayTitle = subTab === 'me' ? t('research.reports.mine') : t('research.reports.group');

  // Resolve custom error messages
  let customErrorMsg = '';
  if (isError) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      if (subTab === 'me') {
        if (status === 403) {
          customErrorMsg = t('research.reports.forbiddenMine');
        } else if (status === 404) {
          customErrorMsg = t('research.reports.notFound');
        } else {
          customErrorMsg = t('research.reports.loadMineFailed');
        }
      } else {
        if (status === 403) {
          customErrorMsg = t('research.reports.forbiddenGroup');
        } else if (status === 404) {
          customErrorMsg = t('research.reports.notFound');
        } else {
          customErrorMsg = t('research.reports.loadGroupFailed');
        }
      }
    } else {
      customErrorMsg =
        subTab === 'me'
          ? t('research.reports.loadMineFailed')
          : t('research.reports.loadGroupFailed');
    }
  }

  customErrorMsg = getApiErrorMessage(error, {
    fallback:
      subTab === 'me'
        ? t('research.reports.loadMineFailed')
        : t('research.reports.loadGroupFailed'),
    forbidden:
      subTab === 'me'
        ? t('research.reports.forbiddenMine')
        : t('research.reports.forbiddenGroup'),
  });

  // Resolve custom empty states
  const emptyMsg =
    subTab === 'me'
      ? t('research.reports.emptyMine')
      : t('research.reports.emptyGroup');

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm space-y-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <h3 className="text-lg font-medium text-slate-950">{displayTitle}</h3>
        </div>

        {/* Sub-tabs for Leader to switch views */}
        {isLeader && !scope && (
          <div className="flex shrink-0 gap-1.5 rounded-md bg-slate-100 p-1">
            <button
              onClick={() => setSubTab('me')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                subTab === 'me'
                  ? 'bg-white text-blue-700 shadow-sm font-medium'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {t('research.reports.mine')}
            </button>
            <button
              onClick={() => setSubTab('group')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                subTab === 'group'
                  ? 'bg-white text-blue-700 shadow-sm font-medium'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {t('research.reports.group')}
            </button>
          </div>
        )}
      </div>

      {isLoading ? (
        <LoadingState className="py-8">{t('research.report.loadingList')}</LoadingState>
      ) : isError ? (
        <ErrorState onRetry={refetch} className="py-8">
          {customErrorMsg}
        </ErrorState>
      ) : latestReports.length === 0 ? (
        subTab === 'me' ? (
          myTasks.length === 0 ? (
            <EmptyState className="py-12">{t('research.report.noTasksToSubmit')}</EmptyState>
          ) : (
            <div className="space-y-4">
              <p className="text-sm text-slate-600 font-semibold">
                {t('research.report.noSubmittedWithTasks')}
              </p>
              <div className="space-y-3">
                {myTasks.map((task) => (
                  <article key={task.id} className="rounded-md border border-slate-200 bg-white p-4">
                    <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                      <div className="min-w-0 flex-1">
                        {task.milestoneTitle && (
                          <p className="text-xs text-slate-500 mb-1">
                            {t('research.task.milestonePrefix')}: <span className="font-semibold text-slate-700">{task.milestoneTitle}</span>
                          </p>
                        )}
                        <h4 className="font-semibold text-slate-950">{task.title}</h4>
                        {task.description ? <p className="mt-1 text-sm text-slate-600">{task.description}</p> : null}
                        <p className="mt-2 text-xs text-slate-500">
                          {t('research.task.deadline')}: {formatDate(task.deadline, locale)} · {t('research.task.status')}: {task.statusLabel}
                        </p>
                      </div>
                      <div className="shrink-0">
                        <Button
                          onClick={() =>
                            setUploadModalConfig({
                              isOpen: true,
                              report: {
                                taskId: task.id,
                                taskTitle: task.title,
                                milestoneId: task.milestoneId,
                                projectId,
                              } as any,
                            })
                          }
                          size="sm"
                        >
                          {t('research.report.submit')}
                        </Button>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          )
        ) : (
          <EmptyState className="py-12">{emptyMsg}</EmptyState>
        )
      ) : (
        <div className="space-y-4">
          {latestReports.map((report) => {
            const isMyReport = report.submittedById === currentUserId;

            return (
              <article
                key={report.id}
                className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm hover:border-slate-300 transition duration-200 space-y-4"
              >
                {/* Task Title & Report Status */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-50 pb-3">
                  <div className="min-w-0">
                    <h4 className="text-base font-medium text-slate-900 leading-snug truncate" title={report.taskTitle || report.title}>
                      {report.taskTitle || t('research.report.commonTask')}
                    </h4>
                  </div>
                  
                  <div>
                    <ReportStatusBadge status={report.status} submittedByGroupRole={report.submittedByGroupRole} />
                  </div>
                </div>

                {/* Submitter & File Details */}
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 text-sm bg-slate-50/50 rounded-lg p-3 border border-slate-100/50">
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{t('research.report.uploader')}</span>
                    <span className="block mt-0.5 font-medium text-slate-800 truncate" title={getSubmitterLabel(report)}>
                      {getSubmitterLabel(report)}
                    </span>
                  </div>
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{t('research.report.milestone')}</span>
                    <span className="block mt-0.5 font-medium text-slate-800 truncate" title={report.milestoneTitle || 'N/A'}>
                      {report.milestoneTitle || t('research.report.commonTask')}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{t('research.report.latest')}</span>
                    <span className="block mt-0.5 font-medium text-blue-700">v{report.version}</span>
                  </div>
                  <div>
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{t('research.report.submittedAt')}</span>
                    <span className="block mt-0.5 text-slate-700 font-medium">{formatDate(report.createdAt, locale)}</span>
                  </div>
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">{t('research.product.fileName')}</span>
                    <button
                      onClick={() => handleDownload(report)}
                      className="block mt-0.5 text-blue-600 hover:text-blue-800 font-semibold underline truncate text-left w-full"
                      title={report.fileName || t('research.report.noAttachment')}
                    >
                      {report.fileName || t('research.report.noAttachment')}
                    </button>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                  <div className="flex flex-wrap gap-2.5">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() =>
                        setHistoryModalConfig({
                          isOpen: true,
                          taskId: report.taskId!,
                          taskTitle: report.taskTitle || t('research.report.title'),
                        })
                      }
                    >
                      {t('research.report.history')}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() =>
                        setCommentsModalConfig({
                          isOpen: true,
                          reportId: report.id,
                          taskTitle: report.taskTitle || t('research.report.title'),
                          version: report.version,
                        })
                      }
                    >
                      {t('research.report.viewCommentsCount', undefined, { count: report.commentCount ?? 0 })}
                    </Button>

                    {/* Submit / Resubmit Report Button — only when status allows */}
                    {isMyReport && (report.status === 'NEEDS_REVISION' || report.status === 'LEADER_REJECTED' || report.status === 'MANAGER_REJECTED') && (
                      <Button
                        size="sm"
                        onClick={() => setUploadModalConfig({ isOpen: true, report })}
                      >
                        {t('research.report.resubmit')}
                      </Button>
                    )}
                  </div>

                  <div className="flex items-center gap-3">
                    {/* Leader inline check button - visible only on all group reports view */}
                    {role === 'GROUP_LEADER' && subTab === 'group' && (
                      <>
                        <LeaderReviewButton
                          currentUserId={currentUserId}
                          groupId={groupId}
                          report={report}
                        />
                        {Number(report.submittedById) === Number(currentUserId) && report.status === 'SUBMITTED' && (
                          <span className="text-xs italic text-slate-500 bg-slate-100 px-2.5 py-1.5 rounded-md border border-slate-200">
                            {t('research.report.managerDirectReview')}
                          </span>
                        )}
                      </>
                    )}
                    {/* Manager inline review actions */}
                    {role === 'LAB_MANAGER' && (
                      <ManagerReviewActions labId={labId} report={report} />
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Report History Modal */}
      {historyModalConfig && (
        <ReportHistoryModal
          isOpen={historyModalConfig.isOpen}
          onClose={() => setHistoryModalConfig(null)}
          taskId={historyModalConfig.taskId}
          taskTitle={historyModalConfig.taskTitle}
          currentUserId={currentUserId}
          role={
            role === 'LAB_MANAGER'
              ? 'LAB_MANAGER'
              : role === 'GROUP_LEADER' && subTab === 'group'
              ? 'GROUP_LEADER'
              : 'STUDENT_MEMBER'
          }
          labId={labId}
          groupId={groupId}
        />
      )}

      {/* Report Comments Modal */}
      {commentsModalConfig && (
        <ReportCommentsModal
          isOpen={commentsModalConfig.isOpen}
          onClose={() => {
            setCommentsModalConfig(null);
            refetch();
          }}
          reportId={commentsModalConfig.reportId}
          taskTitle={commentsModalConfig.taskTitle}
          version={commentsModalConfig.version}
          currentUserId={currentUserId}
        />
      )}

      {/* Resubmit Upload Modal */}
      {uploadModalConfig && (
        <ReportUploadModal
          isOpen={uploadModalConfig.isOpen}
          onClose={() => {
            setUploadModalConfig(null);
            refetch();
          }}
          milestoneId={uploadModalConfig.report.milestoneId}
          projectId={uploadModalConfig.report.projectId}
          groupId={groupId}
          tasks={[
            {
              id: uploadModalConfig.report.taskId!,
              title: uploadModalConfig.report.taskTitle || t('research.report.commonTask'),
            } as any,
          ]}
          title={t('research.report.submitForTask', undefined, { task: uploadModalConfig.report.taskTitle || t('research.report.commonTask') })}
        />
      )}
    </section>
  );
}
