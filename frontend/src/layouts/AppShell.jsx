import {
  AppBar,
  Box,
  Button,
  Chip,
  Container,
  Stack,
  Toolbar,
  Typography
} from "@mui/material";
import { Link as RouterLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function NavButton({ to, children }) {
  return (
    <Button color="inherit" component={RouterLink} to={to}>
      {children}
    </Button>
  );
}

export function AppShell() {
  const { user, logout } = useAuth();

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <AppBar position="sticky">
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Loan Approval DSS
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            {user?.role === "CUSTOMER" && (
              <>
                <NavButton to="/customer/profile">My Profile</NavButton>
                <NavButton to="/customer/loan/new">New Request</NavButton>
                <NavButton to="/customer/loans">My Requests</NavButton>
              </>
            )}
            {user?.role === "STAFF" && (
              <>
                <NavButton to="/staff/requests">Review Queue</NavButton>
                <NavButton to="/staff/dashboard">Dashboard</NavButton>
                <NavButton to="/staff/accounts/new">Create Account</NavButton>
              </>
            )}
            {user?.role === "ADMIN" && (
              <>
                <NavButton to="/admin/users">User Management</NavButton>
              </>
            )}
            <Chip
              label={user?.role ?? "GUEST"}
              size="small"
              color="secondary"
              sx={{ color: "#fff" }}
            />
            <Button color="inherit" onClick={logout}>
              Logout
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>
      <Container sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
