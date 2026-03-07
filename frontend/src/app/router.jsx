import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";
import { CircularProgress, Box } from "@mui/material";
import { RoleRoute } from "@/shared/routing/RoleRoute";
import { AppShell } from "@/shared/layouts/AppShell";

const HomeRedirect = lazy(() => import("@/shared/pages/HomeRedirect"));
const LoginPage = lazy(() => import("@/features/auth/pages/LoginPage"));
const NotFoundPage = lazy(() => import("@/shared/pages/NotFoundPage"));
const AdminUsersPage = lazy(() => import("@/features/admin/pages/AdminUsersPage"));
const CustomerLoanDetailPage = lazy(() => import("@/features/customer/pages/CustomerLoanDetailPage"));
const CustomerLoanNewPage = lazy(() => import("@/features/customer/pages/CustomerLoanNewPage"));
const CustomerLoansPage = lazy(() => import("@/features/customer/pages/CustomerLoansPage"));
const CustomerPaymentsPage = lazy(() => import("@/features/customer/pages/CustomerPaymentsPage"));
const CustomerProfilePage = lazy(() => import("@/features/customer/pages/CustomerProfilePage"));
const StaffDashboardPage = lazy(() => import("@/features/staff/pages/StaffDashboardPage"));
const StaffRequestDetailPage = lazy(() => import("@/features/staff/pages/StaffRequestDetailPage"));
const StaffRequestsPage = lazy(() => import("@/features/staff/pages/StaffRequestsPage"));
const StaffUserCreatePage = lazy(() => import("@/features/staff/pages/StaffUserCreatePage"));

function SuspenseWrapper({ children }) {
  return (
    <Suspense
      fallback={
        <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
          <CircularProgress />
        </Box>
      }
    >
      {children}
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <SuspenseWrapper><LoginPage /></SuspenseWrapper>
  },
  {
    path: "/",
    element: (
      <RoleRoute allow={["CUSTOMER", "STAFF", "ADMIN"]}>
        <AppShell />
      </RoleRoute>
    ),
    children: [
      {
        index: true,
        element: <SuspenseWrapper><HomeRedirect /></SuspenseWrapper>
      },
      {
        path: "admin/users",
        element: (
          <RoleRoute allow={["ADMIN"]}>
            <SuspenseWrapper><AdminUsersPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "customer/loan/new",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerLoanNewPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "customer/loans",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerLoansPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "customer/loans/:id",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerLoanDetailPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "customer/profile",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerProfilePage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "customer/payments",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerPaymentsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/requests",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffRequestsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/requests/:id",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffRequestDetailPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/dashboard",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffDashboardPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/accounts/new",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffUserCreatePage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "*",
        element: <SuspenseWrapper><NotFoundPage /></SuspenseWrapper>
      }
    ]
  }
]);

