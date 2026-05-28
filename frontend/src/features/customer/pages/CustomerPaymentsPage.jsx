import {
  Alert,
  Box,
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
  TablePagination,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getMyLoansApi } from "@/features/customer/api/loanApi";
import {
  createPaymentConfirmationApi,
  cancelPaymentConfirmationApi,
  downloadPaymentProofApi,
  getMyPaymentsPagedApi,
  replacePaymentConfirmationApi
} from "@/features/customer/api/paymentApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { formatFileSize, isAcceptedPaymentProofFile, PAYMENT_PROOF_ACCEPT } from "@/shared/utils/files";
import {
  labelPaymentConfirmationStatus,
  labelRepaymentStatus
} from "@/shared/utils/labels";
import ConfirmDialog from "@/shared/components/ConfirmDialog";

function ratingColor(rating) {
  if (rating >= 20) {
    return "success";
  }
  if (rating >= 0) {
    return "info";
  }
  return "error";
}

function confirmationColor(status) {
  if (status === "CONFIRMED") {
    return "success";
  }
  if (status === "REJECTED") {
    return "error";
  }
  if (status === "CANCELLED_BY_CUSTOMER") {
    return "default";
  }
  return "warning";
}

function repaymentColor(status) {
  if (status === "EARLY") {
    return "success";
  }
  if (status === "ON_TIME") {
    return "info";
  }
  return "error";
}

function dueStateLabel(loan) {
  if (!loan?.nextDueDate) {
    return "-";
  }
  if (loan.nextPaymentOverdue) {
    const days = Number(loan.nextPaymentOverdueDays || 0);
    return days > 0 ? `Trễ hạn ${days} ngày` : "Trễ hạn";
  }
  return "Chưa quá hạn";
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("vi-VN");
}

const paymentFieldKeywords = {
  loanRequestId: ["khoản vay", "loan request", "loanRequestId"],
  proof: ["biên lai", "chứng từ", "proof", "file"],
  note: ["ghi chú", "note"]
};

const PAYMENT_RECIPIENT = Object.freeze({
  bankName: (import.meta.env.VITE_PAYMENT_BANK_NAME || "").trim(),
  bankId: (import.meta.env.VITE_PAYMENT_BANK_ID || "").trim(),
  accountNo: (import.meta.env.VITE_PAYMENT_ACCOUNT_NO || "").trim(),
  accountName: (import.meta.env.VITE_PAYMENT_ACCOUNT_NAME || "").trim(),
  qrTemplate: (import.meta.env.VITE_PAYMENT_QR_TEMPLATE || "compact2").trim() || "compact2",
  transferPrefix: (import.meta.env.VITE_PAYMENT_TRANSFER_PREFIX || "THANHTOAN").trim() || "THANHTOAN"
});

function normalizeAccountNumber(value) {
  return String(value || "").replace(/\s+/g, "");
}

function formatAccountNumber(value) {
  const normalized = normalizeAccountNumber(value);
  if (!normalized) {
    return "";
  }
  return normalized.replace(/(.{4})/g, "$1 ").trim();
}

function buildTransferReference(loan) {
  if (!loan) {
    return "";
  }
  const dueMonth = loan.nextDueDate ? String(loan.nextDueDate).slice(0, 7) : new Date().toISOString().slice(0, 7);
  const loanToken = loan.id != null ? `KH${loan.id}` : "KH";
  const installmentToken = loan.nextInstallmentNumber != null ? `KY${loan.nextInstallmentNumber}` : "KY";
  return [PAYMENT_RECIPIENT.transferPrefix, loanToken, installmentToken, dueMonth].join(" ");
}

function buildVietQrUrl({ bankId, accountNo, accountName, amount, addInfo, template }) {
  const normalizedAccountNumber = normalizeAccountNumber(accountNo);
  if (!bankId || !normalizedAccountNumber || !accountName) {
    return "";
  }

  const query = new URLSearchParams();
  if (Number(amount) > 0) {
    query.set("amount", String(Math.round(Number(amount))));
  }
  if (addInfo) {
    query.set("addInfo", addInfo);
  }
  query.set("accountName", accountName);

  return `https://img.vietqr.io/image/${encodeURIComponent(bankId)}-${encodeURIComponent(normalizedAccountNumber)}-${encodeURIComponent(template)}.png?${query.toString()}`;
}

export default function CustomerPaymentsPage() {
  const { accessToken } = useAuth();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [loans, setLoans] = useState([]);
  const [payments, setPayments] = useState([]);
  const [confirmationRequests, setConfirmationRequests] = useState([]);
  const [currentRating, setCurrentRating] = useState(0);
  const [fieldErrors, setFieldErrors] = useState({});
  const [proofInputKey, setProofInputKey] = useState(0);
  const [confirmationToCancel, setConfirmationToCancel] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentRowsPerPage, setPaymentRowsPerPage] = useState(10);
  const [paymentTotalRows, setPaymentTotalRows] = useState(0);
  const [form, setForm] = useState({
    loanRequestId: "",
    note: "",
    proof: null
  });

  const loadData = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    setFieldErrors({});
    try {
      const [loansResponse, paymentResponse] = await Promise.all([
        getMyLoansApi(accessToken),
        getMyPaymentsPagedApi(accessToken, {
          page: paymentPage,
          size: paymentRowsPerPage
        })
      ]);

      setLoans(Array.isArray(loansResponse) ? loansResponse : []);
      setPayments(Array.isArray(paymentResponse?.items) ? paymentResponse.items : []);
      setConfirmationRequests(Array.isArray(paymentResponse?.confirmationRequests) ? paymentResponse.confirmationRequests : []);
      setCurrentRating(Number(paymentResponse?.currentRating || 0));
      setPaymentTotalRows(Number(paymentResponse?.totalElements || 0));
    } catch (err) {
      setError(err.message || "Không tải được dữ liệu thanh toán");
    } finally {
      setLoading(false);
    }
  }, [accessToken, paymentPage, paymentRowsPerPage]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const repaymentLoans = useMemo(
    () =>
      loans.filter(
        (loan) =>
          loan.status === "ACTIVE" || loan.status === "OVERDUE" || loan.status === "CLOSED"
      ),
    [loans]
  );

  const payableLoans = useMemo(
    () =>
      repaymentLoans.filter(
        (loan) =>
          (loan.status === "ACTIVE" || loan.status === "OVERDUE")
          && Number(loan.remainingRepayableAmount || 0) > 0
      ),
    [repaymentLoans]
  );

  const loanMap = useMemo(() => {
    const map = new Map();
    repaymentLoans.forEach((loan) => {
      map.set(String(loan.id), loan);
    });
    return map;
  }, [repaymentLoans]);

  const pendingConfirmationMap = useMemo(() => {
    const map = new Map();
    confirmationRequests
      .filter((confirmation) => confirmation.status === "PENDING_REVIEW")
      .forEach((confirmation) => {
        map.set(String(confirmation.loanRequestId), confirmation);
      });
    return map;
  }, [confirmationRequests]);

  useEffect(() => {
    setForm((prev) => {
      const stillValid = payableLoans.some((loan) => String(loan.id) === prev.loanRequestId);
      const nextLoanId = stillValid ? prev.loanRequestId : payableLoans[0] ? String(payableLoans[0].id) : "";
      if (nextLoanId === prev.loanRequestId) {
        return prev;
      }
      return {
        ...prev,
        loanRequestId: nextLoanId
      };
    });
  }, [payableLoans]);

  const selectedLoan = useMemo(
    () => payableLoans.find((loan) => String(loan.id) === form.loanRequestId) || null,
    [form.loanRequestId, payableLoans]
  );

  const selectedPendingConfirmation = useMemo(
    () => (selectedLoan ? pendingConfirmationMap.get(String(selectedLoan.id)) || null : null),
    [pendingConfirmationMap, selectedLoan]
  );

  const transferReference = useMemo(
    () => buildTransferReference(selectedLoan),
    [selectedLoan]
  );

  const paymentQrUrl = useMemo(
    () =>
      selectedLoan
        ? buildVietQrUrl({
            bankId: PAYMENT_RECIPIENT.bankId,
            accountNo: PAYMENT_RECIPIENT.accountNo,
            accountName: PAYMENT_RECIPIENT.accountName,
            amount: selectedLoan.nextAmountDue,
            addInfo: transferReference,
            template: PAYMENT_RECIPIENT.qrTemplate
          })
        : "",
    [selectedLoan, transferReference]
  );

  const paymentRecipientConfigured = Boolean(
    PAYMENT_RECIPIENT.bankName
    && PAYMENT_RECIPIENT.bankId
    && PAYMENT_RECIPIENT.accountNo
    && PAYMENT_RECIPIENT.accountName
  );

  const selectedLoanHasPendingConfirmation = useMemo(
    () => Boolean(selectedPendingConfirmation),
    [selectedPendingConfirmation]
  );

  const busy = submitting || cancellingId != null;

  const resetProofSelection = () => {
    setForm((prev) => ({
      ...prev,
      proof: null
    }));
    setProofInputKey((prev) => prev + 1);
  };

  const handleChange = (field) => (event) => {
    setFieldErrors((prev) => clearFieldError(prev, field));
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleProofChange = (event) => {
    setFieldErrors((prev) => clearFieldError(prev, "proof"));
    const file = event.target.files?.[0] || null;
    if (!file) {
      resetProofSelection();
      return;
    }
    if (!isAcceptedPaymentProofFile(file)) {
      const message = "Biên lai chuyển khoản phải là ảnh JPG, JPEG, PNG hoặc WEBP.";
      setSubmitError(message);
      setFieldErrors({ proof: message });
      event.target.value = "";
      return;
    }
    setSubmitError("");
    setForm((prev) => ({
      ...prev,
      proof: file
    }));
  };

  const handleDownloadProof = async (confirmation) => {
    try {
      await downloadPaymentProofApi(accessToken, confirmation.id, confirmation.proofFileName);
    } catch (err) {
      setError(err.message || "Không tải được biên lai chuyển khoản");
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError("");
    setFieldErrors({});
    setSubmitSuccess("");
    const isReplacingPendingConfirmation = Boolean(selectedPendingConfirmation);

    if (!selectedLoan) {
      const message = "Không còn khoản vay nào đang chờ xác nhận thanh toán.";
      setSubmitError(message);
      setFieldErrors({ loanRequestId: message });
      return;
    }
    if (!form.proof) {
      const message = isReplacingPendingConfirmation
        ? "Vui lòng tải lên biên lai mới trước khi thay biên lai đang chờ."
        : "Vui lòng tải lên biên lai chuyển khoản trước khi gửi xác nhận.";
      setSubmitError(message);
      setFieldErrors({ proof: message });
      return;
    }

    setSubmitting(true);
    try {
      if (isReplacingPendingConfirmation) {
        await replacePaymentConfirmationApi(accessToken, selectedPendingConfirmation.id, {
          note: form.note.trim(),
          proof: form.proof
        });
      } else {
        await createPaymentConfirmationApi(accessToken, {
          loanRequestId: Number(form.loanRequestId),
          note: form.note.trim(),
          proof: form.proof
        });
      }
      await loadData();
      setSubmitSuccess(isReplacingPendingConfirmation
        ? `Đã thay biên lai chờ đối chiếu cho khoản vay #${selectedLoan.id}. Nhân viên sẽ đối chiếu biên lai mới.`
        : `Đã gửi biên lai chuyển khoản cho khoản vay #${selectedLoan.id}. Nhân viên sẽ đối chiếu và xác nhận kết quả thanh toán.`
      );
      setForm((prev) => ({
        ...prev,
        note: "",
        proof: null
      }));
      setProofInputKey((prev) => prev + 1);
    } catch (err) {
      const message = err.message || "Không gửi được yêu cầu xác nhận thanh toán";
      setSubmitError(message);
      setFieldErrors(mapFieldErrors(message, paymentFieldKeywords));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelPendingConfirmation = async () => {
    const confirmation = confirmationToCancel;
    setConfirmationToCancel(null);
    if (!confirmation) {
      return;
    }

    setCancellingId(confirmation.id);
    setSubmitError("");
    setSubmitSuccess("");
    setError("");
    try {
      await cancelPaymentConfirmationApi(accessToken, confirmation.id);
      await loadData();
      if (String(confirmation.loanRequestId) === form.loanRequestId) {
        setForm((prev) => ({
          ...prev,
          note: "",
          proof: null
        }));
        setProofInputKey((prev) => prev + 1);
      }
      setSubmitSuccess(
        `Đã hủy biên lai chờ đối chiếu của khoản vay #${confirmation.loanRequestId}. Bạn có thể gửi lại biên lai mới ngay bây giờ.`
      );
    } catch (err) {
      setError(err.message || "Không hủy được yêu cầu xác nhận thanh toán");
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Thanh toán và điểm tín nhiệm</Typography>
      <Typography color="text.secondary">
        Khách hàng không tự ghi nhận đã thanh toán. Bạn chỉ gửi biên lai chuyển khoản; nhân viên sẽ đối chiếu số tiền,
        thời điểm giao dịch và xác nhận kết quả trả sớm, đúng hạn hoặc trễ hạn.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải dữ liệu thanh toán...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && (
        <>
          <Paper sx={{ p: 2 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                <Typography variant="subtitle1">Điểm tín nhiệm thanh toán hiện tại</Typography>
                <Chip label={currentRating} color={ratingColor(currentRating)} sx={{ minWidth: 72, justifyContent: "center" }} />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Điểm chỉ thay đổi sau khi nhân viên xác nhận biên lai hợp lệ và hệ thống xác định giao dịch đó là trả sớm, đúng hạn hoặc trễ hạn.
              </Typography>
            </Stack>
          </Paper>

          {repaymentLoans.length === 0 && (
            <Alert severity="info">Bạn chưa có khoản vay nào đang hoạt động để gửi xác nhận thanh toán.</Alert>
          )}

          <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Alert severity="info">
                Có thể gửi biên lai thanh toán một phần hoặc trả đủ cho kỳ hiện tại, hoặc tất toán toàn bộ dư nợ còn lại.
                Hệ thống chưa hỗ trợ trả trước một phần cho nhiều kỳ tương lai trong một lần đối chiếu.
              </Alert>
              {selectedLoanHasPendingConfirmation && (
                <Alert severity="warning">
                  Khoản vay đang chọn đang có biên lai <strong>{selectedPendingConfirmation.proofFileName}</strong> gửi lúc{" "}
                  {formatDateTime(selectedPendingConfirmation.createdAt)} chờ nhân viên đối chiếu. Bạn có thể hủy yêu cầu này
                  hoặc tải biên lai mới để thay thế ngay.
                </Alert>
              )}

              {selectedLoan?.nextPaymentOverdue && (
                <Alert severity="warning">
                  Kỳ thanh toán hiện tại đã quá hạn {Number(selectedLoan.nextPaymentOverdueDays || 0)} ngày. Hệ thống chỉ clear quá hạn khi biên lai được xác nhận đủ phần còn thiếu của kỳ.
                </Alert>
              )}

              {paymentRecipientConfigured ? (
                <Paper
                  variant="outlined"
                  sx={{
                    p: 2,
                    bgcolor: "#f8fbff",
                    borderColor: "primary.light"
                  }}
                >
                  <Grid container spacing={2} alignItems="stretch">
                    <Grid item xs={12} lg={7}>
                      <Stack spacing={1.5}>
                        <Stack spacing={0.5}>
                          <Typography variant="h6">Thông tin chuyển khoản</Typography>
                          <Typography variant="body2" color="text.secondary">
                            Quét QR để điền sẵn tài khoản nhận tiền, nội dung chuyển khoản và số tiền kỳ hiện tại.
                          </Typography>
                        </Stack>
                        <Grid container spacing={1.5}>
                          <Grid item xs={12} md={6}>
                            <TextField
                              label="Tên ngân hàng"
                              value={PAYMENT_RECIPIENT.bankName}
                              fullWidth
                              InputProps={{ readOnly: true }}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              label="Số tài khoản"
                              value={formatAccountNumber(PAYMENT_RECIPIENT.accountNo)}
                              fullWidth
                              InputProps={{ readOnly: true }}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <TextField
                              label="Tên người thụ hưởng / tên công ty"
                              value={PAYMENT_RECIPIENT.accountName}
                              fullWidth
                              InputProps={{ readOnly: true }}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <TextField
                              label="Nội dung chuyển khoản mẫu"
                              value={transferReference}
                              fullWidth
                              InputProps={{ readOnly: true }}
                              placeholder="Chọn khoản vay để hệ thống tạo nội dung chuyển khoản"
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <TextField
                              label="Số tiền cần trả kỳ này"
                              value={selectedLoan ? formatVnd(selectedLoan.nextAmountDue) : ""}
                              fullWidth
                              InputProps={{ readOnly: true }}
                              placeholder="Chọn khoản vay để hiển thị số tiền"
                            />
                          </Grid>
                        </Grid>
                        <Alert severity="info">
                          QR đang điền sẵn số tiền đến hạn của kỳ hiện tại. Nếu bạn muốn trả số tiền khác như trả một phần hoặc tất toán,
                          hãy chuyển khoản thủ công nhưng vẫn giữ nguyên nội dung chuyển khoản mẫu để nhân viên đối chiếu nhanh.
                        </Alert>
                      </Stack>
                    </Grid>
                    <Grid item xs={12} lg={5}>
                      <Stack
                        spacing={1}
                        alignItems="center"
                        justifyContent="center"
                        sx={{ height: "100%" }}
                      >
                        {paymentQrUrl ? (
                          <Box
                            component="img"
                            src={paymentQrUrl}
                            alt={`QR thanh toán cho khoản vay #${selectedLoan?.id || ""}`}
                            sx={{
                              width: "100%",
                              maxWidth: 320,
                              borderRadius: 3,
                              border: "1px solid",
                              borderColor: "divider",
                              bgcolor: "#fff",
                              boxShadow: 1
                            }}
                          />
                        ) : (
                          <Alert severity="info" sx={{ width: "100%" }}>
                            Chọn khoản vay cần thanh toán để hiện mã QR chuyển khoản.
                          </Alert>
                        )}
                        <Typography variant="caption" color="text.secondary" textAlign="center">
                          QR được tạo theo chuẩn VietQR với tài khoản nhận tiền, số tiền kỳ này và nội dung chuyển khoản mẫu.
                        </Typography>
                      </Stack>
                    </Grid>
                  </Grid>
                </Paper>
              ) : (
                <Alert severity="warning">
                  Chưa cấu hình thông tin tài khoản nhận tiền cho màn thanh toán. Hãy bổ sung các biến môi trường `VITE_PAYMENT_*`
                  trước khi dùng tính năng này.
                </Alert>
              )}

              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}

              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    label="Khoản vay cần gửi biên lai"
                    value={form.loanRequestId}
                    onChange={handleChange("loanRequestId")}
                    fullWidth
                    required
                    disabled={busy || payableLoans.length === 0}
                    {...fieldErrorProps(fieldErrors, "loanRequestId")}
                  >
                    {payableLoans.map((loan) => (
                      <MenuItem key={loan.id} value={String(loan.id)}>
                        #{loan.id} - Đến hạn: {formatVnd(loan.nextAmountDue)} - Còn lại: {formatVnd(loan.remainingRepayableAmount)}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} md={3}>
                  <TextField
                    label="Số tiền đến hạn kỳ này"
                    value={selectedLoan ? formatVnd(selectedLoan.nextAmountDue) : ""}
                    fullWidth
                    InputProps={{ readOnly: true }}
                  />
                </Grid>
                <Grid item xs={12} md={3}>
                  <TextField
                    label="Dư nợ còn lại"
                    value={selectedLoan ? formatVnd(selectedLoan.remainingRepayableAmount) : ""}
                    fullWidth
                    InputProps={{ readOnly: true }}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Kỳ đang chờ thanh toán"
                    value={selectedLoan?.nextInstallmentNumber ? `Kỳ #${selectedLoan.nextInstallmentNumber}` : ""}
                    fullWidth
                    InputProps={{ readOnly: true }}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    label="Ngày đến hạn"
                    value={selectedLoan?.nextDueDate || ""}
                    fullWidth
                    color={selectedLoan?.nextPaymentOverdue ? "warning" : "primary"}
                    helperText={selectedLoan ? dueStateLabel(selectedLoan) : " "}
                    InputProps={{ readOnly: true }}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <Button
                    variant="outlined"
                    component="label"
                    fullWidth
                    color={fieldErrors.proof ? "error" : "primary"}
                    disabled={busy || payableLoans.length === 0}
                    sx={{ height: 56, borderWidth: fieldErrors.proof ? 2 : 1 }}
                  >
                    {form.proof ? "Đổi ảnh biên lai" : selectedLoanHasPendingConfirmation ? "Tải biên lai mới để thay" : "Tải lên ảnh biên lai"}
                    <input
                      key={proofInputKey}
                      hidden
                      type="file"
                      accept={PAYMENT_PROOF_ACCEPT}
                      onChange={handleProofChange}
                    />
                  </Button>
                  {fieldErrors.proof && (
                    <Typography variant="caption" color="error" sx={{ display: "block", mt: 0.75 }}>
                      {fieldErrors.proof}
                    </Typography>
                  )}
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    label="Ghi chú của khách hàng"
                    value={form.note}
                    onChange={handleChange("note")}
                    fullWidth
                    placeholder="Ví dụ: chuyển khoản qua ứng dụng ngân hàng lúc sáng nay"
                    disabled={busy || payableLoans.length === 0}
                    {...fieldErrorProps(fieldErrors, "note")}
                  />
                  {form.proof && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                      Đã chọn: {form.proof.name} ({formatFileSize(form.proof.size)})
                    </Typography>
                  )}
                </Grid>
                <Grid item xs={12}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                    <Button
                      type="submit"
                      variant="contained"
                      disabled={busy || payableLoans.length === 0}
                    >
                      {submitting
                        ? selectedLoanHasPendingConfirmation
                          ? "Đang thay..."
                          : "Đang gửi..."
                        : selectedLoanHasPendingConfirmation
                          ? "Thay biên lai đang chờ"
                          : "Gửi xác nhận thanh toán"}
                    </Button>
                    {selectedPendingConfirmation && (
                      <Button
                        variant="text"
                        color="warning"
                        disabled={busy}
                        onClick={() => setConfirmationToCancel(selectedPendingConfirmation)}
                      >
                        Hủy biên lai đang chờ
                      </Button>
                    )}
                  </Stack>
                </Grid>
              </Grid>
            </Stack>
          </Paper>

          <Paper sx={{ overflowX: "auto" }}>
            <Stack sx={{ p: 2, pb: 0 }}>
              <Typography variant="h6">Yêu cầu xác nhận thanh toán</Typography>
              <Typography variant="body2" color="text.secondary">
                Đây là các biên lai bạn đã gửi để nhân viên đối chiếu.
              </Typography>
            </Stack>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Thời điểm gửi</TableCell>
                  <TableCell>Khoản vay</TableCell>
                  <TableCell>Kỳ / ngày đến hạn</TableCell>
                  <TableCell>Số tiền kỳ này</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Kết quả</TableCell>
                  <TableCell>Biên lai</TableCell>
                  <TableCell>Ghi chú</TableCell>
                  <TableCell>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {confirmationRequests.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9} align="center">
                      Chưa có yêu cầu xác nhận thanh toán nào.
                    </TableCell>
                  </TableRow>
                )}
                {confirmationRequests.map((confirmation) => (
                  <TableRow key={confirmation.id} hover>
                    <TableCell>{formatDateTime(confirmation.createdAt)}</TableCell>
                    <TableCell>
                      #{confirmation.loanRequestId}
                      {loanMap.get(String(confirmation.loanRequestId))
                        ? ` (${formatVnd(loanMap.get(String(confirmation.loanRequestId)).remainingRepayableAmount)})`
                        : ""}
                    </TableCell>
                    <TableCell>
                      Kỳ #{confirmation.expectedInstallmentNumber} / {confirmation.expectedDueDate}
                    </TableCell>
                    <TableCell>{formatVnd(confirmation.expectedAmountDue)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={confirmationColor(confirmation.status)}
                        label={labelPaymentConfirmationStatus(confirmation.status)}
                      />
                    </TableCell>
                    <TableCell>
                      {confirmation.status === "CONFIRMED" ? (
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                          <Chip
                            size="small"
                            color={repaymentColor(confirmation.repaymentStatus)}
                            label={labelRepaymentStatus(confirmation.repaymentStatus)}
                          />
                          <Typography variant="body2">
                            {confirmation.ratingDelta == null
                              ? "-"
                              : confirmation.ratingDelta > 0
                                ? `+${confirmation.ratingDelta}`
                                : confirmation.ratingDelta}
                          </Typography>
                        </Stack>
                      ) : confirmation.status === "REJECTED" ? (
                        confirmation.rejectionReason || "-"
                      ) : confirmation.status === "CANCELLED_BY_CUSTOMER" ? (
                        "Khách hàng đã hủy biên lai trước khi nhân viên đối chiếu"
                      ) : (
                        "Đang chờ nhân viên đối chiếu"
                      )}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="text"
                        size="small"
                        onClick={() => handleDownloadProof(confirmation)}
                      >
                        Xem biên lai
                      </Button>
                    </TableCell>
                    <TableCell>{confirmation.customerNote || confirmation.staffNote || "-"}</TableCell>
                    <TableCell>
                      {confirmation.status === "PENDING_REVIEW" ? (
                        <Button
                          variant="text"
                          color="warning"
                          size="small"
                          onClick={() => setConfirmationToCancel(confirmation)}
                          disabled={busy}
                        >
                          {cancellingId === confirmation.id ? "Đang hủy..." : "Hủy"}
                        </Button>
                      ) : (
                        "-"
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>

          <Paper sx={{ overflowX: "auto" }}>
            <Stack sx={{ p: 2, pb: 0 }}>
              <Typography variant="h6">Lịch sử thanh toán đã xác nhận</Typography>
              <Typography variant="body2" color="text.secondary">
                Chỉ các biên lai đã được nhân viên xác nhận mới xuất hiện ở đây.
              </Typography>
            </Stack>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Thời điểm giao dịch</TableCell>
                  <TableCell>Khoản vay</TableCell>
                  <TableCell>Ngày đến hạn</TableCell>
                  <TableCell>Số tiền đến hạn</TableCell>
                  <TableCell>Số tiền đã xác nhận</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Biến động điểm</TableCell>
                  <TableCell>Ghi chú</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {payments.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} align="center">
                      Chưa có khoản thanh toán nào được xác nhận.
                    </TableCell>
                  </TableRow>
                )}
                {payments.map((payment) => (
                  <TableRow key={payment.id} hover>
                    <TableCell>{formatDateTime(payment.paidAt)}</TableCell>
                    <TableCell>#{payment.loanRequestId}</TableCell>
                    <TableCell>{payment.dueDate}</TableCell>
                    <TableCell>{formatVnd(payment.amountDue)}</TableCell>
                    <TableCell>{formatVnd(payment.amountPaid)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={repaymentColor(payment.repaymentStatus)}
                        label={labelRepaymentStatus(payment.repaymentStatus)}
                      />
                    </TableCell>
                    <TableCell>{payment.ratingDelta > 0 ? `+${payment.ratingDelta}` : payment.ratingDelta}</TableCell>
                    <TableCell>{payment.note || "-"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={paymentTotalRows}
              page={paymentPage}
              onPageChange={(_, nextPage) => setPaymentPage(nextPage)}
              rowsPerPage={paymentRowsPerPage}
              onRowsPerPageChange={(event) => {
                setPaymentRowsPerPage(Number(event.target.value));
                setPaymentPage(0);
              }}
              rowsPerPageOptions={[5, 10, 25]}
              labelRowsPerPage="Số dòng"
            />
          </Paper>
        </>
      )}

      <ConfirmDialog
        open={confirmationToCancel != null}
        title="Hủy biên lai chờ đối chiếu"
        message={confirmationToCancel
          ? `Bạn có chắc muốn hủy biên lai đang chờ đối chiếu cho khoản vay #${confirmationToCancel.loanRequestId}? Sau khi hủy, bạn có thể gửi lại biên lai mới.`
          : ""}
        confirmText="Hủy biên lai"
        cancelText="Đóng"
        onConfirm={handleCancelPendingConfirmation}
        onCancel={() => setConfirmationToCancel(null)}
      />
    </Stack>
  );
}
