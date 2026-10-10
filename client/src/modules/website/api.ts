import { API_BASE_URL, apiClient } from '../../shared/api';
import type { Response } from '../../shared/types';
import type { PublishedWebsiteContent, WebsiteSaveRequest, WebsiteState } from '../../../../contracts/website-content';
import { withPages } from './content';

export interface WebsiteMemberSource {
  id: number;
  fullName: string | null;
  username: string;
  email: string;
  status: 'ACTIVE' | 'PENDING_VERIFICATION' | 'INACTIVE' | 'SUSPENDED';
}

export async function getWebsiteMemberSources(): Promise<WebsiteMemberSource[]> {
  const response = await apiClient.get<Response<WebsiteMemberSource[]>>('/api/admin/website/member-sources');
  return response.data.data;
}

export async function getWebsiteState(): Promise<WebsiteState> {
  const response = await apiClient.get<Response<WebsiteState>>('/api/admin/website');
  const state = response.data.data;
  return { ...state, draft: state.draft ? withPages(state.draft) : null,
    published: state.published ? withPages(state.published) : null };
}
export async function saveWebsite(request: WebsiteSaveRequest, publish: boolean): Promise<WebsiteState> {
  const payload = { ...request, content: withPages(request.content) };
  const response = publish
    ? await apiClient.post<Response<WebsiteState>>('/api/admin/website/publish', payload)
    : await apiClient.put<Response<WebsiteState>>('/api/admin/website/draft', payload);
  const state = response.data.data;
  return { ...state, draft: state.draft ? withPages(state.draft) : null,
    published: state.published ? withPages(state.published) : null };
}
export async function uploadWebsiteImage(file: File): Promise<string> {
  const data = new FormData();
  data.append('file', file);
  const response = await apiClient.post<Response<{ url: string }>>('/api/admin/website/images', data);
  return response.data.data.url;
}
export async function getPublishedWebsite(signal?: AbortSignal): Promise<PublishedWebsiteContent> {
  const response = await fetch(`${API_BASE_URL}/api/public/website`, {
    headers: { Accept: 'application/json' }, signal, credentials: 'omit', cache: 'no-store',
  });
  if (!response.ok) throw new Error('Website content is unavailable');
  const result: Response<PublishedWebsiteContent> = await response.json();
  return result.data;
}

export function websiteAssetUrl(value: string): string {
  if (value.startsWith('/api/')) return `${API_BASE_URL}${value}`;
  if (/^\/(images|fonts)\//.test(value)) return `/lab-website${value}`;
  return value;
}
