import type { StoredUser } from '../api';

export function hasActiveMembership(user: StoredUser | null): boolean {
  return Boolean(
    user?.memberships?.some(
      (membership) => membership.status.toUpperCase() === 'ACTIVE',
    ),
  );
}
