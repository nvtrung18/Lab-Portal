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

  const labsForApply = useMemo(() => {
    return labs.filter((lab) => {
      return isLabActive(lab) && !activeMembershipLabIds.has(lab.id);
    });
  }, [activeMembershipLabIds, labs]);

  const selectedLab = useMemo(
    () => labsForApply.find((lab) => lab.id === selectedLabId),
    [labsForApply, selectedLabId],
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
    <section>
      <div className="mb-5 flex items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-slate-950">Phòng thí nghiệm</h2>
          <p className="mt-1 text-sm text-slate-600">
            Danh sách PTN bạn có thể ứng tuyển. PTN đã tham gia được quản lý trong mục PTN của tôi.
          </p>
        </div>
        <span className="shrink-0 text-sm text-slate-500">
          {labsForApply.length} PTN
        </span>
      </div>

      {labsForApply.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600">
          Hiện không còn PTN nào để ứng tuyển.
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {labsForApply.map((lab) => {
            const applyState = getApplyState(lab, applicationsByLabId.get(lab.id));

            return (
              <article
                key={lab.id}
                className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
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

                <p className="mt-4 line-clamp-3 min-h-12 text-sm text-slate-600">
                  {lab.description || 'PTN chưa có mô tả.'}
                </p>

                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
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
                  className="mt-5 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
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
