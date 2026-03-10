import { Navigate, useLocation } from "react-router-dom";
import { Box, CircularProgress } from "@mui/material";
import { useAuth } from "@/features/auth/context/AuthContext";

export function RoleRoute({ allow, children }) {
  const { isAuthenticated, user, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <Box sx={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!allow.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}
