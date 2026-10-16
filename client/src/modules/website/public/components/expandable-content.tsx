import { useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { scrollToEditorialTarget } from '../../editorial-search';

/** Measure the rendered content rather than truncating CMS HTML or guessing its length. */
export function ExpandableContent({ children, title, readMore, showLess, collapseTargetId, defaultExpanded = false }: {
  children: ReactNode; title: string; readMore: string; showLess: string; collapseTargetId?: string;
  defaultExpanded?: boolean;
}) {
  const id = useId();
  const root = useRef<HTMLDivElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [size, setSize] = useState<{ full: number; collapsed: number; hasList: boolean } | null>(null);

  useLayoutEffect(() => {
    const element = content.current;
    if (!element) return;
    let active = true;
    const measure = () => {
      if (!active) return;
      const firstText = element.querySelector('p, li') ?? element;
      const style = window.getComputedStyle(firstText);
      const lineHeight = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.8;
      const next = { full: element.getBoundingClientRect().height, collapsed: lineHeight * 5, hasList: Boolean(element.querySelector('ol, ul')) };
      setSize(previous => previous && Math.abs(previous.full - next.full) < .5
        && Math.abs(previous.collapsed - next.collapsed) < .5 && previous.hasList === next.hasList ? previous : next);
    };
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(element);
    window.addEventListener('resize', measure);
    element.addEventListener('load', measure, true);
    void document.fonts?.ready.then(measure);
    return () => {
      active = false;
      observer?.disconnect();
      window.removeEventListener('resize', measure);
      element.removeEventListener('load', measure, true);
    };
  }, [children]);

  // A five-line crop can bisect a citation, list marker or DOI. Semantic lists stay complete.
  const overflows = size !== null && !size.hasList && size.full > size.collapsed + 1;
  const unrestricted = expanded || (size !== null && !overflows);
  const label = expanded ? showLess : readMore;
  return <div ref={root} className="expandable-content">
    <div id={id} className={`expandable-content-viewport${unrestricted ? ' is-unrestricted' : ''}`}
      style={{ maxHeight: unrestricted ? 'none' : size?.collapsed ?? '9em' }}
      onFocusCapture={event => {
        // A keyboard user reaching a clipped link must be able to see its focus.
        if (!expanded && overflows && size && content.current
          && event.target.getBoundingClientRect().bottom - content.current.getBoundingClientRect().top > size.collapsed) {
          setExpanded(true);
        }
      }}>
      <div ref={content} className="expandable-content-inner">{children}</div>
    </div>
    {overflows && <button type="button" className="expandable-content-toggle"
      aria-expanded={expanded} aria-controls={id} aria-label={`${label}: ${title}`}
      onClick={() => {
        // Keep the article in view when collapsing a long body from its bottom.
        if (expanded && root.current && root.current.getBoundingClientRect().top < 0) {
          const anchor = collapseTargetId ? document.getElementById(collapseTargetId) : null;
          scrollToEditorialTarget(anchor ?? root.current);
        }
        setExpanded(value => !value);
      }}>
      {label}<ChevronDown size={16} aria-hidden="true" className={expanded ? 'is-expanded' : ''} />
    </button>}
  </div>;
}
