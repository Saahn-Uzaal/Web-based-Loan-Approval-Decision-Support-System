import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography
} from "@mui/material";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";

function roleHome(role) {
  if (role === "STAFF") {
    return "/staff/requests";
  }
  if (role === "ADMIN") {
    return "/admin/users";
  }
  return "/customer/loans";
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register, isAuthenticated, user, isInitializing } = useAuth();

  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("CUSTOMER");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!isInitializing && isAuthenticated) {
    const next = roleHome(user.role);
    return <Navigate to={next} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const authUser = isRegisterMode
        ? await register({ email, password, role })
        : await login({ email, password });
      const fallback = roleHome(authUser.role);
      const from = location.state?.from?.pathname || fallback;
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || "Xác thực thất bại");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        px: 2,
        background: "linear-gradient(135deg, #f0f4fb 0%, #dce7fb 100%)"
      }}
    >
      <Paper sx={{ width: "100%", maxWidth: 420, p: 4 }}>
        {isInitializing && <LinearProgress sx={{ mb: 2 }} />}
        <Stack spacing={2}>
          <Typography variant="h4">{isRegisterMode ? "Đăng ký" : "Đăng nhập"}</Typography>
          <Typography color="text.secondary">
            Xác thực hiện đang sử dụng API backend với JWT.
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={isRegisterMode}
                onChange={(event) => setIsRegisterMode(event.target.checked)}
              />
            }
            label={isRegisterMode ? "Chế độ đăng ký" : "Chế độ đăng nhập"}
          />
          {isRegisterMode && (
            <Alert severity="info">
              Bạn có thể đăng ký tài khoản khách hàng hoặc nhân viên để kiểm thử phân quyền RBAC.
            </Alert>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          <Box component="form" onSubmit={handleSubmit}>
            <Stack spacing={2}>
              <TextField
                label="Email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                placeholder="user@example.com"
                required
                fullWidth
              />
              <TextField
                label="Mật khẩu"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                placeholder="Tối thiểu 6 ký tự"
                required
                fullWidth
              />
              {isRegisterMode && (
                <TextField
                  select
                  label="Vai trò"
                  value={role}
                  onChange={(event) => setRole(event.target.value)}
                  fullWidth
                >
                  <MenuItem value="CUSTOMER">Khách hàng</MenuItem>
                  <MenuItem value="STAFF">Nhân viên</MenuItem>
                </TextField>
              )}
              <Button type="submit" variant="contained" size="large" disabled={submitting || isInitializing}>
                {submitting ? "Vui lòng chờ..." : isRegisterMode ? "Đăng ký" : "Đăng nhập"}
              </Button>
            </Stack>
          </Box>
        </Stack>
      </Paper>
    </Box>
  );
}
