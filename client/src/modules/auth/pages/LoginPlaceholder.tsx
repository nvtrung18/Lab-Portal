import { useI18n } from '../../../shared/i18n';

export function LoginPlaceholder() {
  const { t } = useI18n();
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-semibold text-slate-950">{t('auth.loginTitle')}</h1>
    </section>
  );
}
