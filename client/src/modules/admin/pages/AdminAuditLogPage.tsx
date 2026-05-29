import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useAdminAuditLogs } from '../hooks';
import type { AuditLogFilters } from '../api';

// Action label mapping
const actionLabels: Record<string, string> = {
  CHANGE_USER_ROLE: 'Đổi vai trò người dùng',
  BAN_USER: 'Khóa tài khoản',
  UNBAN_USER: 'Mở khóa tài khoản',
  CREATE_LAB: 'Tạo PTN',
  UPDATE_LAB: 'Cập nhật PTN',
  DEACTIVATE_LAB: 'Tạm ngừng PTN',
  ASSIGN_MANAGER: 'Gán quản lý PTN',
  UPDATE_SYSTEM_CONFIG: 'Cập nhật cấu hình hệ thống',
  CREATE_SLOT: 'Tạo ca sử dụng PTN',
  CANCEL_SLOT: 'Hủy ca sử dụng PTN',
  REVIEW_APPLICATION: 'Duyệt đơn đăng ký',
  STUDENT_UPLOAD_REPORT: 'Sinh viên nộp báo cáo',
  LEADER_REVIEW_REPORT: 'Trưởng nhóm duyệt báo cáo',
  MANAGER_REVIEW_REPORT: 'Quản lý duyệt báo cáo',
  STUDENT_UPLOAD_PRODUCT: 'Sinh viên nộp sản phẩm',
  CREATE_RESEARCH_PROJECT: 'Tạo dự án nghiên cứu',
  UPDATE_RESEARCH_PROJECT: 'Cập nhật dự án nghiên cứu',
  CREATE_RESEARCH_GROUP: 'Tạo nhóm nghiên cứu',
  UPDATE_RESEARCH_GROUP: 'Cập nhật nhóm nghiên cứu',
  EVALUATE_STUDENT: 'Đánh giá sinh viên',
};

// Module label mapping
const moduleLabels: Record<string, string> = {
  USER: 'Người dùng',
  LAB: 'Phòng thí nghiệm',
  SYSTEM_CONFIG: 'Cấu hình hệ thống',
  BOOKING: 'Lịch sử dụng PTN',
  CLEANING: 'Trực nhật',
  PENALTY: 'Xử phạt',
  RESEARCH: 'Nghiên cứu khoa học',
  REPORT: 'Báo cáo',
  PRODUCT: 'Sản phẩm',
  EVALUATION: 'Đánh giá',
};

// Target translations
const formatTarget = (targetType?: string, targetId?: number | null) => {
  if (!targetType) return '-';
  const typeMap: Record<string, string> = {
    USER: 'Người dùng',
    LAB: 'Phòng thí nghiệm',
    SYSTEM_CONFIG: 'Cấu hình hệ thống',
    SLOT: 'Ca sử dụng',
    BOOKING: 'Lịch sử dụng',
    CLEANING_TASK: 'Nhiệm vụ trực nhật',
    PENALTY: 'Xử phạt',
    RESEARCH_PROJECT: 'Dự án nghiên cứu',
    RESEARCH_GROUP: 'Nhóm nghiên cứu',
    REPORT: 'Báo cáo',
    PRODUCT: 'Sản phẩm',
    EVALUATION: 'Đánh giá',
  };
  const label = typeMap[targetType.toUpperCase()] || targetType;
  return targetId ? `${label} (ID: ${targetId})` : label;
};

// Role formatting
const formatRole = (role?: string) => {
  if (!role) return '-';
  if (role === 'ADMIN') return 'Quản trị viên';
  if (role === 'LAB_MANAGER' || role === 'MANAGER') return 'Quản lý PTN';
  if (role === 'STUDENT' || role === 'USER') return 'Sinh viên';
  return role;
};

// Format date helper
function formatDateTime(value?: string) {
  if (!value) return 'Chưa cập nhật';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value));
  } catch (error) {
    return value;
  }
}

// Module badge styling class helper
const getModuleBadgeColor = (module: string) => {
  switch (module.toUpperCase()) {
    case 'USER':
      return 'bg-blue-500/10 text-blue-400 border border-blue-500/20';
    case 'LAB':
      return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
    case 'SYSTEM_CONFIG':
      return 'bg-purple-500/10 text-purple-400 border border-purple-500/20';
    case 'BOOKING':
      return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
    case 'CLEANING':
      return 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20';
    case 'PENALTY':
      return 'bg-rose-500/10 text-rose-400 border border-rose-500/20';
    case 'RESEARCH':
      return 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20';
    case 'REPORT':
      return 'bg-teal-500/10 text-teal-400 border border-teal-500/20';
    case 'PRODUCT':
      return 'bg-sky-500/10 text-sky-400 border border-sky-500/20';
    case 'EVALUATION':
      return 'bg-pink-500/10 text-pink-400 border border-pink-500/20';
    default:
      return 'bg-slate-500/10 text-slate-400 border border-slate-500/20';
  }
};

export function AdminAuditLogPage() {
  const [page, setPage] = useState(0);
  const [moduleInput, setModuleInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [fromDateInput, setFromDateInput] = useState('');
  const [toDateInput, setToDateInput] = useState('');
  const [keywordInput, setKeywordInput] = useState('');

  const [appliedFilters, setAppliedFilters] = useState<AuditLogFilters>({});

  const { data, isLoading, isError, refetch } = useAdminAuditLogs(page, appliedFilters);

  const handleSearch = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setPage(0);

    let formattedFromDate: string | undefined = undefined;
    let formattedToDate: string | undefined = undefined;

    if (fromDateInput) {
      formattedFromDate = new Date(`${fromDateInput}T00:00:00`).toISOString();
    }
    if (toDateInput) {
      formattedToDate = new Date(`${toDateInput}T23:59:59`).toISOString();
    }

    setAppliedFilters({
      module: moduleInput || undefined,
      action: actionInput || undefined,
      fromDate: formattedFromDate,
      toDate: formattedToDate,
      keyword: keywordInput.trim() || undefined,
    });
  };

  const handleReset = () => {
    setPage(0);
    setModuleInput('');
    setActionInput('');
    setFromDateInput('');
    setToDateInput('');
    setKeywordInput('');
    setAppliedFilters({});
  };

  const handlePrevPage = () => {
    if (page > 0) {
      setPage(page - 1);
    }
  };

  const handleNextPage = () => {
    if (data && page < data.totalPages - 1) {
      setPage(page + 1);
    }
  };

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-white">Nhật ký vận hành</h2>
        <p className="text-sm text-slate-400">Theo dõi các thao tác quan trọng trong hệ thống.</p>
      </div>

      {/* Filter controls */}
      <form
        onSubmit={handleSearch}
        className="grid gap-4 rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-sm sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="filter-keyword" className="text-xs font-medium text-slate-400">Từ khóa</label>
          <input
            id="filter-keyword"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
            placeholder="Tìm người dùng, đối tượng..."
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-module" className="text-xs font-medium text-slate-400">Module</label>
          <select
            id="filter-module"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
            value={moduleInput}
            onChange={(e) => setModuleInput(e.target.value)}
          >
            <option value="">Tất cả module</option>
            {Object.entries(moduleLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-action" className="text-xs font-medium text-slate-400">Hành động</label>
          <select
            id="filter-action"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
            value={actionInput}
            onChange={(e) => setActionInput(e.target.value)}
          >
            <option value="">Tất cả hành động</option>
            {Object.entries(actionLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-from-date" className="text-xs font-medium text-slate-400">Từ ngày</label>
          <input
            id="filter-from-date"
            type="date"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white [color-scheme:dark]"
            value={fromDateInput}
            onChange={(e) => setFromDateInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-to-date" className="text-xs font-medium text-slate-400">Đến ngày</label>
          <input
            id="filter-to-date"
            type="date"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white [color-scheme:dark]"
            value={toDateInput}
            onChange={(e) => setToDateInput(e.target.value)}
          />
        </div>

        <div className="sm:col-span-2 md:col-span-3 lg:col-span-5 flex justify-end gap-2 pt-2 border-t border-slate-800">
          <Button
            type="button"
            className="border-slate-700 bg-transparent text-slate-200 hover:bg-slate-800"
            size="sm"
            variant="outline"
            onClick={handleReset}
          >
            Đặt lại
          </Button>
          <Button
            type="submit"
            className="bg-white text-slate-950 hover:bg-slate-200"
            size="sm"
          >
            Tìm kiếm
          </Button>
        </div>
      </form>

      {/* Content State */}
      {isLoading ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-12 shadow-sm text-center">
          <LoadingState className="text-slate-300">Đang tải nhật ký vận hành...</LoadingState>
        </div>
      ) : isError ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
          <ErrorState className="border-red-950 bg-red-950/20 text-red-400" onRetry={() => refetch()}>
            Không thể tải nhật ký vận hành.
          </ErrorState>
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-12 shadow-sm text-center">
          <EmptyState className="border-slate-800 bg-slate-950 text-slate-400">
            Chưa có nhật ký vận hành.
          </EmptyState>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-800 bg-slate-900 shadow-sm overflow-hidden">
          {/* Responsive Table */}
          <div className="max-w-full overscroll-x-contain overflow-x-auto">
            <table className="w-full min-w-[950px] divide-y divide-slate-800 text-sm text-left">
              <thead>
                <tr className="text-xs font-semibold uppercase text-slate-400 bg-slate-950/50">
                  <th className="px-4 py-3">Thời gian</th>
                  <th className="px-4 py-3">Người thực hiện</th>
                  <th className="px-4 py-3">Vai trò</th>
                  <th className="px-4 py-3">Module</th>
                  <th className="px-4 py-3">Hành động</th>
                  <th className="px-4 py-3">Đối tượng</th>
                  <th className="px-4 py-3">Mô tả</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 bg-transparent">
                {data.items.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-950/20 transition-colors">
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatDateTime(log.createdAt)}
                    </td>
                    <td className="px-4 py-4.5 text-slate-100 font-medium whitespace-nowrap">
                      {log.actorName || `ID: ${log.actorId}`}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatRole(log.actorRole)}
                    </td>
                    <td className="px-4 py-4.5 whitespace-nowrap">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${getModuleBadgeColor(log.module)}`}>
                        {moduleLabels[log.module] || log.module}
                      </span>
                    </td>
                    <td className="px-4 py-4.5 text-slate-100 font-medium whitespace-nowrap">
                      {actionLabels[log.action] || log.action}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatTarget(log.targetType, log.targetId)}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 max-w-xs truncate" title={log.description}>
                      {log.description || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-slate-800 bg-slate-950/25 px-4 py-3.5 text-sm text-slate-400">
            <div>
              Hiển thị từ <span className="font-semibold text-slate-200">{page * data.size + 1}</span> đến{' '}
              <span className="font-semibold text-slate-200">
                {Math.min((page + 1) * data.size, data.totalElements)}
              </span>{' '}
              trong tổng số <span className="font-semibold text-slate-200">{data.totalElements}</span> nhật ký
            </div>

            <div className="flex items-center gap-2">
              <Button
                className="border-slate-700 bg-transparent text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:hover:bg-transparent"
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={handlePrevPage}
              >
                Trước
              </Button>
              <div className="flex items-center gap-1.5">
                {Array.from({ length: data.totalPages }).map((_, index) => {
                  // Only display max 5 page buttons around current page
                  if (
                    index === 0 ||
                    index === data.totalPages - 1 ||
                    Math.abs(index - page) <= 1
                  ) {
                    return (
                      <button
                        key={index}
                        className={`h-8 w-8 rounded-md text-xs font-semibold transition ${
                          page === index
                            ? 'bg-white text-slate-950'
                            : 'text-slate-400 hover:bg-slate-800 hover:text-white'
                        }`}
                        onClick={() => setPage(index)}
                      >
                        {index + 1}
                      </button>
                    );
                  }
                  if (index === 1 || index === data.totalPages - 2) {
                    return <span key={index} className="text-slate-600 px-0.5">...</span>;
                  }
                  return null;
                })}
              </div>
              <Button
                className="border-slate-700 bg-transparent text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:hover:bg-transparent"
                size="sm"
                variant="outline"
                disabled={page >= data.totalPages - 1}
                onClick={handleNextPage}
              >
                Sau
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
