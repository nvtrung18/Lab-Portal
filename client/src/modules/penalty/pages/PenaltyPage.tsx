import { PenaltyItem } from '../components';
import { useMyPenalties, useSubmitComplaint } from '../hooks';
import { useI18n } from '../../../shared/i18n';

export function PenaltyPage() {
  const { t } = useI18n();
  const { data: penalties = [], isLoading, isError, refetch } = useMyPenalties();
  const submitComplaint = useSubmitComplaint();

  if (isLoading) {
    return (
      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="h-6 w-56 animate-pulse rounded bg-slate-200" />
        <div className="mt-5 space-y-3">
          <div className="h-36 animate-pulse rounded bg-slate-100" />
          <div className="h-36 animate-pulse rounded bg-slate-100" />
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-xl border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm dark:border-red-900/50 dark:bg-slate-900 dark:text-red-400">
        {t('penalty.loadFailed')}
        <button className="ml-3 min-h-11 font-semibold underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500" type="button" onClick={() => refetch()}>
          {t('common.retry')}
        </button>
      </section>
    );
  }

  return (
    <section className="space-y-4">
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('penalty.title')}</h1>
      </div>

      {!penalties.length ? (
        <div className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
          {t('penalty.empty')}
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {penalties.map((penalty) => (
            <PenaltyItem
              key={penalty.id}
              isSubmitting={submitComplaint.isPending}
              penalty={penalty}
              onSubmitComplaint={(penaltyId, content) =>
                submitComplaint.mutate({ penaltyId, content })
              }
            />
          ))}
        </div>
      )}
    </section>
  );
}
