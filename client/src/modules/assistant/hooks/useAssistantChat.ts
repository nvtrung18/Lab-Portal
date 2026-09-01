import { useMutation } from '@tanstack/react-query';

import { chatWithAssistant } from '../api';
import type { AssistantChatRequest, AssistantKey } from '../types';

export function useAssistantChat() {
  return useMutation({
    mutationFn: ({ assistantKey, request }: { assistantKey: AssistantKey; request: AssistantChatRequest }) =>
      chatWithAssistant(assistantKey, request),
  });
}
