import type { ArticleSource } from '../../../../../../contracts/website-content';
import { ArrowUpRight } from 'lucide-react';
import { useLab } from './lab-provider';
import { Button } from './ui/button';

export function ArticleSourceDetails({ value }: { value: ArticleSource }) {
  const { l } = useLab();
  return <>
    {value.sourceName?.trim() && <dl>
      <dt>{l('Source', 'Nguồn nội dung')}</dt>
      <dd>{value.sourceName}</dd>
    </dl>}
    {value.sourceUrl && /^https?:\/\/\S+$/.test(value.sourceUrl) && <Button asChild>
      <a href={value.sourceUrl} target="_blank" rel="noopener noreferrer">
        {l('View original source', 'Xem nguồn gốc')}
        <ArrowUpRight size={16} />
      </a>
    </Button>}
  </>;
}
