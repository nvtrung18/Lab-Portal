import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { useLab } from '../hooks';

export function LabOverviewPage() {
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: managedLab, isLoading: isLoadingLab, isError } = useLab(managedLabId);

  if (isLoadingUser || isLoadingLab) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-40 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 grid gap-4 md:grid-cols-2">
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
          <div className="h-16 animate-pulse rounded bg-slate-100" />
        </div>
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        Tài khoản quản lý hiện chưa được phân công PTN.
      </section>
    );
  }

  if (isError || !managedLab) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải thông tin PTN đang quản lý.
      </section>
    );
  }

  const displayedStatus =
    managedLab.status === 'AVAILABLE' || managedLab.status === 'ACTIVE'
      ? 'Đang hoạt động'
      : managedLab.status === 'MAINTENANCE'
        ? 'Đang bảo trì'
        : 'Ngừng hoạt động';

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Quản lý PTN
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Tổng quan PTN</h2>
        <p className="mt-2 text-sm text-slate-600">
          Chỉ hiển thị thông tin PTN mà quản lý hiện tại phụ trách.
        </p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-200 pb-5 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">
              {managedLab.labName}
            </h3>
            <p className="mt-1 text-sm text-slate-600">
              {managedLab.description || 'PTN chưa có mô tả.'}
            </p>
          </div>
          <span className="inline-flex w-fit rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
            {displayedStatus}
          </span>
        </div>

        <dl className="mt-6 grid gap-5 md:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-slate-500">Quản lý</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.manager?.fullName || managedLab.manager?.email || 'Chưa phân công'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Khoa / đơn vị</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.department || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Địa điểm</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.location || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Sức chứa</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.capacity ?? 'Chưa cập nhật'}
            </dd>
          </div>
        </dl>
      </div>
    </section>
  );
}
