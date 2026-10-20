import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useAdminAuditLogs } from '../hooks';
import type { AuditLogFilters } from '../api';

// Target translations
type Translator = (key: string, fallback?: string) => string;

const actionLabelKeys: Record<string, string> = {
  CHANGE_USER_ROLE: 'audit.action.changeUserRole',
  CHANGE_LAB_MEMBER_ROLE: 'audit.action.changeLabMemberRole',
  BAN_USER: 'audit.action.banUser',
  UNBAN_USER: 'audit.action.unbanUser',
  CREATE_LAB: 'audit.action.createLab',
  UPDATE_LAB: 'audit.action.updateLab',
  DEACTIVATE_LAB: 'audit.action.deactivateLab',
  ASSIGN_MANAGER: 'audit.action.assignManager',
  UPDATE_SYSTEM_CONFIG: 'audit.action.updateSystemConfig',
  CREATE_SLOT: 'audit.action.createSlot',
  CANCEL_SLOT: 'audit.action.cancelSlot',
  REVIEW_APPLICATION: 'audit.action.reviewApplication',
  STUDENT_UPLOAD_REPORT: 'audit.action.studentUploadReport',
  LEADER_REVIEW_REPORT: 'audit.action.leaderReviewReport',
  MANAGER_REVIEW_REPORT: 'audit.action.managerReviewReport',
  STUDENT_UPLOAD_PRODUCT: 'audit.action.studentUploadProduct',
  CREATE_RESEARCH_PROJECT: 'audit.action.createResearchProject',
  UPDATE_RESEARCH_PROJECT: 'audit.action.updateResearchProject',
  CREATE_RESEARCH_GROUP: 'audit.action.createResearchGroup',
  UPDATE_RESEARCH_GROUP: 'audit.action.updateResearchGroup',
  EVALUATE_STUDENT: 'audit.action.evaluateStudent',
};

const moduleLabelKeys: Record<string, string> = {
  USER: 'audit.module.user',
  LAB: 'audit.module.lab',
  SYSTEM_CONFIG: 'audit.module.systemConfig',
  BOOKING: 'audit.module.booking',
  CLEANING: 'audit.module.cleaning',
  PENALTY: 'audit.module.penalty',
  RESEARCH: 'audit.module.research',
  REPORT: 'audit.module.report',
  PRODUCT: 'audit.module.product',
  EVALUATION: 'audit.module.evaluation',
};

const targetLabelKeys: Record<string, string> = {
  USER: 'audit.target.user',
  MEMBERSHIP: 'audit.target.membership',
  LAB: 'audit.target.lab',
  SYSTEM_CONFIG: 'audit.target.systemConfig',
  SLOT: 'audit.target.slot',
  BOOKING: 'audit.target.booking',
  CLEANING_TASK: 'audit.target.cleaningTask',
  PENALTY: 'audit.target.penalty',
  RESEARCH_PROJECT: 'audit.target.researchProject',
  RESEARCH_GROUP: 'audit.target.researchGroup',
  REPORT: 'audit.target.report',
  PRODUCT: 'audit.target.product',
  EVALUATION: 'audit.target.evaluation',
};

function formatTargetLocalized(targetType: string | undefined, targetId: number | null | undefined, t: Translator) {
  if (!targetType) return '-';
  const normalizedType = targetType.toUpperCase();
  const label = t(targetLabelKeys[normalizedType] ?? '', targetType);
  return targetId ? `${label} (ID: ${targetId})` : label;
}

function formatRoleLocalized(role: string | undefined, t: Translator) {
  if (!role) return '-';
  if (role === 'ADMIN') return t('audit.role.admin');
  if (role === 'LAB_MANAGER' || role === 'MANAGER') return t('audit.role.manager');
  if (role === 'STUDENT' || role === 'USER') return t('audit.role.student');
  return role;
}

function formatLocalizedDateTime(value: string | undefined, locale: 'vi' | 'en', t: Translator) {
  if (!value) return t('common.notUpdated');
  try {
    return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value));
  } catch {
    return value;
  }
}

// Module badge styling class helper
const getModuleBadgeColor = (module: string) => {
  switch (module.toUpperCase()) {
    case 'USER':
      return 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20 dark:bg-blue-950/60 dark:text-blue-300 dark:ring-blue-500/30';
    case 'LAB':
      return 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20 dark:bg-emerald-950/60 dark:text-emerald-300 dark:ring-emerald-500/30';
    case 'SYSTEM_CONFIG':
      return 'bg-purple-50 text-purple-700 ring-1 ring-purple-600/20 dark:bg-purple-950/60 dark:text-purple-300 dark:ring-purple-500/30';
    case 'BOOKING':
      return 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20 dark:bg-amber-950/60 dark:text-amber-300 dark:ring-amber-500/30';
    case 'CLEANING':
      return 'bg-cyan-50 text-cyan-700 ring-1 ring-cyan-600/20 dark:bg-cyan-950/60 dark:text-cyan-300 dark:ring-cyan-500/30';
    case 'PENALTY':
      return 'bg-rose-50 text-rose-700 ring-1 ring-rose-600/20 dark:bg-rose-950/60 dark:text-rose-300 dark:ring-rose-500/30';
    case 'RESEARCH':
      return 'bg-indigo-50 text-indigo-700 ring-1 ring-indigo-600/20 dark:bg-indigo-950/60 dark:text-indigo-300 dark:ring-indigo-500/30';
    case 'REPORT':
      return 'bg-teal-50 text-teal-700 ring-1 ring-teal-600/20 dark:bg-teal-950/60 dark:text-teal-300 dark:ring-teal-500/30';
    case 'PRODUCT':
      return 'bg-sky-50 text-sky-700 ring-1 ring-sky-600/20 dark:bg-sky-950/60 dark:text-sky-300 dark:ring-sky-500/30';
    case 'EVALUATION':
      return 'bg-pink-50 text-pink-700 ring-1 ring-pink-600/20 dark:bg-pink-950/60 dark:text-pink-300 dark:ring-pink-500/30';
    default:
      return 'bg-slate-100 text-slate-700 ring-1 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700';
  }
};

export function AdminAuditLogPage() {
  const { locale, t } = useI18n();
  const [page, setPage] = useState(0);
  const [moduleInput, setModuleInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [fromDateInput, setFromDateInput] = useState('');
  const [toDateInput, setToDateInput] = useState('');
  const [keywordInput, setKeywordInput] = useState('');

  const [appliedFilters, setAppliedFilters] = useState<AuditLogFilters>({});

  const { data, isLoading, isError, refetch } = useAdminAuditLogs(page, appliedFilters);
  const lastPage = (data?.totalPages ?? 0) - 1;
  // Include ellipsis positions without allocating an entry for every page.
  const paginationIndexes = [...new Set([0, 1, page - 1, page, page + 1, lastPage - 1, lastPage])]
    .filter((index) => index >= 0 && index <= lastPage)
    .sort((left, right) => left - right);

  const handleSearch = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setPage(0);

    let formattedFromDate: string | undefined = undefined;
    let formattedToDate: string | undefined = undefined;

    if (fromDateInput) {
      formattedFromDate = new Date(`${fromDateInput}T00:00:00`).toISOString();
    }
    if (toDateInput) {
      formattedToDate = new Date(`${toDateInput}T23:59:59`).toISOString();
    }

    setAppliedFilters({
      module: moduleInput || undefined,
      action: actionInput || undefined,
      fromDate: formattedFromDate,
      toDate: formattedToDate,
      keyword: keywordInput.trim() || undefined,
    });
  };

  const handleReset = () => {
    setPage(0);
    setModuleInput('');
    setActionInput('');
    setFromDateInput('');
    setToDateInput('');
    setKeywordInput('');
    setAppliedFilters({});
  };

  const handlePrevPage = () => {
    if (page > 0) {
      setPage(page - 1);
    }
  };

  const handleNextPage = () => {
    if (data && page < data.totalPages - 1) {
      setPage(page + 1);
    }
  };

  return (
    <section className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          {t('audit.title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {locale === 'vi'
            ? 'Theo dõi toàn bộ lịch sử hoạt động, kiểm toán và thay đổi bảo mật của hệ thống'
            : 'Track system operational history, audit trails, and security changes'}
        </p>
      </div>

      {/* Filter controls */}
      <form
        onSubmit={handleSearch}
        className="grid min-w-0 gap-3 rounded-xl border border-slate-200/90 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/60 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5"
      >
        <div className="flex flex-col gap-1.5">
          <label htmlFor="filter-keyword" className="text-xs font-semibold text-slate-600 dark:text-slate-400">
            {t('audit.filter.keyword')}
          </label>
          <input
            id="filter-keyword"
            className="h-10 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-sm text-slate-900 placeholder:text-slate-400 transition-all focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-teal-400 dark:focus:bg-slate-900"
            placeholder={t('audit.filter.keywordPlaceholder')}
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="filter-module" className="text-xs font-semibold text-slate-600 dark:text-slate-400">
            {t('audit.filter.module')}
          </label>
          <select
            id="filter-module"
            className="h-10 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
            value={moduleInput}
            onChange={(e) => setModuleInput(e.target.value)}
          >
            <option value="">{t('audit.filter.allModules')}</option>
            {Object.entries(moduleLabelKeys).map(([key, labelKey]) => (
              <option key={key} value={key}>
                {t(labelKey)}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="filter-action" className="text-xs font-semibold text-slate-600 dark:text-slate-400">
            {t('audit.filter.action')}
          </label>
          <select
            id="filter-action"
            className="h-10 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-sm font-medium text-slate-700 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-200 dark:focus:border-teal-400 dark:focus:bg-slate-900"
            value={actionInput}
            onChange={(e) => setActionInput(e.target.value)}
          >
            <option value="">{t('audit.filter.allActions')}</option>
            {Object.entries(actionLabelKeys).map(([key, labelKey]) => (
              <option key={key} value={key}>
                {t(labelKey)}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="filter-from-date" className="text-xs font-semibold text-slate-600 dark:text-slate-400">
            {t('audit.filter.fromDate')}
          </label>
          <input
            id="filter-from-date"
            type="date"
            className="h-10 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-sm text-slate-900 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-100 dark:focus:border-teal-400 dark:focus:bg-slate-900"
            value={fromDateInput}
            onChange={(e) => setFromDateInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="filter-to-date" className="text-xs font-semibold text-slate-600 dark:text-slate-400">
            {t('audit.filter.toDate')}
          </label>
          <input
            id="filter-to-date"
            type="date"
            className="h-10 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50/50 px-3 text-sm text-slate-900 transition focus:border-teal-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500/20 dark:border-slate-700/80 dark:bg-slate-950/50 dark:text-slate-100 dark:focus:border-teal-400 dark:focus:bg-slate-900"
            value={toDateInput}
            onChange={(e) => setToDateInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-3 dark:border-slate-800 sm:col-span-2 sm:flex-row sm:justify-end xl:col-span-3 2xl:col-span-5">
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={handleReset}
          >
            {t('audit.reset')}
          </Button>
          <Button
            type="submit"
            size="sm"
            variant="primary"
          >
            {t('audit.search')}
          </Button>
        </div>
      </form>

      {/* Content State */}
      {isLoading ? (
        <div className="rounded-xl border border-slate-200/90 bg-white p-12 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
          <LoadingState className="text-slate-600 dark:text-slate-300">{t('audit.loading')}</LoadingState>
        </div>
      ) : isError ? (
        <div className="rounded-xl border border-slate-200/90 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
          <ErrorState onRetry={() => refetch()}>
            {t('audit.loadError')}
          </ErrorState>
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white p-12 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900/60">
          <EmptyState>
            {t('audit.empty')}
          </EmptyState>
        </div>
      ) : (
        <div className="rounded-xl border border-slate-200/90 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900/60 overflow-hidden">
          {/* Mobile Card List (< md) */}
          <div className="divide-y divide-slate-100 dark:divide-slate-800 md:hidden">
            {data.items.map((log) => (
              <article className="min-w-0 space-y-3 p-4" key={log.id}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <h3 className="min-w-0 flex-1 break-words font-semibold text-slate-900 dark:text-slate-100">
                    {t(actionLabelKeys[log.action] ?? '', log.action)}
                  </h3>
                  <span className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-1 text-xs font-semibold whitespace-nowrap ${getModuleBadgeColor(log.module)}`}>
                    {t(moduleLabelKeys[log.module] ?? '', log.module)}
                  </span>
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400">{formatLocalizedDateTime(log.createdAt, locale, t)}</p>
                <dl className="grid grid-cols-1 min-[380px]:grid-cols-2 gap-x-3 gap-y-2.5 text-xs">
                  <div className="min-w-0">
                    <dt className="text-slate-400 dark:text-slate-500">{t('audit.table.actor')}</dt>
                    <dd className="mt-0.5 break-words font-medium text-slate-800 dark:text-slate-200">{log.actorName || `ID: ${log.actorId}`}</dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-slate-400 dark:text-slate-500">{t('audit.table.role')}</dt>
                    <dd className="mt-0.5 break-words text-slate-700 dark:text-slate-300">{formatRoleLocalized(log.actorRole, t)}</dd>
                  </div>
                  <div className="min-w-0 min-[380px]:col-span-2">
                    <dt className="text-slate-400 dark:text-slate-500">{t('audit.table.target')}</dt>
                    <dd className="mt-0.5 break-words text-slate-700 dark:text-slate-300">{formatTargetLocalized(log.targetType, log.targetId, t)}</dd>
                  </div>
                  <div className="min-w-0 min-[380px]:col-span-2">
                    <dt className="text-slate-400 dark:text-slate-500">{t('audit.table.description')}</dt>
                    <dd className="mt-0.5 break-words text-slate-600 dark:text-slate-400">{log.description || '—'}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>

          {/* Desktop Table (>= md) */}
          <div aria-label={t('audit.title')} className="app-table-frame hidden min-w-0 max-w-full overflow-x-auto md:block" role="region" tabIndex={0}>
            <table className="w-full min-w-[960px] text-sm text-left border-collapse">
              <thead className="border-b border-slate-200/90 bg-slate-50/80 text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3.5 min-w-[150px] font-semibold">{t('audit.table.time')}</th>
                  <th className="px-4 py-3.5 min-w-[140px] font-semibold">{t('audit.table.actor')}</th>
                  <th className="px-4 py-3.5 min-w-[110px] font-semibold">{t('audit.table.role')}</th>
                  <th className="px-4 py-3.5 min-w-[120px] font-semibold">{t('audit.table.module')}</th>
                  <th className="px-4 py-3.5 min-w-[170px] font-semibold">{t('audit.table.action')}</th>
                  <th className="px-4 py-3.5 min-w-[170px] font-semibold">{t('audit.table.target')}</th>
                  <th className="px-4 py-3.5 min-w-[200px] font-semibold">{t('audit.table.description')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70">
                {data.items.map((log) => (
                  <tr key={log.id} className="transition-colors hover:bg-slate-50/80 dark:hover:bg-slate-800/40">
                    <td className="px-4 py-3.5 text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                      {formatLocalizedDateTime(log.createdAt, locale, t)}
                    </td>
                    <td className="px-4 py-3.5 font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {log.actorName || `ID: ${log.actorId}`}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300 whitespace-nowrap">
                      {formatRoleLocalized(log.actorRole, t)}
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap">
                      <span className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap ${getModuleBadgeColor(log.module)}`}>
                        {t(moduleLabelKeys[log.module] ?? '', log.module)}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      {t(actionLabelKeys[log.action] ?? '', log.action)}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-300 whitespace-nowrap">
                      {formatTargetLocalized(log.targetType, log.targetId, t)}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 dark:text-slate-400 max-w-xs truncate" title={log.description}>
                      {log.description || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-col items-center justify-between gap-4 border-t border-slate-200/90 bg-slate-50/50 px-4 py-3.5 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-950/25 dark:text-slate-400 lg:flex-row">
            <div className="text-center lg:text-left">
              {t('audit.pagination.showing')} <span className="font-semibold text-slate-900 dark:text-slate-200">{page * data.size + 1}</span> {t('audit.pagination.to')}{' '}
              <span className="font-semibold text-slate-900 dark:text-slate-200">
                {Math.min((page + 1) * data.size, data.totalElements)}
              </span>{' '}
                {t('audit.pagination.of')} <span className="font-semibold text-slate-900 dark:text-slate-200">{data.totalElements}</span> {t('audit.pagination.entries')}
            </div>

            <div className="flex max-w-full flex-wrap items-center justify-center gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={handlePrevPage}
              >
                {t('audit.previous')}
              </Button>
              <div className="flex flex-wrap items-center justify-center gap-1.5">
                {paginationIndexes.map((index) => {
                  // Only display max 5 page buttons around current page
                  if (
                    index === 0 ||
                    index === data.totalPages - 1 ||
                    Math.abs(index - page) <= 1
                  ) {
                    return (
                      <button
                        key={index}
                        type="button"
                        aria-label={`${t('common.page')} ${index + 1}`}
                        aria-current={page === index ? 'page' : undefined}
                        className={`app-button h-8 w-8 min-h-[32px] rounded-md text-xs font-semibold transition focus-visible:outline-none focus-visible:ring-2 ${
                          page === index
                            ? 'app-button-primary'
                            : 'app-button-ghost'
                        }`}
                        onClick={() => setPage(index)}
                      >
                        {index + 1}
                      </button>
                    );
                  }
                  if (index === 1 || index === data.totalPages - 2) {
                    return <span key={index} className="text-slate-400 dark:text-slate-600 px-0.5">...</span>;
                  }
                  return null;
                })}
              </div>
              <Button
                size="sm"
                variant="outline"
                disabled={page >= data.totalPages - 1}
                onClick={handleNextPage}
              >
                {t('audit.next')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
