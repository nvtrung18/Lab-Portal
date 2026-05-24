import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import {
  getActiveMemberships,
  getManagedLabId,
  getManagedLabName,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { GroupPage, TopicPage } from '../components';
import type { ResearchTopic } from '../types';
import { useCurrentUser } from '../../user/hooks';

export function ResearchPage() {
  const { data: currentUser, isLoading } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const activeMemberships = useMemo(() => getActiveMemberships(currentUser), [currentUser]);
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const [selectedLabId, setSelectedLabId] = useState<number | null>(null);
  const [selectedTopic, setSelectedTopic] = useState<ResearchTopic | null>(null);

  const availableLabs = useMemo(() => {
    if (isManager && managedLabId) {
      return [{ id: managedLabId, name: managedLabName ?? `PTN #${managedLabId}` }];
    }

    return activeMemberships
      .map((membership) => ({
        id: getMembershipLabId(membership),
        name: getMembershipLabName(membership),
      }))
      .filter((lab): lab is { id: number; name: string } => Boolean(lab.id));
  }, [activeMemberships, isManager, managedLabId, managedLabName]);

  useEffect(() => {
    if (!availableLabs.length) {
      setSelectedLabId(null);
      setSelectedTopic(null);
      return;
    }

    setSelectedLabId((current) =>
      current && availableLabs.some((lab) => lab.id === current) ? current : availableLabs[0].id,
    );
  }, [availableLabs]);

  useEffect(() => {
    setSelectedTopic(null);
  }, [selectedLabId]);

  const handleSelectTopic = useCallback((topic: ResearchTopic | null) => {
    setSelectedTopic(topic);
  }, []);

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-52 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-40 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isStudent && !activeMemberships.length) {
    return <Navigate to="/app/labs" replace />;
  }

  if (isManager && !managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        Bạn chưa được phân công quản lý PTN nào.
      </section>
    );
  }

  const canCreateOfficialResearch = isManager && Boolean(selectedLabId);

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">Nghiên cứu khoa học</h2>
            <p className="mt-2 text-sm text-slate-600">
              Quản lý chủ đề, nhóm và đề tài nghiên cứu trong PTN.
            </p>
          </div>

          {availableLabs.length > 1 ? (
            <label className="block text-sm font-medium text-slate-700 lg:w-80">
              PTN đang chọn
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={selectedLabId ?? ''}
                onChange={(event) => setSelectedLabId(Number(event.target.value))}
              >
                {availableLabs.map((lab) => (
                  <option key={lab.id} value={lab.id}>
                    {lab.name}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <div className="rounded-md bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
              {availableLabs[0]?.name ?? 'Chưa chọn PTN'}
            </div>
          )}
        </div>
      </div>

      <TopicPage
        labId={selectedLabId}
        canCreate={canCreateOfficialResearch}
        selectedTopicId={selectedTopic?.id ?? null}
        onSelectTopic={handleSelectTopic}
      />

      <GroupPage labId={selectedLabId} topic={selectedTopic} canCreate={canCreateOfficialResearch} />
    </section>
  );
}
