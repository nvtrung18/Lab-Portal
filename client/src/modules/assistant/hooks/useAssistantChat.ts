import { useMutation } from '@tanstack/react-query';

import { chatWithAssistant, chatWithUnifiedAssistant, resolveAssistantAction } from '../api';
import type { AssistantChatRequest, AssistantKey, UnifiedChatRequest } from '../types';

export function useAssistantChat() {
  return useMutation({
    mutationFn: ({ assistantKey, request }: { assistantKey: AssistantKey; request: AssistantChatRequest }) =>
      chatWithAssistant(assistantKey, request),
  });
}

export function useUnifiedAssistantChat() {
  return useMutation({
    mutationFn: (request: UnifiedChatRequest) => chatWithUnifiedAssistant(request),
  });
}

export function useResolveAssistantAction() {
  return useMutation({
    mutationFn: ({ suggestionId, decision }: {
      suggestionId: number;
      decision: 'confirm' | 'cancel';
    }) => resolveAssistantAction(suggestionId, decision),
  });
}
