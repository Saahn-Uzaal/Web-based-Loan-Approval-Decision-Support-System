import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Stack,
  Typography
} from "@mui/material";
import {
  MarkEmailReadRounded as MailVerifiedIcon,
  ReportProblemRounded as ProblemIcon
} from "@mui/icons-material";
import { Link as RouterLink, useSearchParams } from "react-router-dom";
import { verifyEmailApi } from "@/features/auth/api/authApi";

export default function EmailVerificationPage() {
  const [searchParams] = useSearchParams();
  const [state, setState] = useState({
    loading: true,
    success: "",
    error: "",
    email: ""
  });

  useEffect(() => {
    const token = searchParams.get("token");
    let active = true;

    if (!token) {
      setState({
        loading: false,
        success: "",
        error: "Liên kết xác minh không đầy đủ. Vui lòng mở lại email xác minh mới nhất.",
        email: ""
      });
      return undefined;
    }

    async function runVerification() {
      try {
        const response = await verifyEmailApi(token);
        if (!active) {
          return;
        }
        setState({
          loading: false,
          success: response.message || "Xác minh email thành công.",
          error: "",
          email: response.email || ""
        });
      } catch (err) {
        if (!active) {
          return;
        }
        setState({
          loading: false,
          success: "",
          error: err.message || "Không thể xác minh email.",
          email: ""
        });
      }
    }

    runVerification();
    return () => {
      active = false;
    };
  }, [searchParams]);

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        px: 2,
        py: 4,
        background: `
          radial-gradient(circle at top left, rgba(199,107,62,0.18), transparent 26%),
          radial-gradient(circle at top right, rgba(19,118,108,0.16), transparent 24%),
          linear-gradient(180deg, #fcf6eb 0%, #efe7d7 100%)
        `
      }}
    >
      <Paper
        sx={{
          width: "100%",
          maxWidth: 640,
          p: { xs: 3, md: 4 },
          borderRadius: 6,
          border: "1px solid rgba(9,33,58,0.08)",
          boxShadow: "0 28px 70px rgba(9,33,58,0.10)",
          background: "linear-gradient(180deg, rgba(255,250,241,0.96) 0%, rgba(255,255,255,0.99) 100%)"
        }}
      >
        <Stack spacing={2.5}>
          <Stack spacing={1}>
            <Typography variant="overline" sx={{ letterSpacing: "0.14em", color: "text.secondary" }}>
              Xác minh email
            </Typography>
            <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1.05 }}>
              Hoàn tất kích hoạt tài khoản
            </Typography>
            <Typography color="text.secondary">
              Hệ thống đang kiểm tra liên kết xác minh mà bạn vừa mở từ email đăng ký.
            </Typography>
          </Stack>

          {state.loading && (
            <Stack spacing={2} alignItems="center" sx={{ py: 3 }}>
              <CircularProgress />
              <Typography color="text.secondary">Đang xác minh email, vui lòng chờ một chút...</Typography>
            </Stack>
          )}

          {!state.loading && state.success && (
            <Alert icon={<MailVerifiedIcon fontSize="inherit" />} severity="success" sx={{ borderRadius: 3 }}>
              {state.success}
            </Alert>
          )}

          {!state.loading && state.error && (
            <Alert icon={<ProblemIcon fontSize="inherit" />} severity="error" sx={{ borderRadius: 3 }}>
              {state.error}
            </Alert>
          )}

          {!state.loading && state.email && (
            <Typography variant="body2" color="text.secondary">
              Email đã xử lý: <strong>{state.email}</strong>
            </Typography>
          )}

          <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
            <Button
              component={RouterLink}
              to="/login"
              variant="contained"
              sx={{
                borderRadius: 999,
                px: 3,
                background: "linear-gradient(135deg, #c76b3e 0%, #09213a 100%)",
                "&:hover": {
                  background: "linear-gradient(135deg, #b35d34 0%, #103153 100%)"
                }
              }}
            >
              Về trang đăng nhập
            </Button>
            <Button component={RouterLink} to="/" variant="outlined" sx={{ borderRadius: 999, px: 3 }}>
              Về trang chủ
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
}
