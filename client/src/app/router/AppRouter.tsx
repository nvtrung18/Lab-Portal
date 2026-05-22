import { Navigate, Route, Routes } from 'react-router-dom';

import {
  AdminDashboardPage,
  AdminLabsPage,
  AdminSettingsPage,
  AdminUsersPage,
} from '../../modules/admin/pages';
import {
  ForgotPasswordPage,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyRegisterPage,
} from '../../modules/auth/pages';
import { BookingPage, CheckInPage, LabSlotsPage, MyBookingsPage, SlotDetailPage } from '../../modules/booking/pages';
import {
  ApplicationListPage,
  CleaningPage,
  LabMembersPage,
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

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/register/verify" element={<VerifyRegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
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
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={[STUDENT]} />}>
        <Route path="/app" element={<MainLayout />}>
          <Route path="labs" element={<LabListPage />} />
          <Route element={<ActiveMembershipRoute />}>
            <Route path="my-bookings" element={<MyBookingsPage />} />
            <Route path="research" element={<ResearchPage />} />
            <Route path="other" element={<OtherPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={[LAB_MANAGER]} />}>
        <Route path="/app" element={<MainLayout />}>
          <Route path="applications" element={<ApplicationListPage />} />
          <Route path="lab-overview" element={<LabOverviewPage />} />
          <Route path="lab-info" element={<Navigate to="/app/lab-overview" replace />} />
          <Route path="lab-slots" element={<LabSlotsPage />} />
          <Route path="lab-slots/:slotId" element={<SlotDetailPage />} />
          <Route path="lab-bookings" element={<BookingPage />} />
          <Route path="checkin-scan" element={<CheckInPage />} />
          <Route path="lab-members" element={<LabMembersPage />} />
          <Route path="cleaning" element={<CleaningPage />} />
          <Route path="research" element={<ResearchPage />} />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/app/profile" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
