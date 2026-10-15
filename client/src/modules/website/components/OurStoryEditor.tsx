import { useState } from 'react';
import { BookOpen, Eye, Pencil } from 'lucide-react';
import type { WebsiteContent, WebsiteOurStory, WebsitePage } from '../../../../../contracts/website-content';
import { resolveAboutStory, withAboutStory } from '../../../../../contracts/website-story';
import { Button, Modal } from '../../../shared/components';
import { LAB_BRAND } from '../../../shared/constants';
import { useI18n } from '../../../shared/i18n';
import { BilingualField } from './EditorFields';
import { BilingualRichTextField } from './BilingualRichTextField';

export function OurStoryEditor({ content, page, onChange, onPreview }: {
  content: WebsiteContent; page: WebsitePage; onChange: (content: WebsiteContent) => void;
  onPreview: (content: WebsiteContent, path: string) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [editing, setEditing] = useState<WebsiteOurStory | null>(null);
  const [error, setError] = useState('');
  const update = (patch: Partial<WebsiteOurStory>) => setEditing(current => current ? { ...current, ...patch } : current);
  function validate() {
    if (!editing) return false;
    if (Object.values(editing.descriptionHtml ?? {}).some(html => html.length > 200000)) {
      setError(l('Mô tả có định dạng tối đa 200.000 ký tự HTML cho mỗi ngôn ngữ.', 'Formatted descriptions allow up to 200,000 HTML characters per language.')); return false;
    }
    setError(''); return true;
  }
  return <section className="space-y-4 rounded-lg border border-slate-200 p-5 dark:border-slate-700" aria-label={l('Câu chuyện / Our Story', 'Our Story')}>
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><h3 className="flex items-center gap-2 text-lg font-semibold"><BookOpen size={20} />{l('Câu chuyện / Our Story', 'Our Story')}</h3>
        <p className="mt-2 max-w-2xl text-sm text-slate-500">{page.ourStory == null
          ? l('Đang dùng nội dung Câu chuyện hiện tại. Nội dung này độc lập với Hero Trang chủ.', 'Using the current Our Story content, independently of the Homepage Hero.')
          : l('Câu chuyện đã có cấu hình riêng trong bản nháp. Website công khai chỉ thay đổi sau khi Xuất bản.', 'This draft has a custom Our Story. The public website changes only after Publish.')}</p></div>
      <Button variant="outline" onClick={() => { setError(''); setEditing(resolveAboutStory(page.ourStory, LAB_BRAND, content.copy)); }}><Pencil size={16} />{l('Chỉnh sửa Câu chuyện / Our Story', 'Edit Our Story')}</Button>
    </div>
    {editing && <Modal title={l('Chỉnh sửa Câu chuyện / Our Story', 'Edit Our Story')} size="full" onClose={() => setEditing(null)}>
      <form onSubmit={event => { event.preventDefault(); if (validate()) { onChange(withAboutStory(content, page.id, editing)); setEditing(null); } }}>
        <p className="mb-6 text-sm text-slate-500">{l('Soạn riêng VI/EN cho section Câu chuyện đang có. Áp dụng → Xem trước → Lưu nháp → Xuất bản. Không thay đổi Hero hoặc các nội dung trong Thư viện.', 'Edit VI/EN for the existing Our Story section. Apply → Preview → Save draft → Publish. Hero and Content Library sections stay unchanged.')}</p>
        <div className="min-w-0 space-y-6">
          <BilingualField label={l('Nhãn nhỏ', 'Eyebrow label')} value={editing.eyebrow} maxLength={2000} onChange={eyebrow => update({ eyebrow })} />
          <BilingualField label={l('Tiêu đề chính', 'Main title')} value={editing.title} multiline maxLength={2000} onChange={title => update({ title })} />
          <BilingualRichTextField label={l('Đoạn giới thiệu chi tiết', 'Detailed introduction')} value={editing.descriptionHtml} paragraphs={[editing.description]} variant="intro" onChange={descriptionHtml => update({ descriptionHtml })} />
          <BilingualField label={l('Nội dung nút CTA', 'CTA button label')} value={editing.ctaLabel} maxLength={2000} onChange={ctaLabel => update({ ctaLabel })} />
          <p className="text-xs text-slate-500">{l('Nút tiếp tục dẫn đến trang Thành viên. Để trống nội dung nút để ẩn ở ngôn ngữ tương ứng.', 'The button continues to open People. Leave its label empty to hide it in that language.')}</p>
        </div>
        {error && <p role="alert" className="mt-5 text-sm text-red-600 dark:text-red-400">{error}</p>}
        <div className="mt-7 flex flex-wrap justify-end gap-3">
          <Button variant="outline" onClick={() => setEditing(null)}>{l('Hủy', 'Cancel')}</Button>
          <Button variant="outline" onClick={() => { if (validate()) onPreview(withAboutStory(content, page.id, editing), page.slug ? `/${encodeURIComponent(page.slug)}` : ''); }}><Eye size={16} />{l('Xem trước Câu chuyện', 'Preview Our Story')}</Button>
          <Button type="submit">{l('Áp dụng Câu chuyện vào bản nháp', 'Apply Our Story to draft')}</Button>
        </div>
      </form>
    </Modal>}
  </section>;
}
