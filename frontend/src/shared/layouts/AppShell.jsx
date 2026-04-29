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
import NotificationMenu from "@/shared/components/NotificationMenu";
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
        <Toolbar
          sx={{
            gap: 2,
            py: 1.25,
            alignItems: { xs: "flex-start", lg: "center" },
            flexWrap: "wrap"
          }}
        >
          <Typography
            variant="h6"
            component={RouterLink}
            to="/dashboard"
            sx={{
              flexGrow: 1,
              minWidth: 260,
              textDecoration: "none",
              color: "inherit",
              "&:hover": { opacity: 0.85 }
            }}
          >
            Hệ thống hỗ trợ quyết định cho vay
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <NavButton to="/dashboard">Trang chủ</NavButton>
            {user?.role === "CUSTOMER" && (
              <>
                <NavButton to="/customer/profile">Hồ sơ</NavButton>
                <NavButton to="/customer/loan/new">Tạo hồ sơ vay</NavButton>
                <NavButton to="/customer/loans">Hồ sơ vay</NavButton>
                <NavButton to="/customer/payments">Thanh toán</NavButton>
              </>
            )}
            {user?.role === "STAFF" && (
              <>
                <NavButton to="/staff/requests">Thẩm định</NavButton>
                <NavButton to="/staff/loan-operations">Vận hành khoản vay</NavButton>
                <NavButton to="/staff/information-verifications">Xác minh thông tin</NavButton>
                <NavButton to="/staff/payment-confirmations">Xác nhận thanh toán</NavButton>
                <NavButton to="/staff/secured-procedures">Thủ tục thế chấp</NavButton>
                <NavButton to="/staff/dashboard">Bảng điều khiển</NavButton>
              </>
            )}
            {user?.role === "ADMIN" && (
              <>
                <NavButton to="/admin/users">Quản lý người dùng</NavButton>
                <NavButton to="/admin/accounts/new">Tạo tài khoản</NavButton>
              </>
            )}
            <NotificationMenu />
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
