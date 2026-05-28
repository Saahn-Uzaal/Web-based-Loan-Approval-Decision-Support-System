import {
  AppBar,
  Box,
  Button,
  Chip,
  Container,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Typography
} from "@mui/material";
import KeyboardArrowDownRoundedIcon from "@mui/icons-material/KeyboardArrowDownRounded";
import { useState } from "react";
import { Link as RouterLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import NotificationMenu from "@/shared/components/NotificationMenu";
import { labelRole } from "@/shared/utils/labels";

const ROLE_NAVIGATION = {
  CUSTOMER: [
    { label: "Hồ sơ", to: "/customer/profile" },
    { label: "Tạo hồ sơ vay", to: "/customer/loan/new" },
    { label: "Hồ sơ vay", to: "/customer/loans" },
    { label: "Thanh toán", to: "/customer/payments" }
  ],
  STAFF: [
    { label: "Xác minh thông tin", to: "/staff/information-verifications" },
    { label: "Thẩm định", to: "/staff/requests" },
    { label: "Thủ tục thế chấp", to: "/staff/secured-procedures" },
    { label: "Vận hành khoản vay", to: "/staff/loan-operations" },
    { label: "Xác nhận thanh toán", to: "/staff/payment-confirmations" },
    { label: "Bảng điều khiển", to: "/staff/dashboard" },
    { label: "Danh sách nợ xấu", to: "/credit-bureau" }
  ],
  ADMIN: [
    { label: "Quản lý người dùng", to: "/admin/users" },
    { label: "Tạo tài khoản", to: "/admin/accounts/new" },
    { label: "Danh sách nợ xấu", to: "/credit-bureau" }
  ]
};

function NavButton({ to, children }) {
  return (
    <Button color="inherit" component={RouterLink} to={to}>
      {children}
    </Button>
  );
}

function RoleNavigationMenu({ role }) {
  const location = useLocation();
  const [anchorEl, setAnchorEl] = useState(null);

  const items = ROLE_NAVIGATION[role] || [];
  const open = Boolean(anchorEl);

  if (items.length === 0) {
    return null;
  }

  return (
    <>
      <Button
        color="inherit"
        endIcon={<KeyboardArrowDownRoundedIcon />}
        onClick={(event) => setAnchorEl(event.currentTarget)}
      >
        {labelRole(role)}
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={() => setAnchorEl(null)}
        keepMounted
      >
        {items.map((item) => (
          <MenuItem
            key={item.to}
            component={RouterLink}
            to={item.to}
            selected={location.pathname.startsWith(item.to)}
            onClick={() => setAnchorEl(null)}
          >
            {item.label}
          </MenuItem>
        ))}
      </Menu>
    </>
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
            <RoleNavigationMenu role={user?.role} />
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
