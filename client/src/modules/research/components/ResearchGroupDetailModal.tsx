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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <section className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <h3 className="text-lg font-semibold text-slate-950">Chi tiết nhóm nghiên cứu</h3>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        {isLoading ? (
          <p className="mt-5 text-sm text-slate-600">Đang tải chi tiết nhóm nghiên cứu...</p>
        ) : isError || !group ? (
          <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            Không thể tải chi tiết nhóm nghiên cứu.
            <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
              Tải lại
            </button>
          </div>
        ) : (
          <div className="mt-5 space-y-6">
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
                <div className="mt-3 overflow-x-auto rounded-md border border-slate-200">
                  <table className="min-w-full divide-y divide-slate-200 text-sm">
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
                </div>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
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
