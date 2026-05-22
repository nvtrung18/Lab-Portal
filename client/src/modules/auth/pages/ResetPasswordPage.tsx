import { Navigate } from 'react-router-dom';

export function ResetPasswordPage() {
  return <Navigate to="/forgot-password" replace />;
}
