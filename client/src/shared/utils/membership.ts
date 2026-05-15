interface ManagedLabLike {
  id?: number;
  name?: string;
  labName?: string;
}

export interface MembershipLike {
  id?: number;
  labId?: number;
  labName?: string;
  role?: string;
  status?: string;
  joinedAt?: string;
  createdAt?: string;
  lab?: {
    id?: number;
    name?: string;
    labName?: string;
  };
}

interface UserWithLabScope {
  id?: number;
  memberships?: MembershipLike[];
  managedLab?: ManagedLabLike | null;
  managedLabId?: number | null;
}

interface LabWithManager {
  id: number;
  labName?: string;
  manager?: {
    id?: number;
  } | null;
}

export function getMembershipLabId(membership: MembershipLike): number | null {
  return membership.labId ?? membership.lab?.id ?? null;
}

export function getMembershipLabName(membership: MembershipLike): string {
  return membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'Lab';
}

export function getActiveMemberships(
  user: UserWithLabScope | null | undefined,
): MembershipLike[] {
  return (
    user?.memberships?.filter(
      (membership) => membership.status?.toUpperCase() === 'ACTIVE',
    ) ?? []
  );
}

export function hasActiveMembership(user: UserWithLabScope | null | undefined): boolean {
  return Boolean(
    getActiveMemberships(user).length,
  );
}

export function getManagedLabId(
  user: UserWithLabScope | null | undefined,
  labs: LabWithManager[] = [],
): number | null {
  const directLabId = user?.managedLab?.id ?? user?.managedLabId;

  if (directLabId) {
    return directLabId;
  }

  const matchedLab = labs.find((lab) => lab.manager?.id === user?.id);
  return matchedLab?.id ?? null;
}

export function getManagedLabName(
  user: UserWithLabScope | null | undefined,
  labs: LabWithManager[] = [],
): string | null {
  const directName = user?.managedLab?.name ?? user?.managedLab?.labName;

  if (directName) {
    return directName;
  }

  const managedLabId = getManagedLabId(user, labs);
  return labs.find((lab) => lab.id === managedLabId)?.labName ?? null;
}
