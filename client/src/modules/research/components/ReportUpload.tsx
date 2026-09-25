import axios from 'axios';
import { useState } from 'react';
import type { ChangeEvent, FormEvent, ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { submitReport, replaceReport } from '../api';
import type { ResearchReport, SubmitReportPayload, ReplaceReportPayload } from '../types';

interface ReportUploadProps {
  taskId: number;
  milestoneId: number;
  projectId?: number | null;
  groupId?: number | null;
  mode?: 'create' | 'replace' | 'resubmit';
  reportId?: number | null;
  initialValues?: ResearchReport | null;
  onSuccess?: (report: ResearchReport) => void;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_EXTENSIONS = new Set(['pdf', 'doc', 'docx']);
const ALLOWED_MIME_TYPES = new Set([
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
]);
const EMPTY_FORM = {
  title: '',
  contentDone: '',
  result: '',
  difficulty: '',
  nextPlan: '',
  selfAssessment: '',
  evidenceLink: '',
};

export function ReportUpload({
  taskId,
  milestoneId,
  projectId,
  groupId,
  mode = 'create',
  reportId,
  initialValues,
  onSuccess,
}: ReportUploadProps) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [form, setForm] = useState(() => {
    if (mode === 'replace' && initialValues) {
      return {
        title: initialValues.title ?? '',
        contentDone: initialValues.contentDone ?? '',
        result: initialValues.result ?? '',
        difficulty: initialValues.difficulty ?? '',
        nextPlan: initialValues.nextPlan ?? '',
        selfAssessment: initialValues.selfAssessment ?? '',
        evidenceLink: initialValues.evidenceLink ?? '',
      };
    }
    return EMPTY_FORM;
  });
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  const upload = useMutation({
    mutationFn: (payload: SubmitReportPayload | { reportId: number; payload: ReplaceReportPayload }) => {
      if (mode === 'replace') {
        const p = payload as { reportId: number; payload: ReplaceReportPayload };
        return replaceReport(p.reportId, p.payload, setUploadPercent);
      } else {
        return submitReport(payload as SubmitReportPayload, setUploadPercent);
      }
    },
    onMutate: () => {
      setUploadPercent(0);
      setFeedback(null);
    },
    onSuccess: async (report) => {
      setUploadPercent(100);
      const isReplace = mode === 'replace';
      const successText = isReplace ? t('research.report.updated') : mode === 'resubmit' ? t('research.report.resubmitted') : t('research.report.submittedSuccess');
      setFeedback({ kind: 'success', text: successText });
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: queryKeys.research.taskReports(taskId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.tasks(milestoneId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.reports(milestoneId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.myMilestoneReports(milestoneId) }),
      ];
      if (projectId) {
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.milestones(projectId) }));
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) }));
      }
      if (groupId) {
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.myTasks(groupId) }));
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.groupReports(groupId) }));
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroupReports(groupId) }));
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroupMilestones(groupId) }));
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.groupTasks(groupId) }));
      }
      await Promise.all(invalidations);
      toast.success(successText);
      onSuccess?.(report);
    },
    onError: (error) => {
      const message = getErrorMessage(error, t);
      setFeedback({ kind: 'error', text: message });
      toast.error(message);
    },
  });

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const selectedFile = event.target.files?.[0] ?? null;
    const validationError = selectedFile ? validateFile(selectedFile, t) : null;
    setFileError(validationError);
    setFile(validationError ? null : selectedFile);
    setFeedback(null);
    setUploadPercent(0);
    if (validationError) {
      event.target.value = '';
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mode !== 'replace' && !file) {
      setFileError(t('validation.required'));
      return;
    }
    if (mode === 'replace') {
      upload.mutate({
        reportId: reportId!,
        payload: {
          title: form.title.trim(),
          contentDone: form.contentDone.trim(),
          result: form.result.trim(),
          difficulty: form.difficulty.trim(),
          nextPlan: form.nextPlan.trim(),
          selfAssessment: form.selfAssessment.trim(),
          evidenceLink: form.evidenceLink.trim() || undefined,
          file,
        },
      });
    } else {
      upload.mutate({
        milestoneId,
        taskId,
        title: form.title.trim(),
        contentDone: form.contentDone.trim(),
        result: form.result.trim(),
        difficulty: form.difficulty.trim(),
        nextPlan: form.nextPlan.trim(),
        selfAssessment: form.selfAssessment.trim(),
        evidenceLink: form.evidenceLink.trim() || undefined,
        file: file!,
      });
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      <TextInput label={t('research.report.title')} required value={form.title} onChange={(value) => setForm((current) => ({ ...current, title: value }))} />
      <TextArea label={t('research.report.workDone')} required value={form.contentDone} onChange={(value) => setForm((current) => ({ ...current, contentDone: value }))} />
      <TextArea label={t('research.report.result')} required value={form.result} onChange={(value) => setForm((current) => ({ ...current, result: value }))} />
      <TextArea label={t('research.report.difficulty')} required value={form.difficulty} onChange={(value) => setForm((current) => ({ ...current, difficulty: value }))} />
      <TextArea label={t('research.report.nextPlan')} required value={form.nextPlan} onChange={(value) => setForm((current) => ({ ...current, nextPlan: value }))} />
      <TextArea label={t('research.report.selfAssessment')} required value={form.selfAssessment} onChange={(value) => setForm((current) => ({ ...current, selfAssessment: value }))} />
      <TextInput label={t('research.report.evidenceLink')} type="url" value={form.evidenceLink} onChange={(value) => setForm((current) => ({ ...current, evidenceLink: value }))} />
      <Field label={t('research.report.attachment')}>
        <input
          accept=".pdf,.doc,.docx"
          className="block w-full text-sm text-slate-700"
          disabled={upload.isPending}
          required={mode !== 'replace'}
          type="file"
          onChange={handleFileChange}
        />
      </Field>
      {fileError ? <p className="rounded-md bg-red-50 p-3 text-sm font-medium text-red-700">{fileError}</p> : null}
      {file ? <FilePreview file={file} /> : null}
      {upload.isPending || uploadPercent > 0 ? (
        <div aria-label={t('research.report.uploadProgress')} className="space-y-1">
          <div className="flex justify-between text-xs font-medium text-slate-600">
            <span>{upload.isPending ? t('research.report.uploading') : t('research.report.uploaded')}</span>
            <span>{uploadPercent}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${uploadPercent}%` }} />
          </div>
        </div>
      ) : null}
      {feedback ? (
        <p className={`rounded-md p-3 text-sm font-medium ${feedback.kind === 'success' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
          {feedback.text}
        </p>
      ) : null}
      <div className="flex justify-end">
        <Button loading={upload.isPending} loadingText={t('research.report.uploading')} type="submit">
          {mode === 'replace' ? t('research.report.update') : mode === 'resubmit' ? t('research.report.resubmit') : t('research.report.submit')}
        </Button>
      </div>
    </form>
  );
}

function FilePreview({ file }: { file: File }) {
  const extension = getExtension(file.name).toUpperCase();
  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
      <div className="flex items-center gap-3">
        <span className="rounded bg-white px-2 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200">
          {extension}
        </span>
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-800">{file.name}</p>
          <p className="text-xs text-slate-500">{file.type || extension} · {formatFileSize(file.size)}</p>
        </div>
      </div>
    </div>
  );
}

function validateFile(file: File, t: ReturnType<typeof useI18n>['t']) {
  const extension = getExtension(file.name);
  if (!ALLOWED_EXTENSIONS.has(extension) || (file.type && !ALLOWED_MIME_TYPES.has(file.type))) {
    return t('validation.fileType');
  }
  if (file.size > MAX_FILE_SIZE) {
    return t('validation.reportFileSize');
  }
  return null;
}

function getExtension(filename: string) {
  return filename.split('.').pop()?.toLowerCase() ?? '';
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function getErrorMessage(error: unknown, t: ReturnType<typeof useI18n>['t']) {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 403) {
      return t('research.report.uploadForbidden');
    }
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? t('research.report.uploadFailed');
  }
  return t('research.report.uploadFailed');
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold text-slate-700">{label}</span>
      {children}
    </label>
  );
}

function TextInput({
  label,
  onChange,
  required = false,
  type = 'text',
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: string;
  value: string;
}) {
  return (
    <Field label={label}>
      <input
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}

function TextArea({
  label,
  onChange,
  required = false,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  required?: boolean;
  value: string;
}) {
  return (
    <Field label={label}>
      <textarea
        className="min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        required={required}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}
