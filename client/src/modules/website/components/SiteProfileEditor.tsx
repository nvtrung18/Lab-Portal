import type { SiteProfile, WebsiteContent } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { BilingualField, ImageField, TextField } from './EditorFields';

export function SiteProfileEditor({ content, onChange }: { content: WebsiteContent; onChange: (content: WebsiteContent) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const update = (patch: Partial<SiteProfile>) => onChange({ ...content, profile: { ...content.profile, ...patch } });
  return <div className="space-y-7">
    <div className="grid gap-5 md:grid-cols-2">
      <TextField label={l('Tên đầy đủ của lab', 'Full laboratory name')} value={content.profile.name} onChange={name => update({ name })} />
      <TextField label={l('Tên trên logo', 'Logo name')} value={content.profile.shortName} onChange={shortName => update({ shortName })} />
      <TextField label={l('Email liên hệ công khai', 'Public contact email')} type="email" value={content.profile.email} onChange={email => update({ email })} />
      <TextField label={l('Chú thích địa điểm trên banner', 'Banner location caption')} value={content.profile.locationLabel} onChange={locationLabel => update({ locationLabel })} />
      <TextField label={l('Chú thích tọa độ trên banner', 'Banner coordinate caption')} value={content.profile.coordinates} onChange={coordinates => update({ coordinates })} />
    </div>
    <BilingualField label={l('Mô tả website (metadata)', 'Website description (metadata)')} value={content.profile.summary} multiline onChange={summary => update({ summary })} />
    <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={content.profile.showDemoNotice} onChange={e => update({ showDemoNotice: e.target.checked })} />{l('Hiển thị ghi chú dữ liệu mẫu của website', 'Display the template sample content notices')}</label>
    <div className="space-y-6 border-t border-slate-200 pt-6 dark:border-slate-700">
      <h3 className="font-semibold">{l('Ảnh dùng chung trong mẫu', 'Template images')}</h3>
      {Object.entries(content.media).map(([key, value]) => <ImageField key={key} label={key.split('/').pop() ?? key} value={value} onChange={url => onChange({ ...content, media: { ...content.media, [key]: url } })} />)}
    </div>
  </div>;
}
