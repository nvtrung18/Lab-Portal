import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import type { LabData, Locale, LocalizedText, SiteProfile, WebsiteContent, WebsitePage } from '../lib/types';
import { withPages } from '../../content';
import { memberRoleGroups } from '../../member-roles';
interface LabContext {
  locale: Locale; data: LabData; profile: SiteProfile; media: Record<string, string>;
  preview: boolean; loadError: boolean;
  pages: WebsitePage[]; page: WebsitePage | undefined;
  text: (value: LocalizedText) => string;
  l: (en: string, vi: string) => string;
}
const Context = createContext<LabContext | null>(null);
export function LabProvider({ locale, initialContent, loadError, children }: {
  locale: Locale; initialContent: WebsiteContent; loadError: boolean; children: ReactNode;
}) {
  const [searchParams] = useSearchParams();
  const { pathname } = useLocation();
  const preview = searchParams.get('preview') === '1';
  const [previewContent, setPreviewContent] = useState<WebsiteContent | null>(null);
  const content = withPages(preview && previewContent ? previewContent : initialContent);
  const pages = content.pages.filter(item => item.visible !== false);
  const routeSlug = pathname.split('/')[2] ?? '';
  const page = pages.find(item => item.slug === (routeSlug === '_' ? '' : routeSlug));
  useEffect(() => {
    const previous = document.documentElement.lang;
    document.documentElement.lang = locale;
    return () => { document.documentElement.lang = previous; };
  }, [locale]);
  useEffect(() => {
    if (!preview) return;
    const host = window.parent !== window ? window.parent : window.opener;
    if (!host) return;
    const origin = window.location.origin;
    const receive = (event: MessageEvent) => {
      if (event.source !== host || event.origin !== origin) return;
      if (event.data?.type !== 'labportal:website-preview' || !event.data.content?.data) return;
      setPreviewContent(event.data.content as WebsiteContent);
    };
    window.addEventListener('message', receive);
    host.postMessage({ type: 'labportal:website-preview-ready' }, origin);
    return () => window.removeEventListener('message', receive);
  }, [preview]);
  const data: LabData = {
    research: content.data.research.filter(item => item.visible !== false),
    members: content.data.members.filter(item => item.visible !== false),
    publications: content.data.publications.filter(item => item.visible !== false),
    projects: content.data.projects.filter(item => item.visible !== false),
    news: content.data.news.filter(item => item.visible !== false),
  };
  return <Context.Provider value={{
    locale, data, profile: content.profile, media: content.media, preview, loadError, pages, page,
    text: value => value[locale],
    l: (en, vi) => content.copy[en]?.[locale] ?? (locale === 'vi' ? vi ?? '' : en),
  }}>{children}</Context.Provider>;
}
export function useLab() {
  const context = useContext(Context);
  if (!context) throw new Error('LabProvider is required');
  return context;
}
export const roleVi: Record<string, string> = {
  Faculty: 'Giảng viên', Researcher: 'Nhà nghiên cứu', 'PhD student': 'Nghiên cứu sinh',
  'MSc student': 'Học viên cao học', Undergraduate: 'Sinh viên', Alumni: 'Cựu thành viên',
  ...Object.fromEntries(memberRoleGroups.map(group => [group.role, group.label.vi])),
  Journal: 'Tạp chí', Conference: 'Hội nghị', Book: 'Sách', Preprint: 'Bản thảo',
  Ongoing: 'Đang thực hiện', Completed: 'Hoàn thành', News: 'Tin tức', Seminar: 'Hội thảo',
  Achievement: 'Thành tựu', Event: 'Sự kiện',
};
