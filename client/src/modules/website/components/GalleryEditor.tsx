import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, ImagePlus } from 'lucide-react';
import type { ArticleImages, GalleryImage } from '../../../../../contracts/website-content';
import { appendGalleryImages, emptyImageText, MAX_GALLERY_IMAGES, moveGalleryImage, promoteGalleryImage, validWebsiteImageFile } from '../../../../../contracts/website-gallery';
import { useI18n } from '../../../shared/i18n';
import { Button } from '../../../shared/components';
import { uploadWebsiteImage, websiteAssetUrl } from '../api';
import { BilingualField, TextField } from './EditorFields';

export function GalleryEditor({ value, onChange, onBusyChange }: {
  value: ArticleImages; onChange: (value: ArticleImages) => void; onBusyChange?: (busy: boolean) => void;
}) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState('');
  const [error, setError] = useState('');
  const latest = useRef(value);
  const callbacks = useRef({ onChange, onBusyChange });
  const mounted = useRef(true);
  const uploading = useRef(false);
  latest.current = value;
  callbacks.current = { onChange, onBusyChange };
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; callbacks.current.onBusyChange?.(false); };
  }, []);

  function change(next: ArticleImages) {
    // Emit only image fields; never overwrite concurrently edited article text.
    const patch = { image: next.image, imageAlt: next.imageAlt, imageCaption: next.imageCaption, gallery: next.gallery };
    latest.current = { ...latest.current, ...patch };
    callbacks.current.onChange(patch);
  }
  async function upload(files: File[]) {
    if (!files.length || uploading.current) return;
    const slots = MAX_GALLERY_IMAGES - (latest.current.gallery?.length ?? 0) + (latest.current.image ? 0 : 1);
    if (files.length > slots) {
      setError(l(`Chỉ còn ${slots} vị trí ảnh. Tối đa 15 ảnh bổ sung và 1 cover.`, `Only ${slots} image slots remain. Maximum: 15 additional images and 1 cover.`)); return;
    }
    if (files.some(file => !validWebsiteImageFile(file))) {
      setError(l('Mỗi ảnh phải là PNG, JPEG hoặc WebP, có dữ liệu và tối đa 5 MB. Chưa có ảnh nào được tải lên.', 'Each image must be PNG, JPEG or WebP, nonempty and at most 5 MB. No images were uploaded.')); return;
    }
    uploading.current = true; setBusy(true); setError(''); callbacks.current.onBusyChange?.(true);
    const failed: string[] = [];
    try {
      for (let index = 0; index < files.length; index++) {
        if (!mounted.current) break;
        setProgress(`${index + 1}/${files.length}`);
        try {
          const src = await uploadWebsiteImage(files[index]);
          if (mounted.current) change(appendGalleryImages(latest.current, [{ id: crypto.randomUUID(), src, alt: emptyImageText(), caption: emptyImageText() }]));
        } catch { failed.push(files[index].name); }
      }
      if (mounted.current && failed.length) setError(l(`Không tải được: ${failed.join(', ')}. Các ảnh thành công được giữ lại.`, `Upload failed: ${failed.join(', ')}. Successful images were retained.`));
    } finally {
      uploading.current = false;
      if (mounted.current) { setBusy(false); setProgress(''); callbacks.current.onBusyChange?.(false); }
    }
  }
  function metadata(image: GalleryImage) {
    return <>
      <BilingualField label={l('Alt text (mô tả ảnh)', 'Alt text (image description)')} maxLength={2000} value={image.alt} onChange={alt => change({ ...value, gallery: value.gallery!.map(item => item.id === image.id ? { ...item, alt } : item) })} />
      <BilingualField label={l('Chú thích', 'Caption')} maxLength={2000} value={image.caption} onChange={caption => change({ ...value, gallery: value.gallery!.map(item => item.id === image.id ? { ...item, caption } : item) })} />
    </>;
  }
  return <div className="space-y-4" onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void upload(Array.from(event.dataTransfer.files)); }}>
    <fieldset disabled={busy} aria-busy={busy} className="min-w-0 space-y-5">
      <legend className="mb-3 font-semibold">{l('Ảnh bài viết', 'Article images')}</legend>
      <p className="text-sm text-slate-500">{l('Chọn hoặc kéo thả nhiều ảnh. Ảnh đầu tiên làm cover nếu chưa có. Tối đa 15 ảnh bổ sung; PNG / JPEG / WebP, 5 MB/ảnh.', 'Select or drop multiple images. The first becomes the cover if none exists. Up to 15 additional images; PNG / JPEG / WebP, 5 MB each.')}</p>
      <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-md border border-slate-300 px-4 py-2 text-sm dark:border-slate-700 focus-within:ring-2 focus-within:ring-slate-400">
        <ImagePlus size={18} />{l('Thêm nhiều ảnh', 'Add multiple images')}
        <input type="file" multiple accept="image/png,image/jpeg,image/webp" className="sr-only" aria-label={l('Thêm nhiều ảnh', 'Add multiple images')}
          onChange={event => { const files = Array.from(event.target.files ?? []); event.target.value = ''; void upload(files); }} />
      </label>
      <div className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
        <h4 className="font-semibold">{l('Ảnh đại diện (cover)', 'Cover image')}</h4>
        <TextField label={l('Đường dẫn ảnh đại diện', 'Cover image URL')} value={value.image} onChange={image => change({ ...value, image })} maxLength={2000} />
        {value.image && <>
          <img src={websiteAssetUrl(value.image)} alt={value.imageAlt?.[locale] || l('Ảnh đại diện', 'Cover image')} className="h-36 w-full rounded-md bg-slate-100 object-contain dark:bg-slate-900" />
          <BilingualField label={l('Alt text ảnh đại diện', 'Cover alt text')} maxLength={2000} value={value.imageAlt ?? emptyImageText()} onChange={imageAlt => change({ ...value, imageAlt })} />
          <BilingualField label={l('Chú thích ảnh đại diện', 'Cover caption')} maxLength={2000} value={value.imageCaption ?? emptyImageText()} onChange={imageCaption => change({ ...value, imageCaption })} />
          <Button type="button" size="sm" variant="outline" onClick={() => change({ ...value, image: '', imageAlt: emptyImageText(), imageCaption: emptyImageText() })}>{l('Bỏ ảnh đại diện', 'Remove cover')}</Button>
        </>}
      </div>
      <h4 className="font-semibold">{l('Ảnh bổ sung', 'Additional images')} ({value.gallery?.length ?? 0}/{MAX_GALLERY_IMAGES})</h4>
      <ol className="space-y-4">{(value.gallery ?? []).map((image, index) => <li key={image.id} className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
        <div className="flex flex-wrap items-center gap-3">
          <img src={websiteAssetUrl(image.src)} alt={image.alt[locale]} className="h-24 w-32 rounded-md bg-slate-100 object-contain dark:bg-slate-900" />
          <span>{l('Ảnh', 'Image')} {index + 1}</span>
          <Button type="button" size="sm" variant="outline" disabled={index === 0} aria-label={`${l('Đưa ảnh lên', 'Move image up')} ${index + 1}`} onClick={() => change({ ...value, gallery: moveGalleryImage(value.gallery!, image.id, -1) })}><ArrowUp size={16} /></Button>
          <Button type="button" size="sm" variant="outline" disabled={index === value.gallery!.length - 1} aria-label={`${l('Đưa ảnh xuống', 'Move image down')} ${index + 1}`} onClick={() => change({ ...value, gallery: moveGalleryImage(value.gallery!, image.id, 1) })}><ArrowDown size={16} /></Button>
          <Button type="button" size="sm" variant="outline" onClick={() => change(promoteGalleryImage(value, image.id))}>{l('Đặt làm cover', 'Use as cover')}</Button>
          <Button type="button" size="sm" variant="outline" onClick={() => change({ ...value, gallery: value.gallery!.filter(item => item.id !== image.id) })}>{l('Bỏ ảnh', 'Remove image')}</Button>
        </div>
        {metadata(image)}
      </li>)}</ol>
      <p className="text-xs text-slate-500">{l('Bỏ ảnh chỉ gỡ liên kết khỏi bài, không xóa file. Hoàn tất upload rồi Áp dụng → Lưu nháp → Xem trước → Xuất bản.', 'Removing an image only unlinks it; the file is retained. Finish uploads, then Apply → Save Draft → Preview → Publish.')}</p>
    </fieldset>
    {busy && <p role="status" className="text-sm">{l('Đang tải ảnh', 'Uploading images')} {progress}</p>}
    {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
  </div>;
}
