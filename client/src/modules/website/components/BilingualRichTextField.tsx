import { useMemo, useState } from 'react';
import type { Locale, LocalizedText } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { paragraphsToHtml } from '../richText';
import { RichTextEditor } from './RichTextEditor';

export function BilingualRichTextField({ label, value, paragraphs, onChange, variant = 'article', disabled = false }: {
  label: string; value?: LocalizedText | null; paragraphs: LocalizedText[]; onChange: (value: LocalizedText) => void;
  variant?: 'article' | 'intro'; disabled?: boolean;
}) {
  const { locale } = useI18n();
  const [language, setLanguage] = useState<Locale>(locale);
  const content = useMemo(() => value ?? paragraphsToHtml(paragraphs), [value, paragraphs]);
  return <fieldset className="space-y-4">
    <legend className="mb-3 text-sm font-semibold">{label}</legend>
    <div role="group" aria-label={locale === 'vi' ? 'Ngôn ngữ soạn thảo' : 'Editing language'} className="flex gap-2">
      {(['vi', 'en'] as const).map(item => <button type="button" key={item} aria-pressed={language === item} disabled={disabled}
        className={`min-h-11 rounded-md border px-4 py-2 text-sm ${language === item ? 'border-slate-900 bg-slate-900 text-white dark:border-white dark:bg-white dark:text-slate-950' : 'border-slate-300 dark:border-slate-700'}`}
        onClick={() => setLanguage(item)}>{item === 'vi' ? 'Tiếng Việt' : 'English'}</button>)}
    </div>
    <RichTextEditor key={language} variant={variant} disabled={disabled} label={language === 'vi' ? 'Nội dung tiếng Việt' : 'English content'} value={content[language]} onChange={html => onChange({ ...content, [language]: html })} />
  </fieldset>;
}
