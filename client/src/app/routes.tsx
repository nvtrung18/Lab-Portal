import { Navigate, type RouteObject } from 'react-router-dom';

import { LoginPage, RegisterPage } from '../modules/auth/pages';
import { BookingPage } from '../modules/booking/pages';
import { LabPage } from '../modules/lab/pages';
import { ResearchPage } from '../modules/research/pages';
import { ProtectedRoute } from '../shared/components';
import { AuthLayout, MainLayout } from '../shared/layout';

export const appRoutes: RouteObject[] = [
  {
    element: <AuthLayout />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/register',
        element: <RegisterPage />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <MainLayout />,
        children: [
          {
            index: true,
            element: <Navigate to="/labs" replace />,
          },
          {
            path: '/labs',
            element: <LabPage />,
          },
          {
            path: '/booking',
            element: <BookingPage />,
          },
          {
            path: '/research',
            element: <ResearchPage />,
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
