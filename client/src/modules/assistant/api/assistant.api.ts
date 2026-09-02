import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { AssistantChatRequest, AssistantChatResponse, AssistantKey } from '../types';

export async function chatWithAssistant(
  assistantKey: AssistantKey,
  request: AssistantChatRequest,
): Promise<AssistantChatResponse> {
  const requestId = typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}`;
  const response = await apiClient.post<Response<AssistantChatResponse>>(
    `/api/ai/assistants/${assistantKey}/chat`,
    request,
    { headers: { 'X-Request-Id': requestId } },
  );
  return response.data.data;
}
