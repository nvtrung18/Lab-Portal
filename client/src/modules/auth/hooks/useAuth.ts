import { getProfile } from '../../user/api';
import type { UserProfileResponse } from '../../user/api';
import {
  clearAuthTokens,
  getAuthToken,
  getStoredUser,
  setAuthTokens,
  setStoredUser,
  type StoredUser,
} from '../../../shared/api';
import { ADMIN, LAB_MANAGER, STUDENT, type Role } from '../../../shared/constants/roles';
import { hasActiveMembership, type MembershipLike } from '../../../shared/utils/membership';
import { getSafeReturnPath } from '../../../shared/utils/navigation';

function normalizeRoles(roles: string[]): Role[] {
  return roles
    .map((role) => role.replace(/^ROLE_/, '').toUpperCase())
    .filter((role): role is Role =>
      role === ADMIN || role === LAB_MANAGER || role === STUDENT,
    );
}

export function getPrimaryRedirectPath(user: { roles: string[]; memberships?: MembershipLike[] }) {
  const normalizedRoles = normalizeRoles(user.roles);

  if (normalizedRoles.includes(ADMIN)) {
    return '/admin/dashboard';
  }

  if (normalizedRoles.includes(LAB_MANAGER)) {
    return '/app/lab-overview';
  }

  if (normalizedRoles.includes(STUDENT)) {
    return hasActiveMembership(user) ? '/app/dashboard' : '/vi';
  }

  return '/403';
}

export function getPostAuthRedirectPath(
  user: { roles: string[]; memberships?: MembershipLike[] },
  returnUrl: string | null,
) {
  const destination = getPrimaryRedirectPath(user);
  const safeReturnPath = getSafeReturnPath(returnUrl, destination);
  if (destination !== '/vi' && /^\/(vi|en)#lab-admission$/.test(safeReturnPath)) return destination;
  return destination === '/vi' && !/^\/(vi|en)(\/|$)/.test(safeReturnPath)
    ? destination : safeReturnPath;
}

export function useAuth() {
  const token = getAuthToken();
  const user = getStoredUser();

  const saveSession = async (
    accessToken: string,
  ): Promise<{ user: StoredUser; profile: UserProfileResponse }> => {
    setAuthTokens(accessToken);
    const profile = await getProfile();
    const normalizedRoles = normalizeRoles(profile.roles);
    const storedUser: StoredUser = {
      id: profile.id,
      fullName: profile.fullName,
      email: profile.email,
      roles: normalizedRoles,
      managedLab: profile.managedLab?.id
        ? {
            id: profile.managedLab.id,
            name: profile.managedLab.name ?? profile.managedLab.labName ?? 'Lab',
          }
        : null,
      managedLabId: profile.managedLab?.id ?? profile.managedLabId ?? null,
      memberships: profile.memberships?.map((membership) => ({
        labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
        labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'Lab',
        role: membership.role,
        status: membership.status,
        joinedAt: membership.joinedAt ?? membership.createdAt,
      })),
      researchGroupMemberships: profile.researchGroupMemberships,
      groupMemberships: profile.groupMemberships,
      researchGroups: profile.researchGroups,
    };

    setStoredUser(storedUser);
    return { user: storedUser, profile };
  };

  return {
    isAuthenticated: Boolean(token),
    user,
    saveSession,
    logout: clearAuthTokens,
  };
}
