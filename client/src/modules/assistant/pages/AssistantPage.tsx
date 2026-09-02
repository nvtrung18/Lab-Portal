import axios from 'axios';
import { Bot, BookOpen, Send, ShieldCheck, Sparkles } from 'lucide-react';
import { FormEvent, useMemo, useState } from 'react';

import { getStoredRole } from '../../../shared/api';
import { Button } from '../../../shared/components';
import type { Response } from '../../../shared/types';
import { useAssistantChat } from '../hooks';
import type {
  AssistantCapability,
  AssistantChatResponse,
  AssistantKey,
} from '../types';

interface CapabilityOption {
  value: AssistantCapability;
  label: string;
  assistantKey: AssistantKey;
  resourceLabel?: string;
  parentResourceLabel?: string;
}

const capabilityOptions: CapabilityOption[] = [
  { value: 'ADMIN_SYSTEM_SUMMARY', label: 'Tóm tắt trạng thái hệ thống', assistantKey: 'ADMIN_ASSISTANT' },
  { value: 'ADMIN_AUDIT_SUMMARY', label: 'Tóm tắt nhật ký kiểm toán', assistantKey: 'ADMIN_ASSISTANT' },
  { value: 'ADMIN_USER_STATUS_LOOKUP', label: 'Tra cứu trạng thái người dùng', assistantKey: 'ADMIN_ASSISTANT', resourceLabel: 'ID người dùng' },
  { value: 'ADMIN_CONFIG_DRAFT', label: 'Soạn thảo đề xuất cấu hình', assistantKey: 'ADMIN_ASSISTANT' },
  { value: 'ADMIN_ACCOUNT_ACTION_DRAFT', label: 'Soạn thảo thao tác tài khoản', assistantKey: 'ADMIN_ASSISTANT', resourceLabel: 'ID người dùng' },
  { value: 'LAB_POLICY_READ', label: 'Tra cứu chính sách PTN', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID PTN' },
  { value: 'LAB_SLOT_READ', label: 'Tra cứu khung giờ', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID khung giờ' },
  { value: 'LAB_OWN_BOOKING_READ', label: 'Tra cứu booking của tôi', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID booking' },
  { value: 'LAB_MANAGED_SUMMARY', label: 'Tóm tắt PTN đang quản lý', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID PTN' },
  { value: 'LAB_BOOKING_DRAFT', label: 'Soạn thảo booking', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID khung giờ' },
  { value: 'LAB_CHECKIN_GUIDANCE', label: 'Hướng dẫn check-in', assistantKey: 'LAB_ASSISTANT', resourceLabel: 'ID booking' },
  { value: 'RESEARCH_PROJECT_SUMMARY', label: 'Tóm tắt dự án nghiên cứu', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID dự án' },
  { value: 'RESEARCH_GROUP_SUMMARY', label: 'Tóm tắt nhóm nghiên cứu', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID nhóm' },
  { value: 'RESEARCH_ASSIGNED_TASK_READ', label: 'Tra cứu nhiệm vụ được giao', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID nhiệm vụ' },
  { value: 'RESEARCH_TASK_PROPOSAL_DRAFT', label: 'Soạn đề xuất nhiệm vụ', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID nhóm', parentResourceLabel: 'ID dự án' },
  { value: 'RESEARCH_TASK_SUGGESTION_DRAFT', label: 'Gợi ý xử lý nhiệm vụ', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID nhiệm vụ' },
  { value: 'RESEARCH_REPORT_REVIEW_DRAFT', label: 'Soạn nhận xét báo cáo', assistantKey: 'RESEARCH_ASSISTANT', resourceLabel: 'ID báo cáo' },
];

function normalizeRole(role: string | null) {
  return role?.replace(/^ROLE_/, '').toUpperCase() ?? '';
}

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<Response<unknown>> | undefined;
    return body?.message ?? 'Trợ lý chưa sẵn sàng. Vui lòng kiểm tra model hoặc thử lại sau.';
  }
  return 'Không thể kết nối với trợ lý AI.';
}

function AssistantAnswer({ response }: { response: AssistantChatResponse }) {
  return (
    <article aria-live="polite" className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center gap-2 text-sm font-semibold text-slate-950 dark:text-white">
        <Sparkles aria-hidden="true" className="h-5 w-5" />
        Phản hồi có kiểm soát
      </div>
      <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-700 dark:text-slate-200">{response.answer}</p>
      <div className="mt-5 flex flex-wrap gap-2 text-xs text-slate-500 dark:text-slate-400">
        <span className="rounded-full bg-slate-100 px-2 py-1 dark:bg-slate-800">Prompt: {response.promptTokens} tokens</span>
        <span className="rounded-full bg-slate-100 px-2 py-1 dark:bg-slate-800">Completion: {response.completionTokens} tokens</span>
      </div>
      {response.citations.length ? (
        <section className="mt-5 border-t border-slate-200 pt-4 dark:border-slate-800">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-950 dark:text-white">
            <BookOpen aria-hidden="true" className="h-4 w-4" /> Nguồn được cấp quyền
          </h2>
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {response.citations.map((citation) => (
              <li className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300" key={`${citation.documentId}-${citation.chunkIndex}`}>
                <p className="font-semibold text-slate-900 dark:text-white">{citation.sourceType} · {citation.resourceId}</p>
                <p className="mt-1">Phiên bản {citation.version}, đoạn {citation.chunkIndex + 1}{citation.pageNumber ? `, trang ${citation.pageNumber}` : ''}</p>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </article>
  );
}

export function AssistantPage() {
  const role = normalizeRole(getStoredRole());
  const availableOptions = useMemo(
    () => capabilityOptions.filter((option) => role === 'ADMIN' ? option.assistantKey === 'ADMIN_ASSISTANT' : option.assistantKey !== 'ADMIN_ASSISTANT'),
    [role],
  );
  const [capability, setCapability] = useState<AssistantCapability>(availableOptions[0]?.value ?? 'LAB_POLICY_READ');
  const [input, setInput] = useState('');
  const [resourceId, setResourceId] = useState('');
  const [parentResourceId, setParentResourceId] = useState('');
  const [validationError, setValidationError] = useState('');
  const chatMutation = useAssistantChat();
  const selected = availableOptions.find((option) => option.value === capability) ?? availableOptions[0];

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedInput = input.trim();
    if (!selected || !normalizedInput) {
      setValidationError('Vui lòng nhập yêu cầu cho trợ lý.');
      return;
    }
    if (selected.resourceLabel && Number(resourceId) <= 0) {
      setValidationError(`Vui lòng nhập ${selected.resourceLabel.toLowerCase()} hợp lệ.`);
      return;
    }
    if (selected.parentResourceLabel && Number(parentResourceId) <= 0) {
      setValidationError(`Vui lòng nhập ${selected.parentResourceLabel.toLowerCase()} hợp lệ.`);
      return;
    }
    setValidationError('');
    chatMutation.mutate({
      assistantKey: selected.assistantKey,
      request: {
        input: normalizedInput,
        capability: selected.value,
        ...(selected.resourceLabel ? { resourceId: Number(resourceId) } : {}),
        ...(selected.parentResourceLabel ? { parentResourceId: Number(parentResourceId) } : {}),
      },
    });
  };

  return (
    <section className="mx-auto max-w-6xl">
      <header className="mb-6">
        <p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500"><Bot aria-hidden="true" className="h-4 w-4" /> Smart Research Lab</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-950 dark:text-white">Trợ lý AI</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300">Trợ lý chỉ đọc dữ liệu trong phạm vi bạn được cấp quyền. Nội dung soạn thảo không tự động thay đổi dữ liệu nghiệp vụ.</p>
      </header>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)]">
        <form className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900" onSubmit={handleSubmit}>
          <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="assistant-capability">Nghiệp vụ hỗ trợ</label>
          <select id="assistant-capability" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-950 outline-none focus-visible:ring-2 focus-visible:ring-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white" value={capability} onChange={(event) => { setCapability(event.target.value as AssistantCapability); setResourceId(''); setParentResourceId(''); chatMutation.reset(); }}>
            {availableOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>

          {selected?.parentResourceLabel ? <NumberInput id="assistant-parent-resource" label={selected.parentResourceLabel} value={parentResourceId} onChange={setParentResourceId} /> : null}
          {selected?.resourceLabel ? <NumberInput id="assistant-resource" label={selected.resourceLabel} value={resourceId} onChange={setResourceId} /> : null}

          <label className="mt-4 block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="assistant-input">Yêu cầu</label>
          <textarea id="assistant-input" className="mt-2 min-h-40 w-full resize-y rounded-md border border-slate-300 bg-white px-3 py-2 text-base text-slate-950 outline-none focus-visible:ring-2 focus-visible:ring-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white" maxLength={32768} placeholder="Mô tả rõ nội dung bạn cần tra cứu hoặc soạn thảo..." value={input} onChange={(event) => setInput(event.target.value)} />

          {validationError ? <p className="mt-3 text-sm font-medium text-red-700 dark:text-red-300" role="alert">{validationError}</p> : null}
          {chatMutation.isError ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">{getErrorMessage(chatMutation.error)}</p> : null}

          <Button className="mt-4 w-full" loading={chatMutation.isPending} loadingText="Đang xử lý..." type="submit">
            <Send aria-hidden="true" className="h-4 w-4" /> Gửi yêu cầu
          </Button>
          <p className="mt-3 flex items-start gap-2 text-xs leading-5 text-slate-500 dark:text-slate-400"><ShieldCheck aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" /> Mọi yêu cầu đều đi qua Spring authorization và được ghi audit.</p>
        </form>

        <div>
          {chatMutation.data ? <AssistantAnswer response={chatMutation.data} /> : (
            <div className="flex min-h-72 flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-700 dark:bg-slate-900/60">
              <Sparkles aria-hidden="true" className="h-8 w-8 text-slate-400" />
              <h2 className="mt-3 text-base font-semibold text-slate-950 dark:text-white">Sẵn sàng hỗ trợ theo ngữ cảnh</h2>
              <p className="mt-2 max-w-md text-sm leading-6 text-slate-600 dark:text-slate-300">Chọn nghiệp vụ, cung cấp đúng mã tài nguyên và nhập câu hỏi. Nếu model local chưa sẵn sàng, hệ thống sẽ từ chối an toàn.</p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function NumberInput({ id, label, onChange, value }: { id: string; label: string; onChange: (value: string) => void; value: string }) {
  return (
    <label className="mt-4 block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor={id}>
      {label}
      <input id={id} className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 outline-none focus-visible:ring-2 focus-visible:ring-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white" min={1} inputMode="numeric" type="number" value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}
