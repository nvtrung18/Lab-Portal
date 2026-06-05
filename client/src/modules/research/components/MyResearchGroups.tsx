import { useNavigate } from 'react-router-dom';

import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useMyResearchGroups } from '../hooks';
import type { ResearchGroup } from '../types';
import { formatGroupStatus, getStatusClass } from '../utils';

interface MyResearchGroupsProps {
  labId: number;
  currentUserId?: number | null;
}

export function MyResearchGroups({ labId, currentUserId }: MyResearchGroupsProps) {
  const navigate = useNavigate();
  const { data: groups = [], isError, isLoading, refetch } = useMyResearchGroups(labId);

  function getProjectName(group: ResearchGroup) {
    if (group.projectTitle) {
      return group.projectCode ? `${group.projectCode} - ${group.projectTitle}` : group.projectTitle;
    }
    return 'Chưa có đề tài nghiên cứu';
  }

  function handleViewGroup(group: ResearchGroup) {
    if (!group.projectId) {
      toast.error('Nhóm nghiên cứu này chưa được liên kết với đề tài nào.');
      return;
    }
    navigate(`/app/research/projects/${group.projectId}/groups/${group.id}`);
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-medium text-slate-950">Nhóm nghiên cứu của tôi</h2>
        <p className="mt-2 text-sm text-slate-600">
          Danh sách các nhóm nghiên cứu bạn tham gia trong Phòng thí nghiệm này. Chọn một nhóm để vào không gian làm việc chi tiết.
        </p>
      </div>

      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-56 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      ) : isError ? (
        <ErrorState onRetry={() => refetch()}>
          Không thể tải danh sách nhóm nghiên cứu của bạn.
        </ErrorState>
      ) : groups.length === 0 ? (
        <EmptyState>
          Bạn chưa được phân vào nhóm nghiên cứu nào trong PTN này.
        </EmptyState>
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((group) => {
            const myRole = group.myRole ?? group.members?.find((member) => member.userId === currentUserId)?.role ?? null;
            const isLeader = myRole === 'LEADER';

            return (
              <article
                key={group.id}
                className="flex flex-col justify-between rounded-lg border border-slate-200 bg-white p-5 shadow-sm hover:shadow-md transition duration-200"
              >
                <div className="space-y-4">
                  <div className="flex items-start justify-between gap-3">
                    <h3 className="font-medium text-slate-950 text-base leading-snug line-clamp-1" title={group.name}>
                      {group.name}
                    </h3>
                    <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ring-1 ${getStatusClass(group.status)}`}>
                      {formatGroupStatus(group.status)}
                    </span>
                  </div>

                  <div className="space-y-1">
                    <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      Đề tài nghiên cứu
                    </span>
                    <p className="text-sm font-medium text-slate-800 line-clamp-2 leading-relaxed h-10">
                      {getProjectName(group)}
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-4 border-t border-slate-100 pt-4 text-xs">
                    <div>
                      <span className="block font-semibold text-slate-500">Vai trò của tôi</span>
                      <span className={`inline-flex mt-1 rounded-full px-2 py-0.5 font-medium ${
                        isLeader ? 'bg-blue-50 text-blue-700 ring-1 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-1 ring-slate-100'
                      }`}>
                        {isLeader ? 'Trưởng nhóm' : 'Thành viên'}
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">Trưởng nhóm</span>
                      <span className="block mt-1 text-slate-800 font-medium truncate" title={group.leaderName || 'Chưa phân công'}>
                        {group.leaderName || 'Chưa phân công'}
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">Thành viên</span>
                      <span className="block mt-1 text-slate-800 font-medium">
                        {group.memberCount ?? group.members?.length ?? 0} người
                      </span>
                    </div>
                    <div>
                      <span className="block font-semibold text-slate-500">Chủ đề tuyển</span>
                      <span className="block mt-1 text-slate-800 font-medium truncate" title={group.topicName || 'Chưa cập nhật'}>
                        {group.topicName || 'Chưa cập nhật'}
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
                    Xem chi tiết nhóm
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
