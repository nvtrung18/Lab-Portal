import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useAdminAuditLogs } from '../hooks';
import type { AuditLogFilters } from '../api';

// Target translations
type Translator = (key: string, fallback?: string) => string;

const actionLabelKeys: Record<string, string> = {
  CHANGE_USER_ROLE: 'audit.action.changeUserRole',
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
      return 'bg-blue-500/10 text-blue-400 border border-blue-500/20';
    case 'LAB':
      return 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20';
    case 'SYSTEM_CONFIG':
      return 'bg-purple-500/10 text-purple-400 border border-purple-500/20';
    case 'BOOKING':
      return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
    case 'CLEANING':
      return 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20';
    case 'PENALTY':
      return 'bg-rose-500/10 text-rose-400 border border-rose-500/20';
    case 'RESEARCH':
      return 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20';
    case 'REPORT':
      return 'bg-teal-500/10 text-teal-400 border border-teal-500/20';
    case 'PRODUCT':
      return 'bg-sky-500/10 text-sky-400 border border-sky-500/20';
    case 'EVALUATION':
      return 'bg-pink-500/10 text-pink-400 border border-pink-500/20';
    default:
      return 'bg-slate-500/10 text-slate-400 border border-slate-500/20';
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
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-white">{t('audit.title')}</h2>
      </div>

      {/* Filter controls */}
      <form
        onSubmit={handleSearch}
        className="grid gap-4 rounded-lg border border-slate-800 bg-slate-900 p-4 shadow-sm sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="filter-keyword" className="text-xs font-medium text-slate-400">{t('audit.filter.keyword')}</label>
          <input
            id="filter-keyword"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none placeholder:text-slate-500 focus:border-white"
            placeholder={t('audit.filter.keywordPlaceholder')}
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-module" className="text-xs font-medium text-slate-400">{t('audit.filter.module')}</label>
          <select
            id="filter-module"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
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

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-action" className="text-xs font-medium text-slate-400">{t('audit.filter.action')}</label>
          <select
            id="filter-action"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
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

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-from-date" className="text-xs font-medium text-slate-400">{t('audit.filter.fromDate')}</label>
          <input
            id="filter-from-date"
            type="date"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
            value={fromDateInput}
            onChange={(e) => setFromDateInput(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="filter-to-date" className="text-xs font-medium text-slate-400">{t('audit.filter.toDate')}</label>
          <input
            id="filter-to-date"
            type="date"
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white outline-none focus:border-white"
            value={toDateInput}
            onChange={(e) => setToDateInput(e.target.value)}
          />
        </div>

        <div className="sm:col-span-2 md:col-span-3 lg:col-span-5 flex justify-end gap-2 pt-2 border-t border-slate-800">
          <Button
            type="button"
            className="border-slate-700 bg-transparent text-slate-200 hover:bg-slate-800"
            size="sm"
            variant="outline"
            onClick={handleReset}
          >
            {t('audit.reset')}
          </Button>
          <Button
            type="submit"
            className="bg-white text-slate-950 hover:bg-slate-200"
            size="sm"
          >
            {t('audit.search')}
          </Button>
        </div>
      </form>

      {/* Content State */}
      {isLoading ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-12 shadow-sm text-center">
          <LoadingState className="text-slate-300">{t('audit.loading')}</LoadingState>
        </div>
      ) : isError ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
          <ErrorState className="border-red-950 bg-red-950/20 text-red-400" onRetry={() => refetch()}>
            {t('audit.loadError')}
          </ErrorState>
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-lg border border-slate-800 bg-slate-900 p-12 shadow-sm text-center">
          <EmptyState className="border-slate-800 bg-slate-950 text-slate-400">
            {t('audit.empty')}
          </EmptyState>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-800 bg-slate-900 shadow-sm overflow-hidden">
          {/* Responsive Table */}
          <div className="max-w-full overscroll-x-contain overflow-x-auto">
            <table className="w-full min-w-[950px] divide-y divide-slate-800 text-sm text-left">
              <thead>
                <tr className="text-xs font-semibold uppercase text-slate-400 bg-slate-950/50">
                  <th className="px-4 py-3">{t('audit.table.time')}</th>
                  <th className="px-4 py-3">{t('audit.table.actor')}</th>
                  <th className="px-4 py-3">{t('audit.table.role')}</th>
                  <th className="px-4 py-3">{t('audit.table.module')}</th>
                  <th className="px-4 py-3">{t('audit.table.action')}</th>
                  <th className="px-4 py-3">{t('audit.table.target')}</th>
                  <th className="px-4 py-3">{t('audit.table.description')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 bg-transparent">
                {data.items.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-950/20 transition-colors">
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatLocalizedDateTime(log.createdAt, locale, t)}
                    </td>
                    <td className="px-4 py-4.5 text-slate-100 font-medium whitespace-nowrap">
                      {log.actorName || `ID: ${log.actorId}`}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatRoleLocalized(log.actorRole, t)}
                    </td>
                    <td className="px-4 py-4.5 whitespace-nowrap">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${getModuleBadgeColor(log.module)}`}>
                        {t(moduleLabelKeys[log.module] ?? '', log.module)}
                      </span>
                    </td>
                    <td className="px-4 py-4.5 text-slate-100 font-medium whitespace-nowrap">
                      {t(actionLabelKeys[log.action] ?? '', log.action)}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 whitespace-nowrap">
                      {formatTargetLocalized(log.targetType, log.targetId, t)}
                    </td>
                    <td className="px-4 py-4.5 text-slate-300 max-w-xs truncate" title={log.description}>
                      {log.description || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-slate-800 bg-slate-950/25 px-4 py-3.5 text-sm text-slate-400">
            <div>
              {t('audit.pagination.showing')} <span className="font-semibold text-slate-200">{page * data.size + 1}</span> {t('audit.pagination.to')}{' '}
              <span className="font-semibold text-slate-200">
                {Math.min((page + 1) * data.size, data.totalElements)}
              </span>{' '}
                {t('audit.pagination.of')} <span className="font-semibold text-slate-200">{data.totalElements}</span> {t('audit.pagination.entries')}
            </div>

            <div className="flex items-center gap-2">
              <Button
                className="border-slate-700 bg-transparent text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:hover:bg-transparent"
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={handlePrevPage}
              >
                {t('audit.previous')}
              </Button>
              <div className="flex items-center gap-1.5">
                {Array.from({ length: data.totalPages }).map((_, index) => {
                  // Only display max 5 page buttons around current page
                  if (
                    index === 0 ||
                    index === data.totalPages - 1 ||
                    Math.abs(index - page) <= 1
                  ) {
                    return (
                      <button
                        key={index}
                        className={`h-8 w-8 rounded-md text-xs font-semibold transition ${
                          page === index
                            ? 'bg-white text-slate-950'
                            : 'text-slate-400 hover:bg-slate-800 hover:text-white'
                        }`}
                        onClick={() => setPage(index)}
                      >
                        {index + 1}
                      </button>
                    );
                  }
                  if (index === 1 || index === data.totalPages - 2) {
                    return <span key={index} className="text-slate-600 px-0.5">...</span>;
                  }
                  return null;
                })}
              </div>
              <Button
                className="border-slate-700 bg-transparent text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:hover:bg-transparent"
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
