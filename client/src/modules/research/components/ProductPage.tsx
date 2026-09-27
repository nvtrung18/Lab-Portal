import { FormEvent, useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import { useProductsByProject, useProductsByGroup, useResearchGroupsByProject, useSubmitProduct } from '../hooks';
import type { ResearchProduct, ResearchProductStatus, ResearchProductType } from '../types';
import { formatDate } from '../utils';
import { downloadProductFile } from '../api';

const MAX_FILE_SIZE = 50 * 1024 * 1024;

const PRODUCT_TYPE_VALUES: ResearchProductType[] = [
  'FINAL_REPORT', 'SLIDE', 'SOURCE_CODE', 'DATASET', 'DEMO_VIDEO', 'PAPER', 'SOFTWARE_DEMO', 'OTHER',
];

interface ProductPageProps {
  projectId: number;
  groupId?: number | null;
  role: typeof LAB_MANAGER | typeof STUDENT | string;
  groupRole?: 'LEADER' | 'MEMBER' | null;
  currentUserId?: number | null;
}

type UploadScope = 'group' | 'personal';

export function ProductPage({ projectId, groupId, role, groupRole, currentUserId }: ProductPageProps) {
  const { t } = useI18n();
  const [uploadScope, setUploadScope] = useState<UploadScope | null>(null);
  const [productType, setProductType] = useState<ResearchProductType>('FINAL_REPORT');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [externalLink, setExternalLink] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);

  const { data: projectProducts = [], isError: isProjectError, isLoading: isProjectLoading, refetch: refetchProject } = useProductsByProject(projectId);
  const { data: groupProducts = [], isError: isGroupError, isLoading: isGroupLoading, refetch: refetchGroup } = useProductsByGroup(groupId);

  const personalProducts = groupId && role === STUDENT
    ? projectProducts.filter((product) => product.groupId == null && product.submittedById === currentUserId)
    : [];
  const products = groupId ? [...groupProducts, ...personalProducts] : projectProducts;
  const isError = isProjectError || (Boolean(groupId) && isGroupError);
  const isLoading = isProjectLoading || (Boolean(groupId) && isGroupLoading);
  const refetch = () => { void refetchProject(); if (groupId) void refetchGroup(); };
  const shouldLoadGroups = role === STUDENT;
  const { data: groups = [] } = useResearchGroupsByProject(shouldLoadGroups ? projectId : null);
  const submitProduct = useSubmitProduct(projectId, setUploadProgress);

  const currentGroup = useMemo(() => {
    if (groupId) {
      return groups.find((group) => group.id === groupId) ?? null;
    }
    return groups.find((group) => group.myRole) ?? groups[0] ?? null;
  }, [groupId, groups]);

  const resolvedGroupId = groupId ?? currentGroup?.id ?? null;
  const memberRole = currentGroup?.members?.find((member) => member.userId === currentUserId)?.role ?? null;
  const resolvedGroupRole = groupRole ?? currentGroup?.myRole ?? memberRole;
  const hasGroupContext = Boolean(resolvedGroupId);
  const canUploadGroup = role === STUDENT && resolvedGroupRole === 'LEADER' && hasGroupContext;
  const canUploadPersonal = role === STUDENT && resolvedGroupRole === 'MEMBER' && hasGroupContext;
  const canUpload = canUploadGroup || canUploadPersonal;
  const submitLabel = uploadScope === 'group' ? t('research.product.groupSubmit') : t('research.product.personalSubmit');

  function openUpload(scope: UploadScope) {
    setUploadScope(scope);
    setFormError(null);
    setUploadProgress(0);
  }

  function resetForm() {
    setProductType('FINAL_REPORT');
    setTitle('');
    setDescription('');
    setExternalLink('');
    setFile(null);
    setFormError(null);
    setUploadProgress(0);
    setUploadScope(null);
  }

  function handleFileChange(selectedFile?: File) {
    setFormError(null);
    if (!selectedFile) {
      setFile(null);
      return;
    }
    if (selectedFile.size > MAX_FILE_SIZE) {
      setFile(null);
      setFormError(t('research.product.error.fileSize'));
      return;
    }
    setFile(selectedFile);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    const normalizedLink = externalLink.trim();

    if (!normalizedTitle) {
      setFormError(t('research.product.error.titleRequired'));
      return;
    }
    if (!productType) {
      setFormError(t('research.product.error.typeRequired'));
      return;
    }
    if (!file && !normalizedLink) {
      setFormError(t('research.product.error.sourceRequired'));
      return;
    }
    if (file && file.size > MAX_FILE_SIZE) {
      setFormError(t('research.product.error.fileSize'));
      return;
    }

    submitProduct.mutate(
      {
        projectId,
        groupId: uploadScope === 'group' ? resolvedGroupId : null,
        productType,
        title: normalizedTitle,
        description: description.trim(),
        externalLink: normalizedLink,
        file,
      },
      { onSuccess: resetForm },
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('research.product.title')}</h3>
        </div>
        {canUpload ? (
          <div className="flex flex-wrap gap-2">
            {canUploadGroup ? (
              <Button onClick={() => openUpload('group')}>{t('research.product.groupSubmit')}</Button>
            ) : null}
            {canUploadPersonal ? (
              <Button onClick={() => openUpload('personal')} variant={canUploadGroup ? 'outline' : 'primary'}>
                {t('research.product.personalSubmit')}
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>

      {uploadScope ? (
        <form className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h4 className="font-semibold text-slate-950">{submitLabel}</h4>
            </div>
            <Button disabled={submitProduct.isPending} onClick={resetForm} size="sm" type="button" variant="ghost">
              {t('common.close')}
            </Button>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">{t('research.product.type')}</span>
              <select
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                value={productType}
                onChange={(event) => setProductType(event.target.value as ResearchProductType)}
              >
                {PRODUCT_TYPE_VALUES.map((value) => (
                  <option key={value} value={value}>{getProductTypeLabel(value, t)}</option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">{t('research.product.titleLabel')}</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                maxLength={200}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>
            <label className="block text-sm lg:col-span-2">
              <span className="mb-1 block font-semibold text-slate-700">{t('research.product.description')}</span>
              <textarea
                className="min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">{t('research.product.evidence')}</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                placeholder="https://..."
                value={externalLink}
                onChange={(event) => setExternalLink(event.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">{t('research.product.file')}</span>
              <input
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                type="file"
                onChange={(event) => handleFileChange(event.target.files?.[0])}
              />
            </label>
          </div>

          {file ? (
            <div className="mt-4 rounded-md border border-slate-200 bg-white p-3 text-sm">
              <p className="font-semibold text-slate-800">{file.name}</p>
              <p className="mt-1 text-slate-600">{file.type || t('research.product.unknownFileType')} · {formatFileSize(file.size, t('research.product.noFile'))}</p>
            </div>
          ) : null}

          {submitProduct.isPending && file ? (
            <div className="mt-4">
              <div className="h-2 overflow-hidden rounded-full bg-slate-200">
                <div className="h-full bg-slate-900 transition-all" style={{ width: `${uploadProgress}%` }} />
              </div>
              <p className="mt-1 text-xs font-semibold text-slate-600">{uploadProgress}%</p>
            </div>
          ) : null}

          {formError ? <p className="mt-3 text-sm font-semibold text-red-700">{formError}</p> : null}

          <div className="mt-4 flex flex-wrap gap-2">
            <Button loading={submitProduct.isPending} loadingText={t('research.product.submitting')} type="submit">
              {submitLabel}
            </Button>
            <Button disabled={submitProduct.isPending} onClick={resetForm} type="button" variant="outline">
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      ) : null}

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.product.loading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {t('research.product.loadFailed')}
        </ErrorState>
      ) : !products.length ? (
        <EmptyState className="mt-5">{t('research.product.empty')}</EmptyState>
      ) : (
        <div className="mt-5 space-y-3">
          {products.map((product) => (
            <ProductItem key={product.id} product={product} />
          ))}
        </div>
      )}
    </section>
  );
}

function ProductItem({ product }: { product: ResearchProduct }) {
  const { locale, t } = useI18n();
  const [downloading, setDownloading] = useState(false);

  async function handleDownload() {
    setDownloading(true);
    try {
      const file = await downloadProductFile(product.id);
      const fileUrl = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = fileUrl;
      link.download = product.fileName || `product-${product.id}`;
      link.click();
      URL.revokeObjectURL(fileUrl);
    } catch {
      toast.error(t('common.serverFailure'));
    } finally {
      setDownloading(false);
    }
  }
  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-slate-950">v{product.version}</span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
              {getProductTypeLabel(product.productType, t)}
            </span>
          </div>
          <h4 className="mt-1 break-words font-semibold text-slate-950">{product.title}</h4>
          {product.description ? <p className="mt-2 text-sm text-slate-600">{product.description}</p> : null}
        </div>
        <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(product.status)}`}>
          {getProductStatusLabel(product.status, t)}
        </span>
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <ProductField label={t('research.product.uploader')} value={getSubmitterLabel(product)} />
        <ProductField label={t('research.product.submittedAt')} value={formatDate(product.submittedAt ?? product.createdAt, locale)} />
        <ProductField label={t('research.product.fileName')} value={product.fileName || t('research.product.noFile')} />
        <ProductField label={t('research.product.fileSize')} value={formatFileSize(product.fileSize, t('research.product.noFile'))} />
      </dl>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        {product.fileUrl ? (
          <button
            className="w-fit font-semibold text-blue-700 underline"
            disabled={downloading}
            onClick={handleDownload}
            type="button"
          >
            {t('research.product.download')}
          </button>
        ) : null}
        {product.externalLink ? (
          <a
            className="font-semibold text-blue-700 underline"
            href={product.externalLink}
            rel="noreferrer"
            target="_blank"
          >
            {t('research.product.evidenceLink')}
          </a>
        ) : null}
        {product.fileType ? <span className="text-slate-600">{product.fileType}</span> : null}
      </div>
    </article>
  );
}

function ProductField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 break-words text-slate-600">{value}</dd>
    </div>
  );
}

function getSubmitterLabel(product: ResearchProduct) {
  if (product.submittedByName && product.submittedByEmail) {
    return `${product.submittedByName} (${product.submittedByEmail})`;
  }
  return product.submittedByName ?? product.submittedByEmail ?? `#${product.submittedById ?? 'N/A'}`;
}

function formatFileSize(size: number | null | undefined, noFileLabel: string) {
  if (size == null) {
    return noFileLabel;
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

function getProductTypeLabel(type: ResearchProductType, t: ReturnType<typeof useI18n>['t']) {
  const key = {
    FINAL_REPORT: 'finalReport',
    SLIDE: 'slide',
    SOURCE_CODE: 'sourceCode',
    DATASET: 'dataset',
    DEMO_VIDEO: 'demoVideo',
    PAPER: 'paper',
    SOFTWARE_DEMO: 'softwareDemo',
    OTHER: 'other',
  }[type];
  return t(`research.product.type.${key}`);
}

function getProductStatusLabel(status: ResearchProductStatus, t: ReturnType<typeof useI18n>['t']) {
  const key = {
    SUBMITTED: 'submitted',
    ACCEPTED: 'accepted',
    NEEDS_REVISION: 'needsRevision',
    REJECTED: 'rejected',
  }[status];
  return t(`research.product.status.${key}`);
}

function getStatusClass(status: ResearchProductStatus) {
  if (status === 'ACCEPTED') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }
  if (status === 'NEEDS_REVISION') {
    return 'bg-orange-50 text-orange-700 ring-orange-200';
  }
  if (status === 'REJECTED') {
    return 'bg-red-50 text-red-700 ring-red-200';
  }
  return 'bg-blue-50 text-blue-700 ring-blue-200';
}
