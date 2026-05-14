import { Navigate, Outlet } from 'react-router-dom';

import { getStoredUser } from '../../shared/api';
import { hasActiveMembership } from '../../shared/utils/membership';

export function ActiveMembershipRoute() {
  const user = getStoredUser();

  if (!hasActiveMembership(user)) {
    return <Navigate to="/app/labs" replace />;
  }

  return <Outlet />;
}
