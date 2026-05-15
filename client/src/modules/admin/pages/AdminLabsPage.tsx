import { useMemo, useState } from 'react';

import { LAB_MANAGER } from '../../../shared/constants/roles';
import type { LabResponse } from '../../lab/api';
import { isLabActive, isLabInactive } from '../../lab/utils/labStatus';
import { useAdminLabs, useAdminUsers, useAssignLabManager, useCreateLabWithManager, useUpdateLabStatus } from '../hooks';

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

interface AssignManagerModalProps {
  lab: LabResponse | null;
  onClose: () => void;
}

function AssignManagerModal({ lab, onClose }: AssignManagerModalProps) {
  const assignMutation = useAssignLabManager();
  const managerOptions = useAvailableManagers(lab?.manager?.id);
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
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 px-4">
      <div className="w-full max-w-lg rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">Assign Manager</h3>
            <p className="mt-1 text-sm text-slate-400">{lab.labName}</p>
            <p className="mt-1 text-xs text-slate-500">
              Manager hiện tại: {lab.manager?.fullName || lab.manager?.email || 'Chưa gán'}
            </p>
          </div>
          <button
            type="button"
            className="rounded-md px-2 py-1 text-sm text-slate-400 hover:bg-slate-800"
            disabled={assignMutation.isPending}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-300" htmlFor="managerId">
            LAB_MANAGER khả dụng
          </label>
          {managerOptions.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
              Không có manager khả dụng.
            </div>
          ) : (
            <select
              id="managerId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={managerId}
              disabled={assignMutation.isPending}
              onChange={(event) => setManagerId(event.target.value)}
            >
              <option value="">Chọn manager</option>
              {managerOptions.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.fullName || user.email}
                </option>
              ))}
            </select>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            className="rounded-md border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 transition hover:bg-slate-800"
            disabled={assignMutation.isPending}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="button"
            className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:bg-slate-600"
            disabled={assignMutation.isPending || !managerId}
            onClick={() => void handleSubmit()}
          >
            {assignMutation.isPending ? 'Đang gán...' : 'Confirm'}
          </button>
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
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 px-4">
      <div className="w-full max-w-2xl rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">Add Lab</h3>
            <p className="mt-1 text-sm text-slate-400">Tạo lab mới và gán manager nếu cần.</p>
          </div>
          <button
            type="button"
            className="rounded-md px-2 py-1 text-sm text-slate-400 hover:bg-slate-800"
            disabled={createMutation.isPending}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Lab name</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.labName}
              onChange={(event) => updateField('labName', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Department</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.department}
              onChange={(event) => updateField('department', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Capacity</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              min={1}
              type="number"
              value={form.capacity}
              onChange={(event) => updateField('capacity', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">Location</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.location}
              onChange={(event) => updateField('location', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">Description</span>
            <textarea
              className="min-h-24 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">Manager (optional)</span>
            {managerOptions.length === 0 ? (
              <div className="rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
                Không có manager khả dụng.
              </div>
            ) : (
              <select
                className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
                value={form.managerId}
                onChange={(event) => updateField('managerId', event.target.value)}
              >
                <option value="">Không gán manager</option>
                {managerOptions.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.fullName || user.email}
                  </option>
                ))}
              </select>
            )}
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            className="rounded-md border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 transition hover:bg-slate-800"
            disabled={createMutation.isPending}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            type="button"
            className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:bg-slate-600"
            disabled={createMutation.isPending || !canSubmit}
            onClick={() => void handleSubmit()}
          >
            {createMutation.isPending ? 'Đang tạo...' : 'Create Lab'}
          </button>
        </div>
      </div>
    </div>
  );
}

export function AdminLabsPage() {
  const { data: labs = [], isLoading, isError } = useAdminLabs();
  const updateStatusMutation = useUpdateLabStatus();
  const [search, setSearch] = useState('');
  const [selectedLab, setSelectedLab] = useState<LabResponse | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  const filteredLabs = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return labs.filter((lab) => !keyword || lab.labName.toLowerCase().includes(keyword));
  }, [labs, search]);

  const handleDeactivate = (lab: LabResponse) => {
    const confirmed = window.confirm(
      'Bạn có chắc muốn ngừng hoạt động lab này không? Lab sẽ không còn hiển thị cho student apply và không thể tạo booking mới, nhưng dữ liệu lịch sử vẫn được giữ lại.',
    );

    if (!confirmed) {
      return;
    }

    void updateStatusMutation.mutateAsync({ labId: lab.id, status: 'INACTIVE' });
  };

  const handleRestore = (lab: LabResponse) => {
    const confirmed = window.confirm('Bạn có chắc muốn khôi phục hoạt động lab này không?');

    if (!confirmed) {
      return;
    }

    void updateStatusMutation.mutateAsync({ labId: lab.id, status: 'AVAILABLE' });
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <div className="h-6 w-28 animate-pulse rounded bg-slate-700" />
        <div className="mt-6 space-y-3">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="h-10 animate-pulse rounded bg-slate-800" />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-900 bg-slate-900 p-6 text-sm text-red-300 shadow-sm">
        Không thể tải danh sách lab.
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">Labs</h2>
          <p className="mt-1 text-sm text-slate-400">Quản lý toàn bộ lab và gán LAB_MANAGER.</p>
        </div>
        <div className="flex gap-3">
          <input
            className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
            placeholder="Search lab name"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button
            type="button"
            className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-slate-200"
            onClick={() => setIsAddModalOpen(true)}
          >
            Add Lab
          </button>
        </div>
      </div>

      {filteredLabs.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-700 p-8 text-center text-sm text-slate-400">
          Không có lab phù hợp.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-800 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-400">
                <th className="px-3 py-3">Lab name</th>
                <th className="px-3 py-3">Department</th>
                <th className="px-3 py-3">Manager</th>
                <th className="px-3 py-3">Capacity</th>
                <th className="px-3 py-3">Location</th>
                <th className="px-3 py-3">Status</th>
                <th className="px-3 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {filteredLabs.map((lab) => (
                <tr key={lab.id}>
                  <td className="px-3 py-4 font-medium text-slate-100">{lab.labName}</td>
                  <td className="px-3 py-4 text-slate-300">{lab.department || 'N/A'}</td>
                  <td className="px-3 py-4 text-slate-300">
                    {lab.manager?.fullName || lab.manager?.email || 'Chưa gán'}
                  </td>
                  <td className="px-3 py-4 text-slate-300">{lab.capacity ?? 'N/A'}</td>
                  <td className="px-3 py-4 text-slate-300">{lab.location || 'N/A'}</td>
                  <td className="px-3 py-4">
                    <span
                      className={[
                        'rounded-full px-2 py-1 text-xs font-semibold ring-1',
                        statusClassName(lab.status),
                      ].join(' ')}
                    >
                      {lab.status}
                    </span>
                  </td>
                  <td className="px-3 py-4">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="rounded-md border border-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-100 transition hover:bg-slate-800"
                        onClick={() => window.alert(`${lab.labName}\n${lab.description || 'No description'}`)}
                      >
                        View detail
                      </button>
                      <button
                        type="button"
                        className="rounded-md bg-white px-3 py-1.5 text-xs font-semibold text-slate-950 transition hover:bg-slate-200"
                        onClick={() => setSelectedLab(lab)}
                      >
                        Assign Manager
                      </button>
                      {isLabInactive(lab) ? (
                        <button
                          type="button"
                          className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-900"
                          disabled={updateStatusMutation.isPending}
                          onClick={() => handleRestore(lab)}
                        >
                          Restore
                        </button>
                      ) : null}
                      {isLabActive(lab) || lab.status === 'MAINTENANCE' ? (
                        <button
                          type="button"
                          className="rounded-md bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-amber-700 disabled:cursor-not-allowed disabled:bg-amber-900"
                          disabled={updateStatusMutation.isPending}
                          onClick={() => handleDeactivate(lab)}
                        >
                          Deactivate
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AddLabModal open={isAddModalOpen} onClose={() => setIsAddModalOpen(false)} />
      <AssignManagerModal lab={selectedLab} onClose={() => setSelectedLab(null)} />
    </section>
  );
}
