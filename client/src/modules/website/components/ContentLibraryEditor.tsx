import { useState } from 'react';
import { ArrowDown, ArrowUp, Eye, FileText, Image, Pencil, Plus, Sparkles, Trash2, X } from 'lucide-react';
import type { WebsiteContent, WebsitePage, WebsiteSection } from '../../../../../contracts/website-content';
import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { createSection, withPages, type EditableWebsiteContent } from '../content';
import { inputClass } from './EditorFields';
import { SectionEditor } from './SectionEditor';

export function ContentLibraryEditor({ content, page, onChange, onPreview }: {
  content: EditableWebsiteContent; page: WebsitePage; onChange: (content: WebsiteContent) => void; onPreview: (content: WebsiteContent, path: string) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [search, setSearch] = useState('');
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<{ item: WebsiteSection; creating: boolean } | null>(null);
  const [deleting, setDeleting] = useState<WebsiteSection | null>(null);
  const selectedIds = page.sectionIds ?? [];
  const label = (item: WebsiteSection) => item.title[locale] || item.title[locale === 'vi' ? 'en' : 'vi'] || l('Chưa có tiêu đề', 'Untitled');
  const presentation = (item: WebsiteSection) => item.presentation === 'split' ? l('Ảnh + nội dung', 'Image + text')
    : item.presentation === 'featured' ? l('Nổi bật', 'Featured') : l('Văn bản', 'Text');
  const library = content.contentLibrary.filter(item => `${item.title.vi} ${item.title.en} ${item.description.vi} ${item.description.en}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  function select(ids: string[]) {
    onChange(withPages({ ...content, pages: content.pages.map(item => item.id === page.id ? { ...item, sectionIds: ids } : item) }));
  }
  function move(index: number, offset: number) {
    const next = [...selectedIds];
    const destination = index + offset;
    if (destination < 0 || destination >= next.length) return;
    [next[index], next[destination]] = [next[destination], next[index]];
    select(next);
  }
  function create(preset: WebsiteSection['presentation']) {
    setEditing({ item: { ...createSection(), presentation: preset }, creating: true });
  }
  function editedContent(): WebsiteContent {
    if (!editing) return content;
    const contentLibrary = editing.creating ? [...content.contentLibrary, editing.item]
      : content.contentLibrary.map(item => item.id === editing.item.id ? editing.item : item);
    const pages = editing.creating ? content.pages.map(item => item.id === page.id
      ? { ...item, sectionIds: [...selectedIds, editing.item.id] } : item) : content.pages;
    return withPages({ ...content, contentLibrary, pages });
  }
  function apply() {
    if (uploading) return;
    onChange(editedContent());
    setEditing(null);
  }
  function remove() {
    if (!deleting) return;
    onChange(withPages({ ...content, contentLibrary: content.contentLibrary.filter(item => item.id !== deleting.id),
      pages: content.pages.map(item => ({ ...item, sectionIds: (item.sectionIds ?? []).filter(id => id !== deleting.id) })) }));
    setDeleting(null);
  }
  return <div className="space-y-8">
    <section className="space-y-4" aria-label={l('Nội dung đã chọn cho tab', 'Selected tab content')}>
      <div><h3 className="text-lg font-semibold">{l('Nội dung đã chọn cho tab', 'Selected tab content')} <span className="ml-2 text-sm text-slate-500">({selectedIds.length})</span></h3>
        <p className="mt-1 text-sm text-slate-500">{l('Website hiển thị theo thứ tự bên dưới. Bỏ chọn vẫn giữ nội dung trong thư viện.', 'The website uses the order below. Removing a selection keeps the content in the library.')}</p></div>
      <ol className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-700 dark:border-slate-700">
        {page.sections.map((item, index) => <li key={item.id} className="flex flex-wrap items-center gap-3 p-4">
          <span className="text-sm text-slate-400">{index + 1}.</span>
          <div className="min-w-0 flex-1"><h4 className="break-words text-sm font-semibold">{label(item)}</h4><p className="mt-1 text-xs text-slate-500">{presentation(item)}{item.visible === false && ` · ${l('Đang ẩn trên website', 'Hidden on the website')}`}</p></div>
          <div className="flex flex-wrap gap-1">
            <Button size="sm" variant="outline" disabled={index === 0} aria-label={`${l('Đưa lên', 'Move up')}: ${label(item)}`} onClick={() => move(index, -1)}><ArrowUp size={15} /></Button>
            <Button size="sm" variant="outline" disabled={index === page.sections.length - 1} aria-label={`${l('Đưa xuống', 'Move down')}: ${label(item)}`} onClick={() => move(index, 1)}><ArrowDown size={15} /></Button>
            <Button size="sm" variant="outline" aria-label={`${l('Chỉnh sửa', 'Edit')}: ${label(item)}`} onClick={() => setEditing({ item: structuredClone(item), creating: false })}><Pencil size={15} /></Button>
            <Button size="sm" variant="outline" aria-label={`${l('Bỏ chọn', 'Deselect')}: ${label(item)}`} onClick={() => select(selectedIds.filter(id => id !== item.id))}><X size={15} /></Button>
          </div>
        </li>)}
      </ol>
      {!selectedIds.length && <p className="rounded-lg border border-dashed border-slate-300 p-5 text-sm text-slate-500">{l('Chưa chọn nội dung bổ sung. Chọn từ thư viện hoặc thêm nội dung mới bên dưới.', 'No additional content selected. Choose from the library or create content below.')}</p>}
    </section>
    <section className="space-y-4 border-t border-slate-200 pt-6 dark:border-slate-700">
      <div><h3 className="text-lg font-semibold">{l('Thư viện nội dung', 'Content library')}</h3><p className="mt-1 text-sm text-slate-500">{l('Tích chọn nội dung có sẵn để đưa vào tab này. Nội dung có thể dùng lại ở nhiều tab.', 'Select saved content for this tab. Content can be reused in multiple tabs.')}</p></div>
      <input type="search" className={inputClass} aria-label={l('Tìm trong thư viện', 'Search the library')} placeholder={l('Tìm theo tiêu đề hoặc nội dung…', 'Search titles or content…')} value={search} onChange={event => setSearch(event.target.value)} />
      <div className="divide-y divide-slate-200 dark:divide-slate-700">
        {library.map(item => <div key={item.id} className="flex flex-wrap items-center gap-3 py-4">
          <label className="flex min-h-11 min-w-0 flex-1 items-start gap-3 text-sm">
            <input type="checkbox" className="mt-1" checked={selectedIds.includes(item.id)} onChange={event => select(event.target.checked ? [...selectedIds, item.id] : selectedIds.filter(id => id !== item.id))} />
            <span className="min-w-0"><span className="block break-words font-semibold">{label(item)}</span><span className="mt-1 block line-clamp-2 text-slate-500">{item.description[locale]}</span><span className="mt-2 block text-xs text-slate-500">{presentation(item)}{item.visible === false && ` · ${l('Đang ẩn — chỉnh sửa để cho phép hiển thị', 'Hidden — edit to allow display')}`}</span></span>
          </label>
          <div className="flex gap-1"><Button size="sm" variant="outline" aria-label={`${l('Chỉnh sửa', 'Edit')}: ${label(item)}`} onClick={() => setEditing({ item: structuredClone(item), creating: false })}><Pencil size={15} /></Button><Button size="sm" variant="outline" aria-label={`${l('Xóa khỏi thư viện', 'Delete from library')}: ${label(item)}`} onClick={() => setDeleting(item)}><Trash2 size={15} /></Button></div>
        </div>)}
      </div>
      {!library.length && <p className="py-4 text-sm text-slate-500">{content.contentLibrary.length ? l('Không có nội dung phù hợp.', 'No matching content.') : l('Thư viện chưa có nội dung. Thêm theo mẫu hoặc tự soạn để bắt đầu.', 'The library is empty. Choose a preset or write content to start.')}</p>}
    </section>
    <section className="space-y-4 border-t border-slate-200 pt-6 dark:border-slate-700">
      <h3 className="text-lg font-semibold">{l('Thêm nội dung mới', 'Create content')}</h3>
      <div className="flex flex-wrap gap-3"><Button variant="outline" onClick={() => create('standard')}><FileText size={16} />{l('Mẫu văn bản', 'Text preset')}</Button><Button variant="outline" onClick={() => create('split')}><Image size={16} />{l('Mẫu ảnh + nội dung', 'Image + text preset')}</Button><Button variant="outline" onClick={() => create('featured')}><Sparkles size={16} />{l('Mẫu nổi bật', 'Featured preset')}</Button><Button onClick={() => create('standard')}><Plus size={16} />{l('Tự soạn', 'Write content')}</Button></div>
      <p className="text-xs text-slate-500">{l('Nội dung mới để trống để bạn nhập thông tin. Khi áp dụng, nội dung được lưu vào thư viện và chọn cho tab này.', 'New content starts empty. Applying adds it to the library and selects it for this tab.')}</p>
    </section>
    {editing && <Modal title={editing.creating ? l('Thêm nội dung', 'Create content') : l('Chỉnh sửa nội dung', 'Edit content')} size="full" closeDisabled={uploading} onClose={() => { if (!uploading) setEditing(null); }}>
      <form onSubmit={event => { event.preventDefault(); apply(); }}>
        {!editing.creating && <p className="mb-5 text-sm text-slate-500">{l('Thay đổi áp dụng cho mọi tab đang chọn nội dung này, sau khi xuất bản.', 'After publication, edits apply to every tab using this content.')}</p>}
        <SectionEditor onBusyChange={setUploading} value={editing.item} onChange={item => setEditing({ ...editing, item })} />
        <div className="mt-7 flex flex-wrap justify-end gap-3"><Button disabled={uploading} variant="outline" onClick={() => setEditing(null)}>{l('Hủy', 'Cancel')}</Button><Button disabled={uploading} variant="outline" onClick={() => onPreview(editedContent(), page.slug ? `/${encodeURIComponent(page.slug)}` : '')}><Eye size={16} />{l('Xem trước', 'Preview')}</Button><Button disabled={uploading} type="submit">{l('Áp dụng vào bản nháp', 'Apply to draft')}</Button></div>
      </form>
    </Modal>}
    {deleting && <Modal title={l('Xóa khỏi thư viện?', 'Delete from library?')} onClose={() => setDeleting(null)}><p className="text-sm">{l('Nội dung sẽ bị xóa khỏi thư viện và bỏ chọn ở mọi tab. Website công khai thay đổi sau khi xuất bản.', 'Content will be deleted from the library and deselected in every tab. The public website changes after publication.')}</p><div className="mt-6 flex justify-end gap-3"><Button variant="outline" onClick={() => setDeleting(null)}>{l('Hủy', 'Cancel')}</Button><Button variant="danger" onClick={remove}>{l('Xóa nội dung', 'Delete content')}</Button></div></Modal>}
  </div>;
}
