import { Navigate, Outlet } from 'react-router-dom';

import { getAuthToken, getStoredRole } from '../api';
import { Forbidden } from './Forbidden';

interface RoleBasedRouteProps {
  allowedRoles: string[];
}

export function RoleBasedRoute({ allowedRoles }: RoleBasedRouteProps) {
  const token = getAuthToken();
  const role = getStoredRole();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (!role || !allowedRoles.includes(role)) {
    return <Forbidden />;
  }

  return <Outlet />;
}
