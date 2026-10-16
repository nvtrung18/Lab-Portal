import { ArrowUpRight } from 'lucide-react';
import { useLab } from './lab-provider';
import { ArticleGallery } from './article-gallery';
import SiteLink from './site-link';
import { RichTextBody } from './rich-text-body';
import { ExpandableContent } from './expandable-content';

export function DynamicSections({ sectionId, reveal = false }: { sectionId?: string; reveal?: boolean } = {}) {
  const { page, text, locale } = useLab();
  return <>{page?.sections.filter(item => item.visible !== false && (!sectionId || item.id === sectionId)).map(item =>
    <section key={item.id} id={`section-${item.id}`} className={`section editorial-section editorial-${item.presentation}`}>
      <div className={`container editorial-layout ${item.image || item.gallery?.length ? 'editorial-with-image' : ''}`}>
        <div className="editorial-copy">
          {text(item.eyebrow) && <span className="eyebrow"><span />{text(item.eyebrow)}</span>}
          <h2 id={`section-${item.id}-title`}>{text(item.title)}</h2>
          <ExpandableContent key={`${item.id}-${locale}`} title={text(item.title)} collapseTargetId={`section-${item.id}-title`} defaultExpanded={reveal}
            readMore={locale === 'vi' ? 'Đọc tiếp' : 'Read more'} showLess={locale === 'vi' ? 'Thu gọn' : 'Show less'}>
            {text(item.description) && <p className="editorial-summary">{text(item.description)}</p>}
            <RichTextBody html={item.bodyHtml} paragraphs={item.body} anchorSection={item} />
          </ExpandableContent>
          {item.linkUrl && text(item.linkLabel) && <SiteLink href={item.linkUrl} resolveTemplate={false} className="text-link">
            {text(item.linkLabel)}<ArrowUpRight size={17} />
          </SiteLink>}
        </div>
        <ArticleGallery key={`${item.id}-gallery-${locale}`} value={item} title={text(item.title)} />
      </div>
    </section>
  )}</>;
}
