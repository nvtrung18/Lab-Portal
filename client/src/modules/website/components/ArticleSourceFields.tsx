import type { ArticleSource } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { TextField } from './EditorFields';

export function ArticleSourceFields({ value, onChange }: {
  value: ArticleSource; onChange: (patch: ArticleSource) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  return <fieldset className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
    <legend className="px-2 text-sm font-semibold">{l('Nguồn / nội dung gốc (tùy chọn)', 'Source / original content (optional)')}</legend>
    <p className="text-sm text-slate-500">{l('Bổ sung nguồn và link bài báo, công bố hoặc trang dự án gốc nếu có. Nội dung do lab tự viết có thể để trống. Link sẽ hiển thị trên trang chi tiết.', 'Add the source and original article, publication or project page link if available. Leave it blank for original lab content. The link appears on the detail page.')}</p>
    <TextField label={l('Tên nguồn / báo / website', 'Source / outlet / website name')} value={value.sourceName ?? ''} onChange={sourceName => onChange({ sourceName })} />
    <TextField label={l('Link gốc (http:// hoặc https://)', 'Original URL (http:// or https://)')} type="url" value={value.sourceUrl ?? ''} onChange={sourceUrl => onChange({ sourceUrl })} />
  </fieldset>;
}
