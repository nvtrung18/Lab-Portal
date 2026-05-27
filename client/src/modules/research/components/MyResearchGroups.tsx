import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { Button, EmptyState, ErrorState, LoadingState, ResponsiveTable } from '../../../shared/components';
import { useMyResearchGroups } from '../hooks';
import type { ResearchGroup, ResearchGroupMember } from '../types';
import { formatDate, formatGroupRole, formatGroupStatus, getStatusClass } from '../utils';
import { MilestoneList } from './MilestoneList';
import { MyResearchTasks } from './MyResearchTasks';
import { GroupReportsTab } from './GroupReportsTab';

interface MyResearchGroupsProps {
  labId: number;
  currentUserId?: number | null;
}

function getMemberName(member: ResearchGroupMember) {
  return member.fullName || member.email || `Người dùng #${member.userId}`;
}

function getProjectName(group: ResearchGroup) {
  if (group.projectTitle) {
    return group.projectCode ? `${group.projectCode} - ${group.projectTitle}` : group.projectTitle;
  }
  return 'Chưa cập nhật';
}

export function MyResearchGroups({ labId, currentUserId }: MyResearchGroupsProps) {
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const { data: groups = [], isError, isLoading, refetch } = useMyResearchGroups(labId);

  useEffect(() => {
    setSelectedGroupId(null);
  }, [labId]);

  const selectedGroup = useMemo(
    () => groups.find((group) => group.id === selectedGroupId) ?? groups[0] ?? null,
    [groups, selectedGroupId],
  );

  const myRoleByGroupId = useMemo(() => {
    return new Map(
      groups.map((group) => [
        group.id,
        group.myRole ?? group.members?.find((member) => member.userId === currentUserId)?.role ?? null,
      ]),
    );
  }, [currentUserId, groups]);
  const selectedGroupRole = selectedGroup ? myRoleByGroupId.get(selectedGroup.id) : null;
  const isLeader = selectedGroupRole === 'LEADER';
  const currentMembership = selectedGroup?.members?.find((member) => member.userId === currentUserId) ?? null;

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold text-slate-950">Nhóm của tôi</h2>
        <p className="mt-2 text-sm text-slate-600">Các nhóm nghiên cứu mà bạn đang tham gia.</p>
      </div>

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        {isLoading ? (
          <LoadingState>Đang tải danh sách nhóm nghiên cứu...</LoadingState>
        ) : isError ? (
          <ErrorState onRetry={() => refetch()}>
            Không thể tải danh sách nhóm nghiên cứu.
          </ErrorState>
        ) : !groups.length ? (
          <EmptyState>
            Bạn chưa được phân vào nhóm nghiên cứu nào trong PTN này.
          </EmptyState>
        ) : (
          <ResponsiveTable>
            <table className="w-full min-w-[760px] divide-y divide-slate-200 text-sm">
              <thead>
                <tr className="text-left text-xs font-semibold uppercase text-slate-500">
                  <th className="px-3 py-3">Tên nhóm</th>
                  <th className="px-3 py-3">Đề tài nghiên cứu</th>
                  <th className="px-3 py-3">Chủ đề nghiên cứu</th>
                  <th className="px-3 py-3">Vai trò của tôi</th>
                  <th className="px-3 py-3">Trưởng nhóm</th>
                  <th className="px-3 py-3">Số thành viên</th>
                  <th className="px-3 py-3">Trạng thái</th>
                  <th className="px-3 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {groups.map((group) => (
                  <tr key={group.id} className="align-top">
                    <td className="px-3 py-3 font-semibold text-slate-950">{group.name}</td>
                    <td className="px-3 py-3 text-slate-600">{getProjectName(group)}</td>
                    <td className="px-3 py-3 text-slate-600">{group.topicName ?? 'Chưa cập nhật'}</td>
                    <td className="px-3 py-3 text-slate-600">{formatGroupRole(myRoleByGroupId.get(group.id))}</td>
                    <td className="px-3 py-3 text-slate-600">{group.leaderName ?? 'Chưa cập nhật'}</td>
                    <td className="px-3 py-3 text-slate-600">{group.memberCount ?? group.members?.length ?? 0}</td>
                    <td className="px-3 py-3">
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                        {formatGroupStatus(group.status)}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right">
                      <Button onClick={() => setSelectedGroupId(group.id)} size="sm" variant="outline">
                        Xem chi tiết
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ResponsiveTable>
        )}
      </section>

      {selectedGroup ? (
        <section className="space-y-6">
          <div className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
            <div className="space-y-6">
              <DetailPanel title="Thông tin nhóm">
                <DescriptionRow label="Tên nhóm" value={selectedGroup.name} />
                <DescriptionRow label="Mục tiêu" value={selectedGroup.objective ?? 'Chưa cập nhật'} />
                <DescriptionRow label="Kế hoạch" value={selectedGroup.plan ?? 'Chưa cập nhật'} />
                <DescriptionRow label="Trạng thái" value={formatGroupStatus(selectedGroup.status)} />
                <DescriptionRow label="Ngày tạo" value={formatDate(selectedGroup.createdAt)} />
              </DetailPanel>

              <DetailPanel title="Thông tin đề tài">
                <DescriptionRow label="Đề tài nghiên cứu" value={getProjectName(selectedGroup)} />
                <DescriptionRow label="Chủ đề nghiên cứu" value={selectedGroup.topicName ?? 'Chưa cập nhật'} />
                <DescriptionRow label="Quản lý PTN / giảng viên hướng dẫn" value={selectedGroup.managerName ?? 'Chưa cập nhật'} />
              </DetailPanel>
            </div>

            <DetailPanel title={isLeader ? 'Thành viên nhóm' : 'Vai trò của tôi'}>
              <div className="space-y-3">
                {(isLeader ? selectedGroup.members ?? [] : currentMembership ? [currentMembership] : []).map((member) => (
                  <div key={member.id} className="flex items-start justify-between gap-3 rounded-md border border-slate-200 p-3">
                    <div>
                      <p className="font-semibold text-slate-950">{getMemberName(member)}</p>
                      <p className="mt-1 text-xs text-slate-500">{member.email ?? `Người dùng #${member.userId}`}</p>
                    </div>
                    <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                      {formatGroupRole(member.role)}
                    </span>
                  </div>
                ))}
              </div>
            </DetailPanel>
          </div>

          {selectedGroup.projectId ? (
            <>
              {!isLeader && currentUserId != null ? (
                <MyResearchTasks
                  currentUserId={currentUserId}
                  groupId={selectedGroup.id}
                  projectId={selectedGroup.projectId}
                />
              ) : null}
              {isLeader ? (
                <GroupReportsTab currentUserId={currentUserId} groupId={selectedGroup.id} />
              ) : null}
              <MilestoneList
                key={`${labId}-${selectedGroup.projectId}`}
                projectId={selectedGroup.projectId}
                groupId={selectedGroup.id}
                canCreate={false}
                showTaskBoard
                taskBoardRole={currentUserId != null ? (isLeader ? 'GROUP_LEADER' : 'STUDENT_MEMBER') : undefined}
                taskBoardCurrentUserId={currentUserId}
                title={isLeader ? 'Bảng tiến độ nhóm' : 'Mốc của tôi'}
                description={isLeader
                  ? 'Theo dõi toàn bộ mốc và nhiệm vụ của nhóm nghiên cứu.'
                  : 'Chỉ hiển thị các mốc có nhiệm vụ được giao cho bạn.'}
                emptyMessage={isLeader
                  ? 'Đề tài này chưa có mốc nghiên cứu nào.'
                  : 'Bạn chưa có mốc hoặc nhiệm vụ nào được giao trong đề tài này.'}
              />
            </>
          ) : null}
        </section>
      ) : null}
    </section>
  );
}

function DetailPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-base font-semibold text-slate-950">{title}</h3>
      <div className="mt-4 space-y-3 text-sm">{children}</div>
    </section>
  );
}

function DescriptionRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 text-slate-600">{value}</dd>
    </div>
  );
}
