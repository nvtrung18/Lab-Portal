import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { useEffect, useState, type ChangeEvent } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button, Modal } from '../../../shared/components';
import { queryKeys } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import { getLabById, type LabResponse } from '../api';
import { useApplyLab } from '../hooks';
import { LABS_QUERY_KEY } from '../hooks/useLabs';
import { isLabActive } from '../utils/labStatus';

const applySchema = z.object({
  cvUrl: z
    .string()
    .trim()
    .optional()
    .refine((value) => !value || /^https?:\/\/.+/i.test(value), 'CV URL không hợp lệ'),
});

type ApplyFormValues = z.infer<typeof applySchema>;

interface ApplyModalProps {
  labId: number | null;
  labName?: string;
  labStatus?: LabResponse['status'];
  onClose: () => void;
}

const INACTIVE_LAB_MESSAGE = 'PTN này đã ngừng hoạt động, không thể nộp đơn.';
const REQUIRED_CV_MESSAGE = 'Vui lòng nhập CV URL hoặc tải lên file CV.';
const MAX_CV_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_CV_EXTENSIONS = ['pdf', 'doc', 'docx'];

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? 'Không thể nộp đơn. Vui lòng thử lại.';
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Không thể nộp đơn. Vui lòng thử lại.';
}

function formatFileSize(size: number) {
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }

  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function validateCvFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';

  if (!ALLOWED_CV_EXTENSIONS.includes(extension)) {
    return 'Tệp CV chỉ hỗ trợ định dạng PDF, DOC hoặc DOCX.';
  }

  if (file.size > MAX_CV_FILE_SIZE) {
    return 'Tệp CV không được vượt quá 10MB.';
  }

  return null;
}

export function ApplyModal({ labId, labName, labStatus, onClose }: ApplyModalProps) {
  const queryClient = useQueryClient();
  const applyMutation = useApplyLab();
  const [serverError, setServerError] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<ApplyFormValues>({
    resolver: zodResolver(applySchema),
    defaultValues: {
      cvUrl: '',
    },
  });

  useEffect(() => {
    if (labId) {
      reset({ cvUrl: '' });
      setServerError(null);
      setSelectedFile(null);
      setFileError(null);
      setFileInputKey((value) => value + 1);
    }
  }, [labId, reset]);

  if (!labId) {
    return null;
  }

  const cvUrlValue = watch('cvUrl')?.trim() ?? '';
  const hasCvPayload = Boolean(cvUrlValue) || Boolean(selectedFile);

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setServerError(null);

    if (!file) {
      setSelectedFile(null);
      setFileError(null);
      return;
    }

    const validationError = validateCvFile(file);
    if (validationError) {
      setSelectedFile(null);
      setFileError(validationError);
      event.target.value = '';
      return;
    }

    setSelectedFile(file);
    setFileError(null);
  };

  const onSubmit = async (values: ApplyFormValues) => {
    setServerError(null);

    try {
      const cvUrl = values.cvUrl?.trim() ?? '';

      if (!cvUrl && !selectedFile) {
        setServerError(REQUIRED_CV_MESSAGE);
        return;
      }

      if (labStatus && !isLabActive({ status: labStatus })) {
        setServerError(INACTIVE_LAB_MESSAGE);
        void queryClient.invalidateQueries({ queryKey: LABS_QUERY_KEY });
        return;
      }

      const latestLab = await queryClient.fetchQuery({
        queryKey: queryKeys.labs.detail(labId),
        queryFn: () => getLabById(labId),
        staleTime: 0,
      });

      if (!isLabActive(latestLab)) {
        setServerError(INACTIVE_LAB_MESSAGE);
        void queryClient.invalidateQueries({ queryKey: LABS_QUERY_KEY });
        return;
      }

      await applyMutation.mutateAsync({
        labId,
        cvUrl: cvUrl || undefined,
        cvFile: selectedFile,
      });
      reset({ cvUrl: '' });
      setSelectedFile(null);
      setFileError(null);
      onClose();
    } catch (error) {
      setServerError(getErrorMessage(error));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Modal
        footer={(
          <>
            <Button disabled={applyMutation.isPending} onClick={onClose} variant="outline">
              Hủy
            </Button>
            <Button
              disabled={!hasCvPayload || Boolean(fileError)}
              loading={applyMutation.isPending}
              loadingText="Đang nộp..."
              type="submit"
            >
              Nộp đơn
            </Button>
          </>
        )}
        onClose={onClose}
        size="md"
        subtitle={labName ? `Nộp CV ứng tuyển vào ${labName}` : 'Nộp CV ứng tuyển vào PTN'}
        title="Ứng tuyển vào PTN"
      >
        <div className="space-y-5">
          <div>
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="cvUrl"
            >
              CV URL
            </label>
            <input
              id="cvUrl"
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="https://drive.google.com/..."
              disabled={applyMutation.isPending}
              {...register('cvUrl')}
            />
            {errors.cvUrl ? (
              <p className="mt-2 text-sm text-red-600">{errors.cvUrl.message}</p>
            ) : null}
          </div>

          <div>
            <label
              className="block text-sm font-medium text-slate-700"
              htmlFor="cvFile"
            >
              Tệp CV
            </label>
            <input
              key={fileInputKey}
              id="cvFile"
              type="file"
              accept=".pdf,.doc,.docx"
              className="mt-2 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 shadow-sm file:mr-3 file:rounded-md file:border-0 file:bg-slate-900 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-white disabled:cursor-not-allowed disabled:bg-slate-50"
              disabled={applyMutation.isPending}
              onChange={handleFileChange}
            />
            <p className="mt-2 text-xs text-slate-500">
              Hỗ trợ PDF, DOC, DOCX. Tối đa 10MB.
            </p>
            {selectedFile ? (
              <div className="mt-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                <span className="font-medium text-slate-950">{selectedFile.name}</span>
                <span className="ml-2 text-slate-500">
                  ({formatFileSize(selectedFile.size)})
                </span>
              </div>
            ) : null}
            {fileError ? (
              <p className="mt-2 text-sm text-red-600">{fileError}</p>
            ) : null}
          </div>

          {serverError ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {serverError}
            </div>
          ) : null}

        </div>
      </Modal>
    </form>
  );
}
