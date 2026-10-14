import axios from 'axios';
import { BookOpenCheck, FileUp, RefreshCw, Trash2 } from 'lucide-react';
import { FormEvent, useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { getStoredRole, getStoredUser } from '../../../shared/api';
import { Button, ConfirmDialog } from '../../../shared/components';
import { translateBackendMessage, useI18n } from '../../../shared/i18n';
import type { Response } from '../../../shared/types';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { useGroupsByTopic, useResearchProjectsByLab, useResearchTopicsByLab } from '../../research/hooks';
import { useCurrentUser } from '../../user/hooks';
import { ingestKnowledgeDocument, reindexKnowledgeDocument, revokeKnowledgeDocument } from '../api';
import type { KnowledgeDocumentRequest, KnowledgeDomain, KnowledgeVisibility } from '../types';

function getError(error: unknown, t: ReturnType<typeof useI18n>['t']) {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as Partial<Response<unknown>> | undefined)?.message;
    if (message === 'RAG reindex must preserve permission metadata and increase version') {
      return t('knowledge.errorReindex');
    }
    if (message === 'An active RAG document already exists for the resource') {
      return t('knowledge.errorDuplicate');
    }
    if (message === 'RAG document not found') {
      return t('knowledge.errorNotFound');
    }
    return translateBackendMessage(message) ?? t('knowledge.errorUpdate');
  }
  return t('knowledge.errorGeneric');
}

const managerScopeOptions: Array<{ value: KnowledgeVisibility; labelKey: string }> = [
  { value: 'LAB_MEMBERS', labelKey: 'knowledge.scopeLabMembers' },
  { value: 'PROJECT_MEMBERS', labelKey: 'knowledge.scopeProjectMembers' },
  { value: 'GROUP_MEMBERS', labelKey: 'knowledge.scopeGroupMembers' },
  { value: 'OWNER', labelKey: 'knowledge.scopeOwner' },
];

export function KnowledgePage() {
  const { t } = useI18n();
  const isAdmin = getStoredRole()?.replace(/^ROLE_/, '') === 'ADMIN';
  const { data: currentUser } = useCurrentUser();
  const scopedUser = currentUser ?? getStoredUser();
  const managedLabId = getManagedLabId(scopedUser);
  const managedLabName = getManagedLabName(scopedUser);
  const projectsQuery = useResearchProjectsByLab(!isAdmin ? managedLabId : null);
  const topicsQuery = useResearchTopicsByLab(!isAdmin ? managedLabId : null);
  const [domain, setDomain] = useState<KnowledgeDomain>(isAdmin ? 'ADMIN' : 'LAB');
  const [visibility, setVisibility] = useState<KnowledgeVisibility>(isAdmin ? 'ADMIN_ONLY' : 'LAB_MEMBERS');
  const [resourceId, setResourceId] = useState('');
  const [version, setVersion] = useState('1');
  const [sourceType, setSourceType] = useState('POLICY');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [projectId, setProjectId] = useState('');
  const [topicId, setTopicId] = useState('');
  const [groupId, setGroupId] = useState('');
  const [documentId, setDocumentId] = useState('');
  const [error, setError] = useState('');
  const [revokeConfirmationOpen, setRevokeConfirmationOpen] = useState(false);
  const [lastResult, setLastResult] = useState<Awaited<ReturnType<typeof ingestKnowledgeDocument>> | null>(null);
  const projectOptions = useMemo(() => (projectsQuery.data ?? []).map((project) => ({
    value: String(project.id),
    label: project.code ? `${project.code} — ${project.title}` : project.title,
  })), [projectsQuery.data]);
  const localizedManagerScopeOptions = useMemo(() => managerScopeOptions.map((option) => ({
    value: option.value,
    label: t(option.labelKey),
  })), [t]);
  const topicOptions = useMemo(() => (topicsQuery.data ?? []).map((topic) => ({
    value: String(topic.id),
    label: topic.name,
  })), [topicsQuery.data]);
  const groupsQuery = useGroupsByTopic(
    visibility === 'GROUP_MEMBERS' ? managedLabId : null,
    visibility === 'GROUP_MEMBERS' && Number(topicId) > 0 ? Number(topicId) : null,
  );
  const groupOptions = useMemo(() => (groupsQuery.data ?? []).map((group) => ({
    value: String(group.id),
    label: group.name,
  })), [groupsQuery.data]);

  const changeVisibility = (nextVisibility: KnowledgeVisibility) => {
    setVisibility(nextVisibility);
    setDomain(nextVisibility === 'PROJECT_MEMBERS' || nextVisibility === 'GROUP_MEMBERS' ? 'RESEARCH' : 'LAB');
    setProjectId('');
    setTopicId('');
    setGroupId('');
    setError('');
  };

  const changeGroup = (nextGroupId: string) => {
    setGroupId(nextGroupId);
    setProjectId('');
    setError('');
  };

  const save = useMutation({
    mutationFn: (request: KnowledgeDocumentRequest) => documentId
      ? reindexKnowledgeDocument(Number(documentId), request)
      : ingestKnowledgeDocument(request),
    onSuccess: (result) => {
      setError('');
      setLastResult(result);
      setDocumentId(String(result.documentId));
      setVersion(String(result.version + 1));
    },
  });
  const revoke = useMutation({
    mutationFn: () => revokeKnowledgeDocument(Number(documentId)),
    onSuccess: () => { setLastResult(null); setDocumentId(''); },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const needsProject = visibility === 'PROJECT_MEMBERS';
    const needsGroup = visibility === 'GROUP_MEMBERS';
    const parsedVersion = Number(version);
    if (!resourceId.trim() || !title.trim() || !content.trim() || !Number.isInteger(parsedVersion) || parsedVersion <= 0) {
      setError(t('knowledge.validationRequired')); return;
    }
    if (!/^[A-Z][A-Z0-9_]*$/.test(sourceType)) {
      setError(t('knowledge.validationSource')); return;
    }
    if (!isAdmin && !managedLabId) { setError(t('knowledge.validationManager')); return; }
    if (needsProject && Number(projectId) <= 0) { setError(t('knowledge.validationProject')); return; }
    if (needsGroup && Number(groupId) <= 0) { setError(t('knowledge.validationGroup')); return; }
    setError(''); setLastResult(null);
    save.mutate({
      domain, resourceId: resourceId.trim(), version: parsedVersion, sourceType,
      title: title.trim(), content: content.trim(), visibility,
      ...(!isAdmin ? { labId: managedLabId! } : {}),
      ...(needsProject ? { projectId: Number(projectId) } : {}),
      ...(needsGroup ? { groupId: Number(groupId) } : {}),
    });
  };

  const startNewDocument = () => {
    setDocumentId('');
    setResourceId('');
    setVersion('1');
    setSourceType('POLICY');
    setTitle('');
    setContent('');
    setProjectId('');
    setTopicId('');
    setGroupId('');
    setDomain(isAdmin ? 'ADMIN' : 'LAB');
    setVisibility(isAdmin ? 'ADMIN_ONLY' : 'LAB_MEMBERS');
    setError('');
    setLastResult(null);
    save.reset();
    revoke.reset();
  };

  return (
    <section className="mx-auto max-w-6xl">
      <header className="mb-6"><p className="flex items-center gap-2 text-sm font-semibold tracking-normal text-slate-500 dark:text-slate-400"><BookOpenCheck aria-hidden="true" className="h-4 w-4" /> {t('knowledge.badge')}</p><h1 className="mt-1 text-2xl font-semibold text-slate-950 dark:text-white">{t('knowledge.title')}</h1></header>
      {!isAdmin && managedLabName ? <div className="mb-5 break-words rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-900 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-200"><span className="font-semibold">{t('knowledge.managedLab')}</span> {managedLabName}</div> : null}
      <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <form className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-5" onSubmit={submit}>
          {documentId ? <div className="mb-4 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-200"><p className="font-semibold">{t('knowledge.updating', undefined, { id: documentId })}</p></div> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            {isAdmin ? <Select disabled label={t('knowledge.scope')} value="ADMIN_ONLY" options={[{ value: 'ADMIN_ONLY', label: t('knowledge.adminOnly') }]} onChange={() => undefined} /> : <Select disabled={Boolean(documentId)} label={t('knowledge.audience')} value={visibility} options={localizedManagerScopeOptions} onChange={(value) => changeVisibility(value as KnowledgeVisibility)} />}
            <TextInput label={t('knowledge.resourceId')} value={resourceId} onChange={setResourceId} placeholder={t('knowledge.resourcePlaceholder')} />
            {visibility === 'PROJECT_MEMBERS' ? <Select disabled={Boolean(documentId) || projectsQuery.isLoading} label={t('knowledge.project')} value={projectId} placeholder={projectsQuery.isLoading ? t('knowledge.loadingProjects') : t('knowledge.choose')} options={projectOptions} onChange={(value) => { setProjectId(value); setError(''); }} /> : null}
            {visibility === 'GROUP_MEMBERS' ? <Select disabled={Boolean(documentId) || topicsQuery.isLoading} label={t('knowledge.topic')} value={topicId} placeholder={topicsQuery.isLoading ? t('knowledge.loadingTopics') : t('knowledge.choose')} options={topicOptions} onChange={(value) => { setTopicId(value); setGroupId(''); setError(''); }} /> : null}
            {visibility === 'GROUP_MEMBERS' && topicId ? <Select disabled={Boolean(documentId) || groupsQuery.isLoading} label={t('knowledge.group')} value={groupId} placeholder={groupsQuery.isLoading ? t('knowledge.loadingGroups') : t('knowledge.choose')} options={groupOptions} onChange={changeGroup} /> : null}
          </div>
          {!isAdmin && (projectsQuery.isError || topicsQuery.isError || groupsQuery.isError) ? <p className="mt-3 text-sm text-red-700 dark:text-red-300" role="alert">{t('knowledge.loadOptionsFailed')}</p> : null}
          {!isAdmin && visibility === 'PROJECT_MEMBERS' && !projectsQuery.isLoading && projectOptions.length === 0 ? <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{t('knowledge.noProjects')}</p> : null}
          {!isAdmin && visibility === 'GROUP_MEMBERS' && !topicsQuery.isLoading && topicOptions.length === 0 ? <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{t('knowledge.noTopics')}</p> : null}
          {!isAdmin && visibility === 'GROUP_MEMBERS' && topicId && !groupsQuery.isLoading && groupOptions.length === 0 ? <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{t('knowledge.noGroups')}</p> : null}
          <details className="mt-4 rounded-md border border-slate-200 px-4 py-3 text-sm dark:border-slate-700">
            <summary className="cursor-pointer font-semibold text-slate-700 dark:text-slate-200">{t('knowledge.advanced')}</summary>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <TextInput label={t('knowledge.sourceType')} value={sourceType} onChange={(value) => setSourceType(value.toUpperCase())} placeholder="POLICY" />
              <NumberInput label={t('knowledge.version')} value={version} onChange={setVersion} />
              {!isAdmin ? <TextInput label={t('knowledge.labId')} value={String(managedLabId ?? '')} onChange={() => undefined} disabled /> : null}
            </div>
          </details>
          <TextInput className="mt-4" label={t('knowledge.documentTitle')} value={title} onChange={setTitle} />
          <label className="mt-4 block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="knowledge-content">{t('knowledge.content')}<textarea id="knowledge-content" className="mt-2 min-h-64 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base leading-6 text-slate-950 dark:border-slate-700 dark:bg-slate-950 dark:text-white" maxLength={512000} value={content} onChange={(event) => setContent(event.target.value)} /></label>
          {error || save.isError ? <p className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">{error || getError(save.error, t)}</p> : null}
          <Button className="mt-5 w-full sm:w-auto" loading={save.isPending} loadingText={t('knowledge.indexing')} type="submit"><FileUp aria-hidden="true" className="h-4 w-4" /> {documentId ? t('knowledge.reindex') : t('knowledge.ingest')}</Button>
        </form>

        <aside className="space-y-5">
          <details className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"><summary className="flex cursor-pointer list-none items-center gap-2 text-base font-semibold text-slate-950 dark:text-white"><RefreshCw aria-hidden="true" className="h-4 w-4" /> {t('knowledge.manageDocuments')}</summary><label className="mt-4 block text-sm font-medium text-slate-700 dark:text-slate-200" htmlFor="knowledge-document-id">{t('knowledge.documentId')}<input id="knowledge-document-id" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base dark:border-slate-700 dark:bg-slate-950 dark:text-white" min={1} type="number" value={documentId} onChange={(event) => setDocumentId(event.target.value)} /></label><Button className="mt-4 w-full" disabled={Number(documentId) <= 0} loading={revoke.isPending} variant="danger" onClick={() => setRevokeConfirmationOpen(true)}><Trash2 aria-hidden="true" className="h-4 w-4" /> {t('knowledge.revoke')}</Button>{revoke.isError ? <p className="mt-3 text-sm text-red-700 dark:text-red-300" role="alert">{getError(revoke.error, t)}</p> : null}</details>
          {lastResult ? <section className="rounded-lg border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200"><h2 className="font-semibold">{t('knowledge.indexed')}</h2><dl className="mt-3 grid grid-cols-2 gap-2"><dt>{t('knowledge.documentId')}</dt><dd className="break-all font-semibold text-right">{lastResult.documentId}</dd><dt>{t('knowledge.storage')}</dt><dd className="break-all font-semibold text-right">{lastResult.namespace}</dd><dt>{t('knowledge.chunks')}</dt><dd className="font-semibold text-right">{lastResult.chunkCount}</dd><dt>{t('knowledge.version')}</dt><dd className="font-semibold text-right">{lastResult.version}</dd></dl><Button className="mt-4 w-full" type="button" variant="outline" onClick={startNewDocument}>{t('knowledge.newDocument')}</Button></section> : null}
        </aside>
      </div>
      <ConfirmDialog
        confirmLabel={t('knowledge.revokeConfirmLabel')}
        isOpen={revokeConfirmationOpen}
        isPending={revoke.isPending}
        message={t('knowledge.revokeConfirm')}
        onCancel={() => setRevokeConfirmationOpen(false)}
        onConfirm={() => revoke.mutate(undefined, { onSuccess: () => setRevokeConfirmationOpen(false) })}
        title={t('knowledge.revokeTitle')}
      />
    </section>
  );
}

function TextInput({ className = '', disabled = false, label, min, onChange, placeholder, type = 'text', value }: { className?: string; disabled?: boolean; label: string; min?: number; onChange: (value: string) => void; placeholder?: string; type?: 'text' | 'number'; value: string }) { const id = `knowledge-${label.toLowerCase().replace(/\s+/g, '-')}`; return <label className={`block text-sm font-semibold text-slate-700 dark:text-slate-200 ${className}`} htmlFor={id}>{label}<input id={id} className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 disabled:bg-slate-100 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:disabled:bg-slate-800" disabled={disabled} min={min} placeholder={placeholder} type={type} value={value} onChange={(event) => onChange(event.target.value)} /></label>; }
function NumberInput({ label, onChange, value }: { label: string; onChange: (value: string) => void; value: string }) { return <TextInput label={label} min={1} type="number" value={value} onChange={onChange} />; }
function Select({ disabled = false, label, onChange, options, placeholder, value }: { disabled?: boolean; label: string; onChange: (value: string) => void; options: Array<{ value: string; label: string }>; placeholder?: string; value: string }) { const id = `knowledge-${label.toLowerCase().replace(/\s+/g, '-')}`; return <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor={id}>{label}<select id={id} className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-950 disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:disabled:bg-slate-800" disabled={disabled} value={value} onChange={(event) => onChange(event.target.value)}>{placeholder ? <option value="">{placeholder}</option> : null}{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>; }
