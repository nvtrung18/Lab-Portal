import { useI18n } from '../../../shared/i18n';

export function LabInfoPage() {
  const { t } = useI18n();

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          {t('lab.info.manager')}
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">{t('lab.info.title')}</h2>
      </div>

      <form className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="grid gap-5 md:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">{t('lab.info.name')}</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder={t('lab.info.namePlaceholder')}
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">{t('lab.info.location')}</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder={t('lab.info.locationPlaceholder')}
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">{t('lab.info.department')}</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder={t('lab.info.departmentPlaceholder')}
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">{t('lab.info.capacity')}</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              min={1}
              placeholder={t('lab.info.capacityPlaceholder')}
              type="number"
            />
          </label>
        </div>

        <label className="mt-5 block space-y-2">
          <span className="text-sm font-medium text-slate-700">{t('lab.info.description')}</span>
          <textarea
            className="min-h-32 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
            placeholder={t('lab.info.descriptionPlaceholder')}
          />
        </label>

        <div className="mt-6 flex justify-end">
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700"
            type="button"
          >
            {t('lab.info.save')}
          </button>
        </div>
      </form>
    </section>
  );
}
