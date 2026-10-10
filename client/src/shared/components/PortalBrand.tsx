import { Network } from 'lucide-react';
import { Link } from 'react-router-dom';

import { useI18n } from '../i18n';

/** The public site's visual mark, with the portal's existing application name. */
export function PortalBrand({ subtitle, onNavigate, tabIndex }: {
  subtitle?: string;
  onNavigate?: () => void;
  tabIndex?: number;
}) {
  const { locale, t } = useI18n();
  return (
    <Link className="portal-brand" to={`/${locale}`} onClick={onNavigate} tabIndex={tabIndex}>
      <span className="portal-brand-mark"><Network aria-hidden="true" size={27} strokeWidth={1.5} /></span>
      <span className="min-w-0">
        <span className="portal-brand-name">{t('common.appTitle')}</span>
        {subtitle ? <span className="portal-brand-subtitle">{subtitle}</span> : null}
      </span>
    </Link>
  );
}
