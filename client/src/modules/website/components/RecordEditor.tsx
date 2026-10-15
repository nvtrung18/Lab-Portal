import type { Entity, LabData, Member, NewsItem, Project, Publication, ResearchArea } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import type { WebsiteMemberSource } from '../api';
import type { LabResponse } from '../../lab/api';
import { BilingualField, BilingualList, ImageField, SelectField, TextField } from './EditorFields';
import { BilingualRichTextField } from './BilingualRichTextField';
import { ArticleSourceFields } from './ArticleSourceFields';
import { GalleryEditor } from './GalleryEditor';
import { MemberScholarEditor } from './MemberScholarEditor';
import { memberRoleGroups } from '../member-roles';

export type EditingRecord =
  | { collection: 'research'; record: ResearchArea }
  | { collection: 'members'; record: Member }
  | { collection: 'publications'; record: Publication }
  | { collection: 'projects'; record: Project }
  | { collection: 'news'; record: NewsItem };

export function createRecord(collection: Exclude<EditingRecord['collection'], 'members' | 'research'>): EditingRecord {
  const base: Entity = { id: `item-${crypto.randomUUID()}`, title: { vi: '', en: '' }, description: { vi: '', en: '' }, visible: true };
  switch (collection) {
    case 'publications': return { collection, record: { ...base, year: null, type: '', authors: [], area: '', venue: { vi: '', en: '' }, project: '', resources: [], links: {} } };
    case 'projects': return { collection, record: { ...base, area: '', category: { vi: '', en: '' }, status: '', period: '', image: '', lead: '', objectives: [] } };
    case 'news': return { collection, record: { ...base, category: '', date: '', image: '', location: { vi: '', en: '' }, body: [], sourceName: '', sourceUrl: '' } };
  }
}

export function createResearchFromLab(lab: LabResponse): Extract<EditingRecord, { collection: 'research' }> {
  const description = lab.description ?? '';
  return { collection: 'research', record: {
    id: `item-${crypto.randomUUID()}`, labId: lab.id, title: { vi: lab.labName, en: lab.labName },
    description: { vi: description, en: description }, visible: true, image: '', icon: 'earth', topics: [],
  } };
}

export function createMemberFromUser(user: WebsiteMemberSource): Extract<EditingRecord, { collection: 'members' }> {
  const name = user.fullName ?? '';
  return { collection: 'members', record: {
    id: `item-${crypto.randomUUID()}`, userId: user.id, title: { vi: name, en: name },
    description: { vi: '', en: '' }, visible: true, role: '', initials: '', area: '',
    email: '', color: '#e1e8eb', image: '',
  } };
}

export function RecordEditor({ value, data, onChange, onBusyChange }: { value: EditingRecord; data: LabData; onChange: (value: EditingRecord) => void; onBusyChange?: (busy: boolean) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const options = (items: Entity[]) => items.map(item => ({ value: item.id, label: item.title[locale] || item.title[locale === 'vi' ? 'en' : 'vi'] || item.id }));
  const common = <>
    <BilingualField label={value.collection === 'news' ? l('Tiêu đề bài viết', 'Article title') : l('Tiêu đề / tên hiển thị', 'Title / display name')} value={value.record.title} onChange={title => onChange({ ...value, record: { ...value.record, title } } as EditingRecord)} />
    <BilingualField label={value.collection === 'news' || value.collection === 'members' ? l('Mô tả ngắn trong danh sách', 'Summary in the list') : l('Mô tả / giới thiệu', 'Description / introduction')} value={value.record.description} multiline onChange={description => onChange({ ...value, record: { ...value.record, description } } as EditingRecord)} />
    <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={value.record.visible !== false} onChange={e => onChange({ ...value, record: { ...value.record, visible: e.target.checked } } as EditingRecord)} />{l('Hiển thị trên website sau khi xuất bản', 'Show on the website after publication')}</label>
  </>;
  let details;
  switch (value.collection) {
    case 'research': {
      const item = value.record;
      const update = (patch: Partial<ResearchArea>) => onChange({ ...value, record: { ...item, ...patch } });
      details = <>
        <GalleryEditor value={item} onChange={update} onBusyChange={onBusyChange} />
        <SelectField label={l('Biểu tượng', 'Icon')} value={item.icon} allowEmpty={false} options={['earth', 'cpu', 'radio', 'network'].map(icon => ({ value: icon, label: icon }))} onChange={icon => update({ icon: icon as ResearchArea['icon'] })} />
        <BilingualList label={l('Các chủ đề', 'Research subjects')} value={item.topics} onChange={topics => update({ topics })} />
      </>;
      break;
    }
    case 'members': {
      const item = value.record;
      const update = (patch: Partial<Member>) => onChange({ ...value, record: { ...item, ...patch } });
      const roles = memberRoleGroups.map(group => ({ value: group.role, label: group.label[locale] }));
      if (item.role && !roles.some(option => option.value === item.role)) {
        roles.push({ value: item.role, label: item.role });
      }
      details = <>
        <ImageField label={l('Ảnh đại diện', 'Portrait')} value={item.image ?? ''} onChange={image => update({ image })} />
        <div className="grid gap-4 md:grid-cols-2">
          <SelectField label={l('Vai trò trong lab', 'Laboratory role')} value={item.role} options={roles} onChange={role => update({ role: role as Member['role'] })} />
          <SelectField label={l('Hướng nghiên cứu', 'Research direction')} value={item.area} options={options(data.research)} onChange={area => update({ area })} />
          <TextField label={l('Email công khai (có thể để trống)', 'Public email (optional)')} type="email" value={item.email} onChange={email => update({ email })} />
          <TextField label={l('Chữ viết tắt trên ảnh đại diện', 'Avatar initials')} value={item.initials} onChange={initials => update({ initials })} />
          <TextField label={l('Màu nền ảnh đại diện', 'Avatar background')} value={item.color} onChange={color => update({ color })} />
        </div>
        <MemberScholarEditor value={item.scholarProfile} onChange={scholarProfile => update({ scholarProfile })} />
      </>;
      break;
    }
    case 'publications': {
      const item = value.record;
      const update = (patch: Partial<Publication>) => onChange({ ...value, record: { ...item, ...patch } });
      const memberIds = new Set(data.members.map(member => member.id));
      details = <>
        <div className="grid gap-4 md:grid-cols-2">
          <SelectField label={l('Loại công bố', 'Publication format')} value={item.type} options={[{ value: 'Journal', label: 'Journal' }, { value: 'Conference', label: 'Conference' }, { value: 'Book', label: 'Book' }, { value: 'Preprint', label: 'Preprint' }]} onChange={type => update({ type: type as Publication['type'] })} />
          <TextField label={l('Năm công bố', 'Publication year')} type="number" value={item.year === null ? '' : String(item.year)} onChange={year => update({ year: year === '' ? null : Number(year) })} />
          <SelectField label={l('Hướng nghiên cứu', 'Research direction')} value={item.area} options={options(data.research)} onChange={area => update({ area })} />
          <SelectField label={l('Đề tài liên quan', 'Related project')} value={item.project} options={options(data.projects)} onChange={project => update({ project })} />
        </div>
        <BilingualField label={l('Tạp chí / hội nghị / nhà xuất bản', 'Journal / conference / publisher')} value={item.venue} onChange={venue => update({ venue })} />
        <fieldset><legend className="mb-3 text-sm font-semibold">{l('Tác giả thuộc lab', 'Lab contributors')}</legend>
          <div className="grid gap-2 md:grid-cols-2">{data.members.map(member => <label key={member.id} className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={item.authors.includes(member.id)} onChange={e => update({ authors: e.target.checked ? [...item.authors, member.id] : item.authors.filter(id => id !== member.id) })} />{member.title[locale] || member.id}</label>)}</div>
        </fieldset>
        <TextField label={l('Tác giả ngoài lab (mỗi dòng một tên)', 'External contributors (one name per line)')} value={item.authors.filter(id => !memberIds.has(id)).join('\n')} multiline onChange={text => update({ authors: [...item.authors.filter(id => memberIds.has(id)), ...(text ? text.split('\n') : [])] })} />
        {(['DOI', 'PDF', 'Code'] as const).map(kind => <div key={kind} className="space-y-2">
          <label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={item.resources.includes(kind)} onChange={e => update({ resources: e.target.checked ? [...item.resources, kind] : item.resources.filter(resource => resource !== kind) })} />{l('Hiển thị nút', 'Show button')} {kind}</label>
          <TextField label={`${kind} URL`} value={item.links?.[kind] ?? ''} type="url" onChange={url => update({ links: { ...item.links, [kind]: url } })} />
        </div>)}
        <ArticleSourceFields value={item} onChange={update} />
        <BilingualRichTextField label={l('Nội dung giới thiệu công bố', 'Publication article content')} value={item.bodyHtml} paragraphs={[]} onChange={bodyHtml => update({ bodyHtml })} />
      </>;
      break;
    }
    case 'projects': {
      const item = value.record;
      const update = (patch: Partial<Project>) => onChange({ ...value, record: { ...item, ...patch } });
      details = <>
        <GalleryEditor value={item} onChange={update} onBusyChange={onBusyChange} />
        <BilingualField label={l('Phân loại', 'Category')} value={item.category} onChange={category => update({ category })} />
        <div className="grid gap-4 md:grid-cols-2">
          <SelectField label={l('Trạng thái', 'Status')} value={item.status} options={[{ value: 'Ongoing', label: l('Đang thực hiện', 'Ongoing') }, { value: 'Completed', label: l('Hoàn thành', 'Completed') }]} onChange={status => update({ status: status as Project['status'] })} />
          <TextField label={l('Thời gian thực hiện', 'Project period')} value={item.period} onChange={period => update({ period })} />
          <SelectField label={l('Hướng nghiên cứu', 'Research direction')} value={item.area} options={options(data.research)} onChange={area => update({ area })} />
          <SelectField label={l('Người phụ trách', 'Project lead')} value={item.lead} options={options(data.members)} onChange={lead => update({ lead })} />
        </div>
        <BilingualList label={l('Mục tiêu', 'Objectives')} value={item.objectives} onChange={objectives => update({ objectives })} />
        <ArticleSourceFields value={item} onChange={update} />
        <BilingualRichTextField label={l('Nội dung giới thiệu dự án', 'Project article content')} value={item.bodyHtml} paragraphs={[]} onChange={bodyHtml => update({ bodyHtml })} />
      </>;
      break;
    }
    case 'news': {
      const item = value.record;
      const update = (patch: Partial<NewsItem>) => onChange({ ...value, record: { ...item, ...patch } });
      details = <>
        <GalleryEditor value={item} onChange={update} onBusyChange={onBusyChange} />
        <div className="grid gap-4 md:grid-cols-2">
          <SelectField label={l('Loại bài viết', 'Article category')} value={item.category} options={[['News','Tin tức'],['Seminar','Hội thảo'],['Achievement','Thành tựu'],['Event','Sự kiện']].map(([category, vi]) => ({ value: category, label: locale === 'vi' ? vi : category }))} onChange={category => update({ category: category as NewsItem['category'] })} />
          <TextField label={l('Ngày đăng / ngày sự kiện', 'Article / event date')} type="date" value={item.date} onChange={date => update({ date })} />
        </div>
        <BilingualField label={l('Địa điểm', 'Location')} value={item.location} onChange={location => update({ location })} />
        <ArticleSourceFields value={item} onChange={update} />
        <BilingualRichTextField label={l('Nội dung bài viết', 'Article content')} value={item.bodyHtml} paragraphs={item.body} onChange={bodyHtml => update({ bodyHtml, body: [] })} />
      </>;
      break;
    }
  }
  return <div className="space-y-6">{common}{details}</div>;
}

export function putRecord(data: LabData, editing: EditingRecord): LabData {
  const upsert = <T extends Entity>(items: T[], record: T): T[] => items.some(item => item.id === record.id)
    ? items.map(item => item.id === record.id ? record : item) : [...items, record];
  switch (editing.collection) {
    case 'research': return { ...data, research: upsert(data.research, editing.record) };
    case 'members': return { ...data, members: upsert(data.members, editing.record) };
    case 'publications': return { ...data, publications: upsert(data.publications, { ...editing.record, authors: editing.record.authors.filter(name => name.trim().length > 0) }) };
    case 'projects': return { ...data, projects: upsert(data.projects, editing.record) };
    case 'news': return { ...data, news: upsert(data.news, editing.record) };
  }
}
