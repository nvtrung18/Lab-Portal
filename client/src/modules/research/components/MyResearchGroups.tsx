import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { useMyResearchGroups } from '../hooks';
import type { ResearchGroup, ResearchGroupMember } from '../types';
import { formatDate, formatGroupStatus, getStatusClass } from '../utils';

interface MyResearchGroupsProps {
  currentUserId?: number | null;
}

function formatRole(role?: string | null) {
  if (role === 'LEADER') {
    return 'Trưởng nhóm';
  }
  if (role === 'MEMBER') {
    return 'Thành viên';
  }
  return 'Chưa cập nhật';
}

function getMemberName(member: ResearchGroupMember) {
  return member.fullName || member.email || `User #${member.userId}`;
}

function getProjectName(group: ResearchGroup) {
  if (group.projectTitle) {
    return group.projectCode ? `${group.projectCode} - ${group.projectTitle}` : group.projectTitle;
  }
  return 'Chưa cập nhật';
}

export function MyResearchGroups({ currentUserId }: MyResearchGroupsProps) {
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const { data: groups = [], isError, isLoading, refetch } = useMyResearchGroups();

  const selectedGroup = useMemo(
    () => groups.find((group) => group.id === selectedGroupId) ?? groups[0] ?? null,
    [groups, selectedGroupId],
  );

  const myRoleByGroupId = useMemo(() => {
    return new Map(
      groups.map((group) => [
        group.id,
        group.members?.find((member) => member.userId === currentUserId)?.role ?? null,
      ]),
    );
  }, [currentUserId, groups]);

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold text-slate-950">Nhóm của tôi</h2>
        <p className="mt-2 text-sm text-slate-600">Các nhóm nghiên cứu mà bạn đang tham gia.</p>
      </div>

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        {isLoading ? (
          <p className="text-sm text-slate-600">Đang tải danh sách nhóm nghiên cứu...</p>
        ) : isError ? (
          <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            Không thể tải danh sách nhóm nghiên cứu.
            <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
              Tải lại
            </button>
          </div>
        ) : !groups.length ? (
          <div className="rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
            Bạn chưa được phân vào nhóm nghiên cứu nào.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
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
                    <td className="px-3 py-3 text-slate-600">{formatRole(myRoleByGroupId.get(group.id))}</td>
                    <td className="px-3 py-3 text-slate-600">{group.leaderName ?? 'Chưa cập nhật'}</td>
                    <td className="px-3 py-3 text-slate-600">{group.memberCount ?? group.members?.length ?? 0}</td>
                    <td className="px-3 py-3">
                      <span className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                        {formatGroupStatus(group.status)}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right">
                      <button
                        className="rounded-md border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                        type="button"
                        onClick={() => setSelectedGroupId(group.id)}
                      >
                        Xem chi tiết
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {selectedGroup ? (
        <section className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
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

          <DetailPanel title="Thành viên nhóm">
            <div className="space-y-3">
              {(selectedGroup.members ?? []).map((member) => (
                <div key={member.id} className="flex items-start justify-between gap-3 rounded-md border border-slate-200 p-3">
                  <div>
                    <p className="font-semibold text-slate-950">{getMemberName(member)}</p>
                    <p className="mt-1 text-xs text-slate-500">{member.email ?? `User #${member.userId}`}</p>
                  </div>
                  <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                    {formatRole(member.role)}
                  </span>
                </div>
              ))}
            </div>
          </DetailPanel>
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
