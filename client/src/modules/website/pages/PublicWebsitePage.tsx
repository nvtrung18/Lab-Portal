import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { getPublishedWebsite } from '../api';
import { demoContent } from '../content';
import { LabProvider, useLab } from '../public/components/lab-provider';
import { SiteFooter, SiteHeader } from '../public/components/site-shell';
import { PageHeading } from '../public/components/shared';
import { HomeView } from '../public/components/views/home';
import { AboutView, ContactView } from '../public/components/views/information';
import { NewsView, PeopleView, ProjectsView, PublicationsView, ResearchView } from '../public/components/views/explore';
import { ProductsView } from '../public/components/views/products';
import { DetailView } from '../public/components/views/detail';
import { getAuthToken } from '../../../shared/api';
import { useCurrentUser } from '../../user/hooks';
import { AdmissionPanel } from '../public/components/admission-panel';
import { LAB_BRAND } from '../../../shared/constants';
import '../public/public.css';

export function PublicWebsitePage() {
  const { publicLocale, pageSlug, recordId } = useParams();
  const [search] = useSearchParams();
  const validLocale = publicLocale === 'vi' || publicLocale === 'en';
  const preview = search.get('preview') === '1';
  const signedIn = Boolean(getAuthToken());
  const account = useCurrentUser({ enabled: validLocale && signedIn && !preview, staleTime: 0, refetchInterval: 30000 });
  const user = !account.isError && signedIn && !preview ? account.data : undefined;
  const published = useQuery({ queryKey: ['public', 'website'],
    queryFn: ({ signal }) => getPublishedWebsite(signal), enabled: validLocale && search.get('preview') !== '1',
    staleTime: 0, refetchOnMount: 'always', retry: false });
  if (!validLocale) return <Navigate to="/404" replace />;
  if (pageSlug === 'admin') return <Navigate to="/admin/website" replace />;
  return <div className="lab-public">
    <LabProvider locale={publicLocale} initialContent={published.data?.content ?? demoContent} loadError={published.isError}>
      <SiteHeader user={user} signedIn={signedIn && !preview} checking={signedIn && account.isLoading} />
      <main id="main-content"><PublicContent recordId={recordId} />
        <AdmissionPanel user={user} signedIn={signedIn} checking={signedIn && account.isLoading}
          failed={signedIn && account.isError} onRefresh={() => { void account.refetch(); }} />
      </main>
      <SiteFooter />
    </LabProvider>
  </div>;
}

function PublicContent({ recordId }: { recordId?: string }) {
  const { page, text, locale } = useLab();
  useEffect(() => {
    const previous = document.title;
    const title = page?.template === 'home' ? LAB_BRAND.slogan
      : page?.template === 'about' ? text(page.navigationTitle) : page ? text(page.title) : '';
    document.title = [title, LAB_BRAND.name].filter(Boolean).join(' | ');
    return () => { document.title = previous; };
  }, [page, locale, text]);
  if (!page) return <section className="section"><div className="container missing-record"><h1>{locale === 'vi' ? 'Trang chưa được xuất bản hoặc đã được ẩn.' : 'This page is unpublished or hidden.'}</h1></div></section>;
  if (recordId) {
    if (!['research', 'people', 'publications', 'projects', 'news'].includes(page.template)) return <Navigate to="/404" replace />;
    return <DetailView section={page.template} id={recordId} />;
  }
  switch (page.template) {
    case 'home': return <HomeView />;
    case 'about': return <AboutView />;
    case 'research': return <ResearchView />;
    case 'people': return <PeopleView />;
    case 'publications': return <PublicationsView />;
    case 'projects': return <ProjectsView />;
    case 'products': return <ProductsView />;
    case 'news': return <NewsView />;
    case 'join': return null;
    case 'contact': return <ContactView />;
    case 'custom': return <PageHeading eyebrow={text(page.navigationTitle) || text(page.title)} title={text(page.title)} description={text(page.description)} />;
  }
}
