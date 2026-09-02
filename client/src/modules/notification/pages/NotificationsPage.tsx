import { Bell, BellRing, CheckCheck, ChevronLeft, ChevronRight } from 'lucide-react';
import { useState } from 'react';

import { Button, EmptyState, ErrorState } from '../../../shared/components';
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from '../hooks';
import type { NotificationItem } from '../types';

const dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateTimeFormatter.format(date);
}

function NotificationRow({ item, onRead }: { item: NotificationItem; onRead: (id: number) => void }) {
  const Icon = item.read ? Bell : BellRing;
  return (
    <li
      className={[
        'flex gap-3 border-b border-slate-200 px-4 py-4 last:border-b-0 sm:px-5 dark:border-slate-800',
        item.read ? 'bg-white dark:bg-slate-900' : 'bg-sky-50/70 dark:bg-sky-950/20',
      ].join(' ')}
    >
      <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200">
        <Icon aria-hidden="true" className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
          <h2 className="text-sm font-semibold text-slate-950 dark:text-white">{item.title}</h2>
          <time className="shrink-0 text-xs text-slate-500 dark:text-slate-400" dateTime={item.createdAt}>
            {formatDateTime(item.createdAt)}
          </time>
        </div>
        <p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">{item.message}</p>
        {!item.read ? (
          <button
            className="mt-2 min-h-11 rounded-md px-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 dark:text-slate-200 dark:hover:bg-slate-800"
            type="button"
            onClick={() => onRead(item.id)}
          >
            Đánh dấu đã đọc
          </button>
        ) : null}
      </div>
    </li>
  );
}

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const { data, isError, isLoading, refetch } = useNotifications(page);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  return (
    <section className="mx-auto max-w-5xl">
      <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <header>
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-500">Trung tâm cập nhật</p>
          <h1 className="mt-1 text-2xl font-semibold text-slate-950 dark:text-white">Thông báo</h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
            Theo dõi thay đổi về nghiên cứu, AI, booking và xác nhận có mặt.
          </p>
        </header>
        <Button
          className="self-start dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          disabled={!data?.unreadCount}
          loading={markAllRead.isPending}
          loadingText="Đang cập nhật..."
          variant="outline"
          onClick={() => markAllRead.mutate()}
        >
          <CheckCheck aria-hidden="true" className="h-4 w-4" />
          Đọc tất cả {data?.unreadCount ? `(${data.unreadCount})` : ''}
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        {isLoading ? (
          <div aria-busy="true" aria-label="Đang tải thông báo" className="space-y-3 p-5">
            {[0, 1, 2].map((item) => (
              <div className="h-20 animate-pulse rounded-md bg-slate-100 dark:bg-slate-800" key={item} />
            ))}
          </div>
        ) : isError ? (
          <ErrorState className="m-5 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" onRetry={() => void refetch()}>
            Không thể tải thông báo.
          </ErrorState>
        ) : !data?.items.length ? (
          <EmptyState className="m-5 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
            Bạn chưa có thông báo nào.
          </EmptyState>
        ) : (
          <ul aria-label="Danh sách thông báo">
            {data.items.map((item) => (
              <NotificationRow item={item} key={item.id} onRead={(id) => markRead.mutate(id)} />
            ))}
          </ul>
        )}

        {data && data.totalPages > 1 ? (
          <nav aria-label="Phân trang thông báo" className="flex items-center justify-between border-t border-slate-200 px-4 py-3 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">Trang {data.page + 1}/{data.totalPages}</p>
            <div className="flex gap-2">
              <Button aria-label="Trang thông báo trước" disabled={page === 0} size="sm" variant="outline" onClick={() => setPage((value) => value - 1)}>
                <ChevronLeft aria-hidden="true" className="h-4 w-4" /> Trước
              </Button>
              <Button aria-label="Trang thông báo sau" disabled={page >= data.totalPages - 1} size="sm" variant="outline" onClick={() => setPage((value) => value + 1)}>
                Sau <ChevronRight aria-hidden="true" className="h-4 w-4" />
              </Button>
            </div>
          </nav>
        ) : null}
      </div>
    </section>
  );
}
