import { useState, useMemo } from 'react';
import axios from 'axios';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
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
  const isLeader = role === 'GROUP_LEADER';
  const isMember = role === 'STUDENT_MEMBER';

  // Toggle state for leaders: 'me' (Báo cáo của tôi) or 'group' (Báo cáo toàn nhóm)
  const [subTab, setSubTab] = useState<'me' | 'group'>(scope ?? (isMember ? 'me' : 'group'));

  // Load My Reports (always queried if needed, enabled conditionally)
  const {
    data: myReports = [],
    isError: isMyError,
    error: myError,
    isLoading: isLoadingMy,
    refetch: refetchMy,
  } = useMyGroupReports(subTab === 'me' ? groupId : null);

  // Load Group Reports (always queried if needed, enabled conditionally)
  const {
    data: allReports = [],
    isError: isAllError,
    error: allError,
    isLoading: isLoadingAll,
    refetch: refetchAll,
  } = useGroupReports(subTab === 'group' ? groupId : null);

  // Load My Tasks if we are in "Báo cáo của tôi" to check for empty states
  const {
    data: myTasks = [],
    isLoading: isLoadingTasks,
  } = useMyResearchTasks(subTab === 'me' ? groupId : null);

  const reports = subTab === 'me' ? myReports : allReports;
  const isLoading = (subTab === 'me' ? isLoadingMy : isLoadingAll) || (subTab === 'me' ? isLoadingTasks : false);
  const isError = subTab === 'me' ? isMyError : isAllError;
  const error = subTab === 'me' ? myError : allError;
  const refetch = subTab === 'me' ? refetchMy : refetchAll;

  // States for actions
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

  // Filter and keep only the latest report version per task per student
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
      toast.error('Không thể tải tài liệu báo cáo. Vui lòng thử lại.');
    } finally {
      setDownloadingId(null);
    }
  }

  function getSubmitterLabel(report: ResearchReport) {
    if (report.submittedById === currentUserId) {
      return 'Báo cáo của tôi';
    }
    return formatReportSubmitterName(report);
  }

  // Resolve custom titles/subtitles
  const displayTitle = subTab === 'me' ? 'Báo cáo của tôi' : 'Báo cáo toàn nhóm';
  const displaySubtitle =
    subTab === 'me'
      ? 'Bạn chỉ có thể xem các báo cáo do mình đã nộp trong nhóm này.'
      : 'Xem và quản lý các báo cáo tiến độ đã nộp của các thành viên trong nhóm nghiên cứu.';

  // Resolve custom error messages
  let customErrorMsg = '';
  if (isError) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      if (subTab === 'me') {
        if (status === 403) {
          customErrorMsg = 'Bạn không có quyền xem báo cáo trong nhóm này.';
        } else if (status === 404) {
          customErrorMsg = 'Không tìm thấy nhóm nghiên cứu.';
        } else {
          customErrorMsg = 'Không thể tải danh sách báo cáo cá nhân.';
        }
      } else {
        if (status === 403) {
          customErrorMsg = 'Bạn không có quyền xem báo cáo toàn nhóm.';
        } else if (status === 404) {
          customErrorMsg = 'Không tìm thấy nhóm nghiên cứu.';
        } else {
          customErrorMsg = 'Không thể tải danh sách báo cáo toàn nhóm.';
        }
      }
    } else {
      customErrorMsg =
        subTab === 'me'
          ? 'Không thể tải danh sách báo cáo cá nhân.'
          : 'Không thể tải danh sách báo cáo toàn nhóm.';
    }
  }

  customErrorMsg = getApiErrorMessage(error, {
    fallback:
      subTab === 'me'
        ? 'Không thể tải danh sách báo cáo cá nhân.'
        : 'Không thể tải danh sách báo cáo toàn nhóm.',
    forbidden:
      subTab === 'me'
        ? 'Bạn không có quyền xem báo cáo trong nhóm này.'
        : 'Bạn không có quyền xem báo cáo toàn nhóm.',
  });

  // Resolve custom empty states
  const emptyMsg =
    subTab === 'me'
      ? 'Bạn chưa nộp báo cáo nào trong nhóm này.'
      : 'Nhóm chưa có báo cáo nào được nộp.';

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm space-y-5">
      {/* Header Info */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <h3 className="text-lg font-bold text-slate-950">{displayTitle}</h3>
          <p className="mt-1 text-sm text-slate-600">{displaySubtitle}</p>
        </div>

        {/* Sub-tabs for Leader to switch views */}
        {isLeader && !scope && (
          <div className="flex shrink-0 gap-1.5 rounded-md bg-slate-100 p-1">
            <button
              onClick={() => setSubTab('me')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                subTab === 'me'
                  ? 'bg-white text-blue-700 shadow-sm font-bold'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Báo cáo của tôi
            </button>
            <button
              onClick={() => setSubTab('group')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                subTab === 'group'
                  ? 'bg-white text-blue-700 shadow-sm font-bold'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Báo cáo toàn nhóm
            </button>
          </div>
        )}
      </div>

      {isLoading ? (
        <LoadingState className="py-8">Đang tải báo cáo...</LoadingState>
      ) : isError ? (
        <ErrorState onRetry={refetch} className="py-8">
          {customErrorMsg}
        </ErrorState>
      ) : latestReports.length === 0 ? (
        subTab === 'me' ? (
          myTasks.length === 0 ? (
            <EmptyState className="py-12">Bạn chưa có nhiệm vụ nào để nộp báo cáo.</EmptyState>
          ) : (
            <div className="space-y-4">
              <p className="text-sm text-slate-600 font-semibold">
                Bạn chưa nộp báo cáo nào. Dưới đây là danh sách nhiệm vụ của bạn:
              </p>
              <div className="space-y-3">
                {myTasks.map((task) => (
                  <article key={task.id} className="rounded-md border border-slate-200 bg-white p-4">
                    <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                      <div className="min-w-0 flex-1">
                        {task.milestoneTitle && (
                          <p className="text-xs text-slate-500 mb-1">
                            Mốc: <span className="font-semibold text-slate-700">{task.milestoneTitle}</span>
                          </p>
                        )}
                        <h4 className="font-semibold text-slate-950">{task.title}</h4>
                        <p className="mt-1 text-sm text-slate-600">{task.description || 'Chưa cập nhật mô tả nhiệm vụ.'}</p>
                        <p className="mt-2 text-xs text-slate-500">
                          Hạn hoàn thành: {formatDate(task.deadline)} · Trạng thái: {task.statusLabel}
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
                          Nộp báo cáo
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
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className="rounded-full bg-blue-50 p-1.5 shrink-0 text-blue-600">
                      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
                      </svg>
                    </div>
                    <h4 className="text-base font-bold text-slate-900 leading-snug truncate" title={report.taskTitle || report.title}>
                      {report.taskTitle || 'Nhiệm vụ chung'}
                    </h4>
                  </div>
                  
                  <div>
                    <ReportStatusBadge status={report.status} submittedByGroupRole={report.submittedByGroupRole} />
                  </div>
                </div>

                {/* Submitter & File Details */}
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 text-sm bg-slate-50/50 rounded-lg p-3 border border-slate-100/50">
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Người nộp</span>
                    <span className="block mt-0.5 font-bold text-slate-800 truncate" title={getSubmitterLabel(report)}>
                      {getSubmitterLabel(report)}
                    </span>
                  </div>
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Mốc</span>
                    <span className="block mt-0.5 font-bold text-slate-800 truncate" title={report.milestoneTitle || 'N/A'}>
                      {report.milestoneTitle || 'Nhiệm vụ chung'}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Báo cáo mới nhất</span>
                    <span className="block mt-0.5 font-bold text-blue-700">v{report.version}</span>
                  </div>
                  <div>
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Ngày nộp</span>
                    <span className="block mt-0.5 text-slate-700 font-medium">{formatDate(report.createdAt)}</span>
                  </div>
                  <div className="min-w-0">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Tên file</span>
                    <button
                      onClick={() => handleDownload(report)}
                      className="block mt-0.5 text-blue-600 hover:text-blue-800 font-semibold underline truncate text-left w-full"
                      title={report.fileName || 'Chưa đính kèm file'}
                    >
                      {report.fileName || 'Chưa đính kèm file'}
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
                          taskTitle: report.taskTitle || 'Báo cáo',
                        })
                      }
                    >
                      <svg className="mr-1.5 h-4 w-4 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                      Xem lịch sử báo cáo
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() =>
                        setCommentsModalConfig({
                          isOpen: true,
                          reportId: report.id,
                          taskTitle: report.taskTitle || 'Báo cáo',
                          version: report.version,
                        })
                      }
                    >
                      <svg className="mr-1.5 h-4 w-4 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                      </svg>
                      Xem góp ý ({report.commentCount ?? 0})
                    </Button>

                    {/* Submit / Resubmit Report Button — only when status allows */}
                    {isMyReport && (report.status === 'NEEDS_REVISION' || report.status === 'LEADER_REJECTED' || report.status === 'MANAGER_REJECTED') && (
                      <Button
                        size="sm"
                        onClick={() => setUploadModalConfig({ isOpen: true, report })}
                      >
                        <svg className="mr-1.5 h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                        </svg>
                        Nộp lại báo cáo
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
                            Báo cáo của bạn sẽ được quản lý PTN duyệt trực tiếp.
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
              title: uploadModalConfig.report.taskTitle || 'Nhiệm vụ',
            } as any,
          ]}
          title={`Nộp báo cáo cho nhiệm vụ: ${
            uploadModalConfig.report.taskTitle || 'Nhiệm vụ'
          }`}
        />
      )}
    </section>
  );
}
