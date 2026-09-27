import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import { getCurrentCoordinates } from '../../../shared/utils';
import type { LabResponse } from '../../lab/api';
import { LabCheckinPolicyCard } from '../../lab/components';
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

function formatLabStatus(status: string, t: (key: string, fallback?: string) => string) {
  const keys: Record<string, string> = {
    AVAILABLE: 'adminLabs.active',
    ACTIVE: 'adminLabs.active',
    MAINTENANCE: 'adminLabs.maintenance',
    INACTIVE: 'adminLabs.inactive',
    ARCHIVED: 'adminLabs.archived',
    CLOSED: 'adminLabs.closed',
  };
  return t(keys[status] ?? '', status);
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
  const { t } = useI18n();
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
            <h3 className="text-lg font-semibold text-white">{t('adminLabs.assignTitle')}</h3>
            <p className="mt-1 text-sm text-slate-400">{lab.labName}</p>
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={assignMutation.isPending}
            onClick={onClose}
          >
            {t('common.close')}
          </Button>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-300" htmlFor="managerId">
            {t('adminLabs.manager')}
          </label>
          {managerOptions.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
              {t('adminLabs.noManagerAvailable')}
            </div>
          ) : (
            <select
              id="managerId"
              className="mt-2 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={managerId}
              disabled={assignMutation.isPending}
              onChange={(event) => setManagerId(event.target.value)}
            >
              <option value="" className="bg-slate-900 text-slate-100">{t('adminLabs.chooseManager')}</option>
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
            {t('common.cancel')}
          </Button>
          <Button
            size="sm"
            variant="primary"
            disabled={assignMutation.isPending || !managerId}
            loading={assignMutation.isPending}
            loadingText={t('common.loading')}
            onClick={() => void handleSubmit()}
          >
            {t('common.confirm')}
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
  const { t } = useI18n();
  const createMutation = useCreateLabWithManager();
  const managerOptions = useAvailableManagers();
  const [form, setForm] = useState({
    labName: '',
    department: '',
    description: '',
    capacity: '1',
    location: '',
    managerId: '',
    checkinLatitude: '',
    checkinLongitude: '',
  });
  const [isLocating, setIsLocating] = useState(false);
  const [locationError, setLocationError] = useState('');
  const autoLocationAttempted = useRef(false);

  const updateField = (field: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleUseCurrentLocation = useCallback(async () => {
    setLocationError('');
    setIsLocating(true);
    try {
      const coordinates = await getCurrentCoordinates();
      setForm((current) => ({
        ...current,
        checkinLatitude: String(coordinates.latitude),
        checkinLongitude: String(coordinates.longitude),
      }));
    } catch (error) {
      setLocationError(error instanceof Error ? error.message : t('common.gpsUnavailable'));
    } finally {
      setIsLocating(false);
    }
  }, [t]);

  useEffect(() => {
    if (!open) {
      autoLocationAttempted.current = false;
      return;
    }
    if (autoLocationAttempted.current || form.checkinLatitude || form.checkinLongitude) return;
    autoLocationAttempted.current = true;
    void handleUseCurrentLocation();
  }, [form.checkinLatitude, form.checkinLongitude, handleUseCurrentLocation, open]);

  if (!open) {
    return null;
  }

  const handleSubmit = async () => {
    const latitude = form.checkinLatitude.trim() ? Number(form.checkinLatitude) : null;
    const longitude = form.checkinLongitude.trim() ? Number(form.checkinLongitude) : null;
    const hasPartialCoordinates = (latitude == null) !== (longitude == null);
    const hasInvalidCoordinates = latitude != null
      && longitude != null
      && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180);
    if (latitude == null || longitude == null || hasPartialCoordinates || hasInvalidCoordinates) {
      setLocationError(t('adminLabs.gpsRequired'));
      return;
    }

    await createMutation.mutateAsync({
      labName: form.labName.trim(),
      department: form.department.trim() || null,
      description: form.description.trim() || null,
      capacity: Number(form.capacity),
      location: form.location.trim(),
      managerId: form.managerId ? Number(form.managerId) : null,
      checkinLatitude: latitude,
      checkinLongitude: longitude,
    });
    setForm({
      labName: '',
      department: '',
      description: '',
      capacity: '1',
      location: '',
      managerId: '',
      checkinLatitude: '',
      checkinLongitude: '',
    });
    setLocationError('');
    onClose();
  };

  const latitude = form.checkinLatitude.trim() ? Number(form.checkinLatitude) : null;
  const longitude = form.checkinLongitude.trim() ? Number(form.checkinLongitude) : null;
  const coordinatesValid = latitude != null && longitude != null
    && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180;
  const canSubmit =
    form.labName.trim().length >= 3 &&
    form.location.trim().length >= 3 &&
    Number(form.capacity) >= 1 &&
    coordinatesValid;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-2xl overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">{t('adminLabs.addTitle')}</h3>
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={createMutation.isPending}
            onClick={onClose}
          >
            {t('common.close')}
          </Button>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.labName')}</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.labName}
              onChange={(event) => updateField('labName', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.department')}</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.department}
              onChange={(event) => updateField('department', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.capacity')}</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              min={1}
              type="number"
              value={form.capacity}
              onChange={(event) => updateField('capacity', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.location')}</span>
            <input
              className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.location}
              onChange={(event) => updateField('location', event.target.value)}
            />
          </label>
          <div className="space-y-3 rounded-md border border-slate-700 bg-slate-950/60 p-3 sm:col-span-2">
            <div>
              <p className="text-sm font-medium text-slate-300">{t('adminLabs.coordinates')}</p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1">
                <span className="text-xs font-medium text-slate-400">{t('adminLabs.latitude')}</span>
                <input
                  className="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white outline-none focus:border-white"
                  inputMode="decimal"
                  readOnly
                  value={form.checkinLatitude}
                />
              </label>
              <label className="space-y-1">
                <span className="text-xs font-medium text-slate-400">{t('adminLabs.longitude')}</span>
                <input
                  className="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white outline-none focus:border-white"
                  inputMode="decimal"
                  readOnly
                  value={form.checkinLongitude}
                />
              </label>
            </div>
            <Button
              size="sm"
              variant="outline"
              disabled={isLocating || createMutation.isPending}
              loading={isLocating}
              loadingText={t('adminLabs.gettingLocation')}
              onClick={() => void handleUseCurrentLocation()}
            >
              {t('adminLabs.currentLocation')}
            </Button>
            {locationError ? <p className="text-sm text-red-300" role="alert">{locationError}</p> : null}
          </div>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.description')}</span>
            <textarea
              className="min-h-24 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-300">{t('adminLabs.optionalManager')}</span>
            {managerOptions.length === 0 ? (
              <div className="rounded-md border border-dashed border-slate-700 p-4 text-sm text-slate-400">
                {t('adminLabs.noManagerAvailableForAssignment')}
              </div>
            ) : (
              <select
                className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
                value={form.managerId}
                onChange={(event) => updateField('managerId', event.target.value)}
              >
                <option value="" className="bg-slate-900 text-slate-100">{t('adminLabs.notAssigned')}</option>
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
            {t('common.cancel')}
          </Button>
          <Button
            size="sm"
            variant="primary"
            disabled={createMutation.isPending || !canSubmit}
            loading={createMutation.isPending}
            loadingText={t('adminLabs.creating')}
            onClick={() => void handleSubmit()}
          >
            {t('adminLabs.create')}
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
  const { locale, t } = useI18n();
  if (!lab) {
    return null;
  }

  const details = [
    { label: t('adminLabs.labName'), value: lab.labName },
    { label: t('adminLabs.department'), value: lab.department || t('adminLabs.noUpdated') },
    { label: t('adminLabs.location'), value: lab.location || t('adminLabs.noUpdated') },
    { label: t('adminLabs.capacity'), value: lab.capacity != null ? String(lab.capacity) : t('adminLabs.noUpdated') },
    { label: t('adminLabs.manager'), value: lab.manager?.fullName || lab.manager?.email || t('adminLabs.notAssigned') },
    { label: t('adminLabs.tableStatus'), value: formatLabStatus(lab.status, t) },
    { label: t('adminLabs.description'), value: lab.description || t('adminLabs.noDescription') },
    { label: t('adminLabs.createdAt'), value: lab.createdAt ? new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(lab.createdAt)) : t('adminLabs.noUpdated') },
    { label: t('adminLabs.updatedAt'), value: lab.updatedAt ? new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(lab.updatedAt)) : t('adminLabs.noUpdated') },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-lg overflow-y-auto rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-white">{t('adminLabs.detailTitle')}</h3>
            <p className="mt-1 text-sm text-slate-400">{lab.labName}</p>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={onClose}
          >
            {t('common.close')}
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
            {t('adminLabs.noManagerAssigned')}
          </div>
        ) : null}

        <div className="mt-6 flex justify-end">
          <Button
            size="sm"
            variant="outline"
            onClick={onClose}
          >
            {t('common.close')}
          </Button>
        </div>
      </div>
    </div>
  );
}

interface LabCheckinPolicyModalProps {
  lab: LabResponse | null;
  onClose: () => void;
}

function LabCheckinPolicyModal({ lab, onClose }: LabCheckinPolicyModalProps) {
  const { t } = useI18n();
  if (!lab) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="max-h-[calc(100dvh-1rem)] w-full max-w-5xl overflow-y-auto rounded-lg border border-slate-800 bg-slate-100 p-3 shadow-xl sm:p-5">
        <div className="mb-3 flex items-start justify-between gap-4 rounded-lg bg-slate-900 p-4">
          <div>
            <h3 className="text-lg font-semibold text-white">{t('adminLabs.checkinPolicyTitle')}</h3>
          </div>
          <Button size="sm" variant="outline" onClick={onClose}>{t('common.close')}</Button>
        </div>
        <LabCheckinPolicyCard labId={lab.id} />
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
  const { t } = useI18n();
  if (!lab || !action) {
    return null;
  }

  const isDeactivate = action === 'deactivate';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/70 p-2 sm:p-4">
      <div className="w-full max-w-md rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-white">
          {isDeactivate ? t('adminLabs.confirmDeactivateTitle') : t('adminLabs.confirmRestoreTitle')}
        </h3>
        <p className="mt-3 text-sm text-slate-300">
          {isDeactivate
            ? t('adminLabs.confirmDeactivateMessage', undefined, { lab: lab.labName })
            : t('adminLabs.confirmRestoreMessage', undefined, { lab: lab.labName })}
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <Button
            size="sm"
            variant="outline"
            disabled={isLoading}
            onClick={onClose}
          >
            {t('common.cancel')}
          </Button>
          <Button
            size="sm"
            variant={isDeactivate ? 'danger' : 'primary'}
            disabled={isLoading}
            loading={isLoading}
            loadingText={t('adminLabs.processing')}
            onClick={onConfirm}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}

// --- Main Page ---

export function AdminLabsPage() {
  const { t } = useI18n();
  const { data: labs = [], isLoading, isError, refetch } = useAdminLabs();
  const { data: users = [] } = useAdminUsers();
  const updateStatusMutation = useUpdateLabStatus();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [managerFilter, setManagerFilter] = useState('');
  const [selectedLab, setSelectedLab] = useState<LabResponse | null>(null);
  const [detailLab, setDetailLab] = useState<LabResponse | null>(null);
  const [policyLab, setPolicyLab] = useState<LabResponse | null>(null);
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
          <span className="font-semibold">{t('adminLabs.alert')}</span>{' '}
          {labsWithoutManager.length === 1
            ? t('adminLabs.activeWithoutManager', undefined, { lab: labsWithoutManager[0].labName })
            : t('adminLabs.activeWithoutManagers', undefined, { count: labsWithoutManager.length, labs: labsWithoutManager.map((l) => `"${l.labName}"`).join(', ') })}
        </div>
      ) : null}

      {unassignedManagers.length > 0 ? (
        <div className="rounded-lg border border-blue-800 bg-blue-950/30 px-4 py-3 text-sm text-blue-300">
          <span className="font-semibold">{t('adminLabs.info')}</span>{' '}
          {unassignedManagers.length === 1
            ? t('adminLabs.unassignedManager', undefined, { manager: unassignedManagers[0].fullName || unassignedManagers[0].email })
            : t('adminLabs.unassignedManagers', undefined, { count: unassignedManagers.length, managers: unassignedManagers.map((u) => `"${u.fullName || u.email}"`).join(', ') })}
        </div>
      ) : null}

      {/* Main content */}
      <div className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-white">{t('adminLabs.title')}</h2>
          </div>
          <div className="flex gap-3">
            <input
              className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
              placeholder={t('adminLabs.searchPlaceholder')}
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
                    ? t('adminLabs.active')
                    : status === 'INACTIVE'
                      ? t('adminLabs.inactive')
                      : t('adminLabs.allStatuses')}
                </option>
              ))}
            </select>
            <select
              className="rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
              value={managerFilter}
              onChange={(event) => setManagerFilter(event.target.value)}
            >
              <option value="" className="bg-slate-900 text-slate-100">{t('adminLabs.allAssignments')}</option>
              <option value="NO_MANAGER" className="bg-slate-900 text-slate-100">{t('adminLabs.noManager')}</option>
            </select>
            <Button
              size="sm"
              variant="primary"
              onClick={() => setIsAddModalOpen(true)}
            >
              {t('adminLabs.add')}
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
                  <th className="px-3 py-3">{t('adminLabs.tableName')}</th>
                  <th className="px-3 py-3">{t('adminLabs.tableDepartment')}</th>
                  <th className="px-3 py-3">{t('adminLabs.tableManager')}</th>
                  <th className="px-3 py-3">{t('adminLabs.tableCapacity')}</th>
                  <th className="px-3 py-3">{t('adminLabs.tableLocation')}</th>
                  <th className="px-3 py-3">{t('adminLabs.tableStatus')}</th>
                  <th className="px-3 py-3 text-right">{t('adminLabs.tableActions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {filteredLabs.map((lab) => (
                  <tr key={lab.id} className="transition-colors hover:bg-slate-950/30">
                    <td className="px-3 py-4 font-medium text-slate-100">{lab.labName}</td>
                    <td className="px-3 py-4 text-slate-300">{lab.department || t('adminLabs.noUpdated')}</td>
                    <td className="px-3 py-4">
                      {lab.manager ? (
                        <span className="text-slate-300">{lab.manager.fullName || lab.manager.email}</span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-amber-950 px-2 py-0.5 text-xs font-medium text-amber-300 ring-1 ring-inset ring-amber-800">
                          {t('adminLabs.noManager')}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-4 text-slate-300">{lab.capacity ?? t('adminLabs.noUpdated')}</td>
                    <td className="px-3 py-4 text-slate-300">{lab.location || t('adminLabs.noUpdated')}</td>
                    <td className="px-3 py-4">
                      <span
                        className={[
                          'rounded-full px-2 py-1 text-xs font-semibold ring-1',
                          statusClassName(lab.status),
                        ].join(' ')}
                      >
                        {formatLabStatus(lab.status, t)}
                      </span>
                    </td>
                    <td className="px-3 py-4">
                      <div className="flex justify-end gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setDetailLab(lab)}
                        >
                          {t('adminLabs.details')}
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setPolicyLab(lab)}
                        >
                          {t('adminLabs.selfCheckin')}
                        </Button>
                        {!lab.manager && (
                          <Button
                            size="sm"
                            variant="primary"
                            onClick={() => setSelectedLab(lab)}
                          >
                            {t('adminLabs.assign')}
                          </Button>
                        )}
                        {isLabInactive(lab) ? (
                          <Button
                            size="sm"
                            variant="success"
                            disabled={updateStatusMutation.isPending}
                            onClick={() => handleRestore(lab)}
                          >
                            {t('adminLabs.restore')}
                          </Button>
                        ) : null}
                        {isLabActive(lab) || lab.status === 'MAINTENANCE' ? (
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={updateStatusMutation.isPending}
                            onClick={() => handleDeactivate(lab)}
                          >
                            {t('adminLabs.deactivate')}
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
      <LabCheckinPolicyModal lab={policyLab} onClose={() => setPolicyLab(null)} />
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
