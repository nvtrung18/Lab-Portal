import type { WebsiteSection } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { BilingualField, SelectField, TextField } from './EditorFields';
import { GalleryEditor } from './GalleryEditor';
import { BilingualRichTextField } from './BilingualRichTextField';

export function SectionEditor({ value, onChange, onBusyChange }: { value: WebsiteSection; onChange: (value: WebsiteSection) => void; onBusyChange?: (busy: boolean) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const update = (patch: Partial<WebsiteSection>) => onChange({ ...value, ...patch });
  return <div className="space-y-5">
    <BilingualField label={l('Tiêu đề nội dung', 'Content heading')} value={value.title} onChange={title => update({ title })} />
    <SelectField label={l('Cách trình bày', 'Presentation')} value={value.presentation} allowEmpty={false}
      options={[{ value: 'standard', label: l('Văn bản', 'Text') }, { value: 'split', label: l('Ảnh và nội dung hai cột', 'Image and text columns') }, { value: 'featured', label: l('Khối nổi bật', 'Featured block') }]}
      onChange={presentation => update({ presentation: presentation as WebsiteSection['presentation'] })} />
    <BilingualField label={l('Dòng nhấn', 'Eyebrow caption')} value={value.eyebrow} onChange={eyebrow => update({ eyebrow })} />
    <BilingualField label={l('Đoạn giới thiệu', 'Summary')} value={value.description} multiline onChange={description => update({ description })} />
    <BilingualRichTextField label={l('Nội dung chính', 'Main content')} value={value.bodyHtml} paragraphs={value.body} onChange={bodyHtml => update({ bodyHtml, body: [] })} />
    <GalleryEditor value={value} onChange={update} onBusyChange={onBusyChange} />
    <BilingualField label={l('Nhãn nút liên kết', 'Action label')} value={value.linkLabel} onChange={linkLabel => update({ linkLabel })} />
    <TextField label={l('Đường dẫn liên kết', 'Action URL')} value={value.linkUrl} onChange={linkUrl => update({ linkUrl })} />
    <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={value.visible !== false} onChange={event => update({ visible: event.target.checked })} />{l('Cho phép hiển thị trên các tab đã chọn', 'Allow display in selected tabs')}</label>
  </div>;
}
