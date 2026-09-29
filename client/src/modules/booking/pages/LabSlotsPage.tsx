import { useState } from 'react';
import { CalendarDays, History, Plus } from 'lucide-react';

import { getManagedLabId } from '../../../shared/utils/membership';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { CancelSlotModal, CreateSlotModal, SlotList } from '../components';
import { useCurrentUser } from '../../user/hooks';

export function LabSlotsPage() {
  const { t } = useI18n();
  const [view, setView] = useState<'active' | 'history'>('active');
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [cancelSlotId, setCancelSlotId] = useState<number | null>(null);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);

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
        {t('layout.unassignedLab')}
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-700"><CalendarDays aria-hidden="true" className="h-5 w-5" /></span>
              <div>
                <h1 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">{t('slots.manageTitle')}</h1>
              </div>
            </div>
          </div>
          <Button className="w-full sm:w-auto" type="button" onClick={() => setIsCreateOpen(true)}>
            <Plus aria-hidden="true" className="h-4 w-4" /> {t('slots.create')}
          </Button>
        </div>
      </div>

      <div className="flex w-full flex-col gap-1 rounded-lg border border-slate-200 bg-white p-1 shadow-sm sm:inline-flex sm:w-auto sm:flex-row" role="tablist" aria-label={t('slots.tabs')}>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'active'}
          className={view === 'active' ? 'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white sm:flex-none' : 'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 sm:flex-none'}
          onClick={() => setView('active')}
        >
          <CalendarDays aria-hidden="true" className="h-4 w-4" /> {t('slots.active')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'history'}
          className={view === 'history' ? 'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white sm:flex-none' : 'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 sm:flex-none'}
          onClick={() => setView('history')}
        >
          <History aria-hidden="true" className="h-4 w-4" /> {t('bookings.history')}
        </button>
      </div>

      <div role="tabpanel">
        <SlotList labId={managedLabId} canCreate mode="manager" view={view} onCancelSlot={setCancelSlotId} />
      </div>
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
