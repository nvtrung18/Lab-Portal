import { ADMIN, LAB_MANAGER, STUDENT } from '../constants/roles';
import { getStoredUser } from '../api';

function normalizeRole(role: string) {
  return role.replace(/^ROLE_/, '');
}

export function getHomePath() {
  const roles = getStoredUser()?.roles.map(normalizeRole) ?? [];

  if (roles.includes(ADMIN)) {
    return '/admin/dashboard';
  }

  if (roles.includes(LAB_MANAGER) || roles.includes(STUDENT)) {
    return '/app';
  }

  return '/login';
}
