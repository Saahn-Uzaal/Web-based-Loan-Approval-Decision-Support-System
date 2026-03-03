import {
  Alert,
  Button,
  Chip,
  CircularProgress,
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
import { useCallback, useEffect, useMemo, useState } from "react";
import { getMyLoansApi } from "../../api/loanApi";
import { createPaymentApi, getMyPaymentsApi } from "../../api/paymentApi";
import { useAuth } from "../../auth/AuthContext";
import { formatVnd } from "../../utils/currency";

function ratingColor(rating) {
  if (rating >= 20) {
    return "success";
  }
  if (rating >= 0) {
    return "info";
  }
  return "error";
}

function statusColor(status) {
  return status === "ON_TIME" ? "success" : "error";
}

function calculateMonthlyDue(loan) {
  const amount = Number(loan?.amount);
  const termMonths = Number(loan?.termMonths);
  if (!Number.isFinite(amount) || !Number.isFinite(termMonths) || termMonths <= 0) {
    return "";
  }
  return (Math.round((amount / termMonths) * 100) / 100).toFixed(2);
}

function parseMoneyInput(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return Number.NaN;
  }
  return Math.round(parsed * 100) / 100;
}

export default function CustomerPaymentsPage() {
  const { accessToken } = useAuth();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [approvedLoans, setApprovedLoans] = useState([]);
  const [currentRating, setCurrentRating] = useState(0);
  const [payments, setPayments] = useState([]);
  const [form, setForm] = useState({
    loanRequestId: "",
    amountDue: "",
    amountPaid: "",
    dueDate: new Date().toISOString().slice(0, 10),
    note: ""
  });

  const loadData = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [loansResponse, paymentResponse] = await Promise.all([
        getMyLoansApi(accessToken),
        getMyPaymentsApi(accessToken)
      ]);

      const allLoans = Array.isArray(loansResponse) ? loansResponse : [];
      const approved = allLoans.filter((loan) => loan.status === "APPROVED");
      setApprovedLoans(approved);

      setPayments(Array.isArray(paymentResponse?.items) ? paymentResponse.items : []);
      setCurrentRating(Number(paymentResponse?.currentRating || 0));

      setForm((prev) => ({
        ...prev,
        loanRequestId: approved.some((loan) => String(loan.id) === prev.loanRequestId)
          ? prev.loanRequestId
          : (approved[0] ? String(approved[0].id) : "")
      }));
    } catch (err) {
      setError(err.message || "Failed to load payment data");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const loanMap = useMemo(() => {
    const map = new Map();
    approvedLoans.forEach((loan) => {
      map.set(String(loan.id), loan);
    });
    return map;
  }, [approvedLoans]);

  const selectedLoan = useMemo(
    () => approvedLoans.find((loan) => String(loan.id) === form.loanRequestId) || null,
    [approvedLoans, form.loanRequestId]
  );

  useEffect(() => {
    const nextAmountDue = selectedLoan ? calculateMonthlyDue(selectedLoan) : "";
    setForm((prev) => {
      if (prev.amountDue === nextAmountDue) {
        return prev;
      }
      return {
        ...prev,
        amountDue: nextAmountDue
      };
    });
  }, [selectedLoan]);

  const amountDueValue = parseMoneyInput(form.amountDue);
  const amountPaidValue = parseMoneyInput(form.amountPaid);
  const isOverpaid = (
    form.amountPaid !== "" &&
    Number.isFinite(amountDueValue) &&
    Number.isFinite(amountPaidValue) &&
    amountPaidValue > amountDueValue
  );

  const handleChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError("");
    setSubmitSuccess("");

    if (!Number.isFinite(amountDueValue) || amountDueValue <= 0) {
      setSubmitError("Amount due is invalid for the selected loan.");
      return;
    }

    if (!Number.isFinite(amountPaidValue) || amountPaidValue < 0) {
      setSubmitError("Please enter a valid amount paid.");
      return;
    }

    if (amountPaidValue > amountDueValue) {
      setSubmitError("Amount paid cannot be greater than amount due. Please do not overpay.");
      return;
    }

    setSubmitting(true);
    try {
      const payload = await createPaymentApi(accessToken, {
        loanRequestId: Number(form.loanRequestId),
        amountDue: amountDueValue,
        amountPaid: amountPaidValue,
        dueDate: form.dueDate,
        note: form.note.trim() || null
      });

      if (payload?.repayment) {
        setPayments((prev) => [payload.repayment, ...prev]);
      }
      setCurrentRating(Number(payload?.currentRating || 0));
      setSubmitSuccess(
        `Payment recorded as ${payload?.repayment?.repaymentStatus || "ON_TIME"}. Rating updated to ${payload?.currentRating}.`
      );
      setForm((prev) => ({
        ...prev,
        amountPaid: "",
        note: ""
      }));
    } catch (err) {
      setSubmitError(err.message || "Failed to record payment");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Payments And Rating</Typography>
      <Typography color="text.secondary">
        Amount due is auto-calculated as loan amount divided by term months.
        Paying exactly the due amount increases rating; paying less decreases rating.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading payment section...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && (
        <>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography variant="subtitle1">Current Payment Rating</Typography>
              <Chip
                label={currentRating}
                color={ratingColor(currentRating)}
                sx={{ minWidth: 72, justifyContent: "center" }}
              />
              <Typography variant="body2" color="text.secondary">
                Match amount due to increase rating. Paying less than due lowers rating.
              </Typography>
            </Stack>
          </Paper>

          {approvedLoans.length === 0 && (
            <Alert severity="info">
              You need at least one APPROVED loan request to record repayments.
            </Alert>
          )}

          <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
            <Stack spacing={2}>
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}

              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <TextField
                    select
                    label="Approved Loan"
                    value={form.loanRequestId}
                    onChange={handleChange("loanRequestId")}
                    fullWidth
                    required
                    disabled={submitting || approvedLoans.length === 0}
                  >
                    {approvedLoans.map((loan) => (
                      <MenuItem key={loan.id} value={String(loan.id)}>
                        #{loan.id} - {formatVnd(loan.amount)} ({loan.termMonths} months)
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Amount Due"
                    type="number"
                    value={form.amountDue}
                    fullWidth
                    required
                    InputProps={{ readOnly: true }}
                    helperText={selectedLoan ? "Auto-filled: total loan / term months" : ""}
                    disabled={submitting || approvedLoans.length === 0}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Amount Paid"
                    type="number"
                    value={form.amountPaid}
                    onChange={handleChange("amountPaid")}
                    fullWidth
                    required
                    error={isOverpaid}
                    helperText={isOverpaid ? "Amount paid cannot be greater than amount due." : ""}
                    inputProps={{ min: 0, step: "0.01" }}
                    disabled={submitting || approvedLoans.length === 0}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Due Date"
                    type="date"
                    value={form.dueDate}
                    onChange={handleChange("dueDate")}
                    fullWidth
                    required
                    InputLabelProps={{ shrink: true }}
                    disabled={submitting || approvedLoans.length === 0}
                  />
                </Grid>
                <Grid item xs={12} md={8}>
                  <TextField
                    label="Note"
                    value={form.note}
                    onChange={handleChange("note")}
                    fullWidth
                    placeholder="Optional note"
                    disabled={submitting || approvedLoans.length === 0}
                  />
                </Grid>
                <Grid item xs={12}>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={submitting || approvedLoans.length === 0}
                  >
                    {submitting ? "Recording..." : "Record Payment"}
                  </Button>
                </Grid>
              </Grid>
            </Stack>
          </Paper>

          <Paper sx={{ overflowX: "auto" }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Paid At</TableCell>
                  <TableCell>Loan</TableCell>
                  <TableCell>Due Date</TableCell>
                  <TableCell>Amount Due</TableCell>
                  <TableCell>Amount Paid</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Rating Delta</TableCell>
                  <TableCell>Note</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {payments.map((payment) => (
                  <TableRow key={payment.id} hover>
                    <TableCell>{payment.paidAt ? new Date(payment.paidAt).toLocaleString() : "-"}</TableCell>
                    <TableCell>
                      #{payment.loanRequestId}
                      {loanMap.get(String(payment.loanRequestId))
                        ? ` (${formatVnd(loanMap.get(String(payment.loanRequestId)).amount)})`
                        : ""}
                    </TableCell>
                    <TableCell>{payment.dueDate}</TableCell>
                    <TableCell>{formatVnd(payment.amountDue)}</TableCell>
                    <TableCell>{formatVnd(payment.amountPaid)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={payment.repaymentStatus}
                        color={statusColor(payment.repaymentStatus)}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={payment.ratingDelta > 0 ? `+${payment.ratingDelta}` : String(payment.ratingDelta)}
                        color={payment.ratingDelta >= 0 ? "success" : "error"}
                      />
                    </TableCell>
                    <TableCell>{payment.note || "-"}</TableCell>
                  </TableRow>
                ))}
                {payments.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8}>
                      <Typography variant="body2" color="text.secondary">
                        No payments recorded yet.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
    </Stack>
  );
}
