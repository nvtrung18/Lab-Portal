import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { AUTH_TOKEN_KEY } from '../api';

export function ProtectedRoute() {
  const location = useLocation();
  const token = localStorage.getItem(AUTH_TOKEN_KEY);

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
