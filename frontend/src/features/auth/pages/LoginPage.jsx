import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import {
  ArrowBackRounded as BackIcon,
  AutoGraphRounded as ScoreIcon,
  LockRounded as LockIcon,
  PaymentsRounded as PaymentIcon,
  ShieldRounded as ShieldIcon
} from "@mui/icons-material";
import { Link as RouterLink, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";

const capabilityCards = [
  {
    title: "DSS scoring",
    description: "Đánh giá credit score, DTI và khuyến nghị phê duyệt trước vòng staff review.",
    icon: <ScoreIcon fontSize="small" />,
    color: "#c76b3e"
  },
  {
    title: "KYC / AML / Fraud",
    description: "Xác minh nhiều lớp trong cùng workflow để giảm approve sai hồ sơ.",
    icon: <ShieldIcon fontSize="small" />,
    color: "#13766c"
  },
  {
    title: "Contract & repayment",
    description: "Sau phê duyệt, hợp đồng và thanh toán tiếp tục được theo dõi trong cùng hệ thống.",
    icon: <PaymentIcon fontSize="small" />,
    color: "#154c79"
  }
];

function roleHome() {
  return "/dashboard";
}

function ModeButton({ active, onClick, children }) {
  return (
    <Button
      onClick={onClick}
      fullWidth
      variant={active ? "contained" : "text"}
      sx={{
        py: 1.15,
        borderRadius: 999,
        fontWeight: 700,
        color: active ? "#fff" : "text.primary",
        bgcolor: active ? "#09213a" : "transparent",
        boxShadow: active ? "0 10px 24px rgba(9,33,58,0.18)" : "none",
        "&:hover": {
          bgcolor: active ? "#103153" : "rgba(9,33,58,0.04)"
        }
      }}
    >
      {children}
    </Button>
  );
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
    return <Navigate to={roleHome(user.role)} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      if (isRegisterMode) {
        await register({ email, password, role });
      } else {
        await login({ email, password });
      }
      const fallback = roleHome();
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
        px: { xs: 2, md: 3 },
        py: { xs: 3, md: 4 },
        background: `
          radial-gradient(circle at top left, rgba(199,107,62,0.18), transparent 26%),
          radial-gradient(circle at top right, rgba(19,118,108,0.16), transparent 24%),
          linear-gradient(180deg, #fcf6eb 0%, #efe7d7 100%)
        `
      }}
    >
      <Box
        sx={{
          maxWidth: 1180,
          mx: "auto",
          display: "grid",
          gridTemplateColumns: { xs: "1fr", lg: "1.08fr 0.92fr" },
          gap: 3,
          alignItems: "stretch"
        }}
      >
        <Paper
          sx={{
            p: { xs: 3, md: 4.5 },
            borderRadius: 7,
            color: "#fff",
            position: "relative",
            overflow: "hidden",
            background: `
              radial-gradient(circle at top right, rgba(255,255,255,0.08), transparent 24%),
              linear-gradient(145deg, #0b2137 0%, #13395a 55%, #0f766e 100%)
            `,
            boxShadow: "0 28px 80px rgba(9,33,58,0.18)"
          }}
        >
          <Box
            sx={{
              position: "absolute",
              top: -80,
              right: -60,
              width: 260,
              height: 260,
              borderRadius: "50%",
              bgcolor: "rgba(255,255,255,0.06)"
            }}
          />
          <Stack spacing={3} sx={{ position: "relative", height: "100%" }}>
            <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={2}>
              <Stack spacing={1}>
                <Chip
                  icon={<LockIcon sx={{ color: "inherit !important" }} />}
                  label="Secure entry"
                  sx={{
                    alignSelf: "flex-start",
                    bgcolor: "rgba(255,255,255,0.12)",
                    color: "#fff",
                    fontWeight: 700
                  }}
                />
                <Typography
                  sx={{
                    fontSize: { xs: "2.2rem", md: "3.6rem" },
                    lineHeight: 0.95,
                    fontWeight: 800,
                    letterSpacing: "-0.04em",
                    maxWidth: 560
                  }}
                >
                  Đăng nhập để vào đúng không gian nghiệp vụ.
                </Typography>
              </Stack>
              <Button
                component={RouterLink}
                to="/"
                startIcon={<BackIcon />}
                sx={{
                  alignSelf: "flex-start",
                  color: "#fff",
                  borderColor: "rgba(255,255,255,0.18)",
                  bgcolor: "rgba(255,255,255,0.06)",
                  px: 2,
                  borderRadius: 999,
                  "&:hover": {
                    bgcolor: "rgba(255,255,255,0.12)"
                  }
                }}
              >
                Về trang chủ
              </Button>
            </Stack>

            <Typography sx={{ maxWidth: 620, color: alpha("#ffffff", 0.76), fontSize: { xs: "1rem", md: "1.06rem" } }}>
              Màn hình này là lớp xác thực sau landing page. Người dùng vào đây khi đã hiểu quy trình cho vay,
              vai trò của họ và sẵn sàng truy cập đúng dashboard thao tác.
            </Typography>

            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
                gap: 2
              }}
            >
              {capabilityCards.map((item) => (
                <Paper
                  key={item.title}
                  sx={{
                    p: 2.2,
                    minHeight: 170,
                    borderRadius: 4,
                    color: "inherit",
                    bgcolor: "rgba(255,255,255,0.08)",
                    border: "1px solid rgba(255,255,255,0.10)",
                    backdropFilter: "blur(8px)"
                  }}
                >
                  <Stack spacing={1.6}>
                    <Box
                      sx={{
                        width: 42,
                        height: 42,
                        borderRadius: 3,
                        bgcolor: alpha(item.color, 0.22),
                        display: "grid",
                        placeItems: "center"
                      }}
                    >
                      {item.icon}
                    </Box>
                    <Typography fontWeight={700}>{item.title}</Typography>
                    <Typography variant="body2" sx={{ color: alpha("#ffffff", 0.7) }}>
                      {item.description}
                    </Typography>
                  </Stack>
                </Paper>
              ))}
            </Box>

            <Paper
              sx={{
                mt: "auto",
                p: 2.5,
                borderRadius: 4,
                bgcolor: "rgba(255,255,255,0.08)",
                border: "1px solid rgba(255,255,255,0.10)"
              }}
            >
              <Stack spacing={1.2}>
                <Typography variant="overline" sx={{ letterSpacing: "0.16em", color: alpha("#fff", 0.64) }}>
                  Access flow
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Public landing page, protected dashboard, role-based routes.
                </Typography>
                <Typography variant="body2" sx={{ color: alpha("#ffffff", 0.72), maxWidth: 560 }}>
                  Sau khi xác thực thành công, người dùng được đưa thẳng vào luồng thao tác theo vai trò thay vì quay lại trang public.
                </Typography>
              </Stack>
            </Paper>
          </Stack>
        </Paper>

        <Paper
          sx={{
            p: { xs: 3, md: 4 },
            borderRadius: 7,
            border: "1px solid rgba(9,33,58,0.08)",
            background: "linear-gradient(180deg, rgba(255,250,241,0.95) 0%, rgba(255,255,255,0.98) 100%)",
            boxShadow: "0 28px 70px rgba(9,33,58,0.10)"
          }}
        >
          {isInitializing && <LinearProgress sx={{ mb: 3 }} />}
          <Stack spacing={3}>
            <Stack spacing={1.25}>
              <Chip
                label={isRegisterMode ? "Create account" : "Sign in"}
                sx={{
                  alignSelf: "flex-start",
                  bgcolor: "rgba(9,33,58,0.06)",
                  color: "#09213a",
                  fontWeight: 700
                }}
              />
              <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1 }}>
                {isRegisterMode ? "Tạo tài khoản để kiểm thử luồng phân quyền" : "Đăng nhập vào hệ thống xét duyệt khoản vay"}
              </Typography>
              <Typography color="text.secondary">
                {isRegisterMode
                  ? "Chỉ hỗ trợ đăng ký CUSTOMER và STAFF. Vai trò ADMIN được bootstrap từ backend."
                  : "Sử dụng email và mật khẩu của tài khoản đã có để truy cập dashboard tương ứng."}
              </Typography>
            </Stack>

            <Box
              sx={{
                p: 0.75,
                borderRadius: 999,
                bgcolor: "rgba(9,33,58,0.05)",
                display: "grid",
                gridTemplateColumns: "1fr 1fr",
                gap: 0.75
              }}
            >
              <ModeButton active={!isRegisterMode} onClick={() => setIsRegisterMode(false)}>
                Đăng nhập
              </ModeButton>
              <ModeButton active={isRegisterMode} onClick={() => setIsRegisterMode(true)}>
                Đăng ký
              </ModeButton>
            </Box>

            {isRegisterMode && (
              <Alert severity="info" sx={{ borderRadius: 3 }}>
                Bạn có thể đăng ký tài khoản khách hàng hoặc nhân viên để kiểm thử RBAC và các route protected.
              </Alert>
            )}

            {error && <Alert severity="error" sx={{ borderRadius: 3 }}>{error}</Alert>}

            <Box component="form" onSubmit={handleSubmit}>
              <Stack spacing={2.25}>
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

                <FormControlLabel
                  control={
                    <Switch
                      checked={isRegisterMode}
                      onChange={(event) => setIsRegisterMode(event.target.checked)}
                    />
                  }
                  label={isRegisterMode ? "Đang ở chế độ đăng ký" : "Đang ở chế độ đăng nhập"}
                  sx={{ m: 0 }}
                />

                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={submitting || isInitializing}
                  sx={{
                    py: 1.45,
                    borderRadius: 999,
                    fontWeight: 800,
                    background: "linear-gradient(135deg, #c76b3e 0%, #09213a 100%)",
                    boxShadow: "0 16px 30px rgba(9,33,58,0.16)",
                    "&:hover": {
                      background: "linear-gradient(135deg, #b35d34 0%, #103153 100%)"
                    }
                  }}
                >
                  {submitting ? "Vui lòng chờ..." : isRegisterMode ? "Tạo tài khoản" : "Đăng nhập"}
                </Button>
              </Stack>
            </Box>

            <Paper
              sx={{
                p: 2,
                borderRadius: 4,
                bgcolor: "rgba(9,33,58,0.03)",
                border: "1px solid rgba(9,33,58,0.06)"
              }}
            >
              <Stack spacing={0.8}>
                <Typography variant="body2" fontWeight={700}>
                  Ghi chú truy cập
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Route không hợp lệ hoặc sai vai trò sẽ được điều hướng về dashboard sau khi đăng nhập.
                </Typography>
              </Stack>
            </Paper>
          </Stack>
        </Paper>
      </Box>
    </Box>
  );
}
