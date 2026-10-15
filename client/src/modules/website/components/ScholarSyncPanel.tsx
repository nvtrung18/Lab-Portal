import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { Member } from '../../../../../contracts/website-content';
import { scholarAuthorId } from '../../../../../contracts/scholar-author-id';
import { getScholarSyncStatus, syncScholarNow } from '../api';

export function ScholarSyncPanel({ members }: { members: Member[] }) {
  const { locale } = useI18n();
  const l = (vi: string, en: string) => locale === 'vi' ? vi : en;
  const client = useQueryClient();
  const key = ['admin', 'website', 'scholar-sync'];
  const query = useQuery({ queryKey: key, queryFn: getScholarSyncStatus, refetchInterval: 5000 });
  const mutation = useMutation({ mutationFn: syncScholarNow, onSuccess: async () => {
    await client.invalidateQueries({ queryKey: key });
    await client.invalidateQueries({ queryKey: ['public', 'website'] });
  } });
  const date = (value: string | null) => value ? new Date(value).toLocaleString(locale === 'vi' ? 'vi-VN' : 'en-GB') : '—';
  const labels: Record<string, string> = {
    QUEUED: l('Đang chờ', 'Queued'), NEVER_SYNCED: l('Chưa đồng bộ', 'Never synced'), RUNNING: l('Đang chờ / đồng bộ', 'Queued / running'),
    SUCCEEDED: l('Thành công', 'Succeeded'), FAILED: l('Thất bại — giữ dữ liệu cũ', 'Failed — previous data retained'),
    RETRY_REQUIRED: l('Lần đồng bộ bị gián đoạn — có thể thử lại', 'Interrupted sync — retry available'),
    CONFIGURATION_REQUIRED: l('Chờ cấu hình API key', 'API key required'),
  };
  return <section aria-label={l('Đồng bộ Google Scholar', 'Google Scholar synchronization')} className="mb-6 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
    <h3 className="font-semibold">Google Scholar</h3>
    <p className="mt-2 text-sm text-slate-500">{l('Snapshot đồng bộ được lưu riêng, không thay đổi bản nháp, revision, tên, vai trò hay ảnh. Website công khai sử dụng snapshot mới cho thành viên đã xuất bản; form biên tập vẫn giữ dữ liệu nhập tay. Hãy lưu nháp trước khi đồng bộ một hồ sơ mới hoặc URL mới.', 'Synced snapshots are stored separately from the CMS draft, revision and edited profile fields. Published profiles use the new snapshot; the editor retains manually entered data. Save the draft before syncing a new member or URL.')}</p>
    {query.data && <p className="mt-2 text-sm">{query.data.enabled ? l('Tự động: 7 ngày/lần.', 'Automatic: every 7 days.') : l('Tự động đang tắt. Có thể đồng bộ ngay khi đã cấu hình key.', 'Automatic sync is off. Manual sync is available when the key is configured.')}</p>}
    {query.data && !query.data.configured && <p role="status" className="mt-2 text-sm text-amber-700 dark:text-amber-300">{l('Backend chưa cấu hình SCHOLAR_SERPAPI_API_KEY. Dữ liệu hiện tại được giữ nguyên.', 'SCHOLAR_SERPAPI_API_KEY is not configured on the backend. Existing data is preserved.')}</p>}
    {query.isLoading && <p role="status">{l('Đang tải trạng thái…', 'Loading status…')}</p>}
    {query.isError && <p role="alert" className="mt-3 text-sm text-red-600">{l('Không tải được trạng thái.', 'Could not load sync status.')} <button type="button" className="underline" onClick={() => { void query.refetch(); }}>{l('Thử lại', 'Retry')}</button></p>}
    {mutation.isError && <p role="alert" className="mt-3 text-sm text-red-600">{l('Không gửi được yêu cầu đồng bộ. Dữ liệu cũ được giữ nguyên.', 'Could not request synchronization. Existing data is preserved.')}</p>}
    {mutation.isSuccess && <p role="status" className="mt-3 text-sm">{l('Đã gửi yêu cầu. Theo dõi trạng thái bên dưới; yêu cầu đang chạy hoặc gửi trong vòng 60 giây sẽ được gộp.', 'Request sent. Check the status below; running or repeated requests within 60 seconds are coalesced.')}</p>}
    <div className="mt-4 space-y-4">{members.filter(member => member.scholarProfile).map(member => {
      const authorId = scholarAuthorId(member.scholarProfile?.url ?? '');
      const status = query.data?.members.find(item => item.memberId === member.id && item.authorId === authorId);
      return <div className="border-t border-slate-200 pt-3 dark:border-slate-700" key={member.id}>
        <div className="flex flex-wrap items-center justify-between gap-3"><strong className="text-sm">{member.title[locale] || member.title.vi || member.title.en}</strong>
          <Button size="sm" variant="outline" disabled={!status || !query.data?.configured || ['RUNNING', 'QUEUED'].includes(status.status) || mutation.isPending}
            onClick={() => { if (status) mutation.mutate(status.authorId); }}>{l('Đồng bộ ngay', 'Sync now')}</Button></div>
        <p className="mt-1 text-sm" role="status">{status ? labels[status.status] ?? status.status : l('Lưu nháp với Scholar URL hợp lệ để kích hoạt.', 'Save a valid Scholar URL in the draft to enable sync.')}{status && <> · {status.publicationCount} {l('bài trong snapshot đồng bộ', 'articles in synced snapshot')}</>}</p>
        {status && <div className="mt-1 text-xs text-slate-500">
          <p>{l('Thành công gần nhất:', 'Last successful sync:')} {date(status.lastSuccessAt)}</p>
          <p>{l('Lần thử gần nhất:', 'Last attempt:')} {date(status.lastAttemptAt)} · {l('Lần tự động tiếp theo:', 'Next scheduled attempt:')} {date(status.nextAttemptAt)}</p>
          {status.sourceUpdatedAt && <p>{l('Thời điểm xử lý từ nguồn:', 'Provider processing time:')} {date(status.sourceUpdatedAt)}</p>}
          {status.error && <p className="text-red-600">{status.error}</p>}
          {status.errors?.length > 0 && <details className="mt-2"><summary>{l('Nhật ký lỗi gần đây', 'Recent error log')}</summary>
            <ul>{status.errors.map((error, index) => <li key={`${error.at}-${index}`}>{date(error.at)} — {error.code}</li>)}</ul></details>}
        </div>}
      </div>;
    })}</div>
  </section>;
}
