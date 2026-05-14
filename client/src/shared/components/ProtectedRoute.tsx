import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { getAuthToken } from '../api';

export function ProtectedRoute() {
  const location = useLocation();
  const token = getAuthToken();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
