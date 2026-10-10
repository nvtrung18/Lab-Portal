import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import type { WebsiteSection } from '../../../../../contracts/website-content';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { createSection } from '../content';
import { BilingualField, BilingualList, ImageField, SelectField, TextField } from './EditorFields';

export function PageSectionsEditor({ sections, onChange }: { sections: WebsiteSection[]; onChange: (sections: WebsiteSection[]) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  function update(next: WebsiteSection) { onChange(sections.map(item => item.id === next.id ? next : item)); }
  function move(index: number, offset: number) {
    const next = [...sections];
    [next[index], next[index + offset]] = [next[index + offset], next[index]];
    onChange(next);
  }
  return <div className="space-y-5">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h3 className="text-lg font-semibold">{l('Các mục nội dung', 'Content sections')}</h3>
        <p className="mt-1 text-sm text-slate-500">{l('Tự đặt tiêu đề và nội dung. Các mục xuất hiện ngay dưới phần giới thiệu trang, theo thứ tự bên dưới.', 'Choose your own headings and content. Sections appear below the page introduction in the order shown.')}</p></div>
      <Button size="sm" onClick={() => onChange([...sections, createSection()])}><Plus size={16} />{l('Thêm mục', 'Add section')}</Button>
    </div>
    {sections.map((item, index) => <details key={item.id} open className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
      <summary className="cursor-pointer break-words font-semibold">{index + 1}. {item.title[locale] || l('Mục chưa có tiêu đề', 'Untitled section')} · {item.presentation === 'featured' ? l('Nổi bật', 'Featured') : item.visible === false ? l('Đang ẩn', 'Hidden') : l('Nội dung', 'Content')}</summary>
      <div className="mt-5 space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={item.visible !== false} onChange={event => update({ ...item, visible: event.target.checked })} />{l('Hiển thị mục này', 'Show this section')}</label>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" disabled={index === 0} aria-label={l('Đưa mục lên', 'Move section up')} onClick={() => move(index, -1)}><ArrowUp size={16} /></Button>
            <Button size="sm" variant="outline" disabled={index === sections.length - 1} aria-label={l('Đưa mục xuống', 'Move section down')} onClick={() => move(index, 1)}><ArrowDown size={16} /></Button>
            <Button size="sm" variant="outline" onClick={() => onChange(sections.filter(section => section.id !== item.id))}><Trash2 size={16} />{l('Bỏ khỏi bản nháp', 'Remove from draft')}</Button>
          </div>
        </div>
        <SelectField label={l('Cách trình bày', 'Presentation')} value={item.presentation} allowEmpty={false}
          options={[{ value: 'standard', label: l('Tiêu đề và nội dung', 'Standard') }, { value: 'featured', label: l('Nổi bật với nền đậm', 'Featured, dark background') }, { value: 'split', label: l('Nội dung và ảnh hai cột', 'Text and image columns') }]}
          onChange={presentation => update({ ...item, presentation: presentation as WebsiteSection['presentation'] })} />
        <BilingualField label={l('Dòng nhấn phía trên', 'Eyebrow caption')} value={item.eyebrow} onChange={eyebrow => update({ ...item, eyebrow })} />
        <BilingualField label={l('Tiêu đề mục', 'Section heading')} value={item.title} onChange={title => update({ ...item, title })} />
        <BilingualField label={l('Đoạn giới thiệu', 'Summary')} value={item.description} multiline onChange={description => update({ ...item, description })} />
        <BilingualList label={l('Đoạn nội dung', 'Paragraph')} value={item.body} onChange={body => update({ ...item, body })} />
        <ImageField label={l('Ảnh minh họa', 'Section image')} value={item.image} onChange={image => update({ ...item, image })} />
        <BilingualField label={l('Nhãn nút liên kết', 'Action label')} value={item.linkLabel} onChange={linkLabel => update({ ...item, linkLabel })} />
        <TextField label={l('Đường dẫn nút: /vi/..., #muc hoặc https://...', 'Action URL: /en/..., #anchor or https://...')} value={item.linkUrl} onChange={linkUrl => update({ ...item, linkUrl })} />
      </div>
    </details>)}
    {!sections.length && <p className="rounded-lg border border-dashed border-slate-300 p-6 text-sm text-slate-500">{l('Chưa có mục bổ sung. Bấm “Thêm mục” để tạo nội dung theo hướng của lab.', 'No additional sections. Choose “Add section” to create your own content.')}</p>}
  </div>;
}
