import { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Maximize2 } from 'lucide-react';
import type { ArticleImages } from '../../../../../../contracts/website-content';
import { articleImages } from '../../../../../../contracts/website-gallery';
import { useLab } from './lab-provider';
import SiteImage from './site-image';
import { Dialog } from './ui/dialog';

export function ArticleGallery({ value, title, frameClass = 'editorial-image', eager = false }: {
  value: ArticleImages; title: string; frameClass?: string; eager?: boolean;
}) {
  const { locale } = useLab();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const images = articleImages(value);
  const [selected, setSelected] = useState(0);
  const [open, setOpen] = useState(false);
  const touch = useRef<{ x: number; y: number } | null>(null);
  const index = Math.min(selected, Math.max(0, images.length - 1));
  const current = images[index];
  const step = (delta: number) => setSelected(previous => (Math.min(previous, images.length - 1) + delta + images.length) % images.length);
  useEffect(() => {
    if (!open) return;
    if (!images.length) { setOpen(false); return; }
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previous; };
  }, [open, images.length]);
  if (!current) return null;
  const position = `${index + 1}/${images.length}`;
  const alt = current.alt[locale] || title;
  return <div className="article-gallery">
    <div className={`${frameClass} gallery-main`}>
      <button type="button" className="gallery-open" aria-label={`${l('Xem ảnh lớn', 'Enlarge image')}: ${title} (${position})`}
        aria-haspopup="dialog" onClick={() => setOpen(true)}>
        <SiteImage src={current.src} alt={alt} loading={eager ? 'eager' : 'lazy'} fetchPriority={eager ? 'high' : undefined} />
        <span className="gallery-indicator"><Maximize2 size={16} aria-hidden="true" />{images.length > 1 && position}</span>
      </button>
    </div>
    {current.caption[locale] && <p className="gallery-caption">{current.caption[locale]}</p>}
    {images.length > 1 && <div className="gallery-thumbnails" role="group" aria-label={l('Chọn ảnh', 'Choose an image')}>
      {images.map((image, imageIndex) => <button type="button" key={`${imageIndex}-${image.id}`}
        aria-pressed={index === imageIndex} aria-label={`${l('Ảnh', 'Image')} ${imageIndex + 1}/${images.length}: ${image.alt[locale] || title}`}
        onClick={() => setSelected(imageIndex)}>
        <SiteImage src={image.src} alt="" loading="lazy" />
      </button>)}
    </div>}
    <Dialog open={open} onOpenChange={setOpen} title={title} className="gallery-lightbox"
      onKeyDown={event => {
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault(); step(event.key === 'ArrowLeft' ? -1 : 1);
        }
      }}>
      {open && <><div className="gallery-lightbox-stage" onTouchStart={event => { const point = event.touches[0]; touch.current = { x: point.clientX, y: point.clientY }; }}
        onTouchEnd={event => {
          const point = event.changedTouches[0];
          if (touch.current && Math.abs(point.clientX - touch.current.x) > 50 && Math.abs(point.clientY - touch.current.y) < 80) step(point.clientX < touch.current.x ? 1 : -1);
          touch.current = null;
        }}>
        <SiteImage src={current.src} alt={alt} loading="eager" />
      </div>
      <div className="gallery-lightbox-controls">
        {images.length > 1 && <button type="button" onClick={() => step(-1)} aria-label={l('Ảnh trước', 'Previous image')}><ChevronLeft size={22} /></button>}
        <span role="status" aria-live="polite">{position}</span>
        {images.length > 1 && <button type="button" onClick={() => step(1)} aria-label={l('Ảnh sau', 'Next image')}><ChevronRight size={22} /></button>}
      </div>
      {current.caption[locale] && <p className="gallery-lightbox-caption">{current.caption[locale]}</p>}</>}
    </Dialog>
  </div>;
}
