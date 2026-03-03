import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getStaffRequestDetailApi, submitStaffDecisionApi } from "../../api/staffApi";
import { useAuth } from "../../auth/AuthContext";
import { formatVnd } from "../../utils/currency";

function InfoCard({ title, children }) {
  return (
    <Paper sx={{ p: 2, height: "100%" }}>
      <Stack spacing={1}>
        <Typography variant="subtitle1">{title}</Typography>
        {children}
      </Stack>
    </Paper>
  );
}

function mapRecommendationToAction(recommendation) {
  if (recommendation === "APPROVE_RECOMMENDED") {
    return "APPROVE";
  }
  if (recommendation === "REJECT_RECOMMENDED") {
    return "REJECT";
  }
  return "ESCALATE";
}

export default function StaffRequestDetailPage() {
  const { id } = useParams();
  const { accessToken } = useAuth();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [decision, setDecision] = useState({
    action: "ESCALATE",
    reason: ""
  });

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      if (!accessToken || !id) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffRequestDetailApi(accessToken, id);
        if (!active) {
          return;
        }
        setDetail(response);
        setDecision((prev) => ({
          action: prev.reason ? prev.action : mapRecommendationToAction(response?.dss?.recommendation),
          reason: prev.reason
        }));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Failed to load request detail");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadDetail();
    return () => {
      active = false;
    };
  }, [accessToken, id]);

  const finalized = detail?.status === "APPROVED" || detail?.status === "REJECTED";

  const handleDecisionChange = (field) => (event) => {
    setDecision((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleSubmitDecision = async (event) => {
    event.preventDefault();
    if (!detail) {
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      await submitStaffDecisionApi(accessToken, detail.id, {
        action: decision.action,
        reason: decision.reason.trim()
      });
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess(`Decision submitted successfully. Current status: ${refreshed.status}.`);
      setDecision((prev) => ({
        ...prev,
        reason: ""
      }));
    } catch (err) {
      setSubmitError(err.message || "Failed to submit decision");
    } finally {
      setSubmitting(false);
    }
  };

  const statusColorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Request Review #{id}</Typography>
      <Typography color="text.secondary">
        Staff view: customer profile, loan details, DSS output, and final action.
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
      {!loading && !detail && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Loan request not found.
          </Typography>
        </Paper>
      )}
      {detail && (
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <InfoCard title="Customer Profile Summary">
            <Typography variant="body2">Customer ID: #{detail.customer?.id}</Typography>
            <Typography variant="body2">Email: {detail.customer?.email || "-"}</Typography>
            <Typography variant="body2">Full name: {detail.customerProfile?.fullName || "-"}</Typography>
            <Typography variant="body2">
              Income: {detail.customerProfile?.monthlyIncome != null ? `${formatVnd(detail.customerProfile.monthlyIncome)} / month` : "-"}
            </Typography>
            <Typography variant="body2">
              DTI: {detail.customerProfile?.debtToIncomeRatio != null ? `${detail.customerProfile.debtToIncomeRatio}%` : "-"}
            </Typography>
            <Typography variant="body2">
              Employment: {detail.customerProfile?.employmentStatus || "-"}
            </Typography>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Loan Details">
            <Typography variant="body2">
              Status: <Chip size="small" color={statusColorMap[detail.status] || "default"} label={detail.status} />
            </Typography>
            <Typography variant="body2">Amount: {formatVnd(detail.amount)}</Typography>
            <Typography variant="body2">Term: {detail.termMonths} months</Typography>
            <Typography variant="body2">Purpose: {detail.purpose}</Typography>
            <Typography variant="body2">Submitted: {new Date(detail.createdAt).toLocaleString()}</Typography>
            <Typography variant="body2">Final reason: {detail.finalReason || "-"}</Typography>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={7}>
          <InfoCard title="DSS Output">
            {!detail.dss && (
              <Alert severity="warning">
                No DSS snapshot found for this request.
              </Alert>
            )}
            {detail.dss && (
              <>
                <Typography variant="body2">Credit score: {detail.dss.creditScore}</Typography>
                <Typography variant="body2">Risk rating: {detail.dss.riskRank}</Typography>
                <Typography variant="body2">Segment: {detail.dss.customerSegment}</Typography>
                <Typography variant="body2">Recommendation: {detail.dss.recommendation}</Typography>
                <Alert severity="info" sx={{ mt: 1 }}>
                  {detail.dss.explanation}
                </Alert>
              </>
            )}
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={5}>
          <InfoCard title="Final Decision">
            <Stack spacing={2} component="form" onSubmit={handleSubmitDecision}>
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
              {finalized && (
                <Alert severity="info">
                  This request is finalized. Additional decisions are disabled.
                </Alert>
              )}
              <TextField
                select
                label="Action"
                value={decision.action}
                onChange={handleDecisionChange("action")}
                disabled={submitting || finalized}
              >
                <MenuItem value="APPROVE">APPROVE</MenuItem>
                <MenuItem value="REJECT">REJECT</MenuItem>
                <MenuItem value="ESCALATE">ESCALATE</MenuItem>
              </TextField>
              <TextField
                label="Reason"
                multiline
                rows={4}
                required
                value={decision.reason}
                onChange={handleDecisionChange("reason")}
                disabled={submitting || finalized}
                placeholder="Reason is mandatory for final action."
              />
              <Button type="submit" variant="contained" disabled={submitting || finalized}>
                {submitting ? "Submitting..." : "Submit Decision"}
              </Button>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid item xs={12}>
          <InfoCard title="Decision Audit History">
            {!detail.decisionAudits?.length && (
              <Typography variant="body2" color="text.secondary">
                No decisions recorded yet.
              </Typography>
            )}
            {detail.decisionAudits?.length > 0 && (
              <Paper variant="outlined" sx={{ overflowX: "auto" }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Time</TableCell>
                      <TableCell>Staff</TableCell>
                      <TableCell>Action</TableCell>
                      <TableCell>Reason</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.decisionAudits.map((audit) => (
                      <TableRow key={audit.id}>
                        <TableCell>{new Date(audit.createdAt).toLocaleString()}</TableCell>
                        <TableCell>{audit.staffEmail}</TableCell>
                        <TableCell>{audit.action}</TableCell>
                        <TableCell>{audit.reason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            )}
          </InfoCard>
        </Grid>
      </Grid>
      )}
      <Divider />
    </Stack>
  );
}
