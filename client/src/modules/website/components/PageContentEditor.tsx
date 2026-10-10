import { useState } from 'react';
import type { WebsiteContent } from '../../../../../contracts/website-content';
import { useI18n } from '../../../shared/i18n';
import { withPages } from '../content';
import { ContentLibraryEditor } from './ContentLibraryEditor';

export function PageContentEditor({ content, onChange, onPreview }: { content: WebsiteContent; onChange: (content: WebsiteContent) => void; onPreview: (content: WebsiteContent, path: string) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const editable = withPages(content);
  const pages = editable.pages;
  const [selectedId, setSelectedId] = useState(pages[0]?.id ?? '');
  const selected = pages.find(page => page.id === selectedId) ?? pages[0];
  const otherLocale = locale === 'vi' ? 'en' : 'vi';
  return <div className="space-y-6">
    <div>
      <h3 className="text-lg font-semibold">{l('Nội dung trong các tab website', 'Website tab content')}</h3>
      <p className="mt-2 max-w-2xl text-sm text-slate-500">{l('Chọn tab, tích chọn nội dung trong thư viện rồi sắp xếp theo ý muốn. Có thể thêm theo mẫu hoặc tự soạn. Menu, thông tin chung và phần giới thiệu cố định của website được giữ nguyên.', 'Choose a tab, select saved content and arrange its order. Use a preset or write your own content. The menu, general information and fixed page introductions stay unchanged.')}</p>
    </div>
    <div className="grid min-w-0 gap-6 2xl:grid-cols-[230px_minmax(0,1fr)]">
      <nav aria-label={l('Các tab website', 'Website tabs')} className="app-section-nav space-y-2 self-start rounded-lg border border-slate-200 p-2 dark:border-slate-700">
        {pages.map(page => <button key={page.id} type="button" className="flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-3 text-left text-sm" aria-current={page.id === selected?.id ? 'page' : undefined} onClick={() => setSelectedId(page.id)}>
          <span className="min-w-0 flex-1 break-words font-semibold">{page.navigationTitle[locale] || page.navigationTitle[otherLocale] || page.title[locale] || page.title[otherLocale] || l('Trang bổ sung', 'Additional page')}</span>
          <span className="text-xs text-slate-500">{page.sectionIds?.length ?? 0}</span>
        </button>)}
      </nav>
      {selected ? <div key={selected.id} className="min-w-0 space-y-6">
        <div className="border-b border-slate-200 pb-4 dark:border-slate-700">
          <p className="text-xs text-slate-500">{l('Đang cấu hình nội dung cho tab', 'Configuring content in')}</p>
          <h3 className="mt-1 text-lg font-semibold">{selected.navigationTitle[locale] || selected.navigationTitle[otherLocale] || selected.title[locale]}</h3>
        </div>
        <ContentLibraryEditor content={editable} page={selected} onChange={onChange} onPreview={onPreview} />
      </div> : <p className="py-10 text-sm text-slate-500">{l('Chưa có tab website được cấu hình.', 'No website tabs are configured.')}</p>}
    </div>
  </div>;
}
