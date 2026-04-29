import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography
} from "@mui/material";
import {
  AccountBalance as LoanIcon,
  Assessment as DssIcon,
  CheckCircle as ApprovedIcon,
  Description as RequestIcon,
  Group as UsersIcon,
  HourglassEmpty as PendingIcon,
  Payment as PaymentIcon,
  Person as ProfileIcon,
  PlaylistAddCheck as ReviewIcon,
  TrendingUp as TrendingIcon,
  Warning as WarningIcon
} from "@mui/icons-material";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link as RouterLink, Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelDssRecommendation, labelLoanStatus } from "@/shared/utils/labels";

function QuickActionCard({ icon, title, description, to, color = "primary.main" }) {
  return (
    <Card
      component={RouterLink}
      to={to}
      sx={{
        textDecoration: "none",
        height: "100%",
        transition: "transform 0.2s, box-shadow 0.2s",
        "&:hover": {
          transform: "translateY(-4px)",
          boxShadow: 6
        },
        cursor: "pointer"
      }}
    >
      <CardContent>
        <Stack spacing={1.5} alignItems="flex-start">
          <Avatar sx={{ bgcolor: color, width: 48, height: 48 }}>
            {icon}
          </Avatar>
          <Typography variant="h6" color="text.primary">
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function StatCard({ icon, label, value, color = "#1f4b99", sublabel }) {
  return (
    <Paper sx={{ p: 2.5, borderLeft: `4px solid ${color}`, height: "100%" }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Avatar sx={{ bgcolor: `${color}22`, color, width: 44, height: 44 }}>
          {icon}
        </Avatar>
        <Stack spacing={0.25} sx={{ minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary" noWrap>
            {label}
          </Typography>
          <Typography variant="h5" fontWeight={700}>
            {value}
          </Typography>
          {sublabel && (
            <Typography variant="caption" color="text.secondary">
              {sublabel}
            </Typography>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}

function statusColor(status) {
  const map = {
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    DISBURSED: "primary",
    ACTIVE: "primary",
    CLOSED: "default",
    REJECTED: "error",
    PENDING: "warning"
  };
  return map[status] || "default";
}

function CustomerHome({ accessToken }) {
  const [loans, setLoans] = useState([]);
  const [profile, setProfile] = useState(null);
  const [payments, setPayments] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const { apiRequest } = await import("@/shared/api/http");

      const [loansRes, profileRes, paymentsRes] = await Promise.allSettled([
        apiRequest("/api/customer/loans", { token: accessToken }),
        apiRequest("/api/customer/profile", { token: accessToken }),
        apiRequest("/api/customer/payments", { token: accessToken })
      ]);

      setLoans(loansRes.status === "fulfilled" && Array.isArray(loansRes.value) ? loansRes.value : []);
      setProfile(profileRes.status === "fulfilled" ? profileRes.value : null);
      setPayments(paymentsRes.status === "fulfilled" ? paymentsRes.value : null);
    } catch (err) {
      setError(err.message || "Không tải được dữ liệu trang chủ");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const stats = useMemo(() => {
    const total = loans.length;
    const pending = loans.filter((loan) => loan.status === "PENDING" || loan.status === "APPOINTMENT_SCHEDULED").length;
    const approvedStatuses = ["APPROVED", "CONTRACTED", "DISBURSED", "ACTIVE"];
    const approved = loans.filter((loan) => approvedStatuses.includes(loan.status)).length;
    const approvedAmount = loans
      .filter((loan) => approvedStatuses.includes(loan.status))
      .reduce((sum, loan) => sum + Number(loan.approvedAmount || loan.amount || 0), 0);
    return { total, pending, approved, approvedAmount };
  }, [loans]);

  const recentLoans = useMemo(() => loans.slice(0, 5), [loans]);
  const hasProfile = Boolean(profile?.fullName);
  const paymentRating = payments?.currentRating ?? profile?.paymentRating ?? 0;

  if (loading) {
    return (
      <Paper sx={{ p: 4 }}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
          <CircularProgress size={24} />
          <Typography>Đang tải trang chủ...</Typography>
        </Stack>
      </Paper>
    );
  }

  return (
    <Stack spacing={3}>
      <Paper
        sx={{
          p: 3,
          background: "linear-gradient(135deg, #1f4b99 0%, #2d6fd3 100%)",
          color: "#fff",
          borderRadius: 3
        }}
      >
        <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" alignItems={{ md: "center" }} spacing={2}>
          <Stack spacing={1}>
            <Typography variant="h4">
              Xin chào{hasProfile ? `, ${profile.fullName}` : ""}!
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.9 }}>
              Theo dõi hồ sơ vay, cập nhật tài chính cá nhân và quản lý các khoản thanh toán của bạn tại đây.
            </Typography>
          </Stack>
          <Button
            component={RouterLink}
            to="/customer/loan/new"
            variant="contained"
            size="large"
            startIcon={<LoanIcon />}
            sx={{
              bgcolor: "#fff",
              color: "#1f4b99",
              fontWeight: 700,
              "&:hover": { bgcolor: "#e8eef7" },
              whiteSpace: "nowrap",
              minWidth: 180
            }}
          >
            Tạo hồ sơ vay
          </Button>
        </Stack>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {!hasProfile && (
        <Alert
          severity="warning"
          action={(
            <Button color="inherit" size="small" component={RouterLink} to="/customer/profile">
              Cập nhật ngay
            </Button>
          )}
        >
          Bạn chưa hoàn thiện hồ sơ cá nhân. Vui lòng cập nhật để hệ thống đánh giá chính xác hơn.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<RequestIcon />}
            label="Tổng hồ sơ"
            value={stats.total}
            color="#1f4b99"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<PendingIcon />}
            label="Đang chờ xử lý"
            value={stats.pending}
            color="#ed6c02"
            sublabel={stats.pending > 0 ? "Hồ sơ cần đợi kết quả" : null}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<ApprovedIcon />}
            label="Đã duyệt"
            value={stats.approved}
            color="#2e7d32"
            sublabel={stats.approvedAmount > 0 ? formatVnd(stats.approvedAmount) : null}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<TrendingIcon />}
            label="Điểm tín nhiệm"
            value={paymentRating}
            color={paymentRating >= 20 ? "#2e7d32" : paymentRating >= 0 ? "#1f4b99" : "#d32f2f"}
            sublabel={paymentRating >= 20 ? "Tốt" : paymentRating >= 0 ? "Trung bình" : "Cần cải thiện"}
          />
        </Grid>
      </Grid>

      <Typography variant="h5">Truy cập nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<LoanIcon />}
            title="Tạo hồ sơ vay"
            description="Nộp hồ sơ vay mới với đánh giá tự động từ DSS"
            to="/customer/loan/new"
            color="primary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<RequestIcon />}
            title="Hồ sơ vay"
            description="Xem danh sách và theo dõi trạng thái hồ sơ"
            to="/customer/loans"
            color="info.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<PaymentIcon />}
            title="Thanh toán"
            description="Ghi nhận thanh toán và theo dõi dư nợ còn lại"
            to="/customer/payments"
            color="success.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<ProfileIcon />}
            title="Hồ sơ cá nhân"
            description="Cập nhật thông tin tài chính và khoản nợ"
            to="/customer/profile"
            color="secondary.main"
          />
        </Grid>
      </Grid>

      <Typography variant="h5">Hồ sơ vay gần đây</Typography>
      {recentLoans.length === 0 ? (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1} alignItems="center">
            <LoanIcon sx={{ fontSize: 48, color: "text.disabled" }} />
            <Typography color="text.secondary">
              Bạn chưa có hồ sơ vay nào. Hãy tạo hồ sơ đầu tiên!
            </Typography>
            <Button variant="outlined" component={RouterLink} to="/customer/loan/new">
              Tạo hồ sơ vay đầu tiên
            </Button>
          </Stack>
        </Paper>
      ) : (
        <Paper sx={{ overflowX: "auto" }}>
          <Box sx={{ minWidth: 600 }}>
            {recentLoans.map((loan, index) => (
              <Box key={loan.id}>
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                  sx={{ px: 2.5, py: 1.5 }}
                >
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ minWidth: 0 }}>
                    <Avatar sx={{ bgcolor: "primary.light", width: 36, height: 36, fontSize: 14 }}>
                      #{loan.id}
                    </Avatar>
                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                      <Typography variant="body1" fontWeight={600} noWrap>
                        {formatVnd(loan.amount)} - {loan.termMonths} tháng
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(loan.createdAt).toLocaleDateString("vi-VN")}
                      </Typography>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip size="small" color={statusColor(loan.status)} label={labelLoanStatus(loan.status)} />
                    <Button size="small" component={RouterLink} to={`/customer/loans/${loan.id}`}>
                      Chi tiết
                    </Button>
                  </Stack>
                </Stack>
                {index < recentLoans.length - 1 && <Divider />}
              </Box>
            ))}
          </Box>
        </Paper>
      )}
    </Stack>
  );
}

function StaffHome({ accessToken }) {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const { apiRequest } = await import("@/shared/api/http");
      const response = await apiRequest("/api/staff/requests", { token: accessToken });
      setRequests(Array.isArray(response) ? response : []);
    } catch (err) {
      setError(err.message || "Không tải được dữ liệu");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const stats = useMemo(() => {
    const total = requests.length;
    const pending = requests.filter((request) => request.status === "PENDING").length;
    return { total, pending };
  }, [requests]);

  const urgentRequests = useMemo(
    () => requests
      .filter((request) => request.status === "PENDING")
      .slice(0, 5),
    [requests]
  );

  if (loading) {
    return (
      <Paper sx={{ p: 4 }}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
          <CircularProgress size={24} />
          <Typography>Đang tải trang chủ...</Typography>
        </Stack>
      </Paper>
    );
  }

  return (
    <Stack spacing={3}>
      <Paper
        sx={{
          p: 3,
          background: "linear-gradient(135deg, #118a71 0%, #1bab8e 100%)",
          color: "#fff",
          borderRadius: 3
        }}
      >
        <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" alignItems={{ md: "center" }} spacing={2}>
          <Stack spacing={1}>
            <Typography variant="h4">
              Bảng điều khiển nhân viên
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.9 }}>
              Xem nhanh hồ sơ cần thẩm định, xác minh và ra quyết định phê duyệt.
            </Typography>
          </Stack>
          <Button
            component={RouterLink}
            to="/staff/requests"
            variant="contained"
            size="large"
            startIcon={<ReviewIcon />}
            sx={{
              bgcolor: "#fff",
              color: "#118a71",
              fontWeight: 700,
              "&:hover": { bgcolor: "#e0f2ee" },
              whiteSpace: "nowrap",
              minWidth: 200
            }}
          >
            Hàng đợi thẩm định
          </Button>
        </Stack>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard icon={<RequestIcon />} label="Tổng hồ sơ" value={stats.total} color="#1f4b99" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<PendingIcon />}
            label="Chờ xử lý"
            value={stats.pending}
            color="#ed6c02"
            sublabel={stats.pending > 0 ? "Cần thẩm định" : "Không có hồ sơ chờ"}
          />
        </Grid>
      </Grid>

      <Typography variant="h5">Truy cập nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<ReviewIcon />}
            title="Hàng đợi thẩm định"
            description="Xem tất cả hồ sơ cần thẩm định và ra quyết định"
            to="/staff/requests"
            color="primary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<DssIcon />}
            title="Bảng điều khiển"
            description="Thống kê và phân tích tổng quan hồ sơ vay"
            to="/staff/dashboard"
            color="secondary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<LoanIcon />}
            title="Vận hành khoản vay"
            description="Theo dõi lịch hẹn, hợp đồng, giải ngân và khoản vay đang hoạt động"
            to="/staff/loan-operations"
            color="success.main"
          />
        </Grid>
      </Grid>

      <Typography variant="h5">
        Hồ sơ cần xử lý{" "}
        {urgentRequests.length > 0 && (
          <Chip size="small" color="warning" label={`${urgentRequests.length}`} sx={{ ml: 1 }} />
        )}
      </Typography>
      {urgentRequests.length === 0 ? (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1} alignItems="center">
            <ApprovedIcon sx={{ fontSize: 48, color: "success.main" }} />
            <Typography color="text.secondary">
              Không có hồ sơ nào cần xử lý.
            </Typography>
          </Stack>
        </Paper>
      ) : (
        <Paper sx={{ overflowX: "auto" }}>
          <Box sx={{ minWidth: 600 }}>
            {urgentRequests.map((request, index) => (
              <Box key={request.id}>
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                  sx={{ px: 2.5, py: 1.5 }}
                >
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ minWidth: 0 }}>
                    <Avatar sx={{ bgcolor: "warning.main", width: 36, height: 36, fontSize: 14 }}>
                      #{request.id}
                    </Avatar>
                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                      <Typography variant="body1" fontWeight={600} noWrap>
                        {formatVnd(request.amount)} - {request.termMonths} tháng
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Khách hàng #{request.customerId} • {new Date(request.createdAt).toLocaleDateString("vi-VN")}
                      </Typography>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {request.dssRecommendation && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label={labelDssRecommendation(request.dssRecommendation)}
                      />
                    )}
                    <Chip size="small" color={statusColor(request.status)} label={labelLoanStatus(request.status)} />
                    <Button size="small" component={RouterLink} to={`/staff/requests/${request.id}`}>
                      Thẩm định
                    </Button>
                  </Stack>
                </Stack>
                {index < urgentRequests.length - 1 && <Divider />}
              </Box>
            ))}
          </Box>
        </Paper>
      )}
    </Stack>
  );
}

function AdminHome() {
  return (
    <Stack spacing={3}>
      <Paper
        sx={{
          p: 3,
          background: "linear-gradient(135deg, #7b1fa2 0%, #ab47bc 100%)",
          color: "#fff",
          borderRadius: 3
        }}
      >
        <Stack spacing={1}>
          <Typography variant="h4">
            Trang quản trị hệ thống
          </Typography>
          <Typography variant="body1" sx={{ opacity: 0.9 }}>
            Quản lý tài khoản người dùng và giám sát hoạt động hệ thống.
          </Typography>
        </Stack>
      </Paper>

      <Typography variant="h5">Truy cập nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<UsersIcon />}
            title="Quản lý người dùng"
            description="Xem, lọc và xóa tài khoản khách hàng hoặc nhân viên"
            to="/admin/users"
            color="secondary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<ProfileIcon />}
            title="Tạo tài khoản"
            description="Tạo tài khoản khách hàng hoặc nhân viên qua khu vực quản trị"
            to="/admin/accounts/new"
            color="info.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<DssIcon />}
            title="Hệ thống DSS"
            description="Chấm điểm tín dụng 300-850, xếp hạng A/B/C/D"
            to="/admin/users"
            color="primary.main"
          />
        </Grid>
      </Grid>

      <Typography variant="h5">Tổng quan hệ thống</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Kiến trúc hệ thống</Typography>
              <Divider />
              {[
                { label: "Giao diện web", value: "React 18 + Vite + MUI" },
                { label: "Máy chủ ứng dụng", value: "Java 17 + Spring Boot 3.5" },
                { label: "Cơ sở dữ liệu", value: "MySQL 8.4 + Flyway" },
                { label: "Xác thực", value: "JWT không lưu phiên + BCrypt" },
                { label: "Phân quyền", value: "Theo vai trò (Khách hàng, Nhân viên, Quản trị)" }
              ].map((item) => (
                <Stack key={item.label} direction="row" justifyContent="space-between">
                  <Typography variant="body2" color="text.secondary">{item.label}</Typography>
                  <Typography variant="body2" fontWeight={600}>{item.value}</Typography>
                </Stack>
              ))}
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Mô-đun nghiệp vụ</Typography>
              <Divider />
              {[
                "Quản lý hồ sơ khách hàng và khoản nợ",
                "DSS chấm điểm tín dụng và khuyến nghị",
                "Đánh giá rủi ro tín dụng, gian lận và vận hành",
                "Xác minh KYC / AML / Gian lận",
                "Hợp đồng vay EMI tự động",
                "Thanh toán và điểm tín nhiệm",
                "Nhật ký kiểm toán tuân thủ"
              ].map((item) => (
                <Stack key={item} direction="row" spacing={1} alignItems="center">
                  <ApprovedIcon sx={{ fontSize: 18, color: "success.main" }} />
                  <Typography variant="body2">{item}</Typography>
                </Stack>
              ))}
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Stack>
  );
}

export default function HomePage() {
  const { isAuthenticated, user, isInitializing, accessToken } = useAuth();

  if (isInitializing) {
    return (
      <Box sx={{ minHeight: "60vh", display: "grid", placeItems: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user.role === "CUSTOMER") {
    return <CustomerHome accessToken={accessToken} />;
  }

  if (user.role === "STAFF") {
    return <StaffHome accessToken={accessToken} />;
  }

  if (user.role === "ADMIN") {
    return <AdminHome />;
  }

  return <Navigate to="/login" replace />;
}
