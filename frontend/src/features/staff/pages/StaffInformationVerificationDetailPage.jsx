import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Divider,
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
import {
  downloadInformationVerificationPayslipApi,
  getInformationVerificationDetailApi,
  reviewInformationVerificationApi
} from "@/features/staff/api/informationVerificationApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { formatFileSize } from "@/shared/utils/files";
import { labelVerificationStatus } from "@/shared/utils/labels";

function StatusChip({ status }) {
  const colorMap = {
    PENDING: "warning",
    PASSED: "success",
    FAILED: "error"
  };

  return <Chip size="small" label={labelVerificationStatus(status)} color={colorMap[status] || "default"} />;
}

export default function StaffInformationVerificationDetailPage() {
  const { customerId } = useParams();
  const { accessToken } = useAuth();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [downloadingPayslip, setDownloadingPayslip] = useState(false);
  const [verifiedMonthlyIncome, setVerifiedMonthlyIncome] = useState("");

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      if (!accessToken || !customerId) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getInformationVerificationDetailApi(accessToken, customerId);
        if (!active) {
          return;
        }
        setDetail(response);
        setReason(response?.rejectionReason || "");
        setVerifiedMonthlyIncome(
          response?.profile?.verifiedMonthlyIncome != null
            ? formatVndInput(response.profile.verifiedMonthlyIncome)
            : ""
        );
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được chi tiết xác minh thông tin");
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
  }, [accessToken, customerId]);

  const handleDecision = async (action) => {
    if (!detail) {
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      await reviewInformationVerificationApi(accessToken, detail.customerId, {
        action,
        reason: reason.trim(),
        verifiedMonthlyIncome: action === "APPROVE" && verifiedMonthlyIncome
          ? parseVndInput(verifiedMonthlyIncome)
          : null
      });
      const refreshed = await getInformationVerificationDetailApi(accessToken, detail.customerId);
      setDetail(refreshed);
      setReason(refreshed?.rejectionReason || "");
      setVerifiedMonthlyIncome(
        refreshed?.profile?.verifiedMonthlyIncome != null
          ? formatVndInput(refreshed.profile.verifiedMonthlyIncome)
          : ""
      );
      setSubmitSuccess(
        action === "APPROVE"
          ? "Đã chấp thuận thông tin kê khai của khách hàng."
          : "Đã từ chối thông tin kê khai của khách hàng."
      );
    } catch (err) {
      setSubmitError(err.message || "Không cập nhật được trạng thái xác minh");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDownloadPayslip = async () => {
    if (!detail?.profile?.payslipFileName) {
      return;
    }
    setDownloadingPayslip(true);
    setError("");
    try {
      await downloadInformationVerificationPayslipApi(accessToken, detail.customerId, detail.profile.payslipFileName);
    } catch (err) {
      setError(err.message || "Không tải được phiếu lương");
    } finally {
      setDownloadingPayslip(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Xác minh thông tin khách hàng #{customerId}</Typography>
      <Typography color="text.secondary">
        Nhân viên đối chiếu thông tin hồ sơ, phiếu lương, các khoản nợ và quyết định xem khách hàng có được tạo hồ sơ vay mới trong tương lai hay không.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải chi tiết khách hàng...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && detail && (
        <>
          <Paper sx={{ p: 3 }}>
            <Stack spacing={1.25}>
              <Typography variant="h6">Tổng quan tài khoản</Typography>
              <Typography variant="body2">Email: {detail.email}</Typography>
              <Typography variant="body2">
                Trạng thái xác minh: <StatusChip status={detail.status} />
              </Typography>
              <Typography variant="body2">
                Thời gian đăng ký: {detail.registeredAt ? new Date(detail.registeredAt).toLocaleString() : "-"}
              </Typography>
              <Typography variant="body2">
                Nhân viên xử lý gần nhất: {detail.reviewedByEmail || "-"}
              </Typography>
              <Typography variant="body2">
                Thời gian xử lý gần nhất: {detail.reviewedAt ? new Date(detail.reviewedAt).toLocaleString() : "-"}
              </Typography>
              {detail.rejectionReason && <Alert severity="warning">{detail.rejectionReason}</Alert>}
            </Stack>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Stack spacing={1.25}>
              <Typography variant="h6">Thông tin hồ sơ khách hàng</Typography>
              {!detail.profile && (
                <Alert severity="warning">
                  Khách hàng chưa hoàn thành hồ sơ. Không thể chấp thuận nếu chưa có dữ liệu để đối chiếu.
                </Alert>
              )}
              {detail.profile && (
                <>
                  <Typography variant="body2">Họ tên: {detail.profile.fullName || "-"}</Typography>
                  <Typography variant="body2">Số điện thoại: {detail.profile.phone || "-"}</Typography>
                  <Typography variant="body2">Ngày sinh: {detail.profile.dateOfBirth || "-"}</Typography>
                  <Typography variant="body2">
                    Thu nhập kê khai: {detail.profile.monthlyIncome != null ? formatVnd(detail.profile.monthlyIncome) : "-"}
                  </Typography>
                  <Typography variant="body2">
                    Thu nhập đã xác minh:{" "}
                    {detail.profile.verifiedMonthlyIncome != null
                      ? formatVnd(detail.profile.verifiedMonthlyIncome)
                      : <em>Chưa xác minh</em>}
                  </Typography>
                  <Typography variant="body2">
                    DTI: {detail.profile.debtToIncomeRatio != null ? `${detail.profile.debtToIncomeRatio}%` : "-"}
                  </Typography>
                  <Typography variant="body2">Điểm thanh toán: {detail.profile.paymentRating ?? "-"}</Typography>
                  <Typography variant="body2">
                    Phiếu lương: {detail.profile.payslipFileName || "-"}
                    {detail.profile.payslipFileSize != null ? ` (${formatFileSize(detail.profile.payslipFileSize)})` : ""}
                  </Typography>
                  <Typography variant="body2">
                    Thời gian tải lên: {detail.profile.payslipUploadedAt ? new Date(detail.profile.payslipUploadedAt).toLocaleString() : "-"}
                  </Typography>
                  {detail.profile.payslipFileName && (
                    <Button
                      variant="outlined"
                      size="small"
                      sx={{ alignSelf: "flex-start" }}
                      onClick={handleDownloadPayslip}
                      disabled={downloadingPayslip}
                    >
                      {downloadingPayslip ? "Đang tải..." : "Tải phiếu lương"}
                    </Button>
                  )}
                </>
              )}
            </Stack>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Khoản nợ hiện tại</Typography>
              {detail.debts.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  Khách hàng chưa khai báo khoản nợ nào.
                </Typography>
              )}
              {detail.debts.length > 0 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Khoản nợ</TableCell>
                      <TableCell>Trả hàng tháng</TableCell>
                      <TableCell>Dư nợ</TableCell>
                      <TableCell>Đơn vị cho vay</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.debts.map((debt) => (
                      <TableRow key={debt.id}>
                        <TableCell>{debt.debtType}</TableCell>
                        <TableCell>{formatVnd(debt.monthlyPayment)}</TableCell>
                        <TableCell>{formatVnd(debt.remainingBalance)}</TableCell>
                        <TableCell>{debt.lenderName || "-"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Stack>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Quyết định xác minh thông tin</Typography>
              <Alert severity="info">
                Mọi thay đổi ở hồ sơ hoặc danh sách khoản nợ của khách hàng sẽ đưa trạng thái này quay về Chờ xác minh.
              </Alert>
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
              <TextField
                label="Thu nhập xác minh (VNĐ)"
                type="text"
                value={verifiedMonthlyIncome}
                onChange={(event) => setVerifiedMonthlyIncome(formatVndInput(event.target.value))}
                disabled={submitting}
                placeholder="Nhập thu nhập đã đối chiếu phiếu lương (bắt buộc khi chấp thuận)"
                fullWidth
                inputProps={{ inputMode: "numeric" }}
                helperText="Số tiền thực tế trên phiếu lương, sẽ được dùng cho việc chấm điểm tín dụng và tính hạn mức."
              />
              <TextField
                label="Lý do từ chối"
                multiline
                minRows={3}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                disabled={submitting}
                placeholder="Nhập lý do nếu cần từ chối thông tin kê khai."
              />
              <Divider />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                <Button
                  variant="contained"
                  color="success"
                  disabled={submitting || !detail.profile || !detail.profile.payslipFileName}
                  onClick={() => handleDecision("APPROVE")}
                >
                  {submitting ? "Đang xử lý..." : "Chấp thuận thông tin"}
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  disabled={submitting}
                  onClick={() => handleDecision("REJECT")}
                >
                  {submitting ? "Đang xử lý..." : "Từ chối thông tin"}
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </>
      )}
    </Stack>
  );
}
