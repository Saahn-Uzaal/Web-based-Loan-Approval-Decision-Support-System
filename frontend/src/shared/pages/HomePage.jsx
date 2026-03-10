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
  LinearProgress,
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
  Security as SecurityIcon,
  TrendingUp as TrendingIcon,
  Warning as WarningIcon
} from "@mui/icons-material";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link as RouterLink, Navigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelLoanStatus, labelDssRecommendation, labelRiskRank, labelRole } from "@/shared/utils/labels";

// â”€â”€â”€ Quickâ€action card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

// â”€â”€â”€ Stat card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

// â”€â”€â”€ Recent loan row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function statusColor(status) {
  const map = { APPROVED: "success", REJECTED: "error", PENDING: "warning", WAITING_SUPERVISOR: "info" };
  return map[status] || "default";
}

// â”€â”€â”€ CUSTOMER HOME â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function CustomerHome({ accessToken }) {
  const [loans, setLoans] = useState([]);
  const [profile, setProfile] = useState(null);
  const [payments, setPayments] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    if (!accessToken) return;
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
      setError(err.message || "KhÃ´ng táº£i Ä‘Æ°á»£c dá»¯ liá»‡u trang chá»§");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => { loadData(); }, [loadData]);

  const stats = useMemo(() => {
    const total = loans.length;
    const pending = loans.filter(l => l.status === "PENDING" || l.status === "WAITING_SUPERVISOR").length;
    const approved = loans.filter(l => l.status === "APPROVED").length;
    const rejected = loans.filter(l => l.status === "REJECTED").length;
    const totalAmount = loans.reduce((sum, l) => sum + Number(l.amount || 0), 0);
    const approvedAmount = loans.filter(l => l.status === "APPROVED").reduce((sum, l) => sum + Number(l.amount || 0), 0);
    return { total, pending, approved, rejected, totalAmount, approvedAmount };
  }, [loans]);

  const recentLoans = useMemo(() => loans.slice(0, 5), [loans]);

  const hasProfile = profile && profile.fullName;
  const paymentRating = payments?.currentRating ?? profile?.paymentRating ?? 0;

  if (loading) {
    return (
      <Paper sx={{ p: 4 }}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
          <CircularProgress size={24} />
          <Typography>Äang táº£i trang chá»§...</Typography>
        </Stack>
      </Paper>
    );
  }

  return (
    <Stack spacing={3}>
      {/* Welcome banner */}
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
              Xin chÃ o{hasProfile ? `, ${profile.fullName}` : ""}! ðŸ‘‹
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.9 }}>
              ChÃ o má»«ng báº¡n Ä‘áº¿n vá»›i há»‡ thá»‘ng há»— trá»£ quyáº¿t Ä‘á»‹nh cho vay. Theo dÃµi há»“ sÆ¡ vay vÃ  quáº£n lÃ½ tÃ i chÃ­nh cá»§a báº¡n táº¡i Ä‘Ã¢y.
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
            Táº¡o há»“ sÆ¡ vay
          </Button>
        </Stack>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {/* Profile completion alert */}
      {!hasProfile && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" size="small" component={RouterLink} to="/customer/profile">
              Cáº­p nháº­t ngay
            </Button>
          }
        >
          Báº¡n chÆ°a hoÃ n thiá»‡n há»“ sÆ¡ cÃ¡ nhÃ¢n. Vui lÃ²ng cáº­p nháº­t Ä‘á»ƒ há»‡ thá»‘ng Ä‘Ã¡nh giÃ¡ chÃ­nh xÃ¡c hÆ¡n.
        </Alert>
      )}

      {/* Stats row */}
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<RequestIcon />}
            label="Tá»•ng há»“ sÆ¡"
            value={stats.total}
            color="#1f4b99"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<PendingIcon />}
            label="Äang chá» xá»­ lÃ½"
            value={stats.pending}
            color="#ed6c02"
            sublabel={stats.pending > 0 ? "Há»“ sÆ¡ cáº§n Ä‘á»£i káº¿t quáº£" : null}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<ApprovedIcon />}
            label="ÄÃ£ duyá»‡t"
            value={stats.approved}
            color="#2e7d32"
            sublabel={stats.approvedAmount > 0 ? formatVnd(stats.approvedAmount) : null}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<TrendingIcon />}
            label="Äiá»ƒm tÃ­n nhiá»‡m"
            value={paymentRating}
            color={paymentRating >= 20 ? "#2e7d32" : paymentRating >= 0 ? "#1f4b99" : "#d32f2f"}
            sublabel={paymentRating >= 20 ? "Tá»‘t" : paymentRating >= 0 ? "Trung bÃ¬nh" : "Cáº§n cáº£i thiá»‡n"}
          />
        </Grid>
      </Grid>

      {/* Quick actions */}
      <Typography variant="h5">Truy cáº­p nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<LoanIcon />}
            title="Táº¡o há»“ sÆ¡ vay"
            description="Ná»™p há»“ sÆ¡ vay má»›i vá»›i Ä‘Ã¡nh giÃ¡ tá»± Ä‘á»™ng tá»« DSS"
            to="/customer/loan/new"
            color="primary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<RequestIcon />}
            title="Há»“ sÆ¡ vay"
            description="Xem danh sÃ¡ch vÃ  theo dÃµi tráº¡ng thÃ¡i há»“ sÆ¡"
            to="/customer/loans"
            color="info.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<PaymentIcon />}
            title="Thanh toÃ¡n"
            description="Ghi nháº­n thanh toÃ¡n vÃ  theo dÃµi ná»£ cÃ²n láº¡i"
            to="/customer/payments"
            color="success.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <QuickActionCard
            icon={<ProfileIcon />}
            title="Há»“ sÆ¡ cÃ¡ nhÃ¢n"
            description="Cáº­p nháº­t thÃ´ng tin tÃ i chÃ­nh vÃ  khoáº£n ná»£"
            to="/customer/profile"
            color="secondary.main"
          />
        </Grid>
      </Grid>

      {/* Recent loans */}
      <Typography variant="h5">Há»“ sÆ¡ vay gáº§n Ä‘Ã¢y</Typography>
      {recentLoans.length === 0 ? (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1} alignItems="center">
            <LoanIcon sx={{ fontSize: 48, color: "text.disabled" }} />
            <Typography color="text.secondary">
              Báº¡n chÆ°a cÃ³ há»“ sÆ¡ vay nÃ o. HÃ£y táº¡o há»“ sÆ¡ Ä‘áº§u tiÃªn!
            </Typography>
            <Button variant="outlined" component={RouterLink} to="/customer/loan/new">
              Táº¡o há»“ sÆ¡ vay Ä‘áº§u tiÃªn
            </Button>
          </Stack>
        </Paper>
      ) : (
        <Paper sx={{ overflowX: "auto" }}>
          <Box sx={{ minWidth: 600 }}>
            {recentLoans.map((loan, idx) => (
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
                        {formatVnd(loan.amount)} â€” {loan.termMonths} thÃ¡ng
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(loan.createdAt).toLocaleDateString("vi-VN")}
                      </Typography>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip size="small" color={statusColor(loan.status)} label={labelLoanStatus(loan.status)} />
                    <Button size="small" component={RouterLink} to={`/customer/loans/${loan.id}`}>
                      Chi tiáº¿t
                    </Button>
                  </Stack>
                </Stack>
                {idx < recentLoans.length - 1 && <Divider />}
              </Box>
            ))}
          </Box>
        </Paper>
      )}
    </Stack>
  );
}

// â”€â”€â”€ STAFF HOME â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function StaffHome({ accessToken }) {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError("");
    try {
      const { apiRequest } = await import("@/shared/api/http");
      const res = await apiRequest("/api/staff/requests", { token: accessToken });
      setRequests(Array.isArray(res) ? res : []);
    } catch (err) {
      setError(err.message || "KhÃ´ng táº£i Ä‘Æ°á»£c dá»¯ liá»‡u");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => { loadData(); }, [loadData]);

  const stats = useMemo(() => {
    const total = requests.length;
    const pending = requests.filter(r => r.status === "PENDING").length;
    const waiting = requests.filter(r => r.status === "WAITING_SUPERVISOR").length;
    return { total, pending, waiting };
  }, [requests]);

  const urgentRequests = useMemo(
    () => requests
      .filter(r => r.status === "PENDING" || r.status === "WAITING_SUPERVISOR")
      .slice(0, 5),
    [requests]
  );

  if (loading) {
    return (
      <Paper sx={{ p: 4 }}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
          <CircularProgress size={24} />
          <Typography>Äang táº£i trang chá»§...</Typography>
        </Stack>
      </Paper>
    );
  }

  return (
    <Stack spacing={3}>
      {/* Welcome banner */}
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
              Báº£ng Ä‘iá»u khiá»ƒn nhÃ¢n viÃªn ðŸ¦
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.9 }}>
              Xem nhanh há»“ sÆ¡ cáº§n tháº©m Ä‘á»‹nh, xÃ¡c minh vÃ  ra quyáº¿t Ä‘á»‹nh phÃª duyá»‡t.
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
            HÃ ng Ä‘á»£i tháº©m Ä‘á»‹nh
          </Button>
        </Stack>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {/* Stats row */}
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard icon={<RequestIcon />} label="Tá»•ng há»“ sÆ¡" value={stats.total} color="#1f4b99" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<PendingIcon />}
            label="Chá» xá»­ lÃ½"
            value={stats.pending}
            color="#ed6c02"
            sublabel={stats.pending > 0 ? "Cáº§n tháº©m Ä‘á»‹nh" : "KhÃ´ng cÃ³ há»“ sÆ¡ chá»"}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<WarningIcon />}
            label="Chá» quáº£n lÃ½"
            value={stats.waiting}
            color="#0288d1"
            sublabel="ÄÃ£ escalate"
          />
        </Grid>
      </Grid>

      {/* Quick actions */}
      <Typography variant="h5">Truy cáº­p nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<ReviewIcon />}
            title="HÃ ng Ä‘á»£i tháº©m Ä‘á»‹nh"
            description="Xem táº¥t cáº£ há»“ sÆ¡ cáº§n tháº©m Ä‘á»‹nh vÃ  ra quyáº¿t Ä‘á»‹nh"
            to="/staff/requests"
            color="primary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<DssIcon />}
            title="Báº£ng Ä‘iá»u khiá»ƒn"
            description="Thá»‘ng kÃª vÃ  phÃ¢n tÃ­ch tá»•ng quan há»“ sÆ¡ vay"
            to="/staff/dashboard"
            color="secondary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<ProfileIcon />}
            title="Táº¡o tÃ i khoáº£n"
            description="Táº¡o tÃ i khoáº£n khÃ¡ch hÃ ng hoáº·c nhÃ¢n viÃªn má»›i"
            to="/staff/accounts/new"
            color="info.main"
          />
        </Grid>
      </Grid>

      {/* Urgent requests */}
      <Typography variant="h5">
        Há»“ sÆ¡ cáº§n xá»­ lÃ½ {urgentRequests.length > 0 && (
          <Chip size="small" color="warning" label={`${urgentRequests.length}`} sx={{ ml: 1 }} />
        )}
      </Typography>
      {urgentRequests.length === 0 ? (
        <Paper sx={{ p: 3 }}>
          <Stack spacing={1} alignItems="center">
            <ApprovedIcon sx={{ fontSize: 48, color: "success.main" }} />
            <Typography color="text.secondary">
              KhÃ´ng cÃ³ há»“ sÆ¡ nÃ o cáº§n xá»­ lÃ½. Tuyá»‡t vá»i!
            </Typography>
          </Stack>
        </Paper>
      ) : (
        <Paper sx={{ overflowX: "auto" }}>
          <Box sx={{ minWidth: 600 }}>
            {urgentRequests.map((req, idx) => (
              <Box key={req.id}>
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                  sx={{ px: 2.5, py: 1.5 }}
                >
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ minWidth: 0 }}>
                    <Avatar sx={{ bgcolor: req.status === "WAITING_SUPERVISOR" ? "info.main" : "warning.main", width: 36, height: 36, fontSize: 14 }}>
                      #{req.id}
                    </Avatar>
                    <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                      <Typography variant="body1" fontWeight={600} noWrap>
                        {formatVnd(req.amount)} â€” {req.termMonths} thÃ¡ng
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        KhÃ¡ch hÃ ng #{req.customerId} Â· {new Date(req.createdAt).toLocaleDateString("vi-VN")}
                      </Typography>
                    </Stack>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {req.dssRecommendation && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label={labelDssRecommendation(req.dssRecommendation)}
                      />
                    )}
                    <Chip size="small" color={statusColor(req.status)} label={labelLoanStatus(req.status)} />
                    <Button size="small" component={RouterLink} to={`/staff/requests/${req.id}`}>
                      Tháº©m Ä‘á»‹nh
                    </Button>
                  </Stack>
                </Stack>
                {idx < urgentRequests.length - 1 && <Divider />}
              </Box>
            ))}
          </Box>
        </Paper>
      )}
    </Stack>
  );
}

// â”€â”€â”€ ADMIN HOME â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function AdminHome() {
  return (
    <Stack spacing={3}>
      {/* Welcome banner */}
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
            Trang quáº£n trá»‹ há»‡ thá»‘ng ðŸ”’
          </Typography>
          <Typography variant="body1" sx={{ opacity: 0.9 }}>
            Quáº£n lÃ½ tÃ i khoáº£n ngÆ°á»i dÃ¹ng vÃ  giÃ¡m sÃ¡t hoáº¡t Ä‘á»™ng há»‡ thá»‘ng.
          </Typography>
        </Stack>
      </Paper>

      {/* Quick actions */}
      <Typography variant="h5">Truy cáº­p nhanh</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<UsersIcon />}
            title="Quáº£n lÃ½ ngÆ°á»i dÃ¹ng"
            description="Xem, lá»c vÃ  xÃ³a tÃ i khoáº£n khÃ¡ch hÃ ng hoáº·c nhÃ¢n viÃªn"
            to="/admin/users"
            color="secondary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<SecurityIcon />}
            title="Báº£o máº­t"
            description="JWT authentication, RBAC, phÃ¢n quyá»n theo vai trÃ²"
            to="/admin/users"
            color="error.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <QuickActionCard
            icon={<DssIcon />}
            title="Há»‡ thá»‘ng DSS"
            description="Cháº¥m Ä‘iá»ƒm tÃ­n dá»¥ng 300â€“850, xáº¿p háº¡ng A/B/C/D"
            to="/admin/users"
            color="primary.main"
          />
        </Grid>
      </Grid>

      {/* System overview */}
      <Typography variant="h5">Tá»•ng quan há»‡ thá»‘ng</Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Kiáº¿n trÃºc há»‡ thá»‘ng</Typography>
              <Divider />
              {[
                { label: "Frontend", value: "React 18 + Vite + MUI" },
                { label: "Backend", value: "Java 17 + Spring Boot 3.5" },
                { label: "Database", value: "MySQL 8.4 + Flyway" },
                { label: "XÃ¡c thá»±c", value: "JWT stateless + BCrypt" },
                { label: "PhÃ¢n quyá»n", value: "RBAC (Customer, Staff, Admin)" }
              ].map(item => (
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
              <Typography variant="h6">Module nghiá»‡p vá»¥</Typography>
              <Divider />
              {[
                "Quáº£n lÃ½ há»“ sÆ¡ khÃ¡ch hÃ ng & khoáº£n ná»£",
                "DSS cháº¥m Ä‘iá»ƒm tÃ­n dá»¥ng & khuyáº¿n nghá»‹",
                "Risk Assessment (credit/fraud/operational)",
                "XÃ¡c minh KYC / AML / Fraud",
                "Há»£p Ä‘á»“ng vay EMI tá»± Ä‘á»™ng",
                "Thanh toÃ¡n & Äiá»ƒm tÃ­n nhiá»‡m",
                "Compliance Audit Log"
              ].map(item => (
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

// â”€â”€â”€ MAIN HOME PAGE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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




