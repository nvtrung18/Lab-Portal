import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { ArrowDown, ArrowUp, Eye, FileText, Globe, Pencil, Plus, Save, Send, Trash2 } from 'lucide-react';
import type { CollectionName, Entity, Member, ResearchArea, WebsiteContent, WebsiteState } from '../../../../../contracts/website-content';
import { queryKeys } from '../../../shared/api';
import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { getWebsiteMemberSources, getWebsiteState, saveWebsite } from '../api';
import { getLabs } from '../../lab/api';
import { demoContent, scholarMembers, withPages, withScholarMembers } from '../content';
import { inputClass } from '../components/EditorFields';
import { PageContentEditor } from '../components/PageContentEditor';
import { createMemberFromUser, createResearchFromLab, createRecord, putRecord, RecordEditor, type EditingRecord } from '../components/RecordEditor';
import { MemberSourcePicker } from '../components/MemberSourcePicker';
import { ResearchSourcePicker } from '../components/ResearchSourcePicker';
import { WebsitePreview } from '../components/WebsitePreview';

const QUERY_KEY = ['admin', 'website'] as const;
type Section = 'copy' | CollectionName;
const sections: { key: Section; vi: string; en: string }[] = [
  { key: 'copy', vi: 'Nội dung các tab', en: 'Tab content' },
  { key: 'research', vi: 'Hướng nghiên cứu', en: 'Research directions' },
  { key: 'members', vi: 'Thành viên', en: 'People' },
  { key: 'publications', vi: 'Publications', en: 'Publications' },
  { key: 'projects', vi: 'Đề tài giới thiệu', en: 'Public research projects' },
  { key: 'news', vi: 'Tin tức & sự kiện', en: 'News & events' },
];

export function AdminWebsitePage() {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: QUERY_KEY, queryFn: getWebsiteState, refetchOnWindowFocus: false });
  const initialized = useRef(false);
  const [content, setContent] = useState<WebsiteContent>(() => structuredClone(demoContent));
  const [saved, setSaved] = useState(() => JSON.stringify(demoContent));
  const [section, setSection] = useState<Section>('copy');
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState<EditingRecord | null>(null);
  const [selectingMember, setSelectingMember] = useState<{ record: Member | null } | null>(null);
  const memberSources = useQuery({ queryKey: ['admin', 'website', 'member-sources'], queryFn: getWebsiteMemberSources,
    enabled: section === 'members' || selectingMember !== null, staleTime: 30_000 });
  const [selectingResearch, setSelectingResearch] = useState<{ record: ResearchArea | null } | null>(null);
  const researchSources = useQuery({ queryKey: queryKeys.admin.labs, queryFn: getLabs,
    enabled: section === 'research' || selectingResearch !== null, staleTime: 30_000 });
  const [preview, setPreview] = useState(false);
  const [previewContent, setPreviewContent] = useState<WebsiteContent | null>(null);
  const [previewPath, setPreviewPath] = useState<string | undefined>();
  const [confirmPublish, setConfirmPublish] = useState(false);
  const [deleting, setDeleting] = useState<Entity | null>(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (!query.data || initialized.current) return;
    initialized.current = true;
    const draft = withPages(query.data.draft ?? query.data.published ?? structuredClone(demoContent));
    setContent(draft); setSaved(JSON.stringify(draft));
  }, [query.data]);
  const dirty = JSON.stringify(withPages(content)) !== saved;
  useEffect(() => {
    if (!dirty) return;
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', prevent);
    return () => window.removeEventListener('beforeunload', prevent);
  }, [dirty]);
  const mutation = useMutation({
    mutationFn: (publish: boolean) => saveWebsite({ revision: query.data?.revision ?? 0, content }, publish),
    onSuccess: (state: WebsiteState, publish: boolean) => {
      queryClient.setQueryData(QUERY_KEY, state);
      if (state.draft) setContent(state.draft);
      setSaved(JSON.stringify(state.draft));
      setConfirmPublish(false); setError('');
      setNotice(publish ? l('Đã xuất bản. Website sẽ hiển thị nội dung mới khi tải lại.', 'Published. Reload the website to see the new content.') : l('Đã lưu bản nháp. Nội dung công khai vẫn giữ nguyên.', 'Draft saved. Published content is unchanged.'));
    },
    onError: (reason: unknown) => {
      setError(isAxiosError<{ message?: string }>(reason) ? reason.response?.data.message ?? l('Không lưu được nội dung.', 'Content could not be saved.') : l('Không lưu được nội dung.', 'Content could not be saved.'));
    },
  });
  const activeCollection: CollectionName | null = section === 'copy' ? null : section;
  const records = activeCollection ? content.data[activeCollection].filter(item => `${item.title.vi} ${item.title.en}`.toLocaleLowerCase().includes(search.toLocaleLowerCase())) : [];
  const editingUser = editing?.collection === 'members'
    ? memberSources.data?.find(user => user.id === editing.record.userId) : undefined;
  const editingLab = editing?.collection === 'research'
    ? researchSources.data?.find(lab => lab.id === editing.record.labId) : undefined;
  const articleEditorTitle = editing?.collection === 'publications' ? l('Biên tập công bố — Publications', 'Publication editor')
    : editing?.collection === 'projects' ? l('Biên tập dự án giới thiệu', 'Public project editor')
      : editing?.collection === 'news' ? l('Biên tập bài viết — Tin tức & sự kiện', 'Article editor — News & events') : null;
  function addRecord(collection: CollectionName) {
    if (collection === 'members') setSelectingMember({ record: null });
    else if (collection === 'research') setSelectingResearch({ record: null });
    else setEditing(createRecord(collection));
  }
  function previewRecord() {
    if (!editing) return;
    const template = editing.collection === 'members' ? 'people' : editing.collection;
    const page = withPages(content).pages.find(item => item.template === template);
    setPreviewContent({ ...content, data: putRecord(content.data, editing) });
    setPreviewPath(page ? `/${locale}/${encodeURIComponent(page.slug)}/${encodeURIComponent(editing.record.id)}` : undefined);
    setPreview(true);
  }
  function edit(id: string) {
    switch (activeCollection) {
      case 'research': { const record = content.data.research.find(item => item.id === id); if (record) setEditing({ collection: 'research', record: structuredClone(record) }); break; }
      case 'members': { const record = content.data.members.find(item => item.id === id); if (record) setEditing({ collection: 'members', record: structuredClone(record) }); break; }
      case 'publications': { const record = content.data.publications.find(item => item.id === id); if (record) setEditing({ collection: 'publications', record: structuredClone(record) }); break; }
      case 'projects': { const record = content.data.projects.find(item => item.id === id); if (record) setEditing({ collection: 'projects', record: structuredClone(record) }); break; }
      case 'news': { const record = content.data.news.find(item => item.id === id); if (record) setEditing({ collection: 'news', record: structuredClone(record) }); break; }
    }
  }
  function move(id: string, offset: number) {
    if (!activeCollection) return;
    const items = [...content.data[activeCollection]];
    const index = items.findIndex(item => item.id === id);
    const destination = index + offset;
    if (index < 0 || destination < 0 || destination >= items.length) return;
    [items[index], items[destination]] = [items[destination], items[index]];
    setContent({ ...content, data: { ...content.data, [activeCollection]: items } });
  }
  function selectRecord(id: string, visible: boolean) {
    if (!activeCollection) return;
    setContent({ ...content, data: { ...content.data,
      [activeCollection]: content.data[activeCollection].map(item => item.id === id ? { ...item, visible } : item) } });
  }
  const publishedAt = query.data?.publishedAt ? new Date(query.data.publishedAt).toLocaleString(locale === 'vi' ? 'vi-VN' : 'en-GB') : l('Chưa xuất bản', 'Not published');
  return <section className="space-y-6">
    <div className="rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900 sm:p-6">
      <div className="app-page-heading flex flex-wrap items-start justify-between gap-5">
        <div><p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500"><Globe size={15} />Website</p>
          <h2 className="text-2xl font-semibold tracking-tight">{l('Quản trị website giới thiệu', 'Laboratory website content')}</h2>
          <p className="mt-2 max-w-2xl text-sm text-slate-500">{l('Chọn nội dung hiển thị trong từng tab, thêm theo mẫu hoặc tự soạn và sắp xếp các mục. Quản lý thành viên, hướng nghiên cứu, Publications, đề tài và tin tức. Xem trước, lưu nháp rồi xuất bản.', 'Select and arrange content in each tab, use presets or write new content. Manage people, research, publications, projects and news. Preview, save a draft and publish.')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => { setPreviewContent(null); setPreviewPath(undefined); setPreview(true); }}><Eye size={16} />{l('Xem trước', 'Preview')}</Button>
          <Button size="sm" variant="secondary" loading={mutation.isPending && mutation.variables === false} loadingText={l('Đang lưu…', 'Saving…')} disabled={!query.data || query.isError || mutation.isPending} onClick={() => { setError(''); setNotice(''); mutation.mutate(false); }}><Save size={16} />{l('Lưu nháp', 'Save draft')}</Button>
          <Button size="sm" disabled={!query.data || query.isError || mutation.isPending} onClick={() => setConfirmPublish(true)}><Send size={16} />{l('Xuất bản', 'Publish')}</Button>
        </div>
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-slate-200 pt-4 text-xs text-slate-500 dark:border-slate-800">
        <span>{dirty ? l('Có thay đổi chưa lưu', 'Unsaved changes') : query.data?.draft ? l('Bản nháp đã lưu', 'Draft saved') : l('Đang dùng nội dung mẫu', 'Using template content')}</span>
        <span>{l('Xuất bản gần nhất:', 'Last published:')} {publishedAt}</span>
        <button type="button" className="min-h-8 underline" disabled={query.isFetching || mutation.isPending} onClick={() => { if (dirty && !window.confirm(l('Bỏ các thay đổi chưa lưu và tải lại?', 'Discard unsaved changes and reload?'))) return; setError(''); setNotice(''); void query.refetch().then(result => { if (!result.data) return; const draft = withPages(result.data.draft ?? result.data.published ?? structuredClone(demoContent)); initialized.current = true; setContent(draft); setSaved(JSON.stringify(draft)); }); }}>{l('Tải lại', 'Reload')}</button>
      </div>
    </div>
    {query.isLoading && <p role="status" className="text-sm text-slate-500">{l('Đang tải bản nháp đã lưu…', 'Loading the saved draft…')}</p>}
    {query.isError && <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{l('Không tải được nội dung từ backend. Bạn đang xem dữ liệu mẫu; lưu và xuất bản sẽ khả dụng sau khi kết nối thành công.', 'Backend content could not be loaded. Template data is shown; saving and publishing become available after reconnecting.')}</p>}
    {notice && <p role="status" className="rounded-lg bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    <div className="grid min-w-0 gap-6 xl:grid-cols-[220px_minmax(0,1fr)]">
      <nav aria-label={l('Các phần nội dung website', 'Website content sections')} className="app-section-nav flex flex-wrap gap-2 self-start rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900 xl:sticky xl:top-28 xl:flex-col">
        {sections.map(item => <button key={item.key} type="button" aria-current={section === item.key ? 'page' : undefined} className={`flex min-h-11 items-center gap-2 rounded-md px-3 py-2 text-left text-sm ${section === item.key ? 'bg-slate-900 font-semibold text-white dark:bg-white dark:text-slate-950' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'}`} onClick={() => { setSection(item.key); setSearch(''); }}><FileText size={16} />{locale === 'vi' ? item.vi : item.en}{item.key !== 'copy' && <span className="ml-auto text-xs opacity-70">{content.data[item.key].length}</span>}</button>)}
      </nav>
      <fieldset disabled={!query.data || query.isLoading || mutation.isPending} className="min-w-0 rounded-xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900 sm:p-6">
        {section === 'copy' && <PageContentEditor content={content} onChange={setContent} onPreview={(draft, path) => { setPreviewContent(draft); setPreviewPath(`/${locale}${path}`); setPreview(true); }} />}
        {activeCollection && <>
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><input aria-label={l('Tìm bản ghi', 'Find records')} type="search" className={`${inputClass} max-w-sm`} placeholder={l('Tìm theo tiêu đề hoặc tên…', 'Search titles or names…')} value={search} onChange={e => setSearch(e.target.value)} /><Button size="sm" onClick={() => addRecord(activeCollection)}><Plus size={16} />{activeCollection === 'members' ? l('Thêm từ người dùng', 'Add from users') : activeCollection === 'research' ? l('Thêm từ nhóm nghiên cứu', 'Add from research groups') : l('Thêm mới', 'Add record')}</Button></div>
          {activeCollection === 'members' && <p className="mb-4 text-sm text-slate-500">{l('Chọn người dùng rồi chỉnh hồ sơ hiển thị riêng cho từng người. Tích Hiển thị, xem trước và xuất bản để đưa lên website.', 'Select users and edit each public profile separately. Check Show, preview and publish to display them on the website.')}</p>}
          {activeCollection === 'members' && <div className="mb-5 flex flex-wrap items-center gap-3">
            <Button size="sm" variant="outline" onClick={() => {
              setContent(withScholarMembers(content)); setSearch('');
              setNotice(l(`Đã cập nhật tên, vai trò và thứ tự phân tầng cho ${scholarMembers.length} hồ sơ Scholar. Xem trước rồi lưu nháp hoặc xuất bản.`, `Updated names, roles and hierarchy order for ${scholarMembers.length} Scholar profiles. Preview, then save or publish.`));
            }}><Plus size={16} />{l(`Áp dụng phân tầng ${scholarMembers.length} hồ sơ Scholar`, `Apply hierarchy to ${scholarMembers.length} Scholar profiles`)}</Button>
            <span className="text-xs text-slate-500">{l('Ngày lấy dữ liệu được ghi riêng trong từng hồ sơ Scholar.', 'Each Scholar profile records its own retrieval date.')}</span>
          </div>}
          {activeCollection === 'research' && <p className="mb-4 text-sm text-slate-500">{l('Chọn nhóm nghiên cứu hiện có rồi chỉnh tên giới thiệu, mô tả, ảnh và chủ đề cho website. Tích Hiển thị, xem trước và xuất bản.', 'Select an existing research group and edit its public name, description, image and topics. Check Show, preview and publish.')}</p>}
          {activeCollection === 'news' && <p className="mb-4 text-sm text-slate-500">{l('Soạn bài viết, tin tức hoặc sự kiện trong form riêng. Có thể bổ sung tên nguồn và link bài gốc, xem trước rồi lưu nháp hoặc xuất bản.', 'Compose articles, news or events in the article form. Optionally add the source name and original article link, preview, then save a draft or publish.')}</p>}
          {activeCollection === 'publications' && <p className="mb-4 text-sm text-slate-500">{l('Nhập loại công bố, tác giả và tài nguyên DOI/PDF/Code; soạn nội dung giới thiệu và bổ sung nguồn/link gốc nếu có. Xem trước rồi lưu nháp hoặc xuất bản.', 'Enter publication type, contributors and DOI/PDF/Code resources; compose the article and optionally add its original source. Preview, then save a draft or publish.')}</p>}
          {activeCollection === 'projects' && <p className="mb-4 text-sm text-slate-500">{l('Nhập thông tin đề tài, mục tiêu và người phụ trách; soạn bài giới thiệu với ảnh, liên kết và nguồn/link gốc nếu có. Xem trước rồi lưu nháp hoặc xuất bản.', 'Enter project details, objectives and lead; compose the project article with images, links and an optional original source. Preview, then save a draft or publish.')}</p>}
          <div className="divide-y divide-slate-200 dark:divide-slate-800">{records.map(item => <article key={item.id} className="flex flex-wrap items-start justify-between gap-4 py-5">
            <label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={item.visible !== false} onChange={event => selectRecord(item.id, event.target.checked)} />{l('Hiển thị', 'Show')}</label>
            <div className="min-w-0 flex-1"><h3 className="break-words text-base font-semibold">{item.title[locale] || item.title[locale === 'vi' ? 'en' : 'vi'] || l('Chưa có tiêu đề', 'Untitled')}</h3><p className="mt-1 line-clamp-2 text-sm text-slate-500">{item.description[locale]}</p><span className={`mt-2 inline-block text-xs ${item.visible === false ? 'text-slate-500' : 'text-emerald-600'}`}>{item.visible === false ? l('Đang ẩn', 'Hidden') : l('Được hiển thị khi xuất bản', 'Visible when published')}</span></div>
            <div className="flex shrink-0 gap-1">
              <button type="button" aria-label={l('Đưa lên', 'Move up')} className="rounded-md p-3 hover:bg-slate-100 dark:hover:bg-slate-800" onClick={() => move(item.id, -1)}><ArrowUp size={16} /></button>
              <button type="button" aria-label={l('Đưa xuống', 'Move down')} className="rounded-md p-3 hover:bg-slate-100 dark:hover:bg-slate-800" onClick={() => move(item.id, 1)}><ArrowDown size={16} /></button>
              <button type="button" aria-label={l('Chỉnh sửa', 'Edit')} className="rounded-md p-3 hover:bg-slate-100 dark:hover:bg-slate-800" onClick={() => edit(item.id)}><Pencil size={16} /></button>
              <button type="button" aria-label={l('Xóa bản ghi', 'Delete record')} className="rounded-md p-3 text-red-600 hover:bg-red-50" onClick={() => setDeleting(item)}><Trash2 size={16} /></button>
            </div>
          </article>)}</div>
          {!records.length && <p className="py-12 text-center text-sm text-slate-500">{l('Chưa có bản ghi phù hợp.', 'No matching records.')}</p>}
        </>}
      </fieldset>
    </div>
    {editing && !selectingMember && !selectingResearch && <Modal title={articleEditorTitle ?? l('Chỉnh sửa nội dung website', 'Edit website content')} size={articleEditorTitle ? 'full' : '2xl'} onClose={() => setEditing(null)}><form onSubmit={e => { e.preventDefault(); setContent({ ...content, data: putRecord(content.data, editing) }); setEditing(null); setNotice(''); }}>
      {editing.collection === 'research' && <div className="mb-6 space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
        <h3 className="text-sm font-semibold">{l('Nhóm nghiên cứu liên kết', 'Linked research group')}</h3>
        <p className="break-words text-sm">{editingLab ? editingLab.labName : editing.record.labId != null ? l(`Nhóm #${editing.record.labId} — thông tin hiện không khả dụng`, `Group #${editing.record.labId} — details currently unavailable`) : l('Nội dung cũ chưa liên kết nhóm nghiên cứu.', 'Legacy content without a linked research group.')}</p>
        <p className="text-xs text-slate-500">{l('Các trường bên dưới chỉ chỉnh nội dung công khai của hướng nghiên cứu trên website.', 'The fields below edit the public website content of this research direction.')}</p>
        <Button size="sm" variant="outline" onClick={() => setSelectingResearch({ record: editing.record })}>{editing.record.labId == null ? l('Liên kết nhóm nghiên cứu', 'Link a research group') : l('Đổi nhóm liên kết', 'Change linked group')}</Button>
      </div>}
      {editing.collection === 'members' && <div className="mb-6 space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
        <h3 className="text-sm font-semibold">{l('Tài khoản liên kết', 'Linked account')}</h3>
        <p className="break-words text-sm">{editingUser ? `${editingUser.fullName || editingUser.username} (@${editingUser.username})` : editing.record.userId != null ? l(`Tài khoản #${editing.record.userId} — thông tin hiện không khả dụng`, `Account #${editing.record.userId} — details currently unavailable`) : l('Hồ sơ cũ chưa liên kết người dùng.', 'Legacy profile without a linked account.')}</p>
        <p className="text-xs text-slate-500">{l('Các trường bên dưới chỉ sửa hồ sơ công khai trên website. Vai trò học thuật không thay đổi quyền tài khoản.', 'The fields below edit only the public website profile. Academic role does not change account permissions.')}</p>
        <Button size="sm" variant="outline" onClick={() => setSelectingMember({ record: editing.record })}>{editing.record.userId == null ? l('Liên kết người dùng', 'Link a user') : l('Đổi người dùng liên kết', 'Change linked user')}</Button>
      </div>}
      <RecordEditor value={editing} data={content.data} onChange={setEditing} /><div className="app-form-actions mt-7 flex flex-wrap justify-end gap-3"><Button variant="outline" onClick={() => setEditing(null)}>{l('Hủy', 'Cancel')}</Button><Button variant="outline" onClick={previewRecord}><Eye size={16} />{l('Xem trước', 'Preview')}</Button><Button type="submit">{l('Áp dụng vào bản nháp', 'Apply to draft')}</Button></div></form></Modal>}
    {selectingMember && <Modal title={l('Chọn Thành viên từ người dùng', 'Select a member from users')} size="xl" onClose={() => setSelectingMember(null)}>
      <MemberSourcePicker users={memberSources.data ?? []} members={content.data.members} target={selectingMember.record}
        loading={memberSources.isPending} failed={memberSources.isError} refreshing={memberSources.isFetching}
        onRetry={() => { void memberSources.refetch(); }} onSelect={user => {
          setEditing(selectingMember.record ? { collection: 'members', record: { ...selectingMember.record, userId: user.id } } : createMemberFromUser(user));
          setSelectingMember(null);
        }} />
    </Modal>}
    {selectingResearch && <Modal title={l('Chọn Hướng nghiên cứu từ nhóm hiện có', 'Select a direction from research groups')} size="xl" onClose={() => setSelectingResearch(null)}>
      <ResearchSourcePicker labs={researchSources.data ?? []} research={content.data.research} target={selectingResearch.record}
        loading={researchSources.isPending} failed={researchSources.isError} refreshing={researchSources.isFetching}
        onRetry={() => { void researchSources.refetch(); }} onSelect={lab => {
          setEditing(selectingResearch.record ? { collection: 'research', record: { ...selectingResearch.record, labId: lab.id } } : createResearchFromLab(lab));
          setSelectingResearch(null);
        }} />
    </Modal>}
    {deleting && activeCollection && <Modal title={l('Xóa nội dung?', 'Delete content?')} onClose={() => setDeleting(null)}><p className="text-sm">{l('Bản ghi sẽ bị xóa khỏi nội dung đang chỉnh sửa. Website công khai chỉ thay đổi khi bạn xuất bản.', 'The record will be removed from the working draft. The public website changes only after publication.')}</p><div className="mt-6 flex justify-end gap-3"><Button variant="outline" onClick={() => setDeleting(null)}>{l('Hủy', 'Cancel')}</Button><Button variant="danger" onClick={() => { setContent({ ...content, data: { ...content.data, [activeCollection]: content.data[activeCollection].filter(item => item.id !== deleting.id) } }); setDeleting(null); }}>{l('Xóa', 'Delete')}</Button></div></Modal>}
    {confirmPublish && <Modal title={l('Xuất bản nội dung đang chỉnh sửa?', 'Publish the current content?')} onClose={() => !mutation.isPending && setConfirmPublish(false)} closeDisabled={mutation.isPending}><p className="text-sm">{l('Các thay đổi, bao gồm trường để trống và bản ghi đã ẩn hoặc xóa, sẽ thay thế nội dung công khai hiện tại.', 'The changes, including empty fields and hidden or deleted records, will replace the currently published content.')}</p>{error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}<div className="mt-6 flex justify-end gap-3"><Button variant="outline" disabled={mutation.isPending} onClick={() => setConfirmPublish(false)}>{l('Hủy', 'Cancel')}</Button><Button loading={mutation.isPending} onClick={() => { setError(''); mutation.mutate(true); }}>{l('Xuất bản', 'Publish')}</Button></div></Modal>}
    {preview && <WebsitePreview content={previewContent ?? content} path={previewPath} onClose={() => { setPreview(false); setPreviewContent(null); }} />}
  </section>;
}
