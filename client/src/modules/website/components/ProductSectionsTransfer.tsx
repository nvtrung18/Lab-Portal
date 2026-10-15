import { useState } from 'react';
import type { WebsiteContent } from '../../../../../contracts/website-content';
import { productTransferCandidates, transferProductSections } from '../../../../../contracts/website-product-transfer';
import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';

export function ProductSectionsTransfer({ content, onChange }: { content: WebsiteContent; onChange: (content: WebsiteContent) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const candidates = productTransferCandidates(content);
  const [selected, setSelected] = useState<string[] | null>(null);
  const [error, setError] = useState('');
  if (!candidates.length) return null;
  return <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-slate-900">
    <p className="mb-3 text-sm">{l('Có nội dung sản phẩm đang được chọn trong Tin & Sự kiện. Bạn có thể chuyển sang Sản phẩm, giữ nguyên nội dung và ảnh.', 'Product sections are currently selected in News. Move them to Products while preserving their content and images.')}</p>
    <Button variant="outline" onClick={() => { setError(''); setSelected(candidates.map(item => item.id)); }}>{l('Chuyển nội dung từ Tin & Sự kiện', 'Move sections from News')}</Button>
    {selected && <Modal title={l('Xác nhận chuyển vào bản nháp?', 'Confirm moving sections in this draft?')} onClose={() => setSelected(null)}>
      <p className="mb-4 text-sm">{l('Thao tác bỏ chọn các mục dưới đây trong News và chọn chúng trong Products. HTML, ảnh, liên kết, hai ngôn ngữ và thư viện được giữ nguyên. Chưa thay đổi website công khai; hãy xem trước, lưu nháp rồi xuất bản khi đã kiểm tra.', 'This unselects the following sections in News and selects them in Products. HTML, images, links, both languages and the library are preserved. The public website changes only after you preview and publish.')}</p>
      {candidates.map(item => <label key={item.id} className="flex min-h-11 items-center gap-3 text-sm">
        <input type="checkbox" checked={selected.includes(item.id)} onChange={event => setSelected(event.target.checked ? [...selected, item.id] : selected.filter(id => id !== item.id))} />
        {item.title[locale] || item.title.vi || item.title.en}
      </label>)}
      {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}
      <div className="mt-6 flex justify-end gap-3"><Button variant="outline" onClick={() => setSelected(null)}>{l('Hủy', 'Cancel')}</Button>
        <Button disabled={!selected.length} onClick={() => {
          try { onChange(transferProductSections(content, selected)); setSelected(null); }
          catch { setError(l('Nội dung đã thay đổi. Đóng hộp thoại và chọn lại.', 'The sections have changed. Close this dialog and select again.')); }
        }}>{l('Chuyển vào bản nháp', 'Move in draft')}</Button></div>
    </Modal>}
  </div>;
}
