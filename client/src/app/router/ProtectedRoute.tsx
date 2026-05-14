import { Navigate, Outlet } from 'react-router-dom';

import { getAuthToken, getStoredUser } from '../../shared/api';
import type { Role } from '../../shared/constants/roles';

interface ProtectedRouteProps {
  allowedRoles?: Role[];
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
    const hasRole = user.roles.some((role) => allowedRoles.includes(role as Role));

    if (!hasRole) {
      return <Navigate to="/403" replace />;
    }
  }

  return <Outlet />;
}
