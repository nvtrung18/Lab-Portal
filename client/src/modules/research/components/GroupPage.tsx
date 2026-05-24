import { useEffect, useMemo, useState } from 'react';

import { useCreateGroup, useGroupsByTopic } from '../hooks';
import type { ResearchGroup, ResearchTopic } from '../types';
import { formatDate, formatGroupStatus, getStatusClass } from '../utils';
import { CreateGroupModal } from './CreateGroupModal';
import { ProjectPage } from './ProjectPage';

interface GroupPageProps {
  labId: number | null;
  topic: ResearchTopic | null;
  canCreate: boolean;
}

export function GroupPage({ labId, topic, canCreate }: GroupPageProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const { data: groups = [], isError, isLoading, refetch } = useGroupsByTopic(labId, topic?.id);
  const createGroup = useCreateGroup(labId, topic?.id);

  useEffect(() => {
    setSelectedGroupId(null);
  }, [topic?.id]);

  useEffect(() => {
    if (!selectedGroupId && groups.length) {
      setSelectedGroupId(groups[0].id);
    }
  }, [groups, selectedGroupId]);

  const selectedGroup = useMemo<ResearchGroup | null>(
    () => groups.find((group) => group.id === selectedGroupId) ?? null,
    [groups, selectedGroupId],
  );

  if (!topic) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
        Vui lòng chọn một chủ đề nghiên cứu để xem nhóm.
      </section>
    );
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Nhóm nghiên cứu</h3>
            <p className="mt-1 text-sm text-slate-600">Danh sách nhóm thuộc chủ đề đang chọn.</p>
          </div>
          {canCreate ? (
            <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white" type="button" onClick={() => setIsCreateOpen(true)}>
              Tạo nhóm nghiên cứu
            </button>
          ) : null}
        </div>

        {isLoading ? (
          <p className="mt-5 text-sm text-slate-600">Đang tải danh sách nhóm nghiên cứu...</p>
        ) : isError ? (
          <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            Không thể tải danh sách nhóm nghiên cứu.
            <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
              Tải lại
            </button>
          </div>
        ) : !groups.length ? (
          <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
            Chưa có nhóm nghiên cứu nào.
          </div>
        ) : (
          <div className="mt-5 space-y-3">
            {groups.map((group) => {
              const isSelected = selectedGroupId === group.id;
              return (
                <article key={group.id} className={['rounded-md border p-4 transition', isSelected ? 'border-slate-900 bg-slate-50' : 'border-slate-200 bg-white hover:border-slate-300'].join(' ')}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h4 className="text-base font-semibold text-slate-950">{group.name}</h4>
                      <p className="mt-1 text-sm text-slate-600">{group.objective || 'Chưa cập nhật mục tiêu nghiên cứu'}</p>
                    </div>
                    <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
                      {formatGroupStatus(group.status)}
                    </span>
                  </div>
                  <p className="mt-3 text-sm text-slate-600">{group.description || 'Chưa cập nhật mô tả nhóm.'}</p>
                  <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                    <div>
                      <dt className="font-semibold text-slate-700">Số đề tài</dt>
                      <dd className="mt-1 text-slate-600">{group.projectCount ?? 0}</dd>
                    </div>
                    <div>
                      <dt className="font-semibold text-slate-700">Số thành viên</dt>
                      <dd className="mt-1 text-slate-600">{group.memberCount ?? 0}</dd>
                    </div>
                    <div>
                      <dt className="font-semibold text-slate-700">Ngày tạo</dt>
                      <dd className="mt-1 text-slate-600">{formatDate(group.createdAt)}</dd>
                    </div>
                  </dl>
                  <button className="mt-4 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100" type="button" onClick={() => setSelectedGroupId(group.id)}>
                    Xem đề tài
                  </button>
                </article>
              );
            })}
          </div>
        )}

        <CreateGroupModal
          isOpen={isCreateOpen}
          labId={labId}
          topic={topic}
          isSubmitting={createGroup.isPending}
          onClose={() => setIsCreateOpen(false)}
          onSubmit={(payload) => createGroup.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
        />
      </section>

      <ProjectPage group={selectedGroup} canCreate={canCreate && Boolean(selectedGroup)} />
    </div>
  );
}
