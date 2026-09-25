import { useI18n } from '../shared/i18n';

export function LabsPlaceholder() {
  const { t } = useI18n();
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-xl font-semibold text-slate-950">{t('lab.list.title')}</h2>
    </section>
  );
}
