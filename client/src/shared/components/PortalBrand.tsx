import { Link } from 'react-router-dom';

import { useI18n } from '../i18n';
import { LAB_BRAND } from '../constants';

/** Shared ALELA identity for authentication and portal navigation. */
export function PortalBrand({ subtitle, onNavigate, tabIndex }: {
  subtitle?: string;
  onNavigate?: () => void;
  tabIndex?: number;
}) {
  const { locale } = useI18n();
  return (
    <Link className="portal-brand" to={`/${locale}`} onClick={onNavigate} tabIndex={tabIndex} aria-label={LAB_BRAND.name}>
      <img className="portal-brand-logo" src={LAB_BRAND.logoSrc} alt="" width={1774} height={887} />
      <span className="min-w-0">
        <span className="portal-brand-name" title={LAB_BRAND.name}>{LAB_BRAND.shortName}</span>
        {subtitle ? <span className="portal-brand-subtitle">{subtitle}</span> : null}
      </span>
    </Link>
  );
}
