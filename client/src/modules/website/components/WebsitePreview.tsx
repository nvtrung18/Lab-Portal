import { useEffect, useRef, useState } from 'react';
import { ExternalLink, Monitor, Smartphone } from 'lucide-react';
import type { WebsiteContent } from '../../../../../contracts/website-content';
import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';

export function WebsitePreview({ content, path, onClose }: { content: WebsiteContent; path?: string; onClose: () => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [mobile, setMobile] = useState(false);
  const [connected, setConnected] = useState(false);
  const frame = useRef<HTMLIFrameElement>(null);
  const popup = useRef<Window | null>(null);
  const contentRef = useRef(content);
  contentRef.current = content;
  const origin = window.location.origin;
  const previewUrl = `${path ?? `/${locale}`}?preview=1`;
  function send(target: Window | null) { target?.postMessage({ type: 'labportal:website-preview', content: contentRef.current }, origin); }
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.origin !== origin || (event.source !== frame.current?.contentWindow && event.source !== popup.current)) return;
      if (event.data?.type === 'labportal:website-preview-ready') { setConnected(true); send(event.source as Window); }
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, [origin]);
  useEffect(() => { send(frame.current?.contentWindow ?? null); send(popup.current); }, [content, origin]);
  return <Modal title={l('Xem trước website giới thiệu', 'Laboratory website preview')} size="full" onClose={onClose}>
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-slate-500">{l('Bản xem trước dùng nội dung đang chỉnh sửa, kể cả thay đổi chưa lưu.', 'Preview includes the current edits, including unsaved changes.')}</p>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant={mobile ? 'outline' : 'secondary'} onClick={() => setMobile(false)}><Monitor size={16} />Desktop</Button>
        <Button size="sm" variant={mobile ? 'secondary' : 'outline'} onClick={() => setMobile(true)}><Smartphone size={16} />Mobile</Button>
        <Button size="sm" variant="outline" onClick={() => { popup.current = window.open(previewUrl, 'labportal-website-preview'); }}><ExternalLink size={16} />{l('Mở riêng', 'Open window')}</Button>
      </div>
    </div>
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-slate-100 p-2 dark:border-slate-700 dark:bg-slate-800"><iframe ref={frame} title={l('Bản xem trước website', 'Website preview')} src={previewUrl} className={`mx-auto h-[min(70dvh,48rem)] w-full border-0 bg-white ${mobile ? 'max-w-[390px]' : ''}`} /></div>
    <p className="mt-3 text-xs text-slate-500" role="status">{connected ? l('Đã kết nối bản xem trước.', 'Preview connected.') : l('Đang kết nối website để xem trước…', 'Connecting to the preview website…')}</p>
  </Modal>;
}
