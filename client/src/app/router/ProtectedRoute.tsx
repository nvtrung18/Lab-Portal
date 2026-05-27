import { Navigate, Outlet } from 'react-router-dom';

import { getAuthToken, getStoredUser } from '../../shared/api';
import type { Role } from '../../shared/constants/roles';

interface ProtectedRouteProps {
  allowedRoles?: Role[];
}

function normalizeRole(role: string): string {
  return role.replace(/^ROLE_/, '');
}

export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const token = getAuthToken();
  const user = getStoredUser();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles?.length) {
    const normalizedAllowedRoles = allowedRoles.map(normalizeRole);
    const hasRole = user.roles
      .map(normalizeRole)
      .some((role) => normalizedAllowedRoles.includes(role));

    if (!hasRole) {
      return <Navigate to="/403" replace />;
    }
  }

  return <Outlet />;
}
