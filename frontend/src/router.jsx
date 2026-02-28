import { createBrowserRouter } from "react-router-dom";
import { RoleRoute } from "./components/RoleRoute";
import { AppShell } from "./layouts/AppShell";
import HomeRedirect from "./pages/HomeRedirect";
import LoginPage from "./pages/LoginPage";
import NotFoundPage from "./pages/NotFoundPage";
import CustomerLoanDetailPage from "./pages/customer/CustomerLoanDetailPage";
import CustomerLoanNewPage from "./pages/customer/CustomerLoanNewPage";
import CustomerLoansPage from "./pages/customer/CustomerLoansPage";
import StaffDashboardPage from "./pages/staff/StaffDashboardPage";
import StaffRequestDetailPage from "./pages/staff/StaffRequestDetailPage";
import StaffRequestsPage from "./pages/staff/StaffRequestsPage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />
  },
  {
    path: "/",
    element: (
      <RoleRoute allow={["CUSTOMER", "STAFF"]}>
        <AppShell />
      </RoleRoute>
    ),
    children: [
      {
        index: true,
        element: <HomeRedirect />
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
        path: "*",
        element: <NotFoundPage />
      }
    ]
  }
]);
