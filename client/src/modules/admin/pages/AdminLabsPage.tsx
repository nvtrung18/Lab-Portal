import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Search, X } from 'lucide-react';

import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  Modal,
  type BadgeVariant,
} from '../../../shared/components';
import { LAB_MANAGER } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import { getCurrentCoordinates } from '../../../shared/utils';
import type { LabResponse } from '../../lab/api';
import { LabCheckinPolicyCard } from '../../lab/components';
import { isLabActive, isLabInactive } from '../../lab/utils/labStatus';
import {
  useAdminLabs,
  useAdminUsers,
  useAssignLabManager,
  useCreateLabWithManager,
  useUpdateLabStatus,
  useAssignableManagers,
} from '../hooks';

function statusBadgeVariant(status: string): BadgeVariant {
  if (status === 'AVAILABLE' || status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'MAINTENANCE') {
    return 'warning';
  }
  return 'neutral';
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
    <Modal title={t('adminLabs.assignTitle')} size="md" onClose={onClose} closeDisabled={assignMutation.isPending} subtitle={lab.labName}>
        <div className="mt-6">
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300" htmlFor="managerId">
            {t('adminLabs.manager')}
          </label>
          {managerOptions.length === 0 ? (
            <div className="mt-2 rounded-md border border-dashed border-slate-300 dark:border-slate-700 p-4 text-sm text-slate-500 dark:text-slate-400">
              {t('adminLabs.noManagerAvailable')}
            </div>
          ) : (
            <select
              id="managerId"
              className="mt-2 min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              value={managerId}
              disabled={assignMutation.isPending}
              onChange={(event) => setManagerId(event.target.value)}
            >
              <option value="" className="bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100">{t('adminLabs.chooseManager')}</option>
              {managerOptions.map((user) => (
                <option key={user.id} value={user.id} className="bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100">
                  {user.fullName || user.email}
                </option>
              ))}
            </select>
          )}
        </div>

        <div className="app-form-actions mt-6 flex flex-col-reverse justify-end gap-3 sm:flex-row">
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
    </Modal>
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
    <Modal title={t('adminLabs.addTitle')} size="lg" onClose={onClose} closeDisabled={createMutation.isPending}>
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.labName')}</span>
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              value={form.labName}
              onChange={(event) => updateField('labName', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.department')}</span>
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              value={form.department}
              onChange={(event) => updateField('department', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.capacity')}</span>
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              min={1}
              type="number"
              value={form.capacity}
              onChange={(event) => updateField('capacity', event.target.value)}
            />
          </label>
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.location')}</span>
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              value={form.location}
              onChange={(event) => updateField('location', event.target.value)}
            />
          </label>
          <div className="space-y-3 rounded-md border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-950/60 p-3 sm:col-span-2">
            <div>
              <p className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.coordinates')}</p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1">
                <span className="text-xs font-medium text-slate-500 dark:text-slate-400">{t('adminLabs.latitude')}</span>
                <input
                  className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
                  inputMode="decimal"
                  readOnly
                  value={form.checkinLatitude}
                />
              </label>
              <label className="space-y-1">
                <span className="text-xs font-medium text-slate-500 dark:text-slate-400">{t('adminLabs.longitude')}</span>
                <input
                  className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
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
            {locationError ? <p className="text-sm text-red-700 dark:text-red-300" role="alert">{locationError}</p> : null}
          </div>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.description')}</span>
            <textarea
              className="min-h-24 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </label>
          <label className="space-y-2 sm:col-span-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('adminLabs.optionalManager')}</span>
            {managerOptions.length === 0 ? (
              <div className="rounded-md border border-dashed border-slate-300 dark:border-slate-700 p-4 text-sm text-slate-500 dark:text-slate-400">
                {t('adminLabs.noManagerAvailableForAssignment')}
              </div>
            ) : (
              <select
                className="min-h-11 w-full rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-3 py-2 text-base text-slate-950 dark:text-white outline-none focus:border-blue-500 sm:text-sm"
                value={form.managerId}
                onChange={(event) => updateField('managerId', event.target.value)}
              >
                <option value="" className="bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100">{t('adminLabs.notAssigned')}</option>
                {managerOptions.map((user) => (
                  <option key={user.id} value={user.id} className="bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100">
                    {user.fullName || user.email}
                  </option>
                ))}
              </select>
            )}
          </label>
        </div>

        <div className="app-form-actions mt-6 flex flex-col-reverse justify-end gap-3 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 pb-4 pt-4 sm:flex-row sm:px-6 sm:pb-6">
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
    </Modal>
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
    <Modal title={t('adminLabs.detailTitle')} size="md" onClose={onClose} subtitle={lab.labName}>
        <dl className="mt-6 divide-y divide-slate-200 dark:divide-slate-800">
          {details.map((item) => (
            <div key={item.label} className="flex min-w-0 flex-col gap-1 py-3 sm:flex-row sm:gap-4">
              <dt className="sm:w-36 shrink-0 text-sm font-medium text-slate-500 dark:text-slate-400">{item.label}</dt>
              <dd className="min-w-0 break-words text-sm text-slate-900 dark:text-slate-100">{item.value}</dd>
            </div>
          ))}
        </dl>

        {!lab.manager ? (
          <div className="mt-4 rounded-md border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950/50 px-3 py-2 text-xs font-medium text-amber-800 dark:text-amber-300">
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
    </Modal>
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
    <Modal title={t('adminLabs.checkinPolicyTitle')} size="2xl" onClose={onClose}>
        <LabCheckinPolicyCard labId={lab.id} />
    </Modal>
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
    <Modal title={isDeactivate ? t('adminLabs.confirmDeactivateTitle') : t('adminLabs.confirmRestoreTitle')} size="sm" onClose={onClose} closeDisabled={isLoading}>
        <p className="mt-3 break-words text-sm text-slate-700 dark:text-slate-300">
          {isDeactivate
            ? t('adminLabs.confirmDeactivateMessage', undefined, { lab: lab.labName })
            : t('adminLabs.confirmRestoreMessage', undefined, { lab: lab.labName })}
        </p>
        <div className="mt-6 flex flex-col-reverse justify-end gap-3 sm:flex-row">
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
    </Modal>
  );
}

// --- Main Page ---

export function AdminLabsPage() {
  const { locale, t } = useI18n();
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
      <section className="rounded-xl border border-slate-200/90 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <LoadingState className="text-slate-600 dark:text-slate-300" />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-xl border border-slate-200/90 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  const hasActiveFilters = Boolean(search || statusFilter || managerFilter);

  return (
    <section className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
              {t('adminLabs.title')}
            </h1>
            <span className="inline-flex shrink-0 items-center rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-semibold text-teal-800 ring-1 ring-inset ring-teal-600/20 dark:bg-teal-950/60 dark:text-teal-300 dark:ring-teal-500/30">
              {filteredLabs.length} {locale === 'vi' ? 'phòng lab' : 'labs'}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {locale === 'vi'
              ? 'Quản lý danh sách phòng lab nghiên cứu, trưởng phòng phụ trách và cấu hình tự điểm danh'
              : 'Manage research laboratories, lab managers, and self-check-in policies'}
          </p>
        </div>
        <Button
          size="sm"
          variant="primary"
          className="inline-flex items-center gap-2 self-start sm:self-auto"
          onClick={() => setIsAddModalOpen(true)}
        >
          <Plus className="h-4 w-4" />
          <span>{t('adminLabs.add')}</span>
        </Button>
      </div>

      {/* Warning & Info banners */}
      {labsWithoutManager.length > 0 ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800/80 dark:bg-amber-950/30 dark:text-amber-300">
          <span className="font-semibold">{t('adminLabs.alert')}</span>{' '}
          {labsWithoutManager.length === 1
            ? t('adminLabs.activeWithoutManager', undefined, { lab: labsWithoutManager[0].labName })
            : t('adminLabs.activeWithoutManagers', undefined, {
                count: labsWithoutManager.length,
                labs: labsWithoutManager.map((l) => `"${l.labName}"`).join(', '),
              })}
        </div>
      ) : null}

      {unassignedManagers.length > 0 ? (
        <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800 dark:border-blue-800/80 dark:bg-blue-950/30 dark:text-blue-300">
          <span className="font-semibold">{t('adminLabs.info')}</span>{' '}
          {unassignedManagers.length === 1
            ? t('adminLabs.unassignedManager', undefined, {
                manager: unassignedManagers[0].fullName || unassignedManagers[0].email,
              })
            : t('adminLabs.unassignedManagers', undefined, {
                count: unassignedManagers.length,
                managers: unassignedManagers.map((u) => `"${u.fullName || u.email}"`).join(', '),
              })}
        </div>
      ) : null}

      {/* Filter Toolbar Card */}
      <div className="rounded-xl border border-slate-200/90 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="relative flex-1 min-w-[240px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
            <input
              aria-label={t('adminLabs.searchPlaceholder')}
              className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/50 pl-9 pr-8 text-sm text-slate-900 placeholder:text-slate-400 transition-all focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              placeholder={t('adminLabs.searchPlaceholder')}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:flex lg:items-center">
            <select
              aria-label={t('adminLabs.tableStatus')}
              className="h-10 min-w-[160px] rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-xs sm:text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              {labStatusFilterOptions.map((status) => (
                <option key={status || 'all'} value={status}>
                  {status === 'ACTIVE'
                    ? t('adminLabs.active')
                    : status === 'INACTIVE'
                      ? t('adminLabs.inactive')
                      : t('adminLabs.allStatuses')}
                </option>
              ))}
            </select>

            <select
              aria-label={t('adminLabs.tableManager')}
              className="h-10 min-w-[180px] rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-xs sm:text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
              value={managerFilter}
              onChange={(event) => setManagerFilter(event.target.value)}
            >
              <option value="">{t('adminLabs.allAssignments')}</option>
              <option value="NO_MANAGER">{t('adminLabs.noManager')}</option>
            </select>

            {hasActiveFilters && (
              <Button
                size="sm"
                variant="ghost"
                className="h-10 text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-100"
                onClick={() => {
                  setSearch('');
                  setStatusFilter('');
                  setManagerFilter('');
                }}
              >
                {locale === 'vi' ? 'Đặt lại' : 'Reset'}
              </Button>
            )}
          </div>
        </div>
      </div>

      {filteredLabs.length === 0 ? (
        <EmptyState className="rounded-xl border border-dashed border-slate-300 bg-white dark:border-slate-800 dark:bg-slate-900/60" />
      ) : (
        <>
          {/* Mobile Card View (< md) */}
          <div className="space-y-3 md:hidden">
            {filteredLabs.map((lab) => (
              <article
                key={lab.id}
                className="rounded-xl border border-slate-200/90 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-slate-800 dark:bg-slate-900/60"
              >
                <div className="flex min-w-0 items-start justify-between gap-2">
                  <h3 className="min-w-0 break-words font-semibold text-slate-900 dark:text-slate-100">
                    {lab.labName}
                  </h3>
                  <Badge variant={statusBadgeVariant(lab.status)}>
                    {formatLabStatus(lab.status, t)}
                  </Badge>
                </div>
                <dl className="mt-3.5 grid grid-cols-1 gap-2.5 border-t border-slate-100 pt-3 text-xs dark:border-slate-800/80 sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-400 dark:text-slate-500">{t('adminLabs.tableDepartment')}</dt>
                    <dd className="mt-0.5 font-medium text-slate-700 dark:text-slate-300 break-words">
                      {lab.department || t('adminLabs.noUpdated')}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-400 dark:text-slate-500">{t('adminLabs.tableManager')}</dt>
                    <dd className="mt-0.5 font-medium text-slate-700 dark:text-slate-300 break-words">
                      {lab.manager ? lab.manager.fullName || lab.manager.email : t('adminLabs.noManager')}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-400 dark:text-slate-500">{t('adminLabs.tableCapacity')}</dt>
                    <dd className="mt-0.5 text-slate-600 dark:text-slate-400">
                      {lab.capacity ?? t('adminLabs.noUpdated')}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-400 dark:text-slate-500">{t('adminLabs.tableLocation')}</dt>
                    <dd className="mt-0.5 text-slate-600 dark:text-slate-400 break-words">
                      {lab.location || t('adminLabs.noUpdated')}
                    </dd>
                  </div>
                </dl>
                <div className="mt-3.5 grid grid-cols-2 gap-2 border-t border-slate-100 pt-3 dark:border-slate-800/80">
                  <Button size="sm" variant="outline" className="w-full justify-center" onClick={() => setDetailLab(lab)}>
                    {t('adminLabs.details')}
                  </Button>
                  <Button size="sm" variant="outline" className="w-full justify-center" onClick={() => setPolicyLab(lab)}>
                    {t('adminLabs.selfCheckin')}
                  </Button>
                  {!lab.manager ? (
                    <Button size="sm" variant="primary" className="col-span-2 w-full justify-center" onClick={() => setSelectedLab(lab)}>
                      {t('adminLabs.assign')}
                    </Button>
                  ) : null}
                  {isLabInactive(lab) ? (
                    <Button
                      size="sm"
                      variant="success"
                      className="col-span-2 w-full justify-center"
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
                      className="col-span-2 w-full justify-center"
                      disabled={updateStatusMutation.isPending}
                      onClick={() => handleDeactivate(lab)}
                    >
                      {t('adminLabs.deactivate')}
                    </Button>
                  ) : null}
                </div>
              </article>
            ))}
          </div>

          {/* Desktop Table View (>= md) */}
          <div
            aria-label={t('adminLabs.title')}
            className="app-table-frame hidden min-w-0 max-w-full overflow-x-auto rounded-xl border border-slate-200/90 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900/60 md:block"
            role="region"
            tabIndex={0}
          >
            <table className="w-full min-w-[960px] text-left text-sm border-collapse">
              <thead className="border-b border-slate-200/90 bg-slate-50/80 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3.5 min-w-[200px] font-semibold">{t('adminLabs.tableName')}</th>
                  <th className="px-4 py-3.5 min-w-[170px] font-semibold">{t('adminLabs.tableDepartment')}</th>
                  <th className="px-4 py-3.5 min-w-[180px] font-semibold">{t('adminLabs.tableManager')}</th>
                  <th className="px-4 py-3.5 min-w-[100px] font-semibold">{t('adminLabs.tableCapacity')}</th>
                  <th className="px-4 py-3.5 min-w-[160px] font-semibold">{t('adminLabs.tableLocation')}</th>
                  <th className="px-4 py-3.5 min-w-[130px] font-semibold">{t('adminLabs.tableStatus')}</th>
                  <th className="px-4 py-3.5 min-w-[260px] text-right font-semibold">{t('adminLabs.tableActions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70">
                {filteredLabs.map((lab) => (
                  <tr key={lab.id} className="transition-colors hover:bg-slate-50/80 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-3.5 font-semibold text-slate-900 dark:text-slate-100">
                      {lab.labName}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300">
                      {lab.department || t('adminLabs.noUpdated')}
                    </td>
                    <td className="px-4 py-3.5">
                      {lab.manager ? (
                        <span className="font-medium text-slate-800 dark:text-slate-200">
                          {lab.manager.fullName || lab.manager.email}
                        </span>
                      ) : (
                        <Badge variant="warning" dot={false}>
                          {t('adminLabs.noManager')}
                        </Badge>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300">
                      {lab.capacity ?? t('adminLabs.noUpdated')}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300">
                      {lab.location || t('adminLabs.noUpdated')}
                    </td>
                    <td className="px-4 py-3.5">
                      <Badge variant={statusBadgeVariant(lab.status)}>
                        {formatLabStatus(lab.status, t)}
                      </Badge>
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <div className="inline-flex items-center justify-end gap-1.5 whitespace-nowrap">
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
        </>
      )}

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
