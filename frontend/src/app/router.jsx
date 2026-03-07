import { createBrowserRouter } from "react-router-dom";
import { RoleRoute } from "@/shared/routing/RoleRoute";
import { AppShell } from "@/shared/layouts/AppShell";
import HomeRedirect from "@/shared/pages/HomeRedirect";
import LoginPage from "@/features/auth/pages/LoginPage";
import NotFoundPage from "@/shared/pages/NotFoundPage";
import AdminUsersPage from "@/features/admin/pages/AdminUsersPage";
import CustomerLoanDetailPage from "@/features/customer/pages/CustomerLoanDetailPage";
import CustomerLoanNewPage from "@/features/customer/pages/CustomerLoanNewPage";
import CustomerLoansPage from "@/features/customer/pages/CustomerLoansPage";
import CustomerPaymentsPage from "@/features/customer/pages/CustomerPaymentsPage";
import CustomerProfilePage from "@/features/customer/pages/CustomerProfilePage";
import StaffDashboardPage from "@/features/staff/pages/StaffDashboardPage";
import StaffRequestDetailPage from "@/features/staff/pages/StaffRequestDetailPage";
import StaffRequestsPage from "@/features/staff/pages/StaffRequestsPage";
import StaffUserCreatePage from "@/features/staff/pages/StaffUserCreatePage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />
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
        element: <HomeRedirect />
      },
      {
        path: "admin/users",
        element: (
          <RoleRoute allow={["ADMIN"]}>
            <AdminUsersPage />
          </RoleRoute>
        )
      },
      {
        path: "customer/loan/new",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <CustomerLoanNewPage />
          </RoleRoute>
        )
      },
      {
        path: "customer/loans",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <CustomerLoansPage />
          </RoleRoute>
        )
      },
      {
        path: "customer/loans/:id",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <CustomerLoanDetailPage />
          </RoleRoute>
        )
      },
      {
        path: "customer/profile",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <CustomerProfilePage />
          </RoleRoute>
        )
      },
      {
        path: "customer/payments",
        element: (
          <RoleRoute allow={["CUSTOMER"]}>
            <CustomerPaymentsPage />
          </RoleRoute>
        )
      },
      {
        path: "staff/requests",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <StaffRequestsPage />
          </RoleRoute>
        )
      },
      {
        path: "staff/requests/:id",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <StaffRequestDetailPage />
          </RoleRoute>
        )
      },
      {
        path: "staff/dashboard",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <StaffDashboardPage />
          </RoleRoute>
        )
      },
      {
        path: "staff/accounts/new",
        element: (
          <RoleRoute allow={["STAFF"]}>
            <StaffUserCreatePage />
          </RoleRoute>
        )
      },
      {
        path: "*",
        element: <NotFoundPage />
      }
    ]
  }
]);

