import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { EditorContent, useEditor, useEditorState } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { Bold, Italic, Underline, List, ListOrdered, Link2, Unlink, ImagePlus, Undo2, Redo2, Quote, Upload } from 'lucide-react';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { uploadWebsiteImage, websiteAssetUrl } from '../api';
import { isWebsiteImage, isWebsiteLink, sanitizeWebsiteHtml } from '../richText';
import { inputClass } from './EditorFields';
import './rich-text.css';

const WebsiteImage = Image.extend({
  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement('img');
      const updateImage = (attrs: typeof node.attrs) => {
        dom.src = websiteAssetUrl(String(attrs.src ?? ''));
        dom.alt = String(attrs.alt ?? '');
        dom.title = String(attrs.title ?? '');
      };
      updateImage(node.attrs);
      return { dom, update(nextNode) {
        if (nextNode.type !== node.type) return false;
        updateImage(nextNode.attrs); return true;
      } };
    };
  },
});

export function RichTextEditor({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const labelId = useId();
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const [panel, setPanel] = useState<'link' | 'image' | null>(null);
  const [url, setUrl] = useState('');
  const [alt, setAlt] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [initialContent] = useState(() => sanitizeWebsiteHtml(value));
  const editor = useEditor({
    extensions: [StarterKit.configure({ heading: { levels: [2, 3] }, code: false, codeBlock: false,
      horizontalRule: false, link: { openOnClick: false, defaultProtocol: 'https', isAllowedUri: isWebsiteLink,
        HTMLAttributes: { target: '_blank', rel: 'noopener noreferrer' } } }), WebsiteImage.configure({ allowBase64: false })],
    content: initialContent,
    immediatelyRender: false,
    shouldRerenderOnTransaction: false,
    editorProps: { attributes: { role: 'textbox', 'aria-multiline': 'true', 'aria-labelledby': labelId },
      transformPastedHTML: html => sanitizeWebsiteHtml(html) },
    onUpdate: ({ editor: current }) => onChangeRef.current(current.isEmpty ? '' : sanitizeWebsiteHtml(current.getHTML())),
  });
  const state = useEditorState({ editor, selector: ({ editor: current }) => current ? {
    bold: current.isActive('bold'), italic: current.isActive('italic'), underline: current.isActive('underline'),
    h2: current.isActive('heading', { level: 2 }), h3: current.isActive('heading', { level: 3 }),
    bullets: current.isActive('bulletList'), numbers: current.isActive('orderedList'), quote: current.isActive('blockquote'),
    link: current.isActive('link'), undo: current.can().undo(), redo: current.can().redo(),
  } : null });
  useEffect(() => {
    if (!editor || editor.isDestroyed) return;
    const html = sanitizeWebsiteHtml(value);
    const current = editor.isEmpty ? '' : sanitizeWebsiteHtml(editor.getHTML());
    if (current !== html) editor.commands.setContent(html, { emitUpdate: false });
  }, [editor, value]);
  useEffect(() => { if (editor && !editor.isDestroyed) editor.setEditable(!busy); }, [editor, busy]);

  function openPanel(next: 'link' | 'image') {
    if (!editor) return;
    const href: unknown = editor.getAttributes('link').href;
    setUrl(next === 'link' && typeof href === 'string' ? href : '');
    setAlt(''); setError(''); setPanel(next);
  }
  function insert() {
    if (!editor || editor.isDestroyed) return;
    const address = url.trim();
    if (panel === 'image') {
      if (!isWebsiteImage(address)) { setError(l('Nhập đường dẫn ảnh http(s) hoặc đường dẫn nội bộ bắt đầu bằng /.', 'Use an http(s) image URL or an internal path starting with /.')); return; }
      editor.chain().focus().setImage({ src: address, alt }).run();
    } else {
      if (!isWebsiteLink(address)) { setError(l('Nhập liên kết http(s), đường dẫn nội bộ hoặc #mục.', 'Use an http(s) URL, internal path or #anchor.')); return; }
      if (editor.state.selection.empty && !editor.isActive('link')) {
        editor.chain().focus().insertContent({ type: 'text', text: address, marks: [{ type: 'link', attrs: { href: address } }] }).run();
      } else editor.chain().focus().extendMarkRange('link').setLink({ href: address }).run();
    }
    setPanel(null); setError('');
  }
  async function upload(file?: File) {
    if (!file || !editor) return;
    if (file.size > 5 * 1024 * 1024) { setError(l('Ảnh không được vượt quá 5 MB.', 'Images must not exceed 5 MB.')); return; }
    setBusy(true); setError('');
    try {
      const src = await uploadWebsiteImage(file);
      if (!editor.isDestroyed) { editor.chain().focus().setImage({ src, alt }).run(); setPanel(null); }
    } catch { setError(l('Không tải được ảnh. Vui lòng thử lại.', 'Image upload failed. Please retry.')); }
    finally { setBusy(false); }
  }
  const control = (name: string, active: boolean | undefined, action: () => void, icon: ReactNode, disabled = false) =>
    <button type="button" key={name} title={name} aria-label={name} aria-pressed={active} disabled={!editor || busy || disabled}
      className={`website-editor-control ${active ? 'is-active' : ''}`} onMouseDown={event => event.preventDefault()} onClick={action}>{icon}</button>;
  return <section className="website-rich-editor space-y-2">
    <h4 id={labelId} className="text-sm font-semibold">{label}</h4>
    <div className="website-editor-frame">
      <div role="group" aria-label={l('Định dạng nội dung', 'Content formatting')} className="website-editor-toolbar">
        {control(l('In đậm', 'Bold'), state?.bold, () => editor?.chain().focus().toggleBold().run(), <Bold size={17} />)}
        {control(l('In nghiêng', 'Italic'), state?.italic, () => editor?.chain().focus().toggleItalic().run(), <Italic size={17} />)}
        {control(l('Gạch chân', 'Underline'), state?.underline, () => editor?.chain().focus().toggleUnderline().run(), <Underline size={17} />)}
        {control(l('Tiêu đề phụ cấp 2', 'Heading 2'), state?.h2, () => editor?.chain().focus().toggleHeading({ level: 2 }).run(), 'H2')}
        {control(l('Tiêu đề phụ cấp 3', 'Heading 3'), state?.h3, () => editor?.chain().focus().toggleHeading({ level: 3 }).run(), 'H3')}
        {control(l('Danh sách', 'Bullet list'), state?.bullets, () => editor?.chain().focus().toggleBulletList().run(), <List size={17} />)}
        {control(l('Danh sách đánh số', 'Numbered list'), state?.numbers, () => editor?.chain().focus().toggleOrderedList().run(), <ListOrdered size={17} />)}
        {control(l('Trích dẫn', 'Quote'), state?.quote, () => editor?.chain().focus().toggleBlockquote().run(), <Quote size={17} />)}
        {control(l('Chèn / sửa liên kết', 'Insert / edit link'), state?.link, () => openPanel('link'), <Link2 size={17} />)}
        {control(l('Gỡ liên kết', 'Remove link'), undefined, () => editor?.chain().focus().extendMarkRange('link').unsetLink().run(), <Unlink size={17} />, !state?.link)}
        {control(l('Chèn ảnh', 'Insert image'), undefined, () => openPanel('image'), <ImagePlus size={17} />)}
        {control(l('Hoàn tác', 'Undo'), undefined, () => editor?.chain().focus().undo().run(), <Undo2 size={17} />, !state?.undo)}
        {control(l('Làm lại', 'Redo'), undefined, () => editor?.chain().focus().redo().run(), <Redo2 size={17} />, !state?.redo)}
      </div>
      <EditorContent editor={editor} className="website-rich-content" />
    </div>
    {panel && <div className="space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-700">
      <label className="block space-y-2 text-sm"><span>{panel === 'image' ? l('Đường dẫn ảnh', 'Image URL') : l('Đường dẫn liên kết', 'Link URL')}</span><input autoFocus className={inputClass} value={url} disabled={busy} onChange={event => setUrl(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); insert(); } }} /></label>
      {panel === 'image' && <label className="block space-y-2 text-sm"><span>{l('Mô tả ảnh', 'Image description')}</span><input className={inputClass} value={alt} disabled={busy} onChange={event => setAlt(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); insert(); } }} /></label>}
      <div className="flex flex-wrap gap-2">
        <Button size="sm" disabled={busy} onClick={insert}>{l('Chèn vào nội dung', 'Insert into content')}</Button>
        {panel === 'image' && <label className="website-editor-upload"><Upload size={16} />{busy ? l('Đang tải ảnh…', 'Uploading…') : l('Tải ảnh lên', 'Upload image')}<input type="file" className="sr-only" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={event => { void upload(event.target.files?.[0]); event.target.value = ''; }} /></label>}
        <Button size="sm" variant="outline" disabled={busy} onClick={() => setPanel(null)}>{l('Đóng', 'Close')}</Button>
      </div>
    </div>}
    {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
    <p className="text-xs text-slate-500">{l('Viết trực tiếp, bôi đen để định dạng. Dùng Chèn ảnh để thêm ảnh giữa bài; có thể hoàn tác các thay đổi.', 'Write directly and select text to format it. Insert images within the article and undo changes.')}</p>
  </section>;
}
