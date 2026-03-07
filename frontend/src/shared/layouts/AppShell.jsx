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
import { useAuth } from "@/features/auth/context/AuthContext";
import { labelRole } from "@/shared/utils/labels";

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
            Hệ thống hỗ trợ quyết định cho vay
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            {user?.role === "CUSTOMER" && (
              <>
                <NavButton to="/customer/profile">Hồ sơ của tôi</NavButton>
                <NavButton to="/customer/loan/new">Tạo hồ sơ vay</NavButton>
                <NavButton to="/customer/loans">Hồ sơ vay của tôi</NavButton>
                <NavButton to="/customer/payments">Thanh toán</NavButton>
              </>
            )}
            {user?.role === "STAFF" && (
              <>
                <NavButton to="/staff/requests">Hàng đợi thẩm định</NavButton>
                <NavButton to="/staff/dashboard">Bảng điều khiển</NavButton>
                <NavButton to="/staff/accounts/new">Tạo tài khoản</NavButton>
              </>
            )}
            {user?.role === "ADMIN" && (
              <>
                <NavButton to="/admin/users">Quản lý người dùng</NavButton>
              </>
            )}
            <Chip
              label={labelRole(user?.role ?? "GUEST")}
              size="small"
              color="secondary"
              sx={{ color: "#fff" }}
            />
            <Button color="inherit" onClick={logout}>
              Đăng xuất
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
