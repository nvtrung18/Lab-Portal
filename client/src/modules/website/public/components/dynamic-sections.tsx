import { ArrowUpRight } from 'lucide-react';
import { useLab } from './lab-provider';
import SiteImage from './site-image';
import SiteLink from './site-link';
import { RichTextBody } from './rich-text-body';

export function DynamicSections() {
  const { page, text } = useLab();
  return <>{page?.sections.filter(item => item.visible !== false).map(item =>
    <section key={item.id} id={`section-${item.id}`} className={`section editorial-section editorial-${item.presentation}`}>
      <div className={`container editorial-layout ${item.image ? 'editorial-with-image' : ''}`}>
        <div className="editorial-copy">
          {text(item.eyebrow) && <span className="eyebrow"><span />{text(item.eyebrow)}</span>}
          <h2>{text(item.title)}</h2>
          {text(item.description) && <p className="editorial-summary">{text(item.description)}</p>}
          <RichTextBody html={item.bodyHtml} paragraphs={item.body} />
          {item.linkUrl && text(item.linkLabel) && <SiteLink href={item.linkUrl} resolveTemplate={false} className="text-link">
            {text(item.linkLabel)}<ArrowUpRight size={17} />
          </SiteLink>}
        </div>
        {item.image && <div className="editorial-image"><SiteImage src={item.image} alt={text(item.title)} loading="lazy" /></div>}
      </div>
    </section>
  )}</>;
}
