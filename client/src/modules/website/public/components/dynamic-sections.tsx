import { ArrowUpRight } from 'lucide-react';
import { useLab } from './lab-provider';
import SiteImage from './site-image';
import SiteLink from './site-link';
import { RichTextBody } from './rich-text-body';
import { ExpandableContent } from './expandable-content';

export function DynamicSections() {
  const { page, text, locale } = useLab();
  return <>{page?.sections.filter(item => item.visible !== false).map(item =>
    <section key={item.id} id={`section-${item.id}`} className={`section editorial-section editorial-${item.presentation}`}>
      <div className={`container editorial-layout ${item.image ? 'editorial-with-image' : ''}`}>
        <div className="editorial-copy">
          {text(item.eyebrow) && <span className="eyebrow"><span />{text(item.eyebrow)}</span>}
          <h2 id={`section-${item.id}-title`}>{text(item.title)}</h2>
          <ExpandableContent key={`${item.id}-${locale}`} title={text(item.title)} collapseTargetId={`section-${item.id}-title`}
            readMore={locale === 'vi' ? 'Đọc tiếp' : 'Read more'} showLess={locale === 'vi' ? 'Thu gọn' : 'Show less'}>
            {text(item.description) && <p className="editorial-summary">{text(item.description)}</p>}
            <RichTextBody html={item.bodyHtml} paragraphs={item.body} />
          </ExpandableContent>
          {item.linkUrl && text(item.linkLabel) && <SiteLink href={item.linkUrl} resolveTemplate={false} className="text-link">
            {text(item.linkLabel)}<ArrowUpRight size={17} />
          </SiteLink>}
        </div>
        {item.image && <div className="editorial-image"><SiteImage src={item.image} alt={text(item.title)} loading="lazy" /></div>}
      </div>
    </section>
  )}</>;
}
