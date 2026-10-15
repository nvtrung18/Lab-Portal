import { useMemo, useState } from 'react';
import seed from '../../../../../contracts/website-demo-content.json';
import type { WebsiteContent } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { BilingualField, inputClass } from './EditorFields';

const groups = [
  ['home', 'Trang chủ', 'Home'], ['about', 'Giới thiệu', 'About'], ['research', 'Nghiên cứu', 'Research'],
  ['people', 'Thành viên', 'People'], ['publications', 'Publications', 'Publications'],
  ['projects', 'Đề tài', 'Research projects'], ['products', 'Sản phẩm', 'Products'], ['news', 'Tin tức', 'News'], ['join', 'Tham gia', 'Join'],
  ['contact', 'Liên hệ', 'Contact'], ['general', 'Nội dung chung', 'General'], ['detail', 'Trang chi tiết', 'Detail pages'],
];
export function TemplateCopyEditor({ content, onChange }: { content: WebsiteContent; onChange: (content: WebsiteContent) => void }) {
  const { locale } = useI18n();
  const [group, setGroup] = useState('home');
  const [search, setSearch] = useState('');
  const sections: Record<string, string> = seed.copySections;
  const texts = useMemo(() => {
    const pageText = new Set(seed.content.pages.flatMap(page => [page.title.en, page.description.en, page.navigationTitle.en]));
    pageText.add('Intelligent systems.'); pageText.add('Meaningful impact.');
    return Object.entries(content.copy).filter(([key, value]) => !pageText.has(key) &&
      (sections[key] ?? 'general') === group && `${key} ${value.vi} ${value.en}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  }, [content.copy, group, search]);
  return <div className="space-y-5">
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-2 text-sm"><span>{locale === 'vi' ? 'Trang cần chỉnh sửa' : 'Page to edit'}</span><select className={inputClass} value={group} onChange={e => setGroup(e.target.value)}>{groups.map(([key, vi, en]) => <option key={key} value={key}>{locale === 'vi' ? vi : en}</option>)}</select></label>
      <label className="space-y-2 text-sm"><span>{locale === 'vi' ? 'Tìm nội dung' : 'Find text'}</span><input className={inputClass} type="search" value={search} onChange={e => setSearch(e.target.value)} /></label>
    </div>
    <p className="text-sm text-slate-500">{locale === 'vi' ? 'Tiêu đề nội dung và phần giới thiệu được chỉnh ở từng tab phía trên. Phần này cập nhật các đoạn thông tin có sẵn trên website; nội dung để trống sẽ giữ trống.' : 'Content headings and introductions are edited in each tab above. Update existing website text here; empty text remains empty.'}</p>
    {texts.map(([key, value], index) => <div key={key} className="rounded-lg border border-slate-200 p-5 dark:border-slate-700">
      <BilingualField label={`${index + 1}. ${key.length > 100 ? `${key.slice(0, 100)}…` : key}`} value={value} multiline onChange={next => onChange({ ...content, copy: { ...content.copy, [key]: next } })} />
    </div>)}
    {!texts.length && <p className="py-10 text-center text-sm text-slate-500">{locale === 'vi' ? 'Không có nội dung phù hợp.' : 'No matching text.'}</p>}
  </div>;
}
