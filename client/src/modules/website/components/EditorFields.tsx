import { useId, useState } from 'react';
import { Upload } from 'lucide-react';
import { useI18n } from '../../../shared/i18n';
import type { LocalizedText } from '../../../../../contracts/website-content';
import { uploadWebsiteImage, websiteAssetUrl } from '../api';

export const inputClass = 'min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 focus:border-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100';
export function TextField({ label, value, onChange, multiline = false, type = 'text' }: {
  label: string; value: string; onChange: (value: string) => void; multiline?: boolean; type?: string;
}) {
  const id = useId();
  return <label htmlFor={id} className="block space-y-2 text-sm font-medium">
    <span>{label}</span>
    {multiline ? <textarea id={id} className={inputClass} rows={4} value={value} onChange={e => onChange(e.target.value)} />
      : <input id={id} className={inputClass} type={type} value={value} onChange={e => onChange(e.target.value)} />}
  </label>;
}
export function BilingualField({ label, value, onChange, multiline = false }: {
  label: string; value: LocalizedText; onChange: (value: LocalizedText) => void; multiline?: boolean;
}) {
  return <fieldset className="space-y-3"><legend className="mb-3 text-sm font-semibold">{label}</legend>
    <div className="grid gap-4 md:grid-cols-2">
      <TextField label="Tiếng Việt" value={value.vi} multiline={multiline} onChange={vi => onChange({ ...value, vi })} />
      <TextField label="English" value={value.en} multiline={multiline} onChange={en => onChange({ ...value, en })} />
    </div>
  </fieldset>;
}
export function SelectField({ label, value, options, onChange, allowEmpty = true }: {
  label: string; value: string; options: { value: string; label: string }[]; onChange: (value: string) => void; allowEmpty?: boolean;
}) {
  const id = useId();
  return <label htmlFor={id} className="block space-y-2 text-sm font-medium"><span>{label}</span>
    <select id={id} className={inputClass} value={value} onChange={e => onChange(e.target.value)}>
      {allowEmpty && <option value="">—</option>}{options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>
  </label>;
}
export function BilingualList({ label, value, onChange }: {
  label: string; value: LocalizedText[]; onChange: (value: LocalizedText[]) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  return <fieldset className="space-y-4"><legend className="text-sm font-semibold">{label}</legend>
    {value.map((item, index) => <div key={index} className="space-y-2 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
      <BilingualField label={`${label} ${index + 1}`} value={item} multiline onChange={next => onChange(value.map((old, i) => i === index ? next : old))} />
      <button type="button" className="text-sm text-red-600" onClick={() => onChange(value.filter((_, i) => i !== index))}>{l('Xóa mục', 'Remove item')}</button>
    </div>)}
    <button type="button" className="min-h-11 text-sm font-semibold underline" onClick={() => onChange([...value, { vi: '', en: '' }])}>{l('Thêm mục', 'Add item')}</button>
  </fieldset>;
}
export function ImageField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const src = websiteAssetUrl(value);
  async function upload(file?: File) {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { setError(l('Ảnh không được vượt quá 5 MB.', 'Images must not exceed 5 MB.')); return; }
    setBusy(true); setError('');
    try { onChange(await uploadWebsiteImage(file)); }
    catch { setError(l('Không tải được ảnh. Vui lòng thử lại.', 'Image upload failed. Please try again.')); }
    finally { setBusy(false); }
  }
  return <div className="space-y-3">
    <TextField label={label} value={value} onChange={onChange} />
    <div className="flex flex-wrap items-center gap-4">
      {value && <img src={src} alt={label} className="h-20 w-32 rounded-md bg-slate-100 object-cover" />}
      <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-700">
        <Upload size={16} />{busy ? l('Đang tải ảnh…', 'Uploading…') : l('Tải ảnh lên', 'Upload image')}
        <input className="sr-only" type="file" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={e => { void upload(e.target.files?.[0]); e.target.value = ''; }} />
      </label>
      <span className="text-xs text-slate-500">PNG / JPEG / WebP · 5 MB</span>
    </div>
    {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
  </div>;
}
