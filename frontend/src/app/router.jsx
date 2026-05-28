import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";
import { CircularProgress, Box } from "@mui/material";
import { RoleRoute } from "@/shared/routing/RoleRoute";
import { AppShell } from "@/shared/layouts/AppShell";

const LandingPage = lazy(() => import("@/shared/pages/LandingPage"));
const HomePage = lazy(() => import("@/shared/pages/HomePage"));
const LoginPage = lazy(() => import("@/features/auth/pages/LoginPage"));
const NotFoundPage = lazy(() => import("@/shared/pages/NotFoundPage"));
const AdminUsersPage = lazy(() => import("@/features/admin/pages/AdminUsersPage"));
const CreditBureauRecordsPage = lazy(() => import("@/features/creditcheck/pages/CreditBureauRecordsPage"));
const CustomerLoanDetailPage = lazy(() => import("@/features/customer/pages/CustomerLoanDetailPage"));
const CustomerLoanNewPage = lazy(() => import("@/features/customer/pages/CustomerLoanNewPage"));
const CustomerLoansPage = lazy(() => import("@/features/customer/pages/CustomerLoansPage"));
const CustomerPaymentsPage = lazy(() => import("@/features/customer/pages/CustomerPaymentsPage"));
const CustomerProfilePage = lazy(() => import("@/features/customer/pages/CustomerProfilePage"));
const StaffDashboardPage = lazy(() => import("@/features/staff/pages/StaffDashboardPage"));
const StaffInformationVerificationDetailPage = lazy(() => import("@/features/staff/pages/StaffInformationVerificationDetailPage"));
const StaffInformationVerificationsPage = lazy(() => import("@/features/staff/pages/StaffInformationVerificationsPage"));
const StaffLoanOperationsPage = lazy(() => import("@/features/staff/pages/StaffLoanOperationsPage"));
const StaffPaymentConfirmationsPage = lazy(() => import("@/features/staff/pages/StaffPaymentConfirmationsPage"));
const StaffRequestDetailPage = lazy(() => import("@/features/staff/pages/StaffRequestDetailPage"));
const StaffRequestsPage = lazy(() => import("@/features/staff/pages/StaffRequestsPage"));
const StaffSecuredProceduresPage = lazy(() => import("@/features/staff/pages/StaffSecuredProceduresPage"));
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
    path: "/",
    element: <SuspenseWrapper><LandingPage /></SuspenseWrapper>
  },
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
        path: "dashboard",
        element: <SuspenseWrapper><HomePage /></SuspenseWrapper>
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
        path: "credit-bureau",
        element: (
          <RoleRoute allow={["STAFF", "ADMIN"]}>
            <SuspenseWrapper><CreditBureauRecordsPage /></SuspenseWrapper>
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
        path: "customer/loans/:id/edit",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <SuspenseWrapper><CustomerLoanNewPage /></SuspenseWrapper>
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
        path: "staff/information-verifications",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffInformationVerificationsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/loan-operations",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffLoanOperationsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/information-verifications/:customerId",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffInformationVerificationDetailPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/payment-confirmations",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffPaymentConfirmationsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/payment-confirmations/:confirmationId",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffPaymentConfirmationsPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/secured-procedures",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffSecuredProceduresPage /></SuspenseWrapper>
          </RoleRoute>
        )
      },
      {
        path: "staff/secured-procedures/:loanRequestId",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <SuspenseWrapper><StaffSecuredProceduresPage /></SuspenseWrapper>
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
        path: "admin/accounts/new",
        element: (
          <RoleRoute allow={["ADMIN"]}>
            <SuspenseWrapper><StaffUserCreatePage /></SuspenseWrapper>
          </RoleRoute>
        )
      }
    ]
  },
  {
    path: "*",
    element: <SuspenseWrapper><NotFoundPage /></SuspenseWrapper>
  }
]);
