import { useState } from 'react';

import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { CancelSlotModal, CreateSlotModal, SlotList } from '../components';
import { useCurrentUser } from '../../user/hooks';

export function LabSlotsPage() {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [cancelSlotId, setCancelSlotId] = useState<number | null>(null);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);

  if (isLoadingUser) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-44 animate-pulse rounded bg-slate-200" />
        <div className="mt-4 h-4 w-80 max-w-full animate-pulse rounded bg-slate-100" />
        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div className="h-44 animate-pulse rounded-lg bg-slate-100" />
          <div className="h-44 animate-pulse rounded-lg bg-slate-100" />
          <div className="h-44 animate-pulse rounded-lg bg-slate-100" />
        </div>
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        Bạn chưa được phân công quản lý phòng thí nghiệm nào.
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">Khung giờ sử dụng</h2>
            <p className="mt-2 text-sm text-slate-600">
              Danh sách khung giờ sử dụng của PTN bạn quản lý.
            </p>
            <p className="mt-3 text-sm font-medium text-slate-800">
              PTN: {managedLabName ?? `#${managedLabId}`}
            </p>
          </div>
          <button
            type="button"
            className="w-fit rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition hover:bg-slate-800"
            onClick={() => setIsCreateOpen(true)}
          >
            Tạo khung giờ
          </button>
        </div>
      </div>

      <SlotList labId={managedLabId} canCreate mode="manager" onCancelSlot={setCancelSlotId} />
      <CreateSlotModal
        labId={managedLabId}
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
      <CancelSlotModal
        labId={managedLabId}
        slotId={cancelSlotId}
        isOpen={Boolean(cancelSlotId)}
        onClose={() => setCancelSlotId(null)}
      />
    </section>
  );
}
