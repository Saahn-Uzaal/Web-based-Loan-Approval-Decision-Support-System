import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Grid,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { getStaffRequestsApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelDssRecommendation, labelLoanStatus, labelRiskRank } from "@/shared/utils/labels";

const recommendationOrder = [
  "APPROVE_RECOMMENDED",
  "ESCALATE_RECOMMENDED",
  "REJECT_RECOMMENDED"
];

const riskRankOrder = ["A", "B", "C", "D"];

function MetricCard({ title, value, description, color }) {
  return (
    <Paper sx={{ p: 3, height: "100%", borderTop: `4px solid ${color}` }}>
      <Stack spacing={1}>
        <Typography variant="subtitle2" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h4">{value}</Typography>
        <Typography variant="body2" color="text.secondary">
          {description}
        </Typography>
      </Stack>
    </Paper>
  );
}

function DistributionSection({ title, rows, total, labelFormatter }) {
  return (
    <Paper sx={{ p: 3, height: "100%" }}>
      <Stack spacing={2}>
        <Typography variant="h6">{title}</Typography>
        {rows.map((item) => {
          const percent = total > 0 ? Math.round((item.count * 100) / total) : 0;
          return (
            <Stack key={item.label} spacing={0.5}>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">{labelFormatter(item.label)}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {item.count} ({percent}%)
                </Typography>
              </Stack>
              <LinearProgress variant="determinate" value={percent} />
            </Stack>
          );
        })}
      </Stack>
    </Paper>
  );
}

function EmptyCard({ message }) {
  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="body2" color="text.secondary">
        {message}
      </Typography>
    </Paper>
  );
}

export default function StaffDashboardPage() {
  const { accessToken } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);

  const loadDashboard = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const response = await getStaffRequestsApi(accessToken);
      setRows(Array.isArray(response) ? response : []);
      setLastUpdatedAt(new Date());
    } catch (err) {
      setError(err.message || "Không tải được dữ liệu bảng điều khiển");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const dashboard = useMemo(() => {
    const totalQueue = rows.length;
    const pending = rows.filter((row) => row.status === "PENDING").length;
    const waitingSupervisor = rows.filter((row) => row.status === "WAITING_SUPERVISOR").length;
    const totalAmount = rows.reduce((sum, row) => sum + Number(row.amount || 0), 0);
    const averageAmount = totalQueue > 0 ? totalAmount / totalQueue : 0;

    const recommendationRows = recommendationOrder.map((label) => ({
      label,
      count: rows.filter((row) => row.dssRecommendation === label).length
    }));

    const riskRows = riskRankOrder.map((label) => ({
      label,
      count: rows.filter((row) => row.riskRank === label).length
    }));

    const priorityRows = [...rows]
      .filter((row) => row.status === "WAITING_SUPERVISOR" || row.dssRecommendation === "REJECT_RECOMMENDED")
      .sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0))
      .slice(0, 6);

    const highValueRows = [...rows]
      .sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0))
      .slice(0, 6);

    return {
      totalQueue,
      pending,
      waitingSupervisor,
      totalAmount,
      averageAmount,
      recommendationRows,
      riskRows,
      priorityRows,
      highValueRows
    };
  }, [rows]);

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", sm: "center" }}>
        <Stack spacing={0.5}>
          <Typography variant="h4">Bảng điều khiển nhân viên</Typography>
          <Typography color="text.secondary">
            Góc nhìn vận hành theo thời gian thực cho hàng đợi thẩm định, tín hiệu DSS và các hồ sơ ưu tiên.
          </Typography>
          {lastUpdatedAt && (
            <Typography variant="caption" color="text.secondary">
              Cập nhật lần cuối: {lastUpdatedAt.toLocaleString()}
            </Typography>
          )}
        </Stack>
        <Button variant="outlined" onClick={loadDashboard} disabled={loading}>
          {loading ? "Đang làm mới..." : "Làm mới"}
        </Button>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải bảng điều khiển...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Tổng hồ sơ trong hàng đợi"
                value={dashboard.totalQueue}
                description="Tổng số hồ sơ hiện đang chờ nhân viên xử lý."
                color="#1976d2"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Chờ xử lý"
                value={dashboard.pending}
                description="Hồ sơ đang chờ thao tác thẩm định đầu tiên."
                color="#ed6c02"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Chờ quản lý duyệt"
                value={dashboard.waitingSupervisor}
                description="Hồ sơ đã được chuyển cấp cao hơn để xem xét."
                color="#0288d1"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Tổng số tiền trong hàng đợi"
                value={formatVnd(dashboard.totalAmount)}
                description={`Giá trị trung bình mỗi hồ sơ: ${formatVnd(dashboard.averageAmount)}`}
                color="#2e7d32"
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="Phân bố khuyến nghị DSS"
                rows={dashboard.recommendationRows}
                total={dashboard.totalQueue}
                labelFormatter={labelDssRecommendation}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="Phân bố hạng rủi ro"
                rows={dashboard.riskRows}
                total={dashboard.totalQueue}
                labelFormatter={labelRiskRank}
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} lg={6}>
              <Paper sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Hồ sơ ưu tiên</Typography>
                  {dashboard.priorityRows.length === 0 && (
                    <EmptyCard message="Hiện tại không có hồ sơ ưu tiên." />
                  )}
                  {dashboard.priorityRows.length > 0 && (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Mã</TableCell>
                          <TableCell>Khách hàng</TableCell>
                          <TableCell>Số tiền</TableCell>
                          <TableCell>Trạng thái</TableCell>
                          <TableCell align="right">Thao tác</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {dashboard.priorityRows.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>#{row.id}</TableCell>
                            <TableCell>{row.customerName || row.customerEmail}</TableCell>
                            <TableCell>{formatVnd(row.amount)}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={labelLoanStatus(row.status)}
                                color={row.status === "WAITING_SUPERVISOR" ? "info" : "warning"}
                              />
                            </TableCell>
                            <TableCell align="right">
                              <Button
                                component={RouterLink}
                                to={`/staff/requests/${row.id}`}
                                variant="outlined"
                                size="small"
                              >
                                Thẩm định
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </Paper>
            </Grid>

            <Grid item xs={12} lg={6}>
              <Paper sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Các hồ sơ có số tiền vay lớn nhất</Typography>
                  {dashboard.highValueRows.length === 0 && (
                    <EmptyCard message="Không tìm thấy hồ sơ trong hàng đợi." />
                  )}
                  {dashboard.highValueRows.length > 0 && (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Mã</TableCell>
                          <TableCell>Khách hàng</TableCell>
                          <TableCell>Số tiền</TableCell>
                          <TableCell>Khuyến nghị DSS</TableCell>
                          <TableCell align="right">Thao tác</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {dashboard.highValueRows.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>#{row.id}</TableCell>
                            <TableCell>{row.customerName || row.customerEmail}</TableCell>
                            <TableCell>{formatVnd(row.amount)}</TableCell>
                            <TableCell>{labelDssRecommendation(row.dssRecommendation)}</TableCell>
                            <TableCell align="right">
                              <Button
                                component={RouterLink}
                                to={`/staff/requests/${row.id}`}
                                variant="outlined"
                                size="small"
                              >
                                Thẩm định
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </Paper>
            </Grid>
          </Grid>
        </>
      )}
    </Stack>
  );
}
