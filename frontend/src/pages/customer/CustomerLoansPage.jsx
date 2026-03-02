import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Paper,
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
import { getMyLoansApi } from "../../api/loanApi";
import { useAuth } from "../../auth/AuthContext";

function StatusChip({ status }) {
  const colorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return <Chip size="small" color={colorMap[status] || "default"} label={status} />;
}

export default function CustomerLoansPage() {
  const { accessToken } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadLoans() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getMyLoansApi(accessToken);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Failed to load loan requests");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadLoans();
    return () => {
      active = false;
    };
  }, [accessToken]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">My Loan Requests</Typography>
      <Typography color="text.secondary">
        Track statuses and review final decisions with reasons.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading loan requests...</Typography>
          </Stack>
        </Paper>
      )}
      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            No loan requests yet. Create your first request from "New Request".
          </Typography>
        </Paper>
      )}
      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Request ID</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Term</TableCell>
              <TableCell>Purpose</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>
                  ${Number(row.amount || 0).toLocaleString(undefined, { maximumFractionDigits: 2 })}
                </TableCell>
                <TableCell>{row.termMonths} months</TableCell>
                <TableCell>{row.purpose}</TableCell>
                <TableCell>
                  <StatusChip status={row.status} />
                </TableCell>
                <TableCell align="right">
                  <Button
                    component={RouterLink}
                    to={`/customer/loans/${row.id}`}
                    variant="outlined"
                    size="small"
                  >
                    View
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
