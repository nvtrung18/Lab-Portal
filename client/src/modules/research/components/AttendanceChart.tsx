import { memo, useMemo } from 'react';

import type { DashboardStats } from '../types';

type AttendanceStudent = DashboardStats['attendance']['byStudent'][number];

interface AttendanceChartProps {
  byStudent: AttendanceStudent[];
}

const MAX_VISIBLE_STUDENTS = 10;

export const AttendanceChart = memo(function AttendanceChart({ byStudent }: AttendanceChartProps) {
  const visibleStudents = useMemo(() => byStudent.slice(0, MAX_VISIBLE_STUDENTS), [byStudent]);
  const hiddenCount = Math.max(0, byStudent.length - MAX_VISIBLE_STUDENTS);

  if (!byStudent.length) {
    return <p className="mt-4 text-sm text-slate-600">Chưa có dữ liệu điểm danh.</p>;
  }

  return (
    <div className="mt-4 overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-3 py-3">Sinh viên</th>
            <th className="px-3 py-3">Tỷ lệ điểm danh</th>
            <th className="px-3 py-3">Số buổi có mặt</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {visibleStudents.map((student) => (
            <tr key={student.studentId || student.studentName}>
              <td className="px-3 py-3 font-medium text-slate-800">{student.studentName || 'Chưa cập nhật'}</td>
              <td className="px-3 py-3 text-slate-600">
                <div className="flex min-w-40 items-center gap-3">
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-emerald-500"
                      style={{ width: `${clampPercent(student.attendanceRate)}%` }}
                    />
                  </div>
                  <span className="w-12 text-right">{formatPercent(student.attendanceRate)}</span>
                </div>
              </td>
              <td className="px-3 py-3 text-slate-600">
                {safeNumber(student.attendanceCount)}/{safeNumber(student.expectedAttendanceCount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {hiddenCount ? (
        <p className="mt-3 text-xs text-slate-500">Còn {hiddenCount} sinh viên khác.</p>
      ) : null}
    </div>
  );
});

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, safeNumber(value)));
}

function formatPercent(value: number) {
  const safeValue = safeNumber(value);
  return `${Number.isInteger(safeValue) ? safeValue : safeValue.toFixed(1)}%`;
}

function safeNumber(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}
