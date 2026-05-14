import { Navigate, type RouteObject } from 'react-router-dom';

import { LoginPage } from '../modules/auth/pages';
import { RoleBasedRoute } from '../shared/components';
import { AuthLayout, MainLayout } from '../shared/layout';
import { ApplicationsPlaceholder } from './ApplicationsPlaceholder';
import { DashboardPlaceholder } from './DashboardPlaceholder';
import { LabsPlaceholder } from './LabsPlaceholder';

export const appRoutes: RouteObject[] = [
  {
    element: <AuthLayout />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
    ],
  },
  {
    element: <RoleBasedRoute allowedRoles={['USER', 'MANAGER']} />,
    children: [
      {
        element: <MainLayout />,
        children: [
          {
            index: true,
            element: <DashboardPlaceholder />,
          },
        ],
      },
    ],
  },
  {
    element: <RoleBasedRoute allowedRoles={['USER']} />,
    children: [
      {
        element: <MainLayout />,
        children: [
          {
            path: '/labs',
            element: <LabsPlaceholder />,
          },
        ],
      },
    ],
  },
  {
    element: <RoleBasedRoute allowedRoles={['MANAGER']} />,
    children: [
      {
        element: <MainLayout />,
        children: [
          {
            path: '/applications',
            element: <ApplicationsPlaceholder />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
];
