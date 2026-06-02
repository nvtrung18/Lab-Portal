import { useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER } from '../../../shared/constants/roles';
import type { LabResponse } from '../../lab/api';
import { isLabActive, isLabInactive } from '../../lab/utils/labStatus';
import type { AdminUser } from '../api';
import {
  useAdminLabs,
  useAdminUsers,
  useAssignLabManager,
  useCreateLabWithManager,
  useUpdateLabStatus,
  useAssignableManagers,
} from '../hooks';

function statusClassName(status: string) {
  if (status === 'AVAILABLE' || status === 'ACTIVE') {
    return 'bg-emerald-950 text-emerald-200 ring-emerald-800';
  }
  if (status === 'MAINTENANCE') {
    return 'bg-amber-950 text-amber-200 ring-amber-800';
  }
  if (status === 'INACTIVE' || status === 'ARCHIVED' || status === 'CLOSED') {
    return 'bg-slate-800 text-slate-300 ring-slate-700';
  }
  return 'bg-slate-800 text-slate-200 ring-slate-700';
}

function formatLabStatus(status: string) {
  const labels: Record<string, string> = {
    AVAILABLE: 'Đang hoạt động',
    ACTIVE: 'Đang hoạt động',
    MAINTENANCE: 'Đang bảo trì',
    INACTIVE: 'Ngừng hoạt động',
    ARCHIVED: 'Đã lưu trữ',
    CLOSED: 'Đã đóng',
  };
  return labels[status] ?? status;
}

const labStatusFilterOptions = ['', 'ACTIVE', 'INACTIVE'];

function getAssignedManagerIds(labs: LabResponse[]) {
  return new Set(
    labs
      .map((lab) => lab.manager?.id)
      .filter((managerId): managerId is number => Boolean(managerId)),
  );
}

function useAvailableManagers(currentLabManagerId?: number | null) {
  const { data: users = [] } = useAdminUsers();
  const { data: labs = [] } = useAdminLabs();
  const assignedManagerIds = useMemo(() => getAssignedManagerIds(labs), [labs]);

  return useMemo(() => {
    return users.filter((user) => {
      if (!user.roles.includes(LAB_MANAGER)) {
        return false;
      }

      return !assignedManagerIds.has(user.id) || user.id === currentLabManagerId;
    });
  }, [assignedManagerIds, currentLabManagerId, users]);
}

// --- Modals ---

interface AssignManagerModalProps {
  lab: LabResponse | null;
  onClose: () => void;
}

function AssignManagerModal({ lab, onClose }: AssignManagerModalProps) {
  const assignMutation = useAssignLabManager();
  const { data: managerOptions = [] } = useAssignableManagers();
  const [managerId, setManagerId] = useState('');

  if (!lab) {
    return null;
  }

  const handleSubmit = async () => {
    if (!managerId) {
      return;
    }

    await assignMutation.mutateAsync({ labId: lab.id, managerId: Number(managerId) });
    setManagerId('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-lg overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">Gán quản lý PTN</h3>
            <p className="mt-1 text-sm text-slate-400">{lab.labName}</p>
            <p className="mt-1 text-xs text-slate-500">
              Quản lý hiện tại: {lab.manager?.fullName || lab.manager?.email || 'Chưa gán'}
            </p>
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={assignMutation.isPending}
            onClick={onClose}
          >
            Đóng
          </Button>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-300" htmlFor="managerId">
            Người quản lý
          </label>
          {managerOptions.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
              Không có quản lý PTN nào khả dụng.
            </div>
          ) : (
            <select
              id="managerId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={managerId}
              disabled={assignMutation.isPending}
              onChange={(event) => setManagerId(event.target.value)}
            >
              <option value="" className="bg-slate-900 text-slate-100">Chọn người quản lý</option>
              {managerOptions.map((user) => (
                <option key={user.id} value={user.id} className="bg-slate-900 text-slate-100">
                  {user.fullName || user.email}
                </option>
              ))}
            </select>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button
            size="sm"
            variant="outline"
            disabled={assignMutation.isPending}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            size="sm"
            variant="primary"
            disabled={assignMutation.isPending || !managerId}
            loading={assignMutation.isPending}
            loadingText="Đang gán..."
            onClick={() => void handleSubmit()}
          >
            Xác nhận
          </Button>
        </div>
      </div>
    </div>
  );
}

interface AddLabModalProps {
  open: boolean;
  onClose: () => void;
}

function AddLabModal({ open, onClose }: AddLabModalProps) {
  const createMutation = useCreateLabWithManager();
  const managerOptions = useAvailableManagers();
  const [form, setForm] = useState({
    labName: '',
    department: '',
    description: '',
    capacity: '1',
    location: '',
    managerId: '',
  });

  if (!open) {
    return null;
  }

  const updateField = (field: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleSubmit = async () => {
    await createMutation.mutateAsync({
      labName: form.labName.trim(),
      department: form.department.trim() || null,
      description: form.description.trim() || null,
      capacity: Number(form.capacity),
      location: form.location.trim(),
      managerId: form.managerId ? Number(form.managerId) : null,
    });
    setForm({
      labName: '',
      department: '',
      description: '',
      capacity: '1',
      location: '',
      managerId: '',
    });
    onClose();
  };

  const canSubmit =
    form.labName.trim().length >= 3 &&
    form.location.trim().length >= 3 &&
    Number(form.capacity) >= 1;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-2xl overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">Thêm phòng thí nghiệm</h3>
            <p className="mt-1 text-sm text-slate-400">Tạo PTN mới và phân công quản lý nếu cần.</p>
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={createMutation.isPending}
            onClick={onClose}
          >
            Đóng
          </Button>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Tên PTN</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.labName}
              onChange={(event) => updateField('labName', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Khoa/Bộ môn</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.department}
              onChange={(event) => updateField('department', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Sức chứa</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              min={1}
              type="number"
              value={form.capacity}
              onChange={(event) => updateField('capacity', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Vị trí</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.location}
              onChange={(event) => updateField('location', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">Mô tả</span>
            <textarea
              className="min-h-24 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">Quản lý PTN (không bắt buộc)</span>
            {managerOptions.length === 0 ? (
              <div className="rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
                Không có quản lý PTN khả dụng.
              </div>
            ) : (
              <select
                className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
                value={form.managerId}
                onChange={(event) => updateField('managerId', event.target.value)}
              >
                <option value="" className="bg-slate-900 text-slate-100">Chưa phân công quản lý</option>
                {managerOptions.map((user) => (
                  <option key={user.id} value={user.id} className="bg-slate-900 text-slate-100">
                    {user.fullName || user.email}
                  </option>
                ))}
              </select>
            )}
          </label>
        </div>

        <div className="sticky -bottom-4 -mx-4 mt-6 flex flex-col-reverse justify-end gap-3 border-t border-slate-800 bg-slate-900 px-4 pb-4 pt-4 sm:-bottom-6 sm:-mx-6 sm:flex-row sm:px-6 sm:pb-6">
          <Button
            size="sm"
            variant="outline"
            disabled={createMutation.isPending}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            size="sm"
            variant="primary"
            disabled={createMutation.isPending || !canSubmit}
            loading={createMutation.isPending}
            loadingText="Đang tạo..."
            onClick={() => void handleSubmit()}
          >
            Tạo PTN
          </Button>
        </div>
      </div>
    </div>
  );
}

interface LabDetailModalProps {
  lab: LabResponse | null;
  onClose: () => void;
}

function LabDetailModal({ lab, onClose }: LabDetailModalProps) {
  if (!lab) {
    return null;
  }

  const details = [
    { label: 'Tên PTN', value: lab.labName },
    { label: 'Khoa/Bộ môn', value: lab.department || 'Chưa cập nhật' },
    { label: 'Vị trí', value: lab.location || 'Chưa cập nhật' },
    { label: 'Sức chứa', value: lab.capacity != null ? String(lab.capacity) : 'Chưa cập nhật' },
    { label: 'Quản lý', value: lab.manager?.fullName || lab.manager?.email || 'Chưa gán' },
    { label: 'Trạng thái', value: formatLabStatus(lab.status) },
    { label: 'Mô tả', value: lab.description || 'Chưa có mô tả' },
    { label: 'Ngày tạo', value: lab.createdAt ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(lab.createdAt)) : 'Chưa cập nhật' },
    { label: 'Cập nhật lần cuối', value: lab.updatedAt ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(lab.updatedAt)) : 'Chưa cập nhật' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-lg overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">Chi tiết phòng thí nghiệm</h3>
            <p className="mt-1 text-sm text-slate-400">{lab.labName}</p>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={onClose}
          >
            Đóng
          </Button>
        </div>

        <dl className="mt-6 divide-y divide-slate-800">
          {details.map((item) => (
            <div key={item.label} className="flex gap-4 py-3">
              <dt className="w-36 shrink-0 text-sm font-medium text-slate-400">{item.label}</dt>
              <dd className="text-sm text-slate-100">{item.value}</dd>
            </div>
          ))}
        </dl>

        {!lab.manager ? (
          <div className="mt-4 rounded-md border border-amber-800 bg-amber-950/50 px-3 py-2 text-xs font-medium text-amber-300">
            PTN này chưa được phân công quản lý.
          </div>
        ) : null}

        <div className="mt-6 flex justify-end">
          <Button
            size="sm"
            variant="outline"
            onClick={onClose}
          >
            Đóng
          </Button>
        </div>
      </div>
    </div>
  );
}

interface ConfirmStatusModalProps {
  lab: LabResponse | null;
  action: 'deactivate' | 'restore' | null;
  isLoading: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

function ConfirmStatusModal({ lab, action, isLoading, onClose, onConfirm }: ConfirmStatusModalProps) {
  if (!lab || !action) {
    return null;
  }

  const isDeactivate = action === 'deactivate';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="w-full max-w-md rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-white">
          {isDeactivate ? 'Xác nhận ngừng hoạt động' : 'Xác nhận khôi phục hoạt động'}
        </h3>
        <p className="mt-3 text-sm text-slate-300">
          {isDeactivate
            ? `Bạn có chắc muốn ngừng hoạt động PTN "${lab.labName}"? PTN sẽ không còn hiển thị cho sinh viên ứng tuyển và không thể tạo lịch sử dụng mới, nhưng dữ liệu lịch sử vẫn được giữ lại.`
            : `Bạn có chắc muốn khôi phục hoạt động PTN "${lab.labName}"?`}
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <Button
            size="sm"
            variant="outline"
            disabled={isLoading}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            size="sm"
            variant={isDeactivate ? 'danger' : 'primary'}
            disabled={isLoading}
            loading={isLoading}
            loadingText="Đang xử lý..."
            onClick={onConfirm}
          >
            Xác nhận
          </Button>
        </div>
      </div>
    </div>
  );
}

// --- Main Page ---

export function AdminLabsPage() {
  const { data: labs = [], isLoading, isError, refetch } = useAdminLabs();
  const { data: users = [] } = useAdminUsers();
  const updateStatusMutation = useUpdateLabStatus();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [managerFilter, setManagerFilter] = useState('');
  const [selectedLab, setSelectedLab] = useState<LabResponse | null>(null);
  const [detailLab, setDetailLab] = useState<LabResponse | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [confirmStatus, setConfirmStatus] = useState<{
    lab: LabResponse;
    action: 'deactivate' | 'restore';
  } | null>(null);

  const filteredLabs = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return labs.filter((lab) => {
      const matchesSearch = !keyword || lab.labName.toLowerCase().includes(keyword);
      const matchesStatus =
        !statusFilter ||
        (statusFilter === 'ACTIVE' && isLabActive(lab)) ||
        (statusFilter === 'INACTIVE' && isLabInactive(lab));
      const matchesManager =
        !managerFilter || (managerFilter === 'NO_MANAGER' && !lab.manager);
      return matchesSearch && matchesStatus && matchesManager;
    });
  }, [labs, search, statusFilter, managerFilter]);

  const labsWithoutManager = useMemo(() => {
    return labs.filter((lab) => !lab.manager && isLabActive(lab));
  }, [labs]);

  const unassignedManagers = useMemo(() => {
    const assignedManagerIds = getAssignedManagerIds(labs);
    return users.filter(
      (user) => user.roles.includes(LAB_MANAGER) && !assignedManagerIds.has(user.id),
    );
  }, [labs, users]);

  const handleDeactivate = (lab: LabResponse) => {
    setConfirmStatus({ lab, action: 'deactivate' });
  };

  const handleRestore = (lab: LabResponse) => {
    setConfirmStatus({ lab, action: 'restore' });
  };

  const executeStatusChange = async () => {
    if (!confirmStatus) return;
    try {
      await updateStatusMutation.mutateAsync({
        labId: confirmStatus.lab.id,
        status: confirmStatus.action === 'deactivate' ? 'INACTIVE' : 'AVAILABLE',
      });
    } finally {
      setConfirmStatus(null);
    }
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <LoadingState className="text-slate-300" />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  return (
    <section className="space-y-4">
      {/* Warning banners */}
      {labsWithoutManager.length > 0 ? (
        <div className="rounded-lg border border-amber-800 bg-amber-950/30 px-4 py-3 text-sm text-amber-300">
          <span className="font-semibold">Cảnh báo:</span>{' '}
          {labsWithoutManager.length === 1
            ? `PTN "${labsWithoutManager[0].labName}" đang hoạt động nhưng chưa có quản lý.`
            : `${labsWithoutManager.length} PTN đang hoạt động chưa có quản lý: ${labsWithoutManager.map((l) => `"${l.labName}"`).join(', ')}.`}
        </div>
      ) : null}

      {unassignedManagers.length > 0 ? (
        <div className="rounded-lg border border-blue-800 bg-blue-950/30 px-4 py-3 text-sm text-blue-300">
          <span className="font-semibold">Thông tin:</span>{' '}
          {unassignedManagers.length === 1
            ? `Quản lý "${unassignedManagers[0].fullName || unassignedManagers[0].email}" chưa được gán PTN.`
            : `${unassignedManagers.length} quản lý chưa được gán PTN: ${unassignedManagers.map((u) => `"${u.fullName || u.email}"`).join(', ')}.`}
        </div>
      ) : null}

      {/* Main content */}
      <div className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-white">Phòng thí nghiệm</h2>
            <p className="mt-1 text-sm text-slate-400">Quản lý toàn bộ PTN và phân công quản lý.</p>
          </div>
          <div className="flex gap-3">
            <input
              className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
              placeholder="Tìm theo tên PTN"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <select
              className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              {labStatusFilterOptions.map((status) => (
                <option key={status || 'all'} value={status} className="bg-slate-900 text-slate-100">
                  {status === 'ACTIVE'
                    ? 'Đang hoạt động'
                    : status === 'INACTIVE'
                      ? 'Ngừng hoạt động'
                      : 'Tất cả trạng thái'}
                </option>
              ))}
            </select>
            <select
              className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={managerFilter}
              onChange={(event) => setManagerFilter(event.target.value)}
            >
              <option value="" className="bg-slate-900 text-slate-100">Tất cả phân công</option>
              <option value="NO_MANAGER" className="bg-slate-900 text-slate-100">Chưa có quản lý</option>
            </select>
            <Button
              size="sm"
              variant="primary"
              onClick={() => setIsAddModalOpen(true)}
            >
              Thêm PTN
            </Button>
          </div>
        </div>

        {filteredLabs.length === 0 ? (
          <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300" />
        ) : (
          <div className="mt-6 max-w-full overscroll-x-contain overflow-x-auto">
            <table className="w-full min-w-[760px] divide-y divide-slate-800 text-sm">
              <thead>
                <tr className="text-left text-xs font-semibold uppercase text-slate-400">
                  <th className="px-3 py-3">Tên PTN</th>
                  <th className="px-3 py-3">Khoa/Bộ môn</th>
                  <th className="px-3 py-3">Quản lý PTN</th>
                  <th className="px-3 py-3">Sức chứa</th>
                  <th className="px-3 py-3">Vị trí</th>
                  <th className="px-3 py-3">Trạng thái</th>
                  <th className="px-3 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {filteredLabs.map((lab) => (
                  <tr key={lab.id} className="transition-colors hover:bg-slate-950/30">
                    <td className="px-3 py-4 font-medium text-slate-100">{lab.labName}</td>
                    <td className="px-3 py-4 text-slate-300">{lab.department || 'Chưa cập nhật'}</td>
                    <td className="px-3 py-4">
                      {lab.manager ? (
                        <span className="text-slate-300">{lab.manager.fullName || lab.manager.email}</span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-amber-950 px-2 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
                          Chưa có quản lý
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{lab.capacity ?? 'Chưa cập nhật'}</td>
                    <td className="px-3 py-4 text-slate-300">{lab.location || 'Chưa cập nhật'}</td>
                    <td className="px-3 py-4">
                      <span
                        className={[
                          'rounded-full px-2 py-1 text-xs font-semibold ring-1',
                          statusClassName(lab.status),
                        ].join(' ')}
                      >
                        {formatLabStatus(lab.status)}
                      </span>
                    </td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setDetailLab(lab)}
                        >
                          Xem chi tiết
                        </Button>
                        {!lab.manager && (
                          <Button
                            size="sm"
                            variant="primary"
                            onClick={() => setSelectedLab(lab)}
                          >
                            Gán quản lý
                          </Button>
                        )}
                        {isLabInactive(lab) ? (
                          <Button
                            size="sm"
                            variant="success"
                            disabled={updateStatusMutation.isPending}
                            onClick={() => handleRestore(lab)}
                          >
                            Khôi phục
                          </Button>
                        ) : null}
                        {isLabActive(lab) || lab.status === 'MAINTENANCE' ? (
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={updateStatusMutation.isPending}
                            onClick={() => handleDeactivate(lab)}
                          >
                            Ngừng hoạt động
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <AddLabModal open={isAddModalOpen} onClose={() => setIsAddModalOpen(false)} />
      <AssignManagerModal lab={selectedLab} onClose={() => setSelectedLab(null)} />
      <LabDetailModal lab={detailLab} onClose={() => setDetailLab(null)} />
      <ConfirmStatusModal
        lab={confirmStatus?.lab ?? null}
        action={confirmStatus?.action ?? null}
        isLoading={updateStatusMutation.isPending}
        onClose={() => setConfirmStatus(null)}
        onConfirm={() => void executeStatusChange()}
      />
    </section>
  );
}
