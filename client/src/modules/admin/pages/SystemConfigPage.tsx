import { useEffect, useMemo, useState, type ReactNode } from 'react';

import { EmptyState, ErrorState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
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
  ai: { enabled: true, maxRequestsPerDay: 100, maxContextTokens: 8192 },
  face: { enabled: true, confidenceThreshold: 0.8, livenessThreshold: 0.8 },
  qrFallback: { enabled: true, tokenTtlSeconds: 300 },
  notification: { enabled: true, maxPageSize: 100 },
  retention: { notificationDays: 90, aiUsageLogDays: 180, faceCheckinLogDays: 180, auditLogDays: 365 },
  operational: { maxPageSize: 100, includeFailureReasonCodes: true },
};

function localizeConfigLabel(t: ReturnType<typeof useI18n>['t'], value: string) {
  return t(value);
}

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

function validateConfig(config: SystemConfig, t: ReturnType<typeof useI18n>['t']) {
  const errors: string[] = [];

  if (config.account.maxLoginAttempts < 1) {
    errors.push(t('adminConfig.validation.maxLoginAttempts'));
  }
  if (config.booking.checkinWindowMinutes <= 0) {
    errors.push(t('adminConfig.validation.checkinWindow'));
  }
  if (config.booking.cancelBeforeMinutes < 0) {
    errors.push(t('adminConfig.validation.cancelBefore'));
  }
  if (config.upload.reportMaxSizeMb <= 0) {
    errors.push(t('adminConfig.validation.reportSize'));
  }
  if (config.upload.productMaxSizeMb <= 0) {
    errors.push(t('adminConfig.validation.productSize'));
  }
  if (config.upload.reportAllowedTypes.length === 0) {
    errors.push(t('adminConfig.validation.reportTypes'));
  }
  if (config.upload.productAllowedTypes.length === 0) {
    errors.push(t('adminConfig.validation.productTypes'));
  }
  if (config.research.evaluationMaxScore <= 0) {
    errors.push(t('adminConfig.validation.score'));
  }
  if (config.ai.maxRequestsPerDay < 1 || config.ai.maxContextTokens < 1) {
    errors.push(t('adminConfig.validation.aiLimits'));
  }
  if (config.face.confidenceThreshold < 0 || config.face.confidenceThreshold > 1 || config.face.livenessThreshold < 0 || config.face.livenessThreshold > 1) {
    errors.push(t('adminConfig.validation.faceThresholds'));
  }
  if (config.qrFallback.tokenTtlSeconds < 1 || config.qrFallback.tokenTtlSeconds > 86400) {
    errors.push(t('adminConfig.validation.qrTtl'));
  }
  if (config.notification.maxPageSize < 1 || config.notification.maxPageSize > 100 || config.operational.maxPageSize < 1 || config.operational.maxPageSize > 100) {
    errors.push(t('adminConfig.validation.pageSize'));
  }
  if (Object.values(config.retention).some((days) => days < 1 || days > 3650)) {
    errors.push(t('adminConfig.validation.retention'));
  }

  return errors;
}

export function SystemConfigPage() {
  const { t } = useI18n();
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
    section: 'account' | 'booking' | 'upload' | 'research' | 'ai' | 'face' | 'qrFallback' | 'notification' | 'retention' | 'operational',
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
    section: 'account' | 'lab' | 'booking' | 'research' | 'ai' | 'face' | 'qrFallback' | 'notification' | 'operational',
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
    const nextErrors = validateConfig(normalizedForm, t);
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
        <p className="mt-6 text-sm text-slate-300">{t('adminConfig.loading')}</p>
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
          {t('adminConfig.loadError')}
        </ErrorState>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <PageHeader />
        <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300">
          {t('adminConfig.empty')}
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
          {updateMutation.isPending ? t('adminConfig.saving') : t('adminConfig.save')}
        </button>
      </div>

      {errors.length > 0 ? (
        <div className="mt-6 rounded-md border border-red-900/70 bg-red-950/40 p-4 text-sm text-red-200">
          <p className="font-semibold">{t('adminConfig.validationTitle')}</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mt-8 space-y-6">
        <ConfigSection title="adminConfig.accountPolicy">
          <ToggleField
            checked={form.account.requireEmailVerification}
            label="adminConfig.requireEmailVerification"
            onChange={(value) => updateBoolean('account', 'requireEmailVerification', value)}
          />
          <SelectField
            label="adminConfig.defaultRegisterRole"
            value={form.account.defaultRegisterRole}
            options={[{ label: 'adminConfig.student', value: 'STUDENT' }]}
            onChange={() => undefined}
          />
          <NumberField
            label="adminConfig.maxLoginAttempts"
            min={1}
            value={form.account.maxLoginAttempts}
            onChange={(value) => updateNumber('account', 'maxLoginAttempts', value)}
          />
        </ConfigSection>

        <ConfigSection title="adminConfig.labPolicy">
          <ToggleField
            checked={form.lab.oneManagerOneLab}
            label="adminConfig.oneManagerOneLab"
            onChange={(value) => updateBoolean('lab', 'oneManagerOneLab', value)}
          />
          <ToggleField
            checked={form.lab.hideInactiveLabsFromStudent}
            label="adminConfig.hideInactiveLabs"
            onChange={(value) => updateBoolean('lab', 'hideInactiveLabsFromStudent', value)}
          />
          <ToggleField
            checked={form.lab.disableApplyForInactiveLab}
            label="adminConfig.disableApplyInactiveLab"
            onChange={(value) => updateBoolean('lab', 'disableApplyForInactiveLab', value)}
          />
          <ToggleField
            checked={form.lab.disableBookingForInactiveLab}
            label="adminConfig.disableBookingInactiveLab"
            onChange={(value) => updateBoolean('lab', 'disableBookingForInactiveLab', value)}
          />
        </ConfigSection>

        <ConfigSection title="adminConfig.bookingPolicy">
          <NumberField
            label="adminConfig.checkinWindow"
            min={1}
            value={form.booking.checkinWindowMinutes}
            onChange={(value) => updateNumber('booking', 'checkinWindowMinutes', value)}
          />
          <NumberField
            label="adminConfig.cancelBefore"
            min={0}
            value={form.booking.cancelBeforeMinutes}
            onChange={(value) => updateNumber('booking', 'cancelBeforeMinutes', value)}
          />
          <ToggleField
            checked={form.booking.hidePastSlots}
            label="adminConfig.hidePastSlots"
            onChange={(value) => updateBoolean('booking', 'hidePastSlots', value)}
          />
          <ToggleField
            checked={form.booking.hideCancelledSlots}
            label="adminConfig.hideCancelledSlots"
            onChange={(value) => updateBoolean('booking', 'hideCancelledSlots', value)}
          />
        </ConfigSection>

        <ConfigSection title="adminConfig.uploadPolicy">
          <NumberField
            label="adminConfig.reportMaxSize"
            min={1}
            value={form.upload.reportMaxSizeMb}
            onChange={(value) => updateNumber('upload', 'reportMaxSizeMb', value)}
          />
          <NumberField
            label="adminConfig.productMaxSize"
            min={1}
            value={form.upload.productMaxSizeMb}
            onChange={(value) => updateNumber('upload', 'productMaxSizeMb', value)}
          />
          <TextField
            label="adminConfig.reportTypes"
            value={reportTypes}
            onChange={setReportTypes}
          />
          <TextField
            label="adminConfig.productTypes"
            value={productTypes}
            onChange={setProductTypes}
          />
        </ConfigSection>

        <ConfigSection title="adminConfig.researchPolicy">
          <NumberField
            label="adminConfig.evaluationMaxScore"
            min={1}
            value={form.research.evaluationMaxScore}
            onChange={(value) => updateNumber('research', 'evaluationMaxScore', value)}
          />
          <ToggleField
            checked={form.research.requireApprovedReportBeforeTaskDone}
            label="adminConfig.requireApprovedReport"
            onChange={(value) => updateBoolean('research', 'requireApprovedReportBeforeTaskDone', value)}
          />
          <ToggleField
            checked={form.research.requireLeaderReviewBeforeManagerReview}
            label="adminConfig.requireLeaderReview"
            onChange={(value) => updateBoolean('research', 'requireLeaderReviewBeforeManagerReview', value)}
          />
          <ToggleField
            checked={form.research.allowMemberPersonalProductUpload}
            label="adminConfig.allowPersonalProduct"
            onChange={(value) => updateBoolean('research', 'allowMemberPersonalProductUpload', value)}
          />
        </ConfigSection>

        <ConfigSection title="adminConfig.aiPolicy">
          <ToggleField checked={form.ai.enabled} label="adminConfig.aiEnabled" onChange={(value) => updateBoolean('ai', 'enabled', value)} />
          <NumberField label="adminConfig.aiRequests" min={1} value={form.ai.maxRequestsPerDay} onChange={(value) => updateNumber('ai', 'maxRequestsPerDay', value)} />
          <NumberField label="adminConfig.aiContext" min={1} value={form.ai.maxContextTokens} onChange={(value) => updateNumber('ai', 'maxContextTokens', value)} />
        </ConfigSection>

        <ConfigSection title="adminConfig.facePolicy">
          <ToggleField checked={form.face.enabled} label="adminConfig.faceEnabled" onChange={(value) => updateBoolean('face', 'enabled', value)} />
          <NumberField label="adminConfig.faceConfidence" min={0} max={1} step={0.01} value={form.face.confidenceThreshold} onChange={(value) => updateNumber('face', 'confidenceThreshold', value)} />
          <NumberField label="adminConfig.faceLiveness" min={0} max={1} step={0.01} value={form.face.livenessThreshold} onChange={(value) => updateNumber('face', 'livenessThreshold', value)} />
        </ConfigSection>

        <ConfigSection title="adminConfig.qrPolicy">
          <ToggleField checked={form.qrFallback.enabled} label="adminConfig.qrEnabled" onChange={(value) => updateBoolean('qrFallback', 'enabled', value)} />
          <NumberField label="adminConfig.qrTtl" min={1} max={86400} value={form.qrFallback.tokenTtlSeconds} onChange={(value) => updateNumber('qrFallback', 'tokenTtlSeconds', value)} />
          <ToggleField checked={form.notification.enabled} label="adminConfig.notificationsEnabled" onChange={(value) => updateBoolean('notification', 'enabled', value)} />
          <NumberField label="adminConfig.notificationPageSize" min={1} max={100} value={form.notification.maxPageSize} onChange={(value) => updateNumber('notification', 'maxPageSize', value)} />
        </ConfigSection>

        <ConfigSection title="adminConfig.retentionPolicy">
          <NumberField label="adminConfig.retentionNotifications" min={1} max={3650} value={form.retention.notificationDays} onChange={(value) => updateNumber('retention', 'notificationDays', value)} />
          <NumberField label="adminConfig.retentionAiLogs" min={1} max={3650} value={form.retention.aiUsageLogDays} onChange={(value) => updateNumber('retention', 'aiUsageLogDays', value)} />
          <NumberField label="adminConfig.retentionFaceLogs" min={1} max={3650} value={form.retention.faceCheckinLogDays} onChange={(value) => updateNumber('retention', 'faceCheckinLogDays', value)} />
          <NumberField label="adminConfig.retentionAuditLogs" min={1} max={3650} value={form.retention.auditLogDays} onChange={(value) => updateNumber('retention', 'auditLogDays', value)} />
        </ConfigSection>

        <ConfigSection title="adminConfig.operationalPolicy">
          <NumberField label="adminConfig.operationalPageSize" min={1} max={100} value={form.operational.maxPageSize} onChange={(value) => updateNumber('operational', 'maxPageSize', value)} />
          <ToggleField checked={form.operational.includeFailureReasonCodes} label="adminConfig.failureReasonCodes" onChange={(value) => updateBoolean('operational', 'includeFailureReasonCodes', value)} />
        </ConfigSection>
      </div>
    </section>
  );
}

function PageHeader() {
  const { t } = useI18n();

  return (
    <header>
      <h2 className="text-xl font-semibold text-white">{t('adminConfig.title')}</h2>
    </header>
  );
}

function ConfigSection({ children, title }: { children: ReactNode; title: string }) {
  const { t } = useI18n();

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
      <h3 className="text-base font-semibold text-white">{localizeConfigLabel(t, title)}</h3>
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
  const { t } = useI18n();

  return (
    <label className="flex min-h-12 items-center justify-between gap-4 rounded-md border border-slate-800 bg-slate-900 px-3 py-2">
      <span className="text-sm font-medium text-slate-200">{localizeConfigLabel(t, label)}</span>
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
  max,
  min,
  onChange,
  step,
  value,
}: {
  label: string;
  max?: number;
  min: number;
  onChange: (value: string) => void;
  step?: number;
  value: number;
}) {
  const { t } = useI18n();

  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{localizeConfigLabel(t, label)}</span>
      <input
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
        min={min}
        max={max}
        step={step}
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
  const { t } = useI18n();

  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{localizeConfigLabel(t, label)}</span>
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
  const { t } = useI18n();

  return (
    <label className="space-y-2">
      <span className="text-sm font-medium text-slate-300">{localizeConfigLabel(t, label)}</span>
      <select
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-white"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {localizeConfigLabel(t, option.label)}
          </option>
        ))}
      </select>
    </label>
  );
}
