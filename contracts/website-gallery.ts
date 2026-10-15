import type { ArticleImages, GalleryImage } from './website-content';

export const MAX_GALLERY_IMAGES = 15;
export const MAX_WEBSITE_IMAGE_BYTES = 5 * 1024 * 1024;
export const WEBSITE_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
export const emptyImageText = () => ({ vi: '', en: '' });

/** Read legacy covers without rewriting the saved CMS document. */
export function articleImages(value: ArticleImages): GalleryImage[] {
  return [
    ...(value.image ? [{ id: 'cover', src: value.image, alt: value.imageAlt ?? emptyImageText(), caption: value.imageCaption ?? emptyImageText() }] : []),
    ...(value.gallery ?? []),
  ];
}

export function appendGalleryImages<T extends ArticleImages>(value: T, uploaded: GalleryImage[]): T {
  const next = { ...value, gallery: [...(value.gallery ?? [])] };
  const additions = [...uploaded];
  if (!next.image && additions.length) {
    const cover = additions.shift()!;
    next.image = cover.src; next.imageAlt = cover.alt; next.imageCaption = cover.caption;
  }
  if (next.gallery.length + additions.length > MAX_GALLERY_IMAGES) throw new Error('GALLERY_LIMIT');
  next.gallery.push(...additions);
  return next;
}

/** Swap instead of discarding the old cover or its bilingual metadata. */
export function promoteGalleryImage<T extends ArticleImages>(value: T, id: string): T {
  const selected = value.gallery?.find(item => item.id === id);
  if (!selected) return value;
  const gallery = value.gallery!.flatMap(item => item.id !== id ? [item] : value.image
    ? [{ id: selected.id, src: value.image, alt: value.imageAlt ?? emptyImageText(), caption: value.imageCaption ?? emptyImageText() }] : []);
  return { ...value, image: selected.src, imageAlt: selected.alt, imageCaption: selected.caption, gallery };
}

export function moveGalleryImage(images: GalleryImage[], id: string, offset: -1 | 1): GalleryImage[] {
  const index = images.findIndex(item => item.id === id);
  const destination = index + offset;
  if (index < 0 || destination < 0 || destination >= images.length) return images;
  const next = [...images];
  [next[index], next[destination]] = [next[destination], next[index]];
  return next;
}

export function validWebsiteImageFile(file: { size: number; type: string }) {
  return file.size > 0 && file.size <= MAX_WEBSITE_IMAGE_BYTES && WEBSITE_IMAGE_TYPES.includes(file.type);
}
