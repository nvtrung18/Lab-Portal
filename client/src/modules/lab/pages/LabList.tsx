import { Search } from 'lucide-react';
import { useMemo, useState } from 'react';

import { getActiveMemberships, getMembershipLabId } from '../../../shared/utils/membership';
import { useUserApplications } from '../../application/hooks';
import { useCurrentUser } from '../../user/hooks';
import { ApplyModal } from '../components';
import type { LabResponse } from '../api';
import { useLabs } from '../hooks';
import { isLabActive } from '../utils/labStatus';

function statusClassName(status: LabResponse['status']) {
  if (status === 'AVAILABLE') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }

  if (status === 'MAINTENANCE') {
    return 'bg-amber-50 text-amber-700 ring-amber-200';
  }

  return 'bg-slate-100 text-slate-600 ring-slate-200';
}

function formatLabStatus(status: LabResponse['status']) {
  return status === 'AVAILABLE' || status === 'ACTIVE'
    ? 'Đang hoạt động'
    : status === 'MAINTENANCE'
      ? 'Đang bảo trì'
      : 'Ngừng hoạt động';
}

function getApplyState(lab: LabResponse, applicationStatus?: string) {
  const status = applicationStatus ?? lab.applicationStatus;

  if (status === 'PENDING') {
    return { disabled: true, label: 'Đang chờ duyệt' };
  }

  if (status === 'APPROVED') {
    return { disabled: false, label: 'Nộp lại CV' };
  }

  if (status === 'REJECTED') {
    return { disabled: false, label: 'Nộp lại CV' };
  }

  if (!isLabActive(lab)) {
    return { disabled: true, label: 'Chưa mở ứng tuyển' };
  }

  return { disabled: false, label: 'Ứng tuyển' };
}

export function LabList() {
  const { data: labs = [], isLoading, isError } = useLabs();
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const { data: userApplications = [], isLoading: isLoadingApplications } =
    useUserApplications(currentUser?.id);
  const [selectedLabId, setSelectedLabId] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  const activeMembershipLabIds = useMemo(() => {
    return new Set(
      getActiveMemberships(currentUser)
        .map(getMembershipLabId)
        .filter((labId): labId is number => Boolean(labId)),
    );
  }, [currentUser]);

  const applicationsByLabId = useMemo(() => {
    const latestApplications = new Map<number, string>();
    const sortedApplications = [...userApplications].sort(
      (first, second) =>
        new Date(second.createdAt ?? second.updatedAt).getTime() -
        new Date(first.createdAt ?? first.updatedAt).getTime(),
    );

    sortedApplications.forEach((application) => {
      if (!latestApplications.has(application.labId)) {
        latestApplications.set(application.labId, application.status);
      }
    });

    return latestApplications;
  }, [userApplications]);

  const availableLabs = useMemo(() => {
    return labs.filter((lab) => {
      return isLabActive(lab) && !activeMembershipLabIds.has(lab.id);
    });
  }, [activeMembershipLabIds, labs]);

  const labsForApply = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLocaleLowerCase('vi-VN');
    if (!normalizedSearch) return availableLabs;
    return availableLabs.filter((lab) => [lab.labName, lab.department, lab.location]
      .some((value) => value?.toLocaleLowerCase('vi-VN').includes(normalizedSearch)));
  }, [availableLabs, searchTerm]);

  const selectedLab = useMemo(
    () => availableLabs.find((lab) => lab.id === selectedLabId),
    [availableLabs, selectedLabId],
  );

  if (isLoading || isLoadingUser || isLoadingApplications) {
    return (
      <section className="space-y-4">
        <div className="h-7 w-28 animate-pulse rounded bg-slate-200" />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <div
              key={index}
              className="h-48 animate-pulse rounded-lg border border-slate-200 bg-white"
            />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải danh sách PTN.
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-7xl">
      <div className="mb-5 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">Danh sách phòng thí nghiệm</h1>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300">
            Danh sách PTN bạn có thể ứng tuyển. PTN đã tham gia được quản lý trong mục PTN của tôi.
          </p>
        </div>
          <label className="relative block w-full lg:max-w-sm" htmlFor="lab-search">
            <span className="sr-only">Tìm phòng thí nghiệm</span>
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              className="min-h-11 w-full rounded-md border border-slate-300 bg-white pl-10 pr-3 text-base text-slate-950 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              id="lab-search"
              placeholder="Tìm theo tên, khoa hoặc địa điểm"
              type="search"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
          </label>
        </div>
        <p className="mt-4 text-sm tabular-nums text-slate-500 dark:text-slate-400" aria-live="polite">
          Hiển thị {labsForApply.length} trong {availableLabs.length} PTN có thể ứng tuyển
        </p>
      </div>

      {labsForApply.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          {searchTerm.trim() ? 'Không tìm thấy PTN phù hợp với từ khóa.' : 'Hiện không còn PTN nào để ứng tuyển.'}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {labsForApply.map((lab) => {
            const applyState = getApplyState(lab, applicationsByLabId.get(lab.id));

            return (
              <article
                key={lab.id}
                className="flex h-full flex-col rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 transition-shadow hover:shadow-md dark:bg-slate-900 dark:ring-slate-800"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="text-base font-semibold text-slate-950">
                      {lab.labName}
                    </h3>
                    <p className="mt-1 text-sm text-slate-600">
                      {lab.department || 'Chưa phân khoa'}
                    </p>
                  </div>
                  <span
                    className={[
                      'rounded-full px-2 py-1 text-xs font-semibold ring-1',
                      statusClassName(lab.status),
                    ].join(' ')}
                  >
                    {formatLabStatus(lab.status)}
                  </span>
                </div>

                <p className="mt-4 line-clamp-3 min-h-12 text-sm leading-6 text-slate-600 dark:text-slate-300">
                  {lab.description || 'PTN chưa có mô tả.'}
                </p>

                <dl className="mt-4 grid flex-1 grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-slate-500">Quản lý</dt>
                    <dd className="font-medium text-slate-950">
                      {lab.manager?.fullName || lab.manager?.email || 'Chưa phân công'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Sức chứa</dt>
                    <dd className="font-medium text-slate-950">
                      {lab.capacity ?? 'Chưa cập nhật'}
                    </dd>
                  </div>
                  <div className="col-span-2">
                    <dt className="text-slate-500">Địa điểm</dt>
                    <dd className="font-medium text-slate-950">
                      {lab.location || 'Chưa cập nhật'}
                    </dd>
                  </div>
                </dl>

                <button
                  type="button"
                  className="mt-5 min-h-11 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-white dark:text-slate-950 dark:hover:bg-slate-200"
                  disabled={applyState.disabled}
                  onClick={() => setSelectedLabId(lab.id)}
                >
                  {applyState.label}
                </button>
              </article>
            );
          })}
        </div>
      )}

      <ApplyModal
        labId={selectedLabId}
        labName={selectedLab?.labName}
        labStatus={selectedLab?.status}
        onClose={() => setSelectedLabId(null)}
      />
    </section>
  );
}
