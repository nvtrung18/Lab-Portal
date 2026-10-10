import { Link } from 'react-router-dom';
import { forwardRef, type ComponentProps } from 'react';
import { useLab } from './lab-provider';

const templates = new Set(['about', 'research', 'people', 'publications', 'projects', 'news', 'join', 'contact']);
const SiteLink = forwardRef<HTMLAnchorElement, Omit<ComponentProps<'a'>, 'href'> & { href: string; resolveTemplate?: boolean }>(function SiteLink({ href, resolveTemplate = true, ...props }, ref) {
  const { preview, pages, page: currentPage } = useLab();
  if (href.startsWith('/')) {
    const url = new URL(href, 'http://website.local');
    const parts = url.pathname.split('/');
    if ((parts[1] === 'en' || parts[1] === 'vi') && resolveTemplate && templates.has(parts[2])) {
      const page = currentPage?.template === parts[2] ? currentPage : pages.find(item => item.template === parts[2]);
      if (!page) return null;
      parts[2] = page.slug || (parts.length > 3 ? '_' : '');
      url.pathname = parts.filter((part, index) => index === 0 || part !== '').join('/');
    }
    if (preview && (parts[1] === 'en' || parts[1] === 'vi')) url.searchParams.set('preview', '1');
    href = `${url.pathname}${url.search}${url.hash}`;
  }
  if (/^(https?:|mailto:|tel:|#)/.test(href)) return <a ref={ref} href={href} {...props} />;
  return <Link ref={ref} to={href} {...props} />;
});
export default SiteLink;
