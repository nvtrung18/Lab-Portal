import { useState } from 'react';
import { Image as ImageIcon, Eye, Pencil } from 'lucide-react';
import type { WebsiteContent, WebsiteHero, WebsitePage } from '../../../../../contracts/website-content';
import { isHeroUrl, resolveHomeHero, withHomeHero } from '../../../../../contracts/website-hero';
import { Button, Modal } from '../../../shared/components';
import { LAB_BRAND } from '../../../shared/constants';
import { useI18n } from '../../../shared/i18n';
import { BilingualField, ImageField } from './EditorFields';
import { BilingualRichTextField } from './BilingualRichTextField';

export function HeroEditor({ content, page, onChange, onPreview }: {
  content: WebsiteContent; page: WebsitePage; onChange: (content: WebsiteContent) => void;
  onPreview: (content: WebsiteContent, path: string) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [editing, setEditing] = useState<WebsiteHero | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const update = (patch: Partial<WebsiteHero>) => setEditing(current => current ? { ...current, ...patch } : current);
  const previewPath = page.slug ? `/${encodeURIComponent(page.slug)}` : '';
  function validate() {
    if (!editing || uploading) return false;
    if (Object.values(editing.descriptionHtml ?? {}).some(html => html.length > 200000)) {
      setError(l('Mô tả có định dạng tối đa 200.000 ký tự HTML cho mỗi ngôn ngữ.', 'Formatted descriptions allow up to 200,000 HTML characters per language.')); return false;
    }
    if ([editing.primaryCta.url.vi, editing.primaryCta.url.en, editing.secondaryCta.url.vi, editing.secondaryCta.url.en].some(url => url.length > 2000 || !isHeroUrl(url))) {
      setError(l('URL của nút phải là https://…, http://…, đường dẫn /… hoặc #mục; có thể để trống để ẩn nút.', 'Button URLs must use https://…, http://…, an internal /… path or #anchor; leave blank to hide a button.')); return false;
    }
    if (editing.backgroundImage.length > 2000 || !/^(?:$|\/(?!\/)[^\s]*|https?:\/\/[^\s]+)$/.test(editing.backgroundImage)) {
      setError(l('Đường dẫn ảnh nền phải là đường dẫn nội bộ /… hoặc URL HTTP(S).', 'The background image must use an internal /… path or an HTTP(S) URL.')); return false;
    }
    setError(''); return true;
  }
  return <section className="space-y-4 rounded-lg border border-slate-200 p-5 dark:border-slate-700" aria-label={l('Hero / Banner giới thiệu', 'Homepage hero / banner')}>
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><h3 className="flex items-center gap-2 text-lg font-semibold"><ImageIcon size={20} />{l('Hero / Banner giới thiệu', 'Homepage hero / banner')}</h3>
        <p className="mt-2 max-w-2xl text-sm text-slate-500">{page.hero == null
          ? l('Đang dùng nội dung và ảnh mặc định hiện tại. Chỉnh sửa chỉ được đưa vào bản nháp sau khi Áp dụng.', 'Using the current default text and image. Edits enter the working draft only after Apply.')
          : l('Hero đã có cấu hình riêng trong bản nháp. Website công khai chỉ thay đổi sau khi Xuất bản.', 'This draft has a custom Hero. The public website changes only after Publish.')}</p></div>
      <Button variant="outline" onClick={() => { setError(''); setEditing(resolveHomeHero(page.hero, LAB_BRAND, content.copy, content.pages)); }}><Pencil size={16} />{l('Chỉnh sửa Hero / Banner giới thiệu', 'Edit homepage Hero / banner')}</Button>
    </div>
    {editing && <Modal title={l('Chỉnh sửa Hero / Banner giới thiệu', 'Edit homepage Hero / banner')} size="full" closeDisabled={uploading} onClose={() => { if (!uploading) setEditing(null); }}>
      <form onSubmit={event => { event.preventDefault(); if (validate() && editing) { onChange(withHomeHero(content, page.id, editing)); setEditing(null); } }}>
        <p className="mb-6 text-sm text-slate-500">{l('Soạn riêng VI/EN. Giữ nguyên bố cục, hiệu ứng và cách phủ ảnh nền của Hero. Áp dụng → Lưu nháp → Xem trước → Xuất bản.', 'Edit VI/EN separately. Hero layout, effects and background positioning stay the same. Apply → Save draft → Preview → Publish.')}</p>
        <fieldset disabled={uploading} className="min-w-0 space-y-6" aria-busy={uploading}>
          <BilingualField label={l('Tiêu đề chính', 'Main title')} value={editing.title} multiline maxLength={2000} onChange={title => update({ title })} />
          <BilingualField label={l('Dòng giới thiệu nhỏ', 'Eyebrow introduction')} value={editing.eyebrow} maxLength={2000} onChange={eyebrow => update({ eyebrow })} />
          <BilingualRichTextField label={l('Đoạn mô tả', 'Description')} value={editing.descriptionHtml} paragraphs={[editing.description]} variant="intro" disabled={uploading} onChange={descriptionHtml => update({ descriptionHtml })} />
          <fieldset className="min-w-0 space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-700"><legend className="px-2 font-semibold">{l('Nút CTA chính', 'Primary CTA')}</legend>
            <BilingualField label={l('Nội dung nút chính', 'Primary button label')} value={editing.primaryCta.label} maxLength={2000} onChange={label => update({ primaryCta: { ...editing.primaryCta, label } })} />
            <BilingualField label={l('URL nút chính', 'Primary button URL')} value={editing.primaryCta.url} maxLength={2000} onChange={url => update({ primaryCta: { ...editing.primaryCta, url } })} />
          </fieldset>
          <fieldset className="min-w-0 space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-700"><legend className="px-2 font-semibold">{l('Nút CTA phụ', 'Secondary CTA')}</legend>
            <BilingualField label={l('Nội dung nút phụ', 'Secondary button label')} value={editing.secondaryCta.label} maxLength={2000} onChange={label => update({ secondaryCta: { ...editing.secondaryCta, label } })} />
            <BilingualField label={l('URL nút phụ', 'Secondary button URL')} value={editing.secondaryCta.url} maxLength={2000} onChange={url => update({ secondaryCta: { ...editing.secondaryCta, url } })} />
          </fieldset>
          <p className="text-xs text-slate-500">{l('URL nhập riêng cho VI/EN: /vi/…, /en/…, #research hoặc https://…. Để trống nhãn hoặc URL để ẩn nút ở ngôn ngữ đó.', 'Enter separate VI/EN URLs: /vi/…, /en/…, #research or https://…. Leave a label or URL empty to hide that button in the selected language.')}</p>
          <ImageField label={l('Ảnh nền Hero', 'Hero background image')} value={editing.backgroundImage} onChange={backgroundImage => update({ backgroundImage })} onBusyChange={setUploading} />
          <BilingualField label={l('Alt text ảnh nền', 'Background alt text')} value={editing.backgroundAlt} maxLength={2000} onChange={backgroundAlt => update({ backgroundAlt })} />
          <BilingualField label={l('Dòng chữ cuối Hero', 'Hero footer text')} value={editing.footerText} maxLength={2000} onChange={footerText => update({ footerText })} />
        </fieldset>
        {error && <p role="alert" className="mt-5 text-sm text-red-600 dark:text-red-400">{error}</p>}
        <div className="mt-7 flex flex-wrap justify-end gap-3">
          <Button disabled={uploading} variant="outline" onClick={() => setEditing(null)}>{l('Hủy', 'Cancel')}</Button>
          <Button disabled={uploading} variant="outline" onClick={() => { if (validate() && editing) onPreview(withHomeHero(content, page.id, editing), previewPath); }}><Eye size={16} />{l('Xem trước Hero', 'Preview Hero')}</Button>
          <Button disabled={uploading} type="submit">{l('Áp dụng Hero vào bản nháp', 'Apply Hero to draft')}</Button>
        </div>
      </form>
    </Modal>}
  </section>;
}
