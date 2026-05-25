import type { ResearchEligibleStudent } from '../types';

interface GroupMemberSelectorProps {
  students: ResearchEligibleStudent[];
  leaderStudentId: number | null;
  memberIds: number[];
  isLoading?: boolean;
  onLeaderChange: (studentId: number) => void;
  onMembersChange: (studentIds: number[]) => void;
}

function getStudentLabel(student: ResearchEligibleStudent) {
  return student.fullName || student.email;
}

export function GroupMemberSelector({
  students,
  leaderStudentId,
  memberIds,
  isLoading,
  onLeaderChange,
  onMembersChange,
}: GroupMemberSelectorProps) {
  const selectedIds = new Set(memberIds);

  function toggleMember(studentId: number) {
    if (selectedIds.has(studentId)) {
      const nextIds = memberIds.filter((id) => id !== studentId);
      onMembersChange(nextIds);
      if (leaderStudentId === studentId && nextIds.length) {
        onLeaderChange(nextIds[0]);
      }
      return;
    }

    onMembersChange([...memberIds, studentId]);
  }

  if (isLoading) {
    return <p className="text-sm text-slate-600">Đang tải danh sách sinh viên...</p>;
  }

  if (!students.length) {
    return (
      <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-700">
        PTN chưa có sinh viên đang hoạt động nào để thêm vào nhóm.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <label className="block text-sm font-medium text-slate-700">
        Trưởng nhóm
        <select
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
          value={leaderStudentId ?? ''}
          onChange={(event) => {
            const nextLeaderId = Number(event.target.value);
            onLeaderChange(nextLeaderId);
            if (!selectedIds.has(nextLeaderId)) {
              onMembersChange([...memberIds, nextLeaderId]);
            }
          }}
        >
          <option value="" disabled>
            Chọn trưởng nhóm
          </option>
          {students.map((student) => (
            <option key={student.userId} value={student.userId}>
              {getStudentLabel(student)}
            </option>
          ))}
        </select>
      </label>

      <div>
        <p className="text-sm font-medium text-slate-700">Thành viên nhóm</p>
        <div className="mt-2 max-h-56 space-y-2 overflow-y-auto rounded-md border border-slate-200 p-3">
          {students.map((student) => (
            <label key={student.userId} className="flex items-start gap-3 rounded-md px-2 py-2 hover:bg-slate-50">
              <input
                className="mt-1"
                type="checkbox"
                checked={selectedIds.has(student.userId)}
                onChange={() => toggleMember(student.userId)}
              />
              <span className="min-w-0">
                <span className="block text-sm font-medium text-slate-900">{getStudentLabel(student)}</span>
                <span className="block truncate text-xs text-slate-500">{student.email}</span>
              </span>
            </label>
          ))}
        </div>
      </div>
    </div>
  );
}
