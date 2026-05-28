import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import {
  downloadInformationVerificationIdentityCardApi,
  downloadInformationVerificationPayslipApi,
  getInformationVerificationDetailApi,
  reviewInformationVerificationApi
} from "@/features/staff/api/informationVerificationApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { formatFileSize } from "@/shared/utils/files";
import { labelCreditBureauStatus, labelVerificationStatus } from "@/shared/utils/labels";

const verificationFieldKeywords = {
  verifiedMonthlyIncome: ["thu nhập", "lương", "verifiedMonthlyIncome"],
  reason: ["lý do", "từ chối", "reason"]
};

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
  const [downloadingIdentityCard, setDownloadingIdentityCard] = useState("");
  const [verifiedMonthlyIncome, setVerifiedMonthlyIncome] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      if (!accessToken || !customerId) {
        return;
      }
      setLoading(true);
      setError("");
      setFieldErrors({});
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
    if (action === "APPROVE" && !verifiedMonthlyIncome) {
      const message = "Vui lòng nhập thu nhập đã xác minh trước khi chấp thuận hồ sơ.";
      setSubmitError(message);
      setFieldErrors({ verifiedMonthlyIncome: message });
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setSubmitSuccess("");
    setFieldErrors({});
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
      const message = err.message || "Không cập nhật được trạng thái xác minh";
      setSubmitError(message);
      setFieldErrors(mapFieldErrors(message, verificationFieldKeywords));
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerifiedIncomeChange = (event) => {
    setFieldErrors((prev) => clearFieldError(prev, "verifiedMonthlyIncome"));
    setVerifiedMonthlyIncome(formatVndInput(event.target.value));
  };

  const handleReasonChange = (event) => {
    setFieldErrors((prev) => clearFieldError(prev, "reason"));
    setReason(event.target.value);
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

  const handleDownloadIdentityCard = async (side) => {
    const fileName = side === "front" ? detail?.profile?.identityCardFrontFileName : detail?.profile?.identityCardBackFileName;
    if (!fileName) {
      return;
    }
    setDownloadingIdentityCard(side);
    setError("");
    try {
      await downloadInformationVerificationIdentityCardApi(accessToken, detail.customerId, side, fileName);
    } catch (err) {
      setError(err.message || "Không tải được ảnh CCCD");
    } finally {
      setDownloadingIdentityCard("");
    }
  };

  const hasSubmittedProfile = Boolean(
    detail?.profile?.identityNumber
      && detail?.profile?.payslipFileName
      && detail?.profile?.identityCardFrontFileName
      && detail?.profile?.identityCardBackFileName
  );
  const hasRetainedVerifiedIncome = detail?.status === "PENDING" && detail?.profile?.verifiedMonthlyIncome != null;

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Xác minh thông tin khách hàng #{customerId}</Typography>
      <Typography color="text.secondary">
        Nhân viên đối chiếu thông tin hồ sơ, phiếu lương và quyết định xem khách hàng có được tạo hồ sơ vay mới trong tương lai hay không.
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
              {detail.profile && !hasSubmittedProfile && (
                <Alert severity="warning">
                  Khách hàng chưa nộp đủ số CCCD, ảnh CCCD hai mặt và phiếu lương. Chưa thể chấp thuận hoặc từ chối cho tới khi có đủ hồ sơ đối chiếu.
                </Alert>
              )}
              {detail.profile && (
                <>
                  <Typography variant="body2">Họ tên: {detail.profile.fullName || "-"}</Typography>
                  <Typography variant="body2">Số điện thoại: {detail.profile.phone || "-"}</Typography>
                  <Typography variant="body2">Số CCCD: {detail.profile.identityNumber || "-"}</Typography>
                  <Typography variant="body2">
                    Số tài khoản nhận giải ngân: {detail.profile.bankAccountNumber || "-"}
                  </Typography>
                  <Typography variant="body2">
                    Ngân hàng nhận giải ngân: {detail.profile.bankName || "-"}
                  </Typography>
                  {!(detail.profile.bankAccountNumber && detail.profile.bankName) && (
                    <Alert severity="info">
                      Hồ sơ gốc hiện chưa có đủ thông tin tài khoản nhận giải ngân. Khách hàng vẫn có thể được xác minh thông tin, nhưng sẽ phải bổ sung trước bước giải ngân.
                    </Alert>
                  )}
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
                  <Typography variant="body2">Điểm tín dụng nội bộ: {detail.profile.creditHistoryScore ?? "-"}</Typography>
                  <Typography variant="body2">Điểm thanh toán: {detail.profile.paymentRating ?? "-"}</Typography>
                  {detail.profile.creditCheck && (
                    <Alert severity={detail.profile.creditCheck.hardReject ? "error" : detail.profile.creditCheck.manualReviewRequired ? "warning" : "info"}>
                      Tra cứu tín dụng nội bộ theo CCCD: {labelCreditBureauStatus(detail.profile.creditCheck.bureauStatus)}.
                      {detail.profile.creditCheck.creditScore != null ? ` Điểm: ${detail.profile.creditCheck.creditScore}.` : ""}
                      {detail.profile.creditCheck.manualReviewRequired ? " Cần thẩm định thủ công." : ""}
                      {detail.profile.creditCheck.hardReject ? " Có cờ từ chối cứng." : ""}
                      {detail.profile.creditCheck.riskNote ? ` Ghi chú: ${detail.profile.creditCheck.riskNote}` : ""}
                    </Alert>
                  )}
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
                  <Typography variant="body2">
                    CCCD mặt trước: {detail.profile.identityCardFrontFileName || "-"}
                    {detail.profile.identityCardFrontFileSize != null ? ` (${formatFileSize(detail.profile.identityCardFrontFileSize)})` : ""}
                  </Typography>
                  <Typography variant="body2">
                    Tải lên lúc: {detail.profile.identityCardFrontUploadedAt ? new Date(detail.profile.identityCardFrontUploadedAt).toLocaleString() : "-"}
                  </Typography>
                  {detail.profile.identityCardFrontFileName && (
                    <Button
                      variant="outlined"
                      size="small"
                      sx={{ alignSelf: "flex-start" }}
                      onClick={() => handleDownloadIdentityCard("front")}
                      disabled={downloadingIdentityCard === "front"}
                    >
                      {downloadingIdentityCard === "front" ? "Đang tải..." : "Tải CCCD mặt trước"}
                    </Button>
                  )}
                  <Typography variant="body2">
                    CCCD mặt sau: {detail.profile.identityCardBackFileName || "-"}
                    {detail.profile.identityCardBackFileSize != null ? ` (${formatFileSize(detail.profile.identityCardBackFileSize)})` : ""}
                  </Typography>
                  <Typography variant="body2">
                    Tải lên lúc: {detail.profile.identityCardBackUploadedAt ? new Date(detail.profile.identityCardBackUploadedAt).toLocaleString() : "-"}
                  </Typography>
                  {detail.profile.identityCardBackFileName && (
                    <Button
                      variant="outlined"
                      size="small"
                      sx={{ alignSelf: "flex-start" }}
                      onClick={() => handleDownloadIdentityCard("back")}
                      disabled={downloadingIdentityCard === "back"}
                    >
                      {downloadingIdentityCard === "back" ? "Đang tải..." : "Tải CCCD mặt sau"}
                    </Button>
                  )}
                </>
              )}
            </Stack>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Quyết định xác minh thông tin</Typography>
              <Alert severity="info">
                Các thay đổi liên quan đến CCCD, phiếu lương hoặc thu nhập sẽ đưa trạng thái này quay về Chờ xác minh.
              </Alert>
              {hasRetainedVerifiedIncome && (
                <Alert severity="info">
                  Mức thu nhập đã duyệt trước đó vẫn được giữ lại vì khách hàng chưa thay đổi lương kê khai hoặc phiếu lương.
                </Alert>
              )}
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
              <TextField
                label="Thu nhập xác minh (VNĐ)"
                type="text"
                value={verifiedMonthlyIncome}
                onChange={handleVerifiedIncomeChange}
                disabled={submitting}
                placeholder="Nhập thu nhập đã đối chiếu phiếu lương (bắt buộc khi chấp thuận)"
                fullWidth
                inputProps={{ inputMode: "numeric" }}
                {...fieldErrorProps(
                  fieldErrors,
                  "verifiedMonthlyIncome",
                  "Số tiền thực tế trên phiếu lương, sẽ được dùng cho việc chấm điểm tín dụng và tính hạn mức."
                )}
              />
              <TextField
                label="Lý do từ chối"
                multiline
                minRows={3}
                value={reason}
                onChange={handleReasonChange}
                disabled={submitting}
                placeholder="Nhập lý do nếu cần từ chối thông tin kê khai."
                {...fieldErrorProps(fieldErrors, "reason")}
              />
              <Divider />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                <Button
                  variant="contained"
                  color="success"
                  disabled={submitting || !hasSubmittedProfile}
                  onClick={() => handleDecision("APPROVE")}
                >
                  {submitting ? "Đang xử lý..." : "Chấp thuận thông tin"}
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  disabled={submitting || !hasSubmittedProfile}
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
