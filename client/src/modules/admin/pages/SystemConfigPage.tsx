import { useEffect, useMemo, useState, type ReactNode } from 'react';

import { EmptyState, ErrorState } from '../../../shared/components';
import { useSystemConfig, useUpdateSystemConfig } from '../hooks';
import type { SystemConfig } from '../api';

const defaultConfig: SystemConfig = {
  account: {
    requireEmailVerification: true,
    defaultRegisterRole: 'STUDENT',
    maxLoginAttempts: 5,
  },
  lab: {
    oneManagerOneLab: true,
    hideInactiveLabsFromStudent: true,
    disableApplyForInactiveLab: true,
    disableBookingForInactiveLab: true,
  },
  booking: {
    checkinWindowMinutes: 10,
    cancelBeforeMinutes: 30,
    hidePastSlots: true,
    hideCancelledSlots: true,
  },
  upload: {
    reportMaxSizeMb: 10,
    productMaxSizeMb: 50,
    reportAllowedTypes: ['pdf', 'doc', 'docx'],
    productAllowedTypes: ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'zip', 'mp4'],
  },
  research: {
    evaluationMaxScore: 10,
    requireApprovedReportBeforeTaskDone: true,
    requireLeaderReviewBeforeManagerReview: true,
    allowMemberPersonalProductUpload: true,
  },
};

function parseTypes(value: string) {
  return Array.from(
    new Set(
      value
        .split(',')
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean),
    ),
  );
}

function formatTypes(value: string[]) {
  return value.join(', ');
}

function validateConfig(config: SystemConfig) {
  const errors: string[] = [];

  if (config.account.maxLoginAttempts < 1) {
    errors.push('Số lần đăng nhập sai tối đa phải lớn hơn hoặc bằng 1.');
  }
  if (config.booking.checkinWindowMinutes <= 0) {
    errors.push('Thời gian check-in đầu ca phải lớn hơn 0 phút.');
  }
  if (config.booking.cancelBeforeMinutes < 0) {
    errors.push('Thời gian cho phép hủy booking phải lớn hơn hoặc bằng 0 phút.');
  }
  if (config.upload.reportMaxSizeMb <= 0) {
    errors.push('Dung lượng tối đa báo cáo phải lớn hơn 0 MB.');
  }
  if (config.upload.productMaxSizeMb <= 0) {
    errors.push('Dung lượng tối đa sản phẩm phải lớn hơn 0 MB.');
  }
  if (config.upload.reportAllowedTypes.length === 0) {
    errors.push('Định dạng báo cáo được phép không được rỗng.');
  }
  if (config.upload.productAllowedTypes.length === 0) {
    errors.push('Định dạng sản phẩm được phép không được rỗng.');
  }
  if (config.research.evaluationMaxScore <= 0) {
    errors.push('Thang điểm đánh giá tối đa phải lớn hơn 0.');
  }

  return errors;
}

export function SystemConfigPage() {
  const { data, isError, isLoading, refetch } = useSystemConfig();
  const updateMutation = useUpdateSystemConfig();
  const [form, setForm] = useState<SystemConfig>(defaultConfig);
  const [reportTypes, setReportTypes] = useState(formatTypes(defaultConfig.upload.reportAllowedTypes));
  const [productTypes, setProductTypes] = useState(formatTypes(defaultConfig.upload.productAllowedTypes));
  const [errors, setErrors] = useState<string[]>([]);

  useEffect(() => {
    if (!data) {
      return;
    }

    setForm(data);
    setReportTypes(formatTypes(data.upload.reportAllowedTypes));
    setProductTypes(formatTypes(data.upload.productAllowedTypes));
    setErrors([]);
  }, [data]);

  const normalizedForm = useMemo<SystemConfig>(
    () => ({
      ...form,
      upload: {
        ...form.upload,
        reportAllowedTypes: parseTypes(reportTypes),
        productAllowedTypes: parseTypes(productTypes),
      },
    }),
    [form, productTypes, reportTypes],
  );

  const updateNumber = (
    section: 'account' | 'booking' | 'upload' | 'research',
    field: string,
    value: string,
  ) => {
    setForm((current) => ({
      ...current,
      [section]: {
        ...current[section],
        [field]: Number(value),
      },
    }));
  };

  const updateBoolean = (
    section: 'account' | 'lab' | 'booking' | 'research',
    field: string,
    value: boolean,
  ) => {
    setForm((current) => ({
      ...current,
      [section]: {
        ...current[section],
        [field]: value,
      },
    }));
  };

  const handleSubmit = async () => {
    const nextErrors = validateConfig(normalizedForm);
    setErrors(nextErrors);

    if (nextErrors.length > 0) {
      return;
    }

    const saved = await updateMutation.mutateAsync(normalizedForm);
    setForm(saved);
    setReportTypes(formatTypes(saved.upload.reportAllowedTypes));
    setProductTypes(formatTypes(saved.upload.productAllowedTypes));
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <PageHeader />
        <p className="mt-6 text-sm text-slate-300">Đang tải cấu hình hệ thống...</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <PageHeader />
        <ErrorState
          className="mt-6 border-red-900/70 bg-red-950/40 text-red-200"
          onRetry={() => void refetch()}
        >
          Không thể tải cấu hình hệ thống.
        </ErrorState>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <PageHeader />
        <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300">
          Chưa có dữ liệu cấu hình hệ thống.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <PageHeader />
        <button
          type="button"
          className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:bg-slate-600"
          disabled={updateMutation.isPending}
          onClick={() => void handleSubmit()}
        >
          {updateMutation.isPending ? 'Đang lưu...' : 'Lưu cấu hình'}
        </button>
      </div>

      {errors.length > 0 ? (
        <div className="mt-6 rounded-md border border-red-900/70 bg-red-950/40 p-4 text-sm text-red-200">
          <p className="font-semibold">Vui lòng kiểm tra lại cấu hình.</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mt-8 space-y-6">
        <ConfigSection title="Chính sách tài khoản">
          <ToggleField
            checked={form.account.requireEmailVerification}
            label="Bắt buộc xác thực email khi đăng ký"
            onChange={(value) => updateBoolean('account', 'requireEmailVerification', value)}
          />
          <SelectField
            label="Role mặc định khi đăng ký"
            value={form.account.defaultRegisterRole}
            options={[{ label: 'Sinh viên', value: 'STUDENT' }]}
            onChange={() => undefined}
          />
          <NumberField
            label="Số lần đăng nhập sai tối đa"
            min={1}
            value={form.account.maxLoginAttempts}
            onChange={(value) => updateNumber('account', 'maxLoginAttempts', value)}
          />
        </ConfigSection>

        <ConfigSection title="Chính sách PTN">
          <ToggleField
            checked={form.lab.oneManagerOneLab}
            label="Mỗi manager chỉ quản lý 1 PTN"
            onChange={(value) => updateBoolean('lab', 'oneManagerOneLab', value)}
          />
          <ToggleField
            checked={form.lab.hideInactiveLabsFromStudent}
            label="Ẩn PTN tạm ngừng khỏi danh sách sinh viên"
            onChange={(value) => updateBoolean('lab', 'hideInactiveLabsFromStudent', value)}
          />
          <ToggleField
            checked={form.lab.disableApplyForInactiveLab}
            label="Không cho ứng tuyển vào PTN tạm ngừng"
            onChange={(value) => updateBoolean('lab', 'disableApplyForInactiveLab', value)}
          />
          <ToggleField
            checked={form.lab.disableBookingForInactiveLab}
            label="Không cho booking vào PTN tạm ngừng"
            onChange={(value) => updateBoolean('lab', 'disableBookingForInactiveLab', value)}
          />
        </ConfigSection>

        <ConfigSection title="Chính sách booking/check-in">
          <NumberField
            label="Thời gian check-in đầu ca, phút"
            min={1}
            value={form.booking.checkinWindowMinutes}
            onChange={(value) => updateNumber('booking', 'checkinWindowMinutes', value)}
          />
          <NumberField
            label="Cho phép hủy booking trước, phút"
            min={0}
            value={form.booking.cancelBeforeMinutes}
            onChange={(value) => updateNumber('booking', 'cancelBeforeMinutes', value)}
          />
          <ToggleField
            checked={form.booking.hidePastSlots}
            label="Chỉ hiển thị ca hiện tại/tương lai"
            onChange={(value) => updateBoolean('booking', 'hidePastSlots', value)}
          />
          <ToggleField
            checked={form.booking.hideCancelledSlots}
            label="Ẩn ca đã hủy khỏi danh sách chính"
            onChange={(value) => updateBoolean('booking', 'hideCancelledSlots', value)}
          />
        </ConfigSection>

        <ConfigSection title="Chính sách upload">
          <NumberField
            label="Dung lượng tối đa báo cáo, MB"
            min={1}
            value={form.upload.reportMaxSizeMb}
            onChange={(value) => updateNumber('upload', 'reportMaxSizeMb', value)}
          />
          <NumberField
            label="Dung lượng tối đa sản phẩm, MB"
            min={1}
            value={form.upload.productMaxSizeMb}
            onChange={(value) => updateNumber('upload', 'productMaxSizeMb', value)}
          />
          <TextField
            label="Định dạng báo cáo được phép"
            value={reportTypes}
            onChange={setReportTypes}
          />
          <TextField
            label="Định dạng sản phẩm được phép"
            value={productTypes}
            onChange={setProductTypes}
          />
        </ConfigSection>

        <ConfigSection title="Chính sách NCKH">
          <NumberField
            label="Thang điểm đánh giá tối đa"
            min={1}
            value={form.research.evaluationMaxScore}
            onChange={(value) => updateNumber('research', 'evaluationMaxScore', value)}
          />
          <ToggleField
            checked={form.research.requireApprovedReportBeforeTaskDone}
            label="Bắt buộc báo cáo được duyệt trước khi hoàn thành task"
            onChange={(value) => updateBoolean('research', 'requireApprovedReportBeforeTaskDone', value)}
          />
          <ToggleField
            checked={form.research.requireLeaderReviewBeforeManagerReview}
            label="Bắt buộc trưởng nhóm kiểm tra trước manager"
            onChange={(value) => updateBoolean('research', 'requireLeaderReviewBeforeManagerReview', value)}
          />
          <ToggleField
            checked={form.research.allowMemberPersonalProductUpload}
            label="Cho phép thành viên upload sản phẩm cá nhân"
            onChange={(value) => updateBoolean('research', 'allowMemberPersonalProductUpload', value)}
          />
        </ConfigSection>
      </div>
    </section>
  );
}

function PageHeader() {
  return (
    <header>
      <h2 className="text-xl font-semibold text-white">Cấu hình hệ thống</h2>
      <p className="mt-2 text-sm text-slate-300">Quản lý chính sách áp dụng cho toàn bộ hệ thống.</p>
    </header>
  );
}

function ConfigSection({ children, title }: { children: ReactNode; title: string }) {
  return (
    <section className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
      <h3 className="text-base font-semibold text-white">{title}</h3>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">{children}</div>
    </section>
  );
}

function ToggleField({
  checked,
  label,
  onChange,
}: {
  checked: boolean;
  label: string;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="flex min-h-12 items-center justify-between gap-4 rounded-md border border-slate-800 bg-slate-900 px-3 py-2">
      <span className="text-sm font-medium text-slate-200">{label}</span>
      <input
        checked={checked}
        className="h-5 w-5 accent-white"
        type="checkbox"
        onChange={(event) => onChange(event.target.checked)}
      />
    </label>
  );
}

function NumberField({
  label,
  min,
  onChange,
  value,
}: {
  label: string;
  min: number;
  onChange: (value: string) => void;
  value: number;
}) {
  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{label}</span>
      <input
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
        min={min}
        type="number"
        value={Number.isFinite(value) ? value : 0}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function TextField({
  label,
  onChange,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{label}</span>
      <input
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
        placeholder="pdf, doc, docx"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function SelectField({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: Array<{ label: string; value: string }>;
  value: string;
}) {
  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{label}</span>
      <select
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
