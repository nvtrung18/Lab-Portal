import type { ScholarMetrics, ScholarProfile, ScholarPublication } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { BilingualField, BilingualList, TextField } from './EditorFields';

export function MemberScholarEditor({ value, onChange }: {
  value: ScholarProfile | null | undefined; onChange: (value: ScholarProfile | null) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  if (!value) return <div className="space-y-3 border-t border-slate-200 pt-5 dark:border-slate-700">
    <p className="text-sm text-slate-500">{l('Có thể bổ sung hồ sơ học thuật cho trang chi tiết thành viên.', 'Optionally add an academic profile to the member detail page.')}</p>
    <button type="button" className="min-h-11 text-sm font-semibold underline" onClick={() => onChange({
      url: '', affiliation: { vi: '', en: '' }, interests: [], homepageUrl: '', retrievedOn: '', metrics: null, publications: [],
    })}>{l('Thêm hồ sơ Google Scholar', 'Add a Google Scholar profile')}</button>
  </div>;
  const update = (patch: Partial<ScholarProfile>) => onChange({ ...value, ...patch });
  const number = (input: string) => input === '' ? null : Number(input);
  const metrics: ScholarMetrics = value.metrics ?? {
    citations: null, hIndex: null, i10Index: null, sinceYear: null,
    recentCitations: null, recentHIndex: null, recentI10Index: null,
  };
  const metricFields: [keyof ScholarMetrics, string][] = [
    ['citations', l('Tổng trích dẫn', 'All-time citations')], ['hIndex', 'h-index'], ['i10Index', 'i10-index'],
    ['sinceYear', l('Năm bắt đầu chỉ số gần đây', 'Recent metrics starting year')],
    ['recentCitations', l('Trích dẫn từ năm trên', 'Citations since that year')],
    ['recentHIndex', l('h-index từ năm trên', 'h-index since that year')],
    ['recentI10Index', l('i10-index từ năm trên', 'i10-index since that year')],
  ];
  const publication = (index: number, patch: Partial<ScholarPublication>) => update({
    publications: value.publications.map((item, i) => i === index ? { ...item, ...patch } : item),
  });
  return <fieldset className="space-y-5 border-t border-slate-200 pt-5 dark:border-slate-700">
    <legend className="text-base font-semibold">{l('Hồ sơ học thuật — Google Scholar', 'Academic profile — Google Scholar')}</legend>
    <p className="text-sm text-slate-500">{l('Thông tin rút gọn nằm trên thẻ thành viên; các trường dưới đây hiển thị ở trang chi tiết. Dữ liệu lấy theo ngày ghi nhận, không tự đồng bộ. Chỉ số chưa có để trống.', 'The member card shows the summary; these fields appear on the detail page. Data is a dated snapshot and does not sync automatically. Leave unknown metrics empty.')}</p>
    <TextField label={l('Liên kết hồ sơ Scholar', 'Scholar profile URL')} value={value.url} type="url" onChange={url => update({ url })} />
    <BilingualField label={l('Chức danh và đơn vị công tác', 'Position and institution')} value={value.affiliation} multiline onChange={affiliation => update({ affiliation })} />
    <BilingualList label={l('Lĩnh vực nghiên cứu', 'Research interests')} value={value.interests} onChange={interests => update({ interests })} />
    <div className="grid gap-4 md:grid-cols-2">
      <TextField label={l('Trang cá nhân (tùy chọn)', 'Personal website (optional)')} value={value.homepageUrl ?? ''} type="url" onChange={homepageUrl => update({ homepageUrl })} />
      <TextField label={l('Ngày lấy dữ liệu', 'Data retrieval date')} value={value.retrievedOn} type="date" onChange={retrievedOn => update({ retrievedOn })} />
    </div>
    <fieldset className="space-y-3"><legend className="text-sm font-semibold">{l('Chỉ số trích dẫn (tùy chọn)', 'Citation metrics (optional)')}</legend>
      <div className="grid gap-4 md:grid-cols-3">{metricFields.map(([key, label]) => <TextField key={key} label={label} type="number" value={metrics[key]?.toString() ?? ''}
        onChange={input => update({ metrics: { ...metrics, [key]: number(input) } })} />)}</div>
    </fieldset>
    <fieldset className="space-y-4"><legend className="text-sm font-semibold">{l('Bài viết từ Scholar', 'Articles from Scholar')}</legend>
      {value.publications.map((item, index) => <fieldset key={index} className="space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-700">
        <legend className="text-sm font-semibold">{l('Bài viết', 'Article')} {index + 1}</legend>
        <TextField label={l('Tiêu đề bài viết', 'Article title')} value={item.title} onChange={title => publication(index, { title })} />
        <TextField label={l('Tác giả (giữ theo nguồn)', 'Authors (as listed by the source)')} value={item.authors} onChange={authors => publication(index, { authors })} />
        <TextField label={l('Nơi công bố (giữ theo nguồn)', 'Venue (as listed by the source)')} value={item.venue} onChange={venue => publication(index, { venue })} />
        <TextField label={l('Link bài viết gốc', 'Original article URL')} value={item.url} type="url" onChange={url => publication(index, { url })} />
        <div className="grid gap-4 md:grid-cols-2">
          <TextField label={l('Năm công bố', 'Publication year')} value={item.year?.toString() ?? ''} type="number" onChange={input => publication(index, { year: number(input) })} />
          <TextField label={l('Số trích dẫn (để trống nếu nguồn không hiển thị)', 'Citations (leave empty if not displayed by the source)')} value={item.citations?.toString() ?? ''} type="number" onChange={input => publication(index, { citations: number(input) })} />
        </div>
        <button type="button" className="min-h-11 text-sm text-red-600" onClick={() => update({ publications: value.publications.filter((_, i) => i !== index) })}>{l('Bỏ bài viết', 'Remove article')}</button>
      </fieldset>)}
      <button type="button" className="min-h-11 text-sm font-semibold underline" onClick={() => update({ publications: [
        ...value.publications, { title: '', authors: '', venue: '', year: null, citations: null, url: '' },
      ] })}>{l('Thêm bài viết', 'Add article')}</button>
    </fieldset>
    <button type="button" className="min-h-11 text-sm text-red-600" onClick={() => onChange(null)}>{l('Bỏ phần hồ sơ Scholar', 'Remove the Scholar profile section')}</button>
  </fieldset>;
}
