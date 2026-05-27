import { Navigate, Outlet } from 'react-router-dom';

import { getStoredUser } from '../../shared/api';
import { hasActiveMembership } from '../../shared/utils/membership';
import { useCurrentUser } from '../../modules/user/hooks';
import { LAB_MANAGER } from '../../shared/constants/roles';

interface ActiveMembershipRouteProps {
  allowLabManager?: boolean;
}

export function ActiveMembershipRoute({ allowLabManager = false }: ActiveMembershipRouteProps) {
  const storedUser = getStoredUser();
  const { data: currentUser, isLoading } = useCurrentUser();
  const user = currentUser
    ? {
        id: currentUser.id,
        fullName: currentUser.fullName,
        email: currentUser.email,
        roles: currentUser.roles.map((role) => role.replace(/^ROLE_/, '')),
        memberships: currentUser.memberships?.map((membership) => ({
          labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
          labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'Lab',
          role: membership.role,
          status: membership.status,
          joinedAt: membership.joinedAt ?? membership.createdAt,
        })),
      }
    : storedUser;

  if (allowLabManager && user?.roles.map((role) => role.replace(/^ROLE_/, '')).includes(LAB_MANAGER)) {
    return <Outlet />;
  }

  if (isLoading && !storedUser) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
        Đang kiểm tra membership...
      </section>
    );
  }

  if (!hasActiveMembership(user)) {
    return <Navigate to="/app/labs" replace />;
  }

  return <Outlet />;
}
