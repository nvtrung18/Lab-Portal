import { FormEvent, useMemo, useState } from 'react';

import { resolveApiAssetUrl } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useProductsByProject, useProductsByGroup, useResearchGroupsByProject, useSubmitProduct } from '../hooks';
import type { ResearchProduct, ResearchProductStatus, ResearchProductType } from '../types';
import { formatDate } from '../utils';

const MAX_FILE_SIZE = 50 * 1024 * 1024;

const PRODUCT_TYPE_OPTIONS: Array<{ value: ResearchProductType; label: string }> = [
  { value: 'FINAL_REPORT', label: 'Báo cáo tổng kết' },
  { value: 'SLIDE', label: 'Slide thuyết trình' },
  { value: 'SOURCE_CODE', label: 'Source code' },
  { value: 'DATASET', label: 'Bộ dữ liệu' },
  { value: 'DEMO_VIDEO', label: 'Video demo' },
  { value: 'PAPER', label: 'Bài báo' },
  { value: 'SOFTWARE_DEMO', label: 'Demo phần mềm' },
  { value: 'OTHER', label: 'Khác' },
];

const PRODUCT_TYPE_LABELS = Object.fromEntries(
  PRODUCT_TYPE_OPTIONS.map((option) => [option.value, option.label]),
) as Record<ResearchProductType, string>;

const STATUS_LABELS: Record<ResearchProductStatus, string> = {
  SUBMITTED: 'Đã nộp',
  ACCEPTED: 'Đã chấp nhận',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  REJECTED: 'Không đạt',
};

interface ProductPageProps {
  projectId: number;
  groupId?: number | null;
  role: typeof LAB_MANAGER | typeof STUDENT | string;
  groupRole?: 'LEADER' | 'MEMBER' | null;
  currentUserId?: number | null;
}

type UploadScope = 'group' | 'personal';

export function ProductPage({ projectId, groupId, role, groupRole, currentUserId }: ProductPageProps) {
  const [uploadScope, setUploadScope] = useState<UploadScope | null>(null);
  const [productType, setProductType] = useState<ResearchProductType>('FINAL_REPORT');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [externalLink, setExternalLink] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);

  const { data: projectProducts = [], isError: isProjectError, isLoading: isProjectLoading, refetch: refetchProject } = useProductsByProject(!groupId ? projectId : null);
  const { data: groupProducts = [], isError: isGroupError, isLoading: isGroupLoading, refetch: refetchGroup } = useProductsByGroup(groupId);

  const products = groupId ? groupProducts : projectProducts;
  const isError = groupId ? isGroupError : isProjectError;
  const isLoading = groupId ? isGroupLoading : isProjectLoading;
  const refetch = groupId ? refetchGroup : refetchProject;
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
  const submitLabel = uploadScope === 'group' ? 'Nộp sản phẩm nhóm' : 'Nộp sản phẩm cá nhân';

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
      setFormError('Dung lượng file không được vượt quá 50MB.');
      return;
    }
    setFile(selectedFile);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    const normalizedLink = externalLink.trim();

    if (!normalizedTitle) {
      setFormError('Tiêu đề sản phẩm là bắt buộc.');
      return;
    }
    if (!productType) {
      setFormError('Loại sản phẩm là bắt buộc.');
      return;
    }
    if (!file && !normalizedLink) {
      setFormError('Cần tải file hoặc nhập link minh chứng.');
      return;
    }
    if (file && file.size > MAX_FILE_SIZE) {
      setFormError('Dung lượng file không được vượt quá 50MB.');
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
          <h3 className="text-lg font-semibold text-slate-950">Sản phẩm nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">Danh sách sản phẩm đã nộp theo đề tài.</p>
        </div>
        {canUpload ? (
          <div className="flex flex-wrap gap-2">
            {canUploadGroup ? (
              <Button onClick={() => openUpload('group')}>Nộp sản phẩm nhóm</Button>
            ) : null}
            {canUploadPersonal ? (
              <Button onClick={() => openUpload('personal')} variant={canUploadGroup ? 'outline' : 'primary'}>
                Nộp sản phẩm cá nhân
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
              <p className="mt-1 text-sm text-slate-600">
                {uploadScope === 'group' ? 'Sản phẩm sẽ được gắn với nhóm nghiên cứu.' : 'Sản phẩm sẽ được ghi nhận cho cá nhân bạn.'}
              </p>
            </div>
            <Button disabled={submitProduct.isPending} onClick={resetForm} size="sm" type="button" variant="ghost">
              Đóng
            </Button>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">Loại sản phẩm</span>
              <select
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                value={productType}
                onChange={(event) => setProductType(event.target.value as ResearchProductType)}
              >
                {PRODUCT_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">Tiêu đề sản phẩm</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                maxLength={200}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>
            <label className="block text-sm lg:col-span-2">
              <span className="mb-1 block font-semibold text-slate-700">Mô tả</span>
              <textarea
                className="min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">Link minh chứng / repository / demo</span>
              <input
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                disabled={submitProduct.isPending}
                placeholder="https://..."
                value={externalLink}
                onChange={(event) => setExternalLink(event.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">File sản phẩm</span>
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
              <p className="mt-1 text-slate-600">{file.type || 'Không rõ loại file'} · {formatFileSize(file.size)}</p>
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
            <Button loading={submitProduct.isPending} loadingText="Đang nộp..." type="submit">
              {submitLabel}
            </Button>
            <Button disabled={submitProduct.isPending} onClick={resetForm} type="button" variant="outline">
              Hủy
            </Button>
          </div>
        </form>
      ) : null}

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải danh sách sản phẩm...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách sản phẩm.
        </ErrorState>
      ) : !products.length ? (
        <EmptyState className="mt-5">Chưa có sản phẩm nghiên cứu nào được nộp.</EmptyState>
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
  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-slate-950">v{product.version}</span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
              {PRODUCT_TYPE_LABELS[product.productType] ?? product.productType}
            </span>
          </div>
          <h4 className="mt-1 break-words font-semibold text-slate-950">{product.title}</h4>
          {product.description ? <p className="mt-2 text-sm text-slate-600">{product.description}</p> : null}
        </div>
        <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(product.status)}`}>
          {STATUS_LABELS[product.status] ?? product.status}
        </span>
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <ProductField label="Người nộp" value={getSubmitterLabel(product)} />
        <ProductField label="Ngày nộp" value={formatDate(product.submittedAt ?? product.createdAt)} />
        <ProductField label="Tên file" value={product.fileName || 'Không có file'} />
        <ProductField label="Dung lượng" value={formatFileSize(product.fileSize)} />
      </dl>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        {product.fileUrl ? (
          <a
            className="font-semibold text-blue-700 underline"
            href={resolveApiAssetUrl(product.fileUrl)}
            rel="noreferrer"
            target="_blank"
          >
            Tải/xem file
          </a>
        ) : null}
        {product.externalLink ? (
          <a
            className="font-semibold text-blue-700 underline"
            href={product.externalLink}
            rel="noreferrer"
            target="_blank"
          >
            Link minh chứng
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

function formatFileSize(size?: number | null) {
  if (size == null) {
    return 'Không có file';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
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
