import { Alert, Chip, CircularProgress, Divider, Grid, Paper, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getLoanDetailApi } from "../../api/loanApi";
import { useAuth } from "../../auth/AuthContext";
import { formatVnd } from "../../utils/currency";

function KeyValue({ label, value }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      {typeof value === "string" ? <Typography>{value}</Typography> : value}
    </Stack>
  );
}

export default function CustomerLoanDetailPage() {
  const { id } = useParams();
  const { accessToken } = useAuth();
  const [loan, setLoan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadLoanDetail() {
      if (!accessToken || !id) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const detail = await getLoanDetailApi(accessToken, id);
        if (!active) {
          return;
        }
        setLoan(detail);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Failed to load loan detail");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadLoanDetail();
    return () => {
      active = false;
    };
  }, [accessToken, id]);

  const statusColorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Loan Request #{id}</Typography>
      <Typography color="text.secondary">
        Final decision and reason will be visible here for customers.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading request details...</Typography>
          </Stack>
        </Paper>
      )}
      {!loading && !loan && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Loan request not found.
          </Typography>
        </Paper>
      )}
      {loan && (
        <Paper sx={{ p: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Status"
                value={
                  <Chip
                    label={loan.status}
                    color={statusColorMap[loan.status] || "default"}
                    size="small"
                  />
                }
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Amount"
                value={formatVnd(loan.amount)}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Term" value={`${loan.termMonths} months`} />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Purpose" value={loan.purpose} />
            </Grid>
          </Grid>
          <Divider sx={{ my: 2 }} />
          <Stack spacing={1}>
            <Typography variant="subtitle2">Final Decision Reason</Typography>
            <Typography color="text.secondary">
              {loan.finalReason || "Waiting for staff review. Decision reason will be shown after finalization."}
            </Typography>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
