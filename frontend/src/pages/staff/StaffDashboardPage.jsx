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
import { getStaffRequestsApi } from "../../api/staffApi";
import { useAuth } from "../../auth/AuthContext";
import { formatVnd } from "../../utils/currency";

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

function DistributionSection({ title, rows, total }) {
  return (
    <Paper sx={{ p: 3, height: "100%" }}>
      <Stack spacing={2}>
        <Typography variant="h6">{title}</Typography>
        {rows.map((item) => {
          const percent = total > 0 ? Math.round((item.count * 100) / total) : 0;
          return (
            <Stack key={item.label} spacing={0.5}>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2">{item.label}</Typography>
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
      setError(err.message || "Failed to load dashboard metrics");
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
          <Typography variant="h4">Staff Dashboard</Typography>
          <Typography color="text.secondary">
            Live operational view for review queue, DSS signals, and urgent cases.
          </Typography>
          {lastUpdatedAt && (
            <Typography variant="caption" color="text.secondary">
              Last updated: {lastUpdatedAt.toLocaleString()}
            </Typography>
          )}
        </Stack>
        <Button variant="outlined" onClick={loadDashboard} disabled={loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </Button>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading dashboard...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Queue Size"
                value={dashboard.totalQueue}
                description="Total requests currently in staff queue."
                color="#1976d2"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Pending"
                value={dashboard.pending}
                description="Requests waiting for first review action."
                color="#ed6c02"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Waiting Supervisor"
                value={dashboard.waitingSupervisor}
                description="Requests escalated for supervisor review."
                color="#0288d1"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={3}>
              <MetricCard
                title="Queue Amount"
                value={formatVnd(dashboard.totalAmount)}
                description={`Average ticket: ${formatVnd(dashboard.averageAmount)}`}
                color="#2e7d32"
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="DSS Recommendation Distribution"
                rows={dashboard.recommendationRows}
                total={dashboard.totalQueue}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <DistributionSection
                title="Risk Rank Distribution"
                rows={dashboard.riskRows}
                total={dashboard.totalQueue}
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} lg={6}>
              <Paper sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Priority Cases</Typography>
                  {dashboard.priorityRows.length === 0 && (
                    <EmptyCard message="No urgent cases right now." />
                  )}
                  {dashboard.priorityRows.length > 0 && (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>ID</TableCell>
                          <TableCell>Customer</TableCell>
                          <TableCell>Amount</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell align="right">Action</TableCell>
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
                                label={row.status}
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
                                Review
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
                  <Typography variant="h6">Top Loan Amounts In Queue</Typography>
                  {dashboard.highValueRows.length === 0 && (
                    <EmptyCard message="No requests found in queue." />
                  )}
                  {dashboard.highValueRows.length > 0 && (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>ID</TableCell>
                          <TableCell>Customer</TableCell>
                          <TableCell>Amount</TableCell>
                          <TableCell>DSS</TableCell>
                          <TableCell align="right">Action</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {dashboard.highValueRows.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>#{row.id}</TableCell>
                            <TableCell>{row.customerName || row.customerEmail}</TableCell>
                            <TableCell>{formatVnd(row.amount)}</TableCell>
                            <TableCell>{row.dssRecommendation || "-"}</TableCell>
                            <TableCell align="right">
                              <Button
                                component={RouterLink}
                                to={`/staff/requests/${row.id}`}
                                variant="outlined"
                                size="small"
                              >
                                Review
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
