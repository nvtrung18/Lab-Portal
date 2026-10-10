import { Navigate, Outlet } from 'react-router-dom';

import { hasActiveMembership } from '../../shared/utils/membership';
import { useCurrentUser } from '../../modules/user/hooks';
import { LAB_MANAGER } from '../../shared/constants/roles';
import { useI18n } from '../../shared/i18n';
import { LoadingState } from '../../shared/components';

interface ActiveMembershipRouteProps {
  allowLabManager?: boolean;
}

export function ActiveMembershipRoute({ allowLabManager = false }: ActiveMembershipRouteProps) {
  const { t } = useI18n();
  const { data: user, isLoading, isFetching, isError, isFetchedAfterMount } = useCurrentUser({ staleTime: 0 });

  if (isLoading || (!isFetchedAfterMount && isFetching)) {
    return <LoadingState className="m-4">{t('membership.checking')}</LoadingState>;
  }

  if (isError || !user) return <Navigate to="/vi" replace />;

  if (allowLabManager && user?.roles.map((role) => role.replace(/^ROLE_/, '')).includes(LAB_MANAGER)) {
    return <Outlet />;
  }

  if (!hasActiveMembership(user)) {
    return <Navigate to="/vi" replace />;
  }

  return <Outlet />;
}
