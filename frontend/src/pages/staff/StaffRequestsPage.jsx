import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { getStaffRequestsApi } from "../../api/staffApi";
import { useAuth } from "../../auth/AuthContext";
import { formatVnd } from "../../utils/currency";

function StatusChip({ status }) {
  const colorMap = {
    PENDING: "warning",
    WAITING_SUPERVISOR: "info",
    APPROVED: "success",
    REJECTED: "error"
  };

  return <Chip size="small" label={status} color={colorMap[status] || "default"} />;
}

export default function StaffRequestsPage() {
  const { accessToken } = useAuth();
  const [status, setStatus] = useState("PENDING");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadQueue() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffRequestsApi(accessToken, status);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Failed to load review queue");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadQueue();
    return () => {
      active = false;
    };
  }, [accessToken, status]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Staff Review Queue</Typography>
      <Typography color="text.secondary">
        Review incoming requests and DSS recommendations.
      </Typography>
      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="status-filter-label">Status filter</InputLabel>
          <Select
            labelId="status-filter-label"
            value={status}
            label="Status filter"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="PENDING">PENDING</MenuItem>
            <MenuItem value="WAITING_SUPERVISOR">WAITING_SUPERVISOR</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading review queue...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            No requests found for status {status}.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Request ID</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>DSS</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>{row.customerName || row.customerEmail}</TableCell>
                <TableCell>{formatVnd(row.amount)}</TableCell>
                <TableCell>
                  {row.dssRecommendation || "-"}
                </TableCell>
                <TableCell>
                  <StatusChip status={row.status} />
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
      </Paper>
    </Stack>
  );
}
