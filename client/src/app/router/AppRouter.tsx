import { Navigate, Route, Routes } from 'react-router-dom';

import {
  AdminDashboardPage,
  AdminLabsPage,
  AdminSettingsPage,
  AdminUsersPage,
} from '../../modules/admin/pages';
import { LoginPage } from '../../modules/auth/pages';
import { BookingPage, MyBookingsPage } from '../../modules/booking/pages';
import {
  ApplicationListPage,
  CleaningPage,
  LabListPage,
  LabOverviewPage,
} from '../../modules/lab/pages';
import { ResearchPage } from '../../modules/research/pages';
import { OtherPage, ProfilePage } from '../../modules/user/pages';
import { AdminLayout, AuthLayout, MainLayout } from '../../layouts';
import { ForbiddenPage, NotFoundPage } from '../../shared/components';
import { ADMIN, LAB_MANAGER, STUDENT } from '../../shared/constants/roles';
import { DashboardPlaceholder } from '../DashboardPlaceholder';
import { ActiveMembershipRoute } from './ActiveMembershipRoute';
import { ProtectedRoute } from './ProtectedRoute';

function PlaceholderPage({ title }: { title: string }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-xl font-semibold text-slate-950">{title}</h2>
      <p className="mt-2 text-sm text-slate-600">Placeholder.</p>
    </section>
  );
}

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>

      <Route path="/403" element={<ForbiddenPage />} />

      <Route element={<ProtectedRoute allowedRoles={[ADMIN]} />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboardPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="labs" element={<AdminLabsPage />} />
          <Route path="system-config" element={<AdminSettingsPage />} />
          <Route path="settings" element={<Navigate to="/admin/system-config" replace />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={[STUDENT, LAB_MANAGER]} />}>
        <Route path="/app" element={<MainLayout />}>
          <Route index element={<Navigate to="/app/profile" replace />} />
          <Route path="dashboard" element={<DashboardPlaceholder />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="research" element={<ResearchPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={[STUDENT]} />}>
        <Route path="/app" element={<MainLayout />}>
          <Route path="labs" element={<LabListPage />} />
          <Route path="my-bookings" element={<MyBookingsPage />} />
          <Route element={<ActiveMembershipRoute />}>
            <Route path="other" element={<OtherPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={[LAB_MANAGER]} />}>
        <Route path="/app" element={<MainLayout />}>
          <Route path="applications" element={<ApplicationListPage />} />
          <Route path="lab-overview" element={<LabOverviewPage />} />
          <Route path="lab-info" element={<Navigate to="/app/lab-overview" replace />} />
          <Route path="lab-slots" element={<PlaceholderPage title="Lab Slots" />} />
          <Route path="lab-bookings" element={<BookingPage />} />
          <Route path="lab-members" element={<PlaceholderPage title="Lab Members" />} />
          <Route path="cleaning" element={<CleaningPage />} />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/app/profile" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
