import { ArrowUpRight, Network } from 'lucide-react';
import { Link, Outlet } from 'react-router-dom';

import { LanguageSwitcher, PortalBrand, ThemeToggle } from '../shared/components';
import { useI18n } from '../shared/i18n';
import { LAB_BRAND } from '../shared/constants';

export function AuthLayout() {
  const { locale } = useI18n();
  const vi = locale === 'vi';
  return (
    <main className="app-shell auth-shell">
      <header className="auth-topbar">
        <PortalBrand subtitle={vi ? 'Nghiên cứu & cộng tác' : 'Research & collaboration'} />
        <div className="auth-topbar-actions">
          <Link className="auth-website-link" to={`/${locale}`} aria-label={vi ? 'Website giới thiệu' : 'Lab website'}>
            <span>{vi ? 'Website giới thiệu' : 'Lab website'}</span><ArrowUpRight aria-hidden="true" size={18} />
          </Link>
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>
      <div className="auth-grid">
        <aside className="auth-story">
          <img className="auth-story-image" src="/lab-website/images/chip.jpg" alt="" />
          <div>
            <p className="auth-story-eyebrow">{LAB_BRAND.name}</p>
            <h2>{LAB_BRAND.slogan}</h2>
            <p className="auth-story-description">{LAB_BRAND.summary[locale]}</p>
          </div>
          <div className="auth-story-footer"><Network aria-hidden="true" size={22} strokeWidth={1.5} /><span>{vi ? 'Nghiên cứu & cộng tác' : 'Research & collaboration'}</span></div>
        </aside>
        <div className="auth-form-pane">
          <div className="auth-form-container"><Outlet /></div>
        </div>
      </div>
    </main>
  );
}
