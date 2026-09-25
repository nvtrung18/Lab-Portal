import { useI18n } from '../i18n';

export function LanguageSwitcher() {
  const { locale, setLocale, t } = useI18n();
  const nextLocale = locale === 'vi' ? 'en' : 'vi';

  return (
    <button
      aria-label={nextLocale === 'en' ? t('common.switchToEnglish') : t('common.switchToVietnamese')}
      className="app-language-toggle app-control-surface inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border text-xs font-bold uppercase tracking-wide shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
      title={nextLocale === 'en' ? t('common.switchToEnglish') : t('common.switchToVietnamese')}
      type="button"
      onClick={() => setLocale(nextLocale)}
    >
      <span aria-hidden="true">{locale === 'vi' ? 'Vi' : 'En'}</span>
    </button>
  );
}
