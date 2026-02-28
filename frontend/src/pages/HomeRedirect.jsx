import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function HomeRedirect() {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user.role === "CUSTOMER") {
    return <Navigate to="/customer/loans" replace />;
  }

  if (user.role === "STAFF") {
    return <Navigate to="/staff/requests" replace />;
  }

  return <Navigate to="/login" replace />;
}
