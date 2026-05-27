import { ErrorState, LoadingState, Modal, ResponsiveTable } from '../../../shared/components';
import { useResearchGroup } from '../hooks';
import { formatDate, formatGroupRole, formatGroupStatus, getStatusClass } from '../utils';

interface ResearchGroupDetailModalProps {
  groupId: number | null;
  onClose: () => void;
}

export function ResearchGroupDetailModal({ groupId, onClose }: ResearchGroupDetailModalProps) {
  const { data: group, isError, isLoading, refetch } = useResearchGroup(groupId);

  if (!groupId) {
    return null;
  }

  const projectName = group?.projectTitle
    ? group.projectCode
      ? `${group.projectCode} - ${group.projectTitle}`
      : group.projectTitle
    : 'Chưa cập nhật';

  return (
    <Modal onClose={onClose} size="xl" title="Chi tiết nhóm nghiên cứu">
        {isLoading ? (
          <LoadingState>Đang tải chi tiết nhóm nghiên cứu...</LoadingState>
        ) : isError || !group ? (
          <ErrorState onRetry={() => refetch()}>
            Không thể tải chi tiết nhóm nghiên cứu.
          </ErrorState>
        ) : (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h4 className="text-base font-semibold text-slate-950">{group.name}</h4>
                <p className="mt-1 text-sm text-slate-600">Đề tài nghiên cứu: {projectName}</p>
              </div>
              <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                {formatGroupStatus(group.status)}
              </span>
            </div>

            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Detail label="Mục tiêu nhóm" value={group.objective ?? 'Chưa cập nhật'} />
              <Detail label="Kế hoạch thực hiện" value={group.plan ?? 'Chưa cập nhật'} />
              <Detail label="Trưởng nhóm" value={group.leaderName ?? 'Chưa cập nhật'} />
              <Detail label="Ngày tạo" value={formatDate(group.createdAt)} />
              <Detail label="Người tạo" value={group.managerName ?? group.createdByName ?? 'Chưa cập nhật'} />
              <Detail label="Trạng thái" value={formatGroupStatus(group.status)} />
            </dl>

            <div>
              <h4 className="text-sm font-semibold text-slate-950">Thành viên nhóm</h4>
              {!group.members?.length ? (
                <p className="mt-3 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                  Chưa có thành viên nhóm.
                </p>
              ) : (
                <ResponsiveTable className="mt-3">
                  <table className="w-full min-w-[520px] divide-y divide-slate-200 text-sm">
                    <thead className="bg-slate-50">
                      <tr className="text-left font-semibold text-slate-700">
                        <th className="px-3 py-3">Thành viên</th>
                        <th className="px-3 py-3">Vai trò</th>
                        <th className="px-3 py-3">Ngày tham gia</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {group.members.map((member) => (
                        <tr key={member.id}>
                          <td className="px-3 py-3">
                            <p className="font-medium text-slate-950">{member.fullName ?? member.email ?? `Sinh viên #${member.userId}`}</p>
                            <p className="mt-1 text-xs text-slate-500">{member.email}</p>
                          </td>
                          <td className="px-3 py-3 text-slate-600">{formatGroupRole(member.role)}</td>
                          <td className="px-3 py-3 text-slate-600">{formatDate(member.joinedAt)}</td>
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
