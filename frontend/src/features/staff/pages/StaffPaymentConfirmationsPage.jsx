import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
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
  TextField,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  downloadStaffPaymentProofApi,
  getStaffPaymentConfirmationDetailApi,
  getStaffPaymentConfirmationsApi,
  getStaffPaymentProofObjectUrlApi,
  reviewStaffPaymentConfirmationApi
} from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import {
  labelLoanStatus,
  labelPaymentConfirmationStatus,
  labelRepaymentStatus
} from "@/shared/utils/labels";

function confirmationColor(status) {
  if (status === "CONFIRMED") {
    return "success";
  }
  if (status === "REJECTED") {
    return "error";
  }
  return "warning";
}

function repaymentColor(status) {
  return status === "ON_TIME" ? "success" : "error";
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("vi-VN");
}

function toDateTimeLocalValue(value) {
  if (!value) {
    return "";
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

function toIsoInstant(localDateTimeValue) {
  if (!localDateTimeValue) {
    return null;
  }
  const date = new Date(localDateTimeValue);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date.toISOString();
}

function buildDueDateTimeLocalValue(dueDate, dayOffset = 0) {
  if (!dueDate) {
    return "";
  }
  const [year, month, day] = dueDate.split("-").map(Number);
  if (!year || !month || !day) {
    return "";
  }
  return toDateTimeLocalValue(new Date(year, month - 1, day + dayOffset, 9, 0, 0, 0));
}

function buildInitialForm(detail) {
  return {
    confirmedAmount: detail?.expectedAmountDue ? formatVndInput(detail.expectedAmountDue) : "",
    confirmedPaidAt: detail?.expectedDueDate ? buildDueDateTimeLocalValue(detail.expectedDueDate) : "",
    bankTransactionCode: "",
    staffNote: "",
    rejectionReason: ""
  };
}

function QueueList({ rows, loading, error, status, setStatus }) {
  const statusLabel = status ? labelPaymentConfirmationStatus(status) : "tất cả trạng thái";

  return (
    <Stack spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="h4">Xác nhận thanh toán</Typography>
        <Typography color="text.secondary">
          Nhân viên đối chiếu bill chuyển khoản và chỉ khi xác nhận hợp lệ thì hệ thống mới ghi nhận thanh toán vào khoản vay.
        </Typography>
      </Stack>

      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="payment-confirmation-filter-label">Lọc trạng thái</InputLabel>
          <Select
            labelId="payment-confirmation-filter-label"
            value={status}
            label="Lọc trạng thái"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="PENDING_REVIEW">Chờ đối chiếu</MenuItem>
            <MenuItem value="CONFIRMED">Đã xác nhận</MenuItem>
            <MenuItem value="REJECTED">Bị từ chối</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải hàng đợi xác nhận thanh toán...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có yêu cầu nào ở bộ lọc {statusLabel}.
          </Typography>
        </Paper>
      )}

      {!loading && rows.length > 0 && (
        <Paper sx={{ overflowX: "auto" }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Mã yêu cầu</TableCell>
                <TableCell>Khách hàng</TableCell>
                <TableCell>Khoản vay</TableCell>
                <TableCell>Kỳ / ngày đến hạn</TableCell>
                <TableCell>Số tiền kỳ này</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Gửi lúc</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>#{row.id}</TableCell>
                  <TableCell>{row.customerName || row.customerEmail}</TableCell>
                  <TableCell>#{row.loanRequestId}</TableCell>
                  <TableCell>
                    Kỳ #{row.expectedInstallmentNumber} / {row.expectedDueDate}
                  </TableCell>
                  <TableCell>{formatVnd(row.expectedAmountDue)}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={confirmationColor(row.status)}
                      label={labelPaymentConfirmationStatus(row.status)}
                    />
                  </TableCell>
                  <TableCell>{formatDateTime(row.createdAt)}</TableCell>
                  <TableCell align="right">
                    <Button
                      component={RouterLink}
                      to={`/staff/payment-confirmations/${row.id}`}
                      variant="outlined"
                      size="small"
                    >
                      Đối chiếu
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Stack>
  );
}

export default function StaffPaymentConfirmationsPage() {
  const { confirmationId } = useParams();
  const { accessToken } = useAuth();
  const [status, setStatus] = useState("");
  const [rows, setRows] = useState([]);
  const [detail, setDetail] = useState(null);
  const [preview, setPreview] = useState(null);
  const [form, setForm] = useState({
    confirmedAmount: "",
    confirmedPaidAt: "",
    bankTransactionCode: "",
    staffNote: "",
    rejectionReason: ""
  });
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    let active = true;

    async function load() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      setSuccess("");
      try {
        if (confirmationId) {
          const response = await getStaffPaymentConfirmationDetailApi(accessToken, confirmationId);
          if (!active) {
            return;
          }
          setDetail(response);
          setForm(buildInitialForm(response));
        } else {
          const response = await getStaffPaymentConfirmationsApi(accessToken, status);
          if (!active) {
            return;
          }
          setRows(Array.isArray(response) ? response : []);
        }
      } catch (err) {
        if (active) {
          setError(err.message || "Không tải được dữ liệu xác nhận thanh toán");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [accessToken, confirmationId, status]);

  useEffect(() => {
    let active = true;
    let objectUrlToRevoke = null;

    async function loadPreview() {
      if (!accessToken || !confirmationId || !detail?.proofContentType) {
        return;
      }
      if (!detail.proofContentType.startsWith("image/")) {
        setPreview(null);
        return;
      }
      try {
        const result = await getStaffPaymentProofObjectUrlApi(accessToken, confirmationId);
        objectUrlToRevoke = result.objectUrl;
        if (!active) {
          window.URL.revokeObjectURL(result.objectUrl);
          return;
        }
        setPreview(result);
      } catch (err) {
        if (active) {
          setError(err.message || "Không tải được ảnh bill chuyển khoản");
        }
      }
    }

    loadPreview();
    return () => {
      active = false;
      if (objectUrlToRevoke) {
        window.URL.revokeObjectURL(objectUrlToRevoke);
      }
    };
  }, [accessToken, confirmationId, detail?.proofContentType]);

  const handleChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleDownloadProof = async () => {
    try {
      await downloadStaffPaymentProofApi(accessToken, confirmationId, detail?.proofFileName);
    } catch (err) {
      setError(err.message || "Không tải được bill chuyển khoản");
    }
  };

  const handleApprove = async () => {
    setSubmitting(true);
    setError("");
    setSuccess("");
    try {
      const response = await reviewStaffPaymentConfirmationApi(accessToken, confirmationId, {
        action: "APPROVE",
        confirmedAmount: parseVndInput(form.confirmedAmount),
        confirmedPaidAt: toIsoInstant(form.confirmedPaidAt),
        bankTransactionCode: form.bankTransactionCode.trim(),
        staffNote: form.staffNote.trim() || null,
        rejectionReason: null
      });
      setDetail(response);
      setSuccess("Đã xác nhận bill hợp lệ và hệ thống đã ghi nhận thanh toán cho khoản vay.");
    } catch (err) {
      setError(err.message || "Không xác nhận được bill chuyển khoản");
    } finally {
      setSubmitting(false);
    }
  };

  const handleReject = async () => {
    setSubmitting(true);
    setError("");
    setSuccess("");
    try {
      const response = await reviewStaffPaymentConfirmationApi(accessToken, confirmationId, {
        action: "REJECT",
        confirmedAmount: null,
        confirmedPaidAt: null,
        bankTransactionCode: null,
        staffNote: form.staffNote.trim() || null,
        rejectionReason: form.rejectionReason.trim()
      });
      setDetail(response);
      setSuccess("Đã từ chối bill chuyển khoản và trả kết quả lại cho khách hàng.");
    } catch (err) {
      setError(err.message || "Không từ chối được bill chuyển khoản");
    } finally {
      setSubmitting(false);
    }
  };

  const handleUseDueDate = () => {
    if (!detail?.expectedDueDate) {
      return;
    }
    setForm((prev) => ({
      ...prev,
      confirmedPaidAt: buildDueDateTimeLocalValue(detail.expectedDueDate)
    }));
  };

  const handleUseLateDate = () => {
    if (!detail?.expectedDueDate) {
      return;
    }
    setForm((prev) => ({
      ...prev,
      confirmedPaidAt: buildDueDateTimeLocalValue(detail.expectedDueDate, 1)
    }));
  };

  if (!confirmationId) {
    return <QueueList rows={rows} loading={loading} error={error} status={status} setStatus={setStatus} />;
  }

  const reviewLocked = detail?.status !== "PENDING_REVIEW";

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", sm: "center" }}>
        <Stack spacing={0.5}>
          <Typography variant="h4">Đối chiếu bill thanh toán #{confirmationId}</Typography>
          <Typography color="text.secondary">
            Nhân viên xác nhận bill hợp lệ, nhập số tiền và thời điểm giao dịch; backend sẽ tự quyết định thanh toán đúng hạn hay trễ hạn.
          </Typography>
        </Stack>
        <Button component={RouterLink} to="/staff/payment-confirmations" variant="outlined">
          Về danh sách
        </Button>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}
      {success && <Alert severity="success">{success}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải yêu cầu xác nhận thanh toán...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && detail && (
        <Grid container spacing={2}>
          <Grid item xs={12} lg={4}>
            <Stack spacing={2}>
              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Tóm tắt yêu cầu</Typography>
                  <Typography variant="body2">Khách hàng: {detail.customerName || detail.customerEmail}</Typography>
                  <Typography variant="body2">Khoản vay: #{detail.loanRequestId}</Typography>
                  <Typography variant="body2">Trạng thái khoản vay: {labelLoanStatus(detail.loanStatus)}</Typography>
                  <Typography variant="body2">Gửi lúc: {formatDateTime(detail.createdAt)}</Typography>
                  <Chip
                    size="small"
                    color={confirmationColor(detail.status)}
                    label={labelPaymentConfirmationStatus(detail.status)}
                    sx={{ alignSelf: "flex-start" }}
                  />
                </Stack>
              </Paper>

              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Thông tin kỳ thanh toán</Typography>
                  <Typography variant="body2">Kỳ đã gửi bill: #{detail.expectedInstallmentNumber}</Typography>
                  <Typography variant="body2">Ngày đến hạn: {detail.expectedDueDate}</Typography>
                  <Typography variant="body2">Số tiền đến hạn tại lúc gửi: {formatVnd(detail.expectedAmountDue)}</Typography>
                  <Typography variant="body2">Dư nợ lúc gửi: {formatVnd(detail.expectedOutstandingAmount)}</Typography>
                  <Typography variant="body2">Số tiền kỳ hiện tại: {formatVnd(detail.currentAmountDue)}</Typography>
                  <Typography variant="body2">Dư nợ hiện tại: {formatVnd(detail.currentOutstandingAmount)}</Typography>
                </Stack>
              </Paper>

              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Bill chuyển khoản</Typography>
                  <Typography variant="body2">{detail.proofFileName}</Typography>
                  <Button variant="outlined" size="small" onClick={handleDownloadProof}>
                    Tải bill
                  </Button>
                  {preview?.objectUrl ? (
                    <Box
                      component="img"
                      src={preview.objectUrl}
                      alt={detail.proofFileName}
                      sx={{
                        width: "100%",
                        borderRadius: 2,
                        border: "1px solid",
                        borderColor: "divider",
                        objectFit: "contain",
                        bgcolor: "#fff"
                      }}
                    />
                  ) : (
                    <Alert severity="info">Nếu bill không phải ảnh xem trực tiếp được, hãy dùng nút tải bill để đối chiếu.</Alert>
                  )}
                </Stack>
              </Paper>
            </Stack>
          </Grid>

          <Grid item xs={12} lg={8}>
            <Paper sx={{ p: 3 }}>
              <Stack spacing={2}>
                {detail.status === "CONFIRMED" && (
                  <Alert severity={detail.repaymentStatus === "ON_TIME" ? "success" : "warning"}>
                    Đã xác nhận bill. Kết quả ghi nhận là {labelRepaymentStatus(detail.repaymentStatus)} với biến động điểm{" "}
                    {detail.ratingDelta > 0 ? `+${detail.ratingDelta}` : detail.ratingDelta}.
                  </Alert>
                )}
                {detail.status === "REJECTED" && (
                  <Alert severity="error">
                    Bill đã bị từ chối{detail.rejectionReason ? `: ${detail.rejectionReason}` : "."}
                  </Alert>
                )}
                {detail.customerNote && <Alert severity="info">Ghi chú từ khách hàng: {detail.customerNote}</Alert>}

                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Số tiền xác nhận"
                      value={reviewLocked ? formatVnd(detail.confirmedAmount || detail.expectedAmountDue) : form.confirmedAmount}
                      onChange={handleChange("confirmedAmount")}
                      fullWidth
                      disabled={reviewLocked || submitting}
                      helperText="Phải bằng số tiền đến hạn kỳ này hoặc bằng toàn bộ dư nợ để tất toán."
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Thời điểm giao dịch trên bill"
                      type="datetime-local"
                      value={reviewLocked ? toDateTimeLocalValue(detail.confirmedPaidAt) : form.confirmedPaidAt}
                      onChange={handleChange("confirmedPaidAt")}
                      fullWidth
                      InputLabelProps={{ shrink: true }}
                      disabled={reviewLocked || submitting}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Mã giao dịch / tham chiếu"
                      value={reviewLocked ? detail.bankTransactionCode || "" : form.bankTransactionCode}
                      onChange={handleChange("bankTransactionCode")}
                      fullWidth
                      disabled={reviewLocked || submitting}
                    />
                  </Grid>
                  {!reviewLocked && (
                    <Grid item xs={12}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                        <Button variant="outlined" onClick={handleUseDueDate} disabled={submitting}>
                          Đặt đúng ngày đến hạn
                        </Button>
                        <Button variant="outlined" onClick={handleUseLateDate} disabled={submitting}>
                          Đặt sau hạn 1 ngày
                        </Button>
                      </Stack>
                    </Grid>
                  )}
                  <Grid item xs={12}>
                    <TextField
                      label="Ghi chú nội bộ"
                      value={reviewLocked ? detail.staffNote || "" : form.staffNote}
                      onChange={handleChange("staffNote")}
                      fullWidth
                      multiline
                      minRows={3}
                      disabled={reviewLocked || submitting}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      label="Lý do từ chối"
                      value={reviewLocked ? detail.rejectionReason || "" : form.rejectionReason}
                      onChange={handleChange("rejectionReason")}
                      fullWidth
                      multiline
                      minRows={3}
                      disabled={reviewLocked || submitting || detail.status === "CONFIRMED"}
                    />
                  </Grid>
                  {detail.reviewedAt && (
                    <Grid item xs={12}>
                      <Typography variant="body2" color="text.secondary">
                        Đã xử lý lúc {formatDateTime(detail.reviewedAt)}
                        {detail.reviewedByEmail ? ` bởi ${detail.reviewedByEmail}` : ""}.
                      </Typography>
                    </Grid>
                  )}
                  {!reviewLocked && (
                    <Grid item xs={12}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                        <Button variant="contained" onClick={handleApprove} disabled={submitting}>
                          {submitting ? "Đang xác nhận..." : "Xác nhận hợp lệ"}
                        </Button>
                        <Button variant="outlined" color="error" onClick={handleReject} disabled={submitting}>
                          {submitting ? "Đang từ chối..." : "Từ chối bill"}
                        </Button>
                      </Stack>
                    </Grid>
                  )}
                </Grid>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}
