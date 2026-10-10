import Link from "./site-link";
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import type { UserProfileResponse } from '../../../user/api';
import { getPrimaryRedirectPath } from '../../../auth/hooks';
import { clearAuthTokens } from '../../../../shared/api';
import { ArrowUpRight, Menu, X, Network, ArrowRight } from "lucide-react";
import { useEffect, useState } from "react";
import { useLab } from "./lab-provider";
import { Button } from "./ui/button";
export function Brand({ light = false }: { light?: boolean }) {
  const { l, profile } = useLab();
  return (
    <span className={`brand ${light ? "brand-light" : ""}`}>
      <span className="brand-symbol">
        <Network size={29} strokeWidth={1.8} />
      </span>
      <span>
        <strong>
          {profile.shortName}<span className="brand-dot">.</span>
        </strong>
        <small>{l("RESEARCH LABORATORY", "PHÒNG THÍ NGHIỆM NGHIÊN CỨU")}</small>
      </span>
    </span>
  );
}
export function SiteHeader({ user, signedIn = false, checking = false }: {
  user?: UserProfileResponse; signedIn?: boolean; checking?: boolean;
}) {
  const { locale, l, profile, loadError, preview, pages, text } = useLab();
  const { pathname, search, hash } = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const destination = user ? getPrimaryRedirectPath(user) : null;
  const canEnter = destination?.startsWith('/admin/') || destination?.startsWith('/app/');
  const admissionReturn = encodeURIComponent(`/${locale}#lab-admission`);
  const loginHref = `/login?returnUrl=${admissionReturn}`;
  const registerHref = `/register?returnUrl=${admissionReturn}`;
  const logout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate(`/${locale}`, { replace: true });
  };
  const navItems = pages.filter(page => page.showInNavigation && (text(page.navigationTitle) || text(page.title)));
  const [open, setOpen] = useState(false);
  useEffect(() => { setOpen(false); }, [pathname]);
  useEffect(() => {
    if (!open) return;
    const closeMenu = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    const desktop = window.matchMedia('(min-width: 1001px)');
    const closeOnDesktop = () => { if (desktop.matches) setOpen(false); };
    window.addEventListener('keydown', closeMenu);
    desktop.addEventListener('change', closeOnDesktop);
    return () => {
      window.removeEventListener('keydown', closeMenu);
      desktop.removeEventListener('change', closeOnDesktop);
    };
  }, [open]);
  const languagePath = (language: 'vi' | 'en') =>
    `${pathname.replace(/^\/(en|vi)(?=\/|$)/, `/${language}`)}${search}${hash}`;
  return (
    <>
      <a className="skip-link" href="#main-content">
        {l("Skip to content", "Đến nội dung")}
      </a>
      <div className="utility-bar">
        <div className={`container utility-inner${signedIn ? ' account-utility' : ''}`}>
          <span>
            {l(
              "Exploring ideas. Connecting disciplines.",
              "Khám phá ý tưởng. Kết nối lĩnh vực.",
            )}
          </span>
          <div className="utility-right">
            <span className="website-content-status">{profile.showDemoNotice && <span className="demo-dot" />}
            {loadError && !preview ? l('Content unavailable · sample preview', 'Chưa tải được nội dung · đang xem mẫu') : preview ? l('Website preview', 'Xem trước website') : profile.showDemoNotice && l(
              "Demo laboratory · Sample content",
              "Phòng lab minh họa · Dữ liệu mẫu",
            )}
            </span>
            {signedIn ? <>
              <span className="account-name">{user?.fullName || user?.email}</span>
              {checking ? <span>{l('Checking access…', 'Đang kiểm tra quyền…')}</span> : canEnter && destination ?
                <Link href={destination}>{l('Enter system', 'Vào hệ thống')}<ArrowUpRight size={12} /></Link>
                : <Link href="#lab-admission">{l('Application status', 'Hồ sơ tham gia')}</Link>}
              <button type="button" className="account-signout" onClick={logout}>{l('Sign out', 'Đăng xuất')}</button>
            </> : null}
          </div>
          <nav className="language-switch" aria-label={l('Website language', 'Ngôn ngữ website')}>
            {(['vi', 'en'] as const).map(language => (
              <Link key={language} href={languagePath(language)} resolveTemplate={false}
                hrefLang={language} lang={language}
                className={locale === language ? 'selected' : ''}
                aria-current={locale === language ? 'true' : undefined}
                aria-label={language === 'vi' ? 'Tiếng Việt' : 'English'}
                onClick={() => setOpen(false)}>
                {language.toUpperCase()}
              </Link>
            ))}
          </nav>
        </div>
      </div>
      <header className="site-header">
        <div className="container header-inner">
          <Link
            href={`/${locale}`}
            aria-label={profile.shortName}
            onClick={() => setOpen(false)}
          >
            <Brand />
          </Link>
          <nav
            className="desktop-nav"
            aria-label={l("Main navigation", "Điều hướng chính")}
          >
            {navItems.map(page => (
              <Link
                key={page.id}
                href={`/${locale}${page.slug ? '/' + page.slug : ''}`}
                resolveTemplate={false}
                className={
                  pathname === `/${locale}${page.slug ? '/' + page.slug : ''}` ||
                  (page.slug && pathname.startsWith(`/${locale}/${page.slug}/`))
                    ? "active"
                    : ""
                }
              >
                {text(page.navigationTitle) || text(page.title)}
              </Link>
            ))}
          </nav>
          <div className="header-actions">
            <div className="auth-header-actions">
              {signedIn ? checking ? <Button size="sm" disabled>{l('Checking access…', 'Đang kiểm tra quyền…')}</Button>
                : <Button asChild size="sm"><Link href={canEnter && destination ? destination : '#lab-admission'}>
                  {canEnter ? l('Enter system', 'Vào hệ thống') : l('Submit documents', 'Nộp hồ sơ')}
                  <ArrowUpRight size={15} />
                </Link></Button>
                : <>
                  <Button asChild variant="outline" size="sm"><Link href={loginHref}>{l('Sign in', 'Đăng nhập')}</Link></Button>
                  <Button asChild size="sm"><Link href={registerHref}>{l('Sign up', 'Đăng ký')}<ArrowUpRight size={15} /></Link></Button>
                </>}
            </div>
            <button
              type="button"
              className="menu-toggle"
              onClick={() => setOpen(!open)}
              aria-expanded={open}
              aria-controls="mobile-navigation"
              aria-label={open ? l("Close navigation", "Đóng điều hướng") : l("Open navigation", "Mở điều hướng")}
            >
              {open ? <X /> : <Menu />}
            </button>
          </div>
        </div>
        {open && (
          <nav
            id="mobile-navigation"
            className="mobile-nav"
            aria-label={l("Mobile navigation", "Điều hướng di động")}
          >
            {!signedIn && <>
              <Link href={loginHref} onClick={() => setOpen(false)}>{l('Sign in', 'Đăng nhập')}<ArrowUpRight size={17} /></Link>
              <Link href={registerHref} onClick={() => setOpen(false)}>{l('Sign up', 'Đăng ký')}<ArrowUpRight size={17} /></Link>
            </>}
            {pages.filter(page => (page.showInNavigation || page.template === 'join' || page.template === 'contact') && (text(page.navigationTitle) || text(page.title))).map(page => (
              <Link
                key={page.id}
                href={`/${locale}${page.slug ? '/' + page.slug : ''}`}
                resolveTemplate={false}
                onClick={() => setOpen(false)}
              >
                {text(page.navigationTitle) || text(page.title)}
                <ArrowUpRight size={17} />
              </Link>
            ))}
          </nav>
        )}
      </header>
    </>
  );
}
export function SiteFooter() {
  const { locale, l, profile, pages, text } = useLab();
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="footer-top">
          <div>
            <Link href={`/${locale}`}>
              <Brand light />
            </Link>
            <p>
              {l(
                "Curiosity drives us. Collaboration connects us.\nResearch moves us forward.",
                "Tò mò dẫn lối. Hợp tác kết nối.\nNghiên cứu đưa chúng ta tiến xa.",
              )}
            </p>
          </div>
          <div>
            <h3>{l("Explore", "Khám phá")}</h3>
            {pages.filter(page => page.showInNavigation && page.slug !== '' && (text(page.navigationTitle) || text(page.title))).slice(0, 4).map(page => (
              <Link href={`/${locale}/${page.slug}`} key={page.id} resolveTemplate={false}>
                {text(page.navigationTitle) || text(page.title)}
              </Link>
            ))}
          </div>
          <div>
            <h3>{l("Connect", "Kết nối")}</h3>
            <Link href={`/${locale}/join`}>
              {l("Join the lab", "Tham gia phòng lab")}
            </Link>
            <Link href={`/${locale}/contact`}>
              {l("Contact us", "Liên hệ")}
            </Link>
            <Link href={`/${locale}/news`}>
              {l("News & events", "Tin & sự kiện")}
            </Link>
            <Link href="/admin/website">
              {l("Website administration", "Quản trị website")}
            </Link>
          </div>
          <div className="footer-contact">
            <h3>
              {l("Let’s explore what’s next.", "Cùng khám phá tương lai.")}
            </h3>
            <Link href={`/${locale}/contact`}>
              {profile.email}
              <ArrowUpRight size={18} />
            </Link>
            <p>
              {l(
                "Hanoi, Vietnam · Illustrative location",
                "Hà Nội, Việt Nam · Địa điểm minh họa",
              )}
            </p>
          </div>
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} {profile.name}.</span>
          <span>
            {profile.showDemoNotice && l(
                "All identities, research and affiliations are fictional demo content.",
              "Mọi danh tính, nghiên cứu và liên kết đều là nội dung minh họa giả định.",
            )}
          </span>
          <a href="#main-content">
            {l("Back to top", "Về đầu trang")}
            <ArrowRight size={13} className="rotate-[-90deg]" />
          </a>
        </div>
      </div>
    </footer>
  );
}
