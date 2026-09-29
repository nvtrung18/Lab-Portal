import { Activity, Bot, ScanFace } from 'lucide-react';
import { FormEvent, useDeferredValue, useMemo, useState } from 'react';

import { getStoredRole } from '../../../shared/api';
import { Button, EmptyState, ErrorState, ResponsiveTable } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { useOperationalLogs } from '../hooks';
import type { OperationalFilters, OperationalLogItem, OperationalLogKind } from '../types';

interface DisplayRow { id: number; createdAt: string; actor: string; scope: string; event: string; outcome: string; detail: string; }

const assistantKeys: Record<string, string> = {
  ADMIN_ASSISTANT: 'assistant.admin',
  LAB_ASSISTANT: 'assistant.lab',
  RESEARCH_ASSISTANT: 'assistant.research',
};

const resourceKeys: Record<string, string> = {
  SYSTEM: 'operations.resource.system',
  AUDIT_LOG: 'audit.title',
  USER_ACCOUNT: 'audit.target.user',
  SYSTEM_CONFIG: 'audit.target.systemConfig',
  LABORATORY: 'audit.target.lab',
  TIME_SLOT: 'audit.target.slot',
  BOOKING: 'audit.target.booking',
  PROJECT: 'audit.target.researchProject',
  GROUP: 'audit.target.researchGroup',
  TASK: 'audit.target.cleaningTask',
  REPORT: 'audit.target.report',
};

const statusKeys: Record<string, string> = {
  SUCCESS: 'common.success',
  FAILED: 'operations.failed',
  DENIED: 'operations.denied',
  PENDING: 'operations.status.pending',
  EDITED: 'operations.status.edited',
  REJECTED: 'operations.status.rejected',
  EXECUTED: 'operations.status.executed',
  NOT_REQUESTED: 'operations.status.notRequested',
  UNAVAILABLE: 'operations.status.unavailable',
};

function enumLabel(value: string, keys: Record<string, string>, locale: Locale) {
  return translate(keys[value] ?? '', locale, value);
}

function resultLabel(value: string, locale: Locale) {
  const key = ({ SUCCESS: 'common.success', FAILED: 'operations.failed', DENIED: 'operations.denied' } as Record<string, string>)[value];
  return translate(key ?? '', locale, value);
}

function failureLabel(value: string | null, locale: Locale) {
  if (!value) return translate('operations.noFailure', locale);
  const key = ({
    NO_MATCH: 'operations.noMatch',
    NO_FACE: 'operations.noFace',
    MULTIPLE_FACES: 'operations.multipleFaces',
    LOW_QUALITY: 'operations.lowQuality',
    SPOOF_DETECTED: 'operations.spoofDetected',
    SERVICE_ERROR: 'operations.serviceError',
  } as Record<string, string>)[value];
  return translate(key ?? '', locale, value);
}

function displayRow(kind: OperationalLogKind, item: OperationalLogItem, locale: Locale): DisplayRow {
  if (kind === 'ai-usage' && 'promptTokens' in item) return {
    id: item.id, createdAt: item.createdAt, actor: `${translate('common.user', locale)} #${item.userId}`,
    scope: item.labId ? `${translate('lab.label', locale)} #${item.labId}` : item.module === 'admin' || item.module === 'lab' || item.module === 'research'
      ? translate(`assistant.${item.module}`, locale) : item.module,
    event: enumLabel(item.assistantKey, assistantKeys, locale), outcome: enumLabel(item.status, statusKeys, locale),
    detail: `${item.promptTokens + item.completionTokens} ${translate('operations.tokens', locale)}${item.errorRecorded ? ` · ${translate('operations.hasError', locale)}` : ''}`,
  };
  if (kind === 'ai-actions' && 'actionType' in item) return {
    id: item.id, createdAt: item.createdAt, actor: `${translate('common.user', locale)} #${item.requestedById}`,
    scope: `${enumLabel(item.resourceType, resourceKeys, locale)} #${item.resourceId}`,
    event: item.actionType === 'CREATE_LAB_SHIFT' ? translate('operations.action.createLabShift', locale) : item.actionType,
    outcome: enumLabel(item.status, statusKeys, locale),
    detail: `${translate('operations.execution', locale)}: ${enumLabel(item.executionStatus, statusKeys, locale)}`,
  };
  const face = item as Extract<OperationalLogItem, { bookingId: number }>;
  return { id: face.id, createdAt: face.createdAt, actor: face.studentName ?? `${translate('common.user', locale)} #${face.userId}`, scope: `${translate('lab.label', locale)} #${face.labId}`, event: `${translate('operations.faceRecognition', locale)} · ${translate('operations.booking', locale)} #${face.bookingId}`, outcome: resultLabel(face.result, locale), detail: failureLabel(face.failureReason, locale) };
}

const tabs: Array<{ kind: OperationalLogKind; labelKey: string }> = [
  { kind: 'ai-usage', labelKey: 'operations.aiUsage' }, { kind: 'ai-actions', labelKey: 'operations.aiActions' }, { kind: 'face-checkins', labelKey: 'operations.faceRecognition' },
];

export function OperationalLogsPage() {
  const { locale, t } = useI18n();
  const isAdmin = getStoredRole()?.replace(/^ROLE_/, '') === 'ADMIN';
  const availableTabs = isAdmin ? tabs : tabs.filter((tab) => tab.kind === 'face-checkins');
  const [kind, setKind] = useState<OperationalLogKind>(availableTabs[0].kind);
  const [page, setPage] = useState(0);
  const [inputs, setInputs] = useState({ userId: '', labId: '', resourceId: '', module: '', assistantKey: '', resourceType: '', result: '', from: '', to: '' });
  const [filters, setFilters] = useState<OperationalFilters>({});
  const deferredFilters = useDeferredValue(filters);
  const query = useOperationalLogs(kind, deferredFilters, page);
  const rows = useMemo(() => query.data?.items.map((item) => displayRow(kind, item, locale)) ?? [], [kind, locale, query.data]);

  const submit = (event: FormEvent) => {
    event.preventDefault(); setPage(0);
    const commonFilters: OperationalFilters = {
      ...(Number(inputs.userId) > 0 ? { userId: Number(inputs.userId) } : {}),
      ...(inputs.from ? { from: new Date(`${inputs.from}T00:00:00`).toISOString() } : {}),
      ...(inputs.to ? { to: new Date(`${inputs.to}T23:59:59`).toISOString() } : {}),
    };
    if (kind === 'ai-usage') {
      setFilters({ ...commonFilters, ...(Number(inputs.labId) > 0 ? { labId: Number(inputs.labId) } : {}), ...(inputs.module.trim() ? { module: inputs.module.trim() } : {}) });
    } else if (kind === 'ai-actions') {
      setFilters({ ...commonFilters, ...(Number(inputs.resourceId) > 0 ? { resourceId: Number(inputs.resourceId) } : {}), ...(inputs.assistantKey ? { assistantKey: inputs.assistantKey } : {}), ...(inputs.resourceType ? { resourceType: inputs.resourceType } : {}) });
    } else {
      setFilters({ ...commonFilters, ...(Number(inputs.labId) > 0 ? { labId: Number(inputs.labId) } : {}), ...(Number(inputs.resourceId) > 0 ? { bookingId: Number(inputs.resourceId) } : {}), ...(inputs.result ? { result: inputs.result } : {}) });
    }
  };
  const reset = () => { setInputs({ userId: '', labId: '', resourceId: '', module: '', assistantKey: '', resourceType: '', result: '', from: '', to: '' }); setFilters({}); setPage(0); };

  return (
    <section className="mx-auto max-w-7xl">
      <header className="mb-5"><p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-400"><Activity aria-hidden="true" className="h-4 w-4" /> {t('operations.evidence')}</p><h1 className="mt-1 text-2xl font-semibold text-slate-950 dark:text-white">{t('operations.title')}</h1></header>
      <div className="mb-5 flex flex-wrap gap-2" role="tablist" aria-label={t('operations.logType')}>{availableTabs.map((tab) => <button aria-selected={kind === tab.kind} className={['min-h-11 rounded-md px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500', kind === tab.kind ? 'bg-slate-900 text-white dark:bg-white dark:text-slate-950' : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200'].join(' ')} key={tab.kind} role="tab" type="button" onClick={() => { setKind(tab.kind); setPage(0); setFilters({}); }}>{tab.kind === 'face-checkins' ? <ScanFace aria-hidden="true" className="mr-2 inline h-4 w-4" /> : <Bot aria-hidden="true" className="mr-2 inline h-4 w-4" />}{t(tab.labelKey)}</button>)}</div>
      <form className="mb-5 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-2 lg:grid-cols-6 dark:border-slate-800 dark:bg-slate-900" onSubmit={submit}>
        <FilterInput label={t('operations.userId')} type="number" value={inputs.userId} onChange={(value) => setInputs((current) => ({ ...current, userId: value }))} />
        {kind !== 'ai-actions' ? <FilterInput label={t('operations.labId')} type="number" value={inputs.labId} onChange={(value) => setInputs((current) => ({ ...current, labId: value }))} /> : null}
        {kind !== 'ai-usage' ? <FilterInput label={kind === 'face-checkins' ? t('operations.bookingId') : t('operations.resourceId')} type="number" value={inputs.resourceId} onChange={(value) => setInputs((current) => ({ ...current, resourceId: value }))} /> : null}
        {kind === 'ai-usage' ? <FilterInput label={t('operations.module')} value={inputs.module} onChange={(value) => setInputs((current) => ({ ...current, module: value }))} /> : null}
        {kind === 'ai-actions' ? <FilterSelect label={t('operations.assistant')} value={inputs.assistantKey} options={Object.keys(assistantKeys).map((value) => ({ value, label: enumLabel(value, assistantKeys, locale) }))} onChange={(value) => setInputs((current) => ({ ...current, assistantKey: value }))} /> : null}
        {kind === 'ai-actions' ? <FilterSelect label={t('operations.resourceType')} value={inputs.resourceType} options={Object.keys(resourceKeys).map((value) => ({ value, label: enumLabel(value, resourceKeys, locale) }))} onChange={(value) => setInputs((current) => ({ ...current, resourceType: value }))} /> : null}
        {kind === 'face-checkins' ? <FilterSelect label={t('operations.result')} value={inputs.result} options={['SUCCESS', 'FAILED', 'DENIED'].map((value) => ({ value, label: enumLabel(value, statusKeys, locale) }))} onChange={(value) => setInputs((current) => ({ ...current, result: value }))} /> : null}
        <FilterInput label={t('operations.fromDate')} type="date" value={inputs.from} onChange={(value) => setInputs((current) => ({ ...current, from: value }))} />
        <FilterInput label={t('operations.toDate')} type="date" value={inputs.to} onChange={(value) => setInputs((current) => ({ ...current, to: value }))} />
        <div className="flex flex-col gap-2 sm:col-span-2 sm:flex-row sm:items-end lg:col-span-6 lg:justify-end"><Button className="w-full sm:w-auto" variant="outline" onClick={reset}>{t('common.reset')}</Button><Button className="w-full sm:w-auto" type="submit">{t('operations.filter')}</Button></div>
      </form>
      {query.isLoading ? <div aria-busy="true" className="space-y-2 rounded-lg border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">{[0,1,2,3].map((value) => <div className="h-12 animate-pulse rounded bg-slate-100 dark:bg-slate-800" key={value} />)}</div> : query.isError ? <ErrorState className="dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" onRetry={() => void query.refetch()}>{t('operations.loadFailed')}</ErrorState> : rows.length === 0 ? <EmptyState className="dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">{t('operations.empty')}</EmptyState> : <ResponsiveTable className="border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"><table className="w-full min-w-[900px] text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-400"><tr><th className="px-4 py-3">{t('operations.time')}</th><th className="px-4 py-3">{t('operations.user')}</th><th className="px-4 py-3">{t('operations.scope')}</th><th className="px-4 py-3">{t('operations.event')}</th><th className="px-4 py-3">{t('operations.result')}</th><th className="px-4 py-3">{t('operations.safeDetail')}</th></tr></thead><tbody className="divide-y divide-slate-200 dark:divide-slate-800">{rows.map((row) => <tr className="text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800/60" key={row.id}><td className="whitespace-nowrap px-4 py-3">{new Date(row.createdAt).toLocaleString(locale === 'en' ? 'en-US' : 'vi-VN')}</td><td className="px-4 py-3">{row.actor}</td><td className="px-4 py-3">{row.scope}</td><td className="px-4 py-3 font-medium">{row.event}</td><td className="px-4 py-3"><span className="inline-flex rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold dark:bg-slate-800">{row.outcome}</span></td><td className="px-4 py-3">{row.detail}</td></tr>)}</tbody></table></ResponsiveTable>}
      {query.data && query.data.totalPages > 1 ? <nav aria-label={t('operations.pagination')} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-slate-500 dark:text-slate-400">{t('common.page')} {query.data.page + 1}/{query.data.totalPages} · {query.data.totalElements} {t('operations.records')}</p><div className="flex flex-wrap gap-2"><Button disabled={page === 0} size="sm" variant="outline" onClick={() => setPage((value) => value - 1)}>{t('common.previous')}</Button><Button disabled={page >= query.data.totalPages - 1} size="sm" variant="outline" onClick={() => setPage((value) => value + 1)}>{t('common.next')}</Button></div></nav> : null}
    </section>
  );
}

function FilterInput({ label, onChange, type = 'text', value }: { label: string; onChange: (value: string) => void; type?: string; value: string }) { const id = `operation-${label.toLowerCase().replace(/\s+/g, '-')}`; return <label className="block text-xs font-semibold text-slate-600 dark:text-slate-300" htmlFor={id}>{label}<input id={id} className="mt-1 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 dark:border-slate-700 dark:bg-slate-950 dark:text-white" min={type === 'number' ? 1 : undefined} type={type} value={value} onChange={(event) => onChange(event.target.value)} /></label>; }
function FilterSelect({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Array<{ value: string; label: string }>; value: string }) { const { t } = useI18n(); const id = `operation-${label.toLowerCase().replace(/\s+/g, '-')}`; return <label className="block text-xs font-semibold text-slate-600 dark:text-slate-300" htmlFor={id}>{label}<select id={id} className="mt-1 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 dark:border-slate-700 dark:bg-slate-950 dark:text-white" value={value} onChange={(event) => onChange(event.target.value)}><option value="">{t('common.all')}</option>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>; }
