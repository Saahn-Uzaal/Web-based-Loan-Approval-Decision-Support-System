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
import { getMyLoansApi } from "@/features/customer/api/loanApi";
import { createPaymentApi, getMyPaymentsApi } from "@/features/customer/api/paymentApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { labelRepaymentStatus } from "@/shared/utils/labels";

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

function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
}

function calculateMonthlyDue(loanAmount, termMonths, remainingAmount) {
  const amount = Number(loanAmount);
  const months = Number(termMonths);
  const remaining = Number(remainingAmount);
  if (!Number.isFinite(amount) || !Number.isFinite(months) || months <= 0 || !Number.isFinite(remaining)) {
    return "";
  }
  const monthlyDue = Math.round(amount / months);
  return formatVndInput(Math.min(monthlyDue, Math.round(remaining)));
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
    } catch (err) {
      setError(err.message || "Không tải được dữ liệu thanh toán của bạn");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const paidByLoanMap = useMemo(() => {
    const map = new Map();
    payments.forEach((payment) => {
      const key = String(payment.loanRequestId);
      const current = map.get(key) || 0;
      map.set(key, roundMoney(current + Number(payment.amountPaid || 0)));
    });
    return map;
  }, [payments]);

  const approvedLoanBalances = useMemo(() => (
    approvedLoans.map((loan) => {
      const key = String(loan.id);
      const principal = roundMoney(Number(loan.amount || 0));
      const totalPaid = roundMoney(paidByLoanMap.get(key) || 0);
      const remainingAmount = Math.max(roundMoney(principal - totalPaid), 0);
      return {
        ...loan,
        principal,
        totalPaid,
        remainingAmount
      };
    })
  ), [approvedLoans, paidByLoanMap]);

  const payableLoans = useMemo(
    () => approvedLoanBalances.filter((loan) => loan.remainingAmount > 0),
    [approvedLoanBalances]
  );

  const loanMap = useMemo(() => {
    const map = new Map();
    approvedLoanBalances.forEach((loan) => {
      map.set(String(loan.id), loan);
    });
    return map;
  }, [approvedLoanBalances]);

  const selectedLoan = useMemo(
    () => payableLoans.find((loan) => String(loan.id) === form.loanRequestId) || null,
    [payableLoans, form.loanRequestId]
  );

  useEffect(() => {
    setForm((prev) => {
      const valid = payableLoans.some((loan) => String(loan.id) === prev.loanRequestId);
      const nextLoanId = valid ? prev.loanRequestId : (payableLoans[0] ? String(payableLoans[0].id) : "");
      if (nextLoanId === prev.loanRequestId) {
        return prev;
      }
      return {
        ...prev,
        loanRequestId: nextLoanId
      };
    });
  }, [payableLoans]);

  useEffect(() => {
    const nextAmountDue = selectedLoan
      ? calculateMonthlyDue(selectedLoan.amount, selectedLoan.termMonths, selectedLoan.remainingAmount)
      : "";
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

  const amountDueValue = parseVndInput(form.amountDue);
  const amountPaidValue = parseVndInput(form.amountPaid);
  const isInvalidAmount = (
    form.amountPaid !== "" &&
    (amountPaidValue == null || amountPaidValue < 0)
  );

  const remainingAfterPayment = selectedLoan && amountPaidValue != null
    ? Math.max(roundMoney(selectedLoan.remainingAmount - amountPaidValue), 0)
    : null;

  const remainingAfterPaymentById = useMemo(() => {
    const map = new Map();
    const groupedPayments = new Map();

    payments.forEach((payment) => {
      const key = String(payment.loanRequestId);
      if (!groupedPayments.has(key)) {
        groupedPayments.set(key, []);
      }
      groupedPayments.get(key).push(payment);
    });

    groupedPayments.forEach((items, loanId) => {
      const loan = loanMap.get(loanId);
      if (!loan) {
        return;
      }
      let remaining = roundMoney(loan.principal);
      const sorted = [...items].sort((a, b) => {
        const timeA = new Date(a.paidAt || a.createdAt || 0).getTime();
        const timeB = new Date(b.paidAt || b.createdAt || 0).getTime();
        if (timeA !== timeB) {
          return timeA - timeB;
        }
        return Number(a.id || 0) - Number(b.id || 0);
      });

      sorted.forEach((payment) => {
        remaining = Math.max(roundMoney(remaining - Number(payment.amountPaid || 0)), 0);
        map.set(String(payment.id), remaining);
      });
    });

    return map;
  }, [payments, loanMap]);

  const handleChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleMoneyChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: formatVndInput(event.target.value)
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError("");
    setSubmitSuccess("");

    if (!selectedLoan) {
      setSubmitError("Không còn khoản vay nào cần thanh toán.");
      return;
    }

    if (amountDueValue == null || amountDueValue <= 0) {
      setSubmitError("Số tiền đến hạn không phù hợp với khoản vay đã chọn.");
      return;
    }

    if (amountPaidValue == null || amountPaidValue < 0) {
      setSubmitError("Vui lòng nhập số tiền đã trả hợp lệ.");
      return;
    }

    const fullyPaid = amountPaidValue >= selectedLoan.remainingAmount;

    setSubmitting(true);
    try {
      const payload = await createPaymentApi(accessToken, {
        loanRequestId: Number(form.loanRequestId),
        amountPaid: amountPaidValue,
        dueDate: form.dueDate,
        note: form.note.trim() || null
      });

      if (payload?.repayment) {
        setPayments((prev) => [payload.repayment, ...prev]);
      }
      setCurrentRating(Number(payload?.currentRating || 0));
      setSubmitSuccess(
        fullyPaid
          ? `Đã ghi nhận thanh toán và tất toán khoản vay #${selectedLoan.id}. Khoản vay này đã được gỡ khỏi danh sách khoản vay đã duyệt.`
          : `Đã ghi nhận thanh toán với trạng thái ${labelRepaymentStatus(payload?.repayment?.repaymentStatus || "Đúng hạn")}. Điểm hiện tại: ${payload?.currentRating}.`
      );
      setForm((prev) => ({
        ...prev,
        amountPaid: "",
        note: ""
      }));
    } catch (err) {
      setSubmitError(err.message || "Không ghi nhận được thanh toán");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Thanh toán và điểm tín nhiệm</Typography>
      <Typography color="text.secondary">
        Hệ thống tự tính số tiền đến hạn và nợ còn lại theo từng khoản vay đã duyệt.
        Khi tất toán, khoản vay sẽ tự động được gỡ khỏi danh sách thanh toán.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải mục thanh toán...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && (
        <>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography variant="subtitle1">Điểm tín nhiệm thanh toán hiện tại</Typography>
              <Chip
                label={currentRating}
                color={ratingColor(currentRating)}
                sx={{ minWidth: 72, justifyContent: "center" }}
              />
              <Typography variant="body2" color="text.secondary">
                Trả đủ hoặc nhiều hơn để tăng điểm. Trả thiếu sẽ bị trừ điểm.
              </Typography>
            </Stack>
          </Paper>

          {approvedLoans.length === 0 && (
            <Alert severity="info">
              Bạn chưa có khoản vay nào ở trạng thái đã duyệt.
            </Alert>
          )}

          {approvedLoans.length > 0 && payableLoans.length === 0 && (
            <Alert severity="success">
              Tất cả khoản vay đã được tất toán. Bạn không còn khoản vay nào cần thanh toán.
            </Alert>
          )}

          <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
            <Stack spacing={2}>
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}

              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    label="Khoản vay đã duyệt"
                    value={form.loanRequestId}
                    onChange={handleChange("loanRequestId")}
                    fullWidth
                    required
                    disabled={submitting || payableLoans.length === 0}
                  >
                    {payableLoans.map((loan) => (
                      <MenuItem key={loan.id} value={String(loan.id)}>
                        #{loan.id} - {formatVnd(loan.amount)} - Còn lại: {formatVnd(loan.remainingAmount)}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>

                <Grid item xs={12} md={3}>
                  <TextField
                    label="Số tiền đến hạn"
                    type="text"
                    value={form.amountDue}
                    fullWidth
                    required
                    InputProps={{ readOnly: true }}
                    helperText={selectedLoan ? "Được tính tự động từ lịch trả và dư nợ" : ""}
                    inputProps={{ inputMode: "numeric" }}
                    disabled={submitting || payableLoans.length === 0}
                  />
                </Grid>

                <Grid item xs={12} md={3}>
                  <TextField
                    label="Nợ còn lại hiện tại"
                    type="text"
                    value={selectedLoan ? formatVndInput(selectedLoan.remainingAmount) : ""}
                    fullWidth
                    InputProps={{ readOnly: true }}
                    inputProps={{ inputMode: "numeric" }}
                    disabled={submitting || payableLoans.length === 0}
                  />
                </Grid>

                <Grid item xs={12} md={4}>
                  <TextField
                    label="Số tiền đã trả"
                    type="text"
                    value={form.amountPaid}
                    onChange={handleMoneyChange("amountPaid")}
                    fullWidth
                    required
                    error={isInvalidAmount}
                    helperText={
                      isInvalidAmount
                        ? "Số tiền đã trả phải lớn hơn hoặc bằng 0."
                        : remainingAfterPayment != null
                          ? `Nợ còn lại sau thanh toán: ${formatVnd(remainingAfterPayment)}`
                          : ""
                    }
                    inputProps={{ inputMode: "numeric" }}
                    disabled={submitting || payableLoans.length === 0}
                  />
                </Grid>

                <Grid item xs={12} md={4}>
                  <TextField
                    label="Ngày đến hạn"
                    type="date"
                    value={form.dueDate}
                    onChange={handleChange("dueDate")}
                    fullWidth
                    required
                    InputLabelProps={{ shrink: true }}
                    disabled={submitting || payableLoans.length === 0}
                  />
                </Grid>

                <Grid item xs={12} md={4}>
                  <TextField
                    label="Ghi chú"
                    value={form.note}
                    onChange={handleChange("note")}
                    fullWidth
                    placeholder="Ghi chú tùy chọn"
                    disabled={submitting || payableLoans.length === 0}
                  />
                </Grid>

                <Grid item xs={12}>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={submitting || payableLoans.length === 0}
                  >
                    {submitting ? "Đang ghi nhận..." : "Ghi nhận thanh toán"}
                  </Button>
                </Grid>
              </Grid>
            </Stack>
          </Paper>

          <Paper sx={{ overflowX: "auto" }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Thời điểm trả</TableCell>
                  <TableCell>Khoản vay</TableCell>
                  <TableCell>Ngày đến hạn</TableCell>
                  <TableCell>Số tiền đến hạn</TableCell>
                  <TableCell>Số tiền đã trả</TableCell>
                  <TableCell>Nợ còn lại</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Biến động điểm</TableCell>
                  <TableCell>Ghi chú</TableCell>
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
                      {remainingAfterPaymentById.has(String(payment.id))
                        ? formatVnd(remainingAfterPaymentById.get(String(payment.id)))
                        : "-"}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={labelRepaymentStatus(payment.repaymentStatus)}
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
                    <TableCell colSpan={9}>
                      <Typography variant="body2" color="text.secondary">
                        Chưa có lịch sử thanh toán.
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
