import { useEffect, useMemo, useState } from 'react';

import {
  getActiveMemberships,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { useLab } from '../../lab/hooks';
import { useCurrentUser } from '../hooks';

const ACTIVE_LAB_STORAGE_KEY = 'student.activeLabId';

export function OtherPage() {
  const { data: currentUser, isLoading } = useCurrentUser();
  const activeMemberships = useMemo(
    () => getActiveMemberships(currentUser),
    [currentUser],
  );
  const activeMembershipLabIds = useMemo(
    () =>
      activeMemberships
        .map(getMembershipLabId)
        .filter((labId): labId is number => Boolean(labId)),
    [activeMemberships],
  );
  const [activeLabId, setActiveLabId] = useState<number | null>(null);

  useEffect(() => {
    if (!activeMembershipLabIds.length) {
      setActiveLabId(null);
      return;
    }

    const storedLabId = Number(localStorage.getItem(ACTIVE_LAB_STORAGE_KEY));
    const nextLabId = activeMembershipLabIds.includes(storedLabId)
      ? storedLabId
      : activeMembershipLabIds[0];

    setActiveLabId(nextLabId);
  }, [activeMembershipLabIds]);

  useEffect(() => {
    if (activeLabId) {
      localStorage.setItem(ACTIVE_LAB_STORAGE_KEY, String(activeLabId));
    }
  }, [activeLabId]);

  const activeMembership = activeMemberships.find(
    (membership) => getMembershipLabId(membership) === activeLabId,
  );
  const { data: activeLab } = useLab(activeLabId);

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-28 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-24 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Student Lab Member
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Other</h2>
        <p className="mt-2 text-sm text-slate-600">
          Khu vực dành cho các lab bạn đã tham gia.
        </p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-sm font-semibold text-slate-950">Lab đang thao tác</h3>
            <p className="mt-1 text-sm text-slate-600">
              activeLabId: {activeLabId ?? 'N/A'}
            </p>
          </div>

          {activeMemberships.length > 1 ? (
            <select
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-950 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10 sm:w-72"
              value={activeLabId ?? ''}
              onChange={(event) => setActiveLabId(Number(event.target.value))}
            >
              {activeMemberships.map((membership) => {
                const labId = getMembershipLabId(membership);
                return (
                  <option key={labId} value={labId ?? ''}>
                    {getMembershipLabName(membership)}
                  </option>
                );
              })}
            </select>
          ) : (
            <span className="rounded-md bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
              {activeMembership ? getMembershipLabName(activeMembership) : 'N/A'}
            </span>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-950">Lab Info</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-slate-500">Lab name</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950">
              {activeLab?.labName ?? (activeMembership ? getMembershipLabName(activeMembership) : 'N/A')}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">Manager</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950">
              {activeLab?.manager?.fullName || activeLab?.manager?.email || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">Membership role</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950">
              {activeMembership?.role || 'MEMBER'}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-slate-500">Joined date</dt>
            <dd className="mt-1 text-sm font-medium text-slate-950">
              {activeMembership?.joinedAt ?? activeMembership?.createdAt ?? 'Chưa cập nhật'}
            </dd>
          </div>
        </dl>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-950">My Cleaning Tasks</h3>
        <p className="mt-2 text-sm text-slate-600">
          Task vệ sinh sẽ được lọc theo activeLabId = {activeLabId ?? 'N/A'}.
        </p>
      </div>
    </section>
  );
}
