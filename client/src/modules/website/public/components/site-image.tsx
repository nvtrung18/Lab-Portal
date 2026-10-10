import type { ComponentProps } from 'react';
import { useLab } from './lab-provider';
import { websiteAssetUrl } from '../../api';

export default function SiteImage({ src, alt, fill, style, loading = 'lazy', ...props }: Omit<ComponentProps<'img'>, 'src'> & { src: string; fill?: boolean }) {
  const { media } = useLab();
  const resolved = media[src] ?? src;
  if (!resolved) return null;
  return <img {...props} src={websiteAssetUrl(resolved)} alt={alt ?? ''} loading={loading}
    style={fill ? { position: 'absolute', inset: 0, width: '100%', height: '100%', ...style } : style} />;
}
