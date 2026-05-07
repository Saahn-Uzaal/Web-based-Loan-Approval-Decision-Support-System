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
import { getInformationVerificationsApi } from "@/features/staff/api/informationVerificationApi";
import {
  getStaffLoanOperationsApi,
  getStaffPaymentConfirmationsApi,
  getStaffRequestsApi,
  getStaffSecuredProceduresApi
} from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelDssRecommendation, labelLoanStatus, labelRiskRank } from "@/shared/utils/labels";

const recommendationOrder = [
  "APPROVE_RECOMMENDED",
  "MANUAL_REVIEW_RECOMMENDED",
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
  const [queues, setQueues] = useState({
    review: [],
    operations: [],
    secured: [],
    infoPending: [],
    paymentPending: []
  });
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
      const [
        reviewResult,
        operationResult,
        securedResult,
        infoPendingResult,
        paymentPendingResult
      ] = await Promise.allSettled([
        getStaffRequestsApi(accessToken),
        getStaffLoanOperationsApi(accessToken),
        getStaffSecuredProceduresApi(accessToken),
        getInformationVerificationsApi(accessToken, "PENDING"),
        getStaffPaymentConfirmationsApi(accessToken, "PENDING_REVIEW")
      ]);

      const toList = (result) => (result.status === "fulfilled" && Array.isArray(result.value) ? result.value : []);
      setQueues({
        review: toList(reviewResult),
        operations: toList(operationResult),
        secured: toList(securedResult),
        infoPending: toList(infoPendingResult),
        paymentPending: toList(paymentPendingResult)
      });
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
    const reviewRows = queues.review;
    const operationRows = queues.operations;
    const securedRows = queues.secured;
    const infoPendingRows = queues.infoPending;
    const paymentPendingRows = queues.paymentPending;
    const totalWorkItems =
      reviewRows.length +
      operationRows.length +
      infoPendingRows.length +
      paymentPendingRows.length;
    const totalReviewAmount = reviewRows.reduce((sum, row) => sum + Number(row.amount || 0), 0);
    const averageReviewAmount = reviewRows.length > 0 ? totalReviewAmount / reviewRows.length : 0;

    const recommendationRows = recommendationOrder.map((label) => ({
      label,
      count: reviewRows.filter((row) => row.dssRecommendation === label).length
    }));

    const riskRows = riskRankOrder.map((label) => ({
      label,
      count: reviewRows.filter((row) => row.riskRank === label).length
    }));

    const priorityRows = [...reviewRows]
      .filter((row) =>
        row.status === "PENDING" || row.status === "NEEDS_MORE_INFO" || row.dssRecommendation === "REJECT_RECOMMENDED"
      )
      .sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0))
      .slice(0, 6);

    const highValueRows = [...reviewRows]
      .sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0))
      .slice(0, 6);

    return {
      totalWorkItems,
      reviewQueue: reviewRows.length,
      operationQueue: operationRows.length,
      securedQueue: securedRows.length,
      infoPendingQueue: infoPendingRows.length,
      paymentPendingQueue: paymentPendingRows.length,
      overdueOperations: operationRows.filter((row) => row.status === "OVERDUE").length,
      totalReviewAmount,
      averageReviewAmount,
      recommendationRows,
      riskRows,
      priorityRows,
      highValueRows
    };
  }, [queues]);

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", sm: "center" }}>
        <Stack spacing={0.5}>
          <Typography variant="h4">Bảng điều khiển nhân viên</Typography>
          <Typography color="text.secondary">
            Theo dõi tổng khối lượng công việc ở thẩm định, vận hành khoản vay, xác minh thông tin và xác nhận thanh toán.
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
                title="Tổng mục công việc"
                value={dashboard.totalWorkItems}
                description="Tổng các việc cần xử lý trên tất cả queue nghiệp vụ của staff."
                color="#1976d2"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Hàng đợi thẩm định"
                value={dashboard.reviewQueue}
                description={`Bao gồm hồ sơ PENDING và NEEDS_MORE_INFO. Giá trị TB: ${formatVnd(dashboard.averageReviewAmount)}`}
                color="#ed6c02"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Vận hành khoản vay"
                value={dashboard.operationQueue}
                description={`Có ${dashboard.overdueOperations} khoản đang quá hạn; thủ tục thế chấp: ${dashboard.securedQueue}.`}
                color="#2e7d32"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Xác minh & thanh toán"
                value={dashboard.infoPendingQueue + dashboard.paymentPendingQueue}
                description={`Xác minh chờ xử lý: ${dashboard.infoPendingQueue}; biên lai chờ đối chiếu: ${dashboard.paymentPendingQueue}.`}
                color="#6a1b9a"
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="Phân bố khuyến nghị DSS (queue thẩm định)"
                rows={dashboard.recommendationRows}
                total={dashboard.reviewQueue}
                labelFormatter={labelDssRecommendation}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="Phân bố hạng rủi ro (queue thẩm định)"
                rows={dashboard.riskRows}
                total={dashboard.reviewQueue}
                labelFormatter={labelRiskRank}
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} lg={6}>
              <Paper sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Hồ sơ thẩm định ưu tiên</Typography>
                  {dashboard.priorityRows.length === 0 && (
                    <EmptyCard message="Hiện tại không có hồ sơ ưu tiên trong queue thẩm định." />
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
                                color={row.dssRecommendation === "REJECT_RECOMMENDED" ? "error" : "warning"}
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
                  <Typography variant="h6">Hồ sơ thẩm định có số tiền vay lớn nhất</Typography>
                  {dashboard.highValueRows.length === 0 && (
                    <EmptyCard message="Không tìm thấy hồ sơ trong queue thẩm định." />
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
