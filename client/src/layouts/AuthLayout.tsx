import { ArrowUpRight, Network } from 'lucide-react';
import { Link, Outlet } from 'react-router-dom';

import { LanguageSwitcher, PortalBrand, ThemeToggle } from '../shared/components';
import { useI18n } from '../shared/i18n';

export function AuthLayout() {
  const { locale, t } = useI18n();
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
            <p className="auth-story-eyebrow">{t('common.appTitle')}</p>
            <h2>{vi ? 'Khám phá ý tưởng.' : 'Exploring ideas.'}<br /><span>{vi ? 'Kết nối lĩnh vực.' : 'Connecting disciplines.'}</span></h2>
            <p className="auth-story-description">{vi ? 'Không gian dành cho các nhóm nghiên cứu, đề tài và hoạt động của phòng lab.' : 'A workspace for research groups, projects and laboratory activities.'}</p>
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
