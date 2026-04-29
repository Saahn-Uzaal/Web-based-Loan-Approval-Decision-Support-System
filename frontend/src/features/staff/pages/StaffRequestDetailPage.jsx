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
import { Link as RouterLink, useParams } from "react-router-dom";
import { downloadInformationVerificationPayslipApi } from "@/features/staff/api/informationVerificationApi";
import {
  completeStaffContractApi,
  disburseStaffLoanApi,
  downloadStaffLoanDocumentApi,
  getStaffRequestDetailApi,
  submitStaffDecisionApi,
  updateStaffCustomerVerificationApi
} from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { formatFileSize } from "@/shared/utils/files";
import {
  labelContractStatus,
  labelCollateralType,
  labelCustomerSegment,
  labelDssRecommendation,
  labelLoanDocumentType,
  labelLoanPurpose,
  labelLoanStatus,
  labelLoanType,
  labelRiskLevel,
  labelRiskRank,
  labelStaffAction,
  labelVerificationStatus
} from "@/shared/utils/labels";

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
  return "";
}

function toDateTimeLocalValue(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function defaultAppointmentInputValue() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(9, 0, 0, 0);
  return toDateTimeLocalValue(date);
}

function toIsoInstant(localDateTime) {
  return localDateTime ? new Date(localDateTime).toISOString() : null;
}

const VERIFICATION_STATUSES = ["PENDING", "PASSED", "FAILED"];
const DECISION_EDITABLE_STATUSES = ["PENDING"];

const decisionFieldKeywords = {
  action: ["hành động", "quyết định", "action"],
  approvedAmount: ["số tiền phê duyệt", "số tiền", "approvedAmount"],
  approvedTermMonths: ["kỳ hạn phê duyệt", "kỳ hạn", "approvedTermMonths"],
  approvedAnnualRate: ["lãi suất", "approvedAnnualRate"],
  scheduledAt: ["lịch hẹn", "thời điểm", "scheduledAt"],
  appointmentNote: ["ghi chú lịch hẹn", "appointmentNote"],
  rejectionReason: ["lý do từ chối", "từ chối", "rejectionReason"]
};

const verificationStepFieldKeywords = {
  documentStatus: ["giấy tờ", "documentStatus"],
  identityStatus: ["định danh", "identityStatus"],
  faceMatchStatus: ["khuôn mặt", "faceMatchStatus"],
  incomeStatus: ["thu nhập", "incomeStatus"],
  kycStatus: ["kyc"],
  amlStatus: ["aml"],
  note: ["ghi chú xác minh", "note"]
};

export default function StaffRequestDetailPage() {
  const { id } = useParams();
  const { accessToken } = useAuth();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submittingContract, setSubmittingContract] = useState(false);
  const [submittingDisbursement, setSubmittingDisbursement] = useState(false);
  const [submittingVerification, setSubmittingVerification] = useState(false);
  const [downloadingPayslip, setDownloadingPayslip] = useState(false);
  const [downloadingDocument, setDownloadingDocument] = useState("");
  const [decisionFieldErrors, setDecisionFieldErrors] = useState({});
  const [verificationFieldErrors, setVerificationFieldErrors] = useState({});
  const [decision, setDecision] = useState({
    action: "",
    scheduledAt: defaultAppointmentInputValue(),
    appointmentNote: "",
    rejectionReason: "",
    approvedAmount: "",
    approvedTermMonths: "",
    approvedAnnualRate: ""
  });
  const [verificationForm, setVerificationForm] = useState({
    documentStatus: "PENDING",
    identityStatus: "PENDING",
    faceMatchStatus: "PENDING",
    incomeStatus: "PENDING",
    kycStatus: "PENDING",
    amlStatus: "PENDING",
    fraudFlag: false,
    note: ""
  });

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      if (!accessToken || !id) {
        return;
      }
      setLoading(true);
      setError("");
      setDecisionFieldErrors({});
      setVerificationFieldErrors({});
      try {
        const response = await getStaffRequestDetailApi(accessToken, id);
        if (!active) {
          return;
        }
        setDetail(response);
        setVerificationForm({
          documentStatus: response?.verification?.documentStatus || "PENDING",
          identityStatus: response?.verification?.identityStatus || "PENDING",
          faceMatchStatus: response?.verification?.faceMatchStatus || "PENDING",
          incomeStatus: response?.verification?.incomeStatus || "PENDING",
          kycStatus: response?.verification?.kycStatus || "PENDING",
          amlStatus: response?.verification?.amlStatus || "PENDING",
          fraudFlag: Boolean(response?.verification?.fraudFlag),
          note: response?.verification?.note || ""
        });
        setDecision((prev) => {
          if (prev.action || prev.appointmentNote || prev.rejectionReason) {
            return prev;
          }
          return {
            ...prev,
            action: response?.appointment ? "APPROVE" : mapRecommendationToAction(response?.dss?.recommendation),
            approvedAmount:
              response?.approvedAmount != null
                ? formatVndInput(response.approvedAmount)
                : response?.amount != null
                  ? formatVndInput(response.amount)
                  : "",
            approvedTermMonths: response?.approvedTermMonths || response?.termMonths || "",
            approvedAnnualRate: response?.approvedAnnualRate || ""
          };
        });
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được chi tiết hồ sơ");
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

  const finalized = detail ? !DECISION_EDITABLE_STATUSES.includes(detail.status) : false;
  const showApprovalFields = decision.action === "APPROVE";
  const showAppointmentFields = showApprovalFields && detail?.loanType === "SECURED";
  const showRejectReasonField = decision.action === "REJECT";
  const hasSelectedAction = decision.action === "APPROVE" || decision.action === "REJECT";

  const handleDecisionChange = (field) => (event) => {
    setDecisionFieldErrors((prev) => clearFieldError(prev, field));
    setDecision((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleApprovedAmountChange = (event) => {
    setDecisionFieldErrors((prev) => clearFieldError(prev, "approvedAmount"));
    setDecision((prev) => ({
      ...prev,
      approvedAmount: formatVndInput(event.target.value)
    }));
  };

  const handleVerificationChange = (field) => (event) => {
    const value = field === "fraudFlag" ? event.target.value === "true" : event.target.value;
    setVerificationFieldErrors((prev) => clearFieldError(prev, field));
    setVerificationForm((prev) => ({
      ...prev,
      [field]: value
    }));
  };

  const handleSaveVerification = async (event) => {
    event.preventDefault();
    if (!detail?.customer?.id) {
      return;
    }
    setSubmittingVerification(true);
    setSubmitError("");
    setSubmitSuccess("");
    setVerificationFieldErrors({});
    try {
      await updateStaffCustomerVerificationApi(accessToken, detail.customer.id, verificationForm);
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess("Đã cập nhật các bước xác minh hồ sơ.");
    } catch (err) {
      const message = err.message || "Không cập nhật được xác minh hồ sơ";
      setSubmitError(message);
      setVerificationFieldErrors(mapFieldErrors(message, verificationStepFieldKeywords));
    } finally {
      setSubmittingVerification(false);
    }
  };

  const handleSubmitDecision = async (event) => {
    event.preventDefault();
    if (!detail) {
      return;
    }
    if (!hasSelectedAction) {
      const message = "Vui lòng chọn hành động.";
      setSubmitError(message);
      setDecisionFieldErrors({ action: message });
      return;
    }
    if (showRejectReasonField && !decision.rejectionReason.trim()) {
      const message = "Vui lòng nhập lý do từ chối.";
      setSubmitError(message);
      setDecisionFieldErrors({ rejectionReason: message });
      return;
    }
    if (showAppointmentFields && !decision.scheduledAt) {
      const message = "Vui lòng chọn lịch hẹn gặp mặt.";
      setSubmitError(message);
      setDecisionFieldErrors({ scheduledAt: message });
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setSubmitSuccess("");
    setDecisionFieldErrors({});
    try {
      const shouldSchedule = showAppointmentFields;
      await submitStaffDecisionApi(accessToken, detail.id, {
        action: decision.action,
        scheduledAt: shouldSchedule ? toIsoInstant(decision.scheduledAt) : null,
        appointmentLocation: "",
        appointmentNote: shouldSchedule ? decision.appointmentNote.trim() : decision.rejectionReason.trim(),
        approvedAmount: decision.action === "APPROVE" ? parseVndInput(decision.approvedAmount) : null,
        approvedTermMonths: decision.action === "APPROVE" && decision.approvedTermMonths !== "" ? Number(decision.approvedTermMonths) : null,
        approvedAnnualRate: decision.action === "APPROVE" && decision.approvedAnnualRate !== "" ? Number(decision.approvedAnnualRate) : null
      });
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess(`Gửi quyết định thành công. Trạng thái hiện tại: ${labelLoanStatus(refreshed.status)}.`);
      setDecision((prev) => ({
        ...prev,
        appointmentNote: "",
        rejectionReason: ""
      }));
    } catch (err) {
      const message = err.message || "Không gửi được quyết định";
      setSubmitError(message);
      setDecisionFieldErrors(mapFieldErrors(message, decisionFieldKeywords));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDownloadPayslip = async () => {
    if (!detail?.customer?.id || !detail?.customerProfile?.payslipFileName) {
      return;
    }
    setDownloadingPayslip(true);
    setError("");
    try {
      await downloadInformationVerificationPayslipApi(
        accessToken,
        detail.customer.id,
        detail.customerProfile.payslipFileName
      );
    } catch (err) {
      setError(err.message || "Không tải được phiếu lương");
    } finally {
      setDownloadingPayslip(false);
    }
  };

  const handleDownloadDocument = async (document) => {
    if (!detail?.id || !document?.documentType) {
      return;
    }
    setDownloadingDocument(document.documentType);
    setError("");
    try {
      await downloadStaffLoanDocumentApi(accessToken, detail.id, document.documentType, document.fileName);
    } catch (err) {
      setError(err.message || "Không tải được chứng từ hồ sơ vay");
    } finally {
      setDownloadingDocument("");
    }
  };

  const handleCompleteContract = async () => {
    if (!detail?.id) {
      return;
    }
    setSubmittingContract(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      const refreshed = await completeStaffContractApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess("Đã hoàn thiện hợp đồng vay cho hồ sơ này.");
    } catch (err) {
      setSubmitError(err.message || "Không hoàn thiện được hợp đồng vay");
    } finally {
      setSubmittingContract(false);
    }
  };

  const handleDisburse = async () => {
    if (!detail?.id) {
      return;
    }
    setSubmittingDisbursement(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      const refreshed = await disburseStaffLoanApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess("Đã giải ngân khoản vay. Khách hàng có thể bắt đầu thanh toán.");
    } catch (err) {
      setSubmitError(err.message || "Không giải ngân được khoản vay");
    } finally {
      setSubmittingDisbursement(false);
    }
  };

  const statusColorMap = {
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    DISBURSED: "primary",
    ACTIVE: "primary",
    CLOSED: "default",
    REJECTED: "error",
    PENDING: "warning"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Thẩm định hồ sơ #{id}</Typography>
      <Typography color="text.secondary">
        Màn hình nhân viên: hồ sơ khách hàng, thông tin khoản vay, kết quả DSS và quyết định cuối cùng.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải chi tiết hồ sơ...</Typography>
          </Stack>
        </Paper>
      )}
      {!loading && !detail && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không tìm thấy hồ sơ vay.
          </Typography>
        </Paper>
      )}
      {detail && (
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <InfoCard title="Tóm tắt hồ sơ khách hàng">
              <Typography variant="body2">Mã khách hàng: #{detail.customer?.id}</Typography>
              <Typography variant="body2">Email: {detail.customer?.email || "-"}</Typography>
              <Typography variant="body2">Họ tên: {detail.customerProfile?.fullName || "-"}</Typography>
              <Typography variant="body2">
                Thu nhập hàng tháng: {detail.customerProfile?.monthlyIncome != null ? formatVnd(detail.customerProfile.monthlyIncome) : "-"}
              </Typography>
              <Typography variant="body2">
                DTI: {detail.customerProfile?.debtToIncomeRatio != null ? `${detail.customerProfile.debtToIncomeRatio}%` : "-"}
              </Typography>
              <Typography variant="body2">
                Phiếu lương: {detail.customerProfile?.payslipFileName || "-"}
                {detail.customerProfile?.payslipFileSize != null
                  ? ` (${formatFileSize(detail.customerProfile.payslipFileSize)})`
                  : ""}
              </Typography>
              <Typography variant="body2">
                Tải lên lúc: {detail.customerProfile?.payslipUploadedAt ? new Date(detail.customerProfile.payslipUploadedAt).toLocaleString() : "-"}
              </Typography>
              {detail.customerProfile?.payslipFileName && (
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
            </InfoCard>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Thông tin khoản vay">
              <Typography variant="body2">
                Trạng thái: <Chip size="small" color={statusColorMap[detail.status] || "default"} label={labelLoanStatus(detail.status)} />
              </Typography>
              <Typography variant="body2">Loại vay: {labelLoanType(detail.loanType)}</Typography>
              <Typography variant="body2">Số tiền: {formatVnd(detail.amount)}</Typography>
              <Typography variant="body2">Kỳ hạn: {detail.termMonths} tháng</Typography>
              <Typography variant="body2">Mục đích: {labelLoanPurpose(detail.purpose)}</Typography>
              {detail.collateralType && (
                <Typography variant="body2">Tài sản bảo đảm: {labelCollateralType(detail.collateralType)}</Typography>
              )}
              <Typography variant="body2">
                Hạn mức tạm tính: {detail.eligibleLimit != null ? formatVnd(detail.eligibleLimit) : "-"}
              </Typography>
              <Typography variant="body2">
                Số tiền phê duyệt: {detail.approvedAmount != null ? formatVnd(detail.approvedAmount) : "-"}
              </Typography>
              <Typography variant="body2">
                Kỳ hạn phê duyệt: {detail.approvedTermMonths != null ? `${detail.approvedTermMonths} tháng` : "-"}
              </Typography>
              <Typography variant="body2">
                Góp hằng tháng: {detail.approvedMonthlyPayment != null ? formatVnd(detail.approvedMonthlyPayment) : "-"}
              </Typography>
              <Typography variant="body2">
                Lãi suất phê duyệt: {detail.approvedAnnualRate != null ? `${(Number(detail.approvedAnnualRate) * 100).toFixed(2)}%/năm` : "-"}
              </Typography>
              <Typography variant="body2">Phiên bản chính sách: {detail.decisionPolicyVersion || "-"}</Typography>
              {detail.intakeNote && <Alert severity="info">{detail.intakeNote}</Alert>}
              <Typography variant="body2">Ngày nộp: {new Date(detail.createdAt).toLocaleString()}</Typography>
              <Typography variant="body2">Ghi chú quyết định: {detail.finalReason || "-"}</Typography>
              {detail.appointment && (
                <Alert severity="success">
                  Lịch hẹn gặp mặt: {new Date(detail.appointment.scheduledAt).toLocaleString()}
                  {detail.appointment.location ? ` tại ${detail.appointment.location}` : ""}
                  {detail.appointment.note ? `. Ghi chú: ${detail.appointment.note}` : ""}
                </Alert>
              )}
              {detail.status === "APPOINTMENT_SCHEDULED" && detail.loanType === "SECURED" && (
                <>
                  <Divider />
                  <Alert severity="warning">
                    Hồ sơ thế chấp đang chờ buổi gặp trực tiếp và thẩm định tài sản. Chỉ hoàn tất hợp đồng sau khi thủ tục này hoàn thành.
                  </Alert>
                  <Button
                    component={RouterLink}
                    to={`/staff/secured-procedures/${detail.id}`}
                    variant="contained"
                    sx={{ alignSelf: "flex-start" }}
                  >
                    Xử lý thủ tục vay thế chấp
                  </Button>
                </>
              )}
              {detail.contract && (
                <>
                  <Divider />
                  <Typography variant="body2">Trạng thái hợp đồng: {labelContractStatus(detail.contract.status)}</Typography>
                  <Typography variant="body2">
                    Thanh toán hàng tháng: {formatVnd(detail.contract.monthlyPayment)}
                  </Typography>
                  <Typography variant="body2">
                    Lãi suất: {(Number(detail.contract.annualInterestRate || 0) * 100).toFixed(2)}%
                  </Typography>
                </>
              )}
              {detail.status === "APPROVED" && !detail.contract && (
                <>
                  <Divider />
                  <Alert severity={detail.loanType === "SECURED" ? "warning" : "info"}>
                    Hồ sơ đã duyệt nhưng chưa có hợp đồng. Với vay thế chấp, chỉ hoàn thiện hợp đồng sau khi khách hàng gặp trực tiếp và đối chiếu tài sản.
                  </Alert>
                  {submitError && <Alert severity="error">{submitError}</Alert>}
                  {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                  {detail.loanType === "SECURED" ? (
                    <Button
                      component={RouterLink}
                      to={`/staff/secured-procedures/${detail.id}`}
                      variant="contained"
                      sx={{ alignSelf: "flex-start" }}
                    >
                      Xử lý thủ tục vay thế chấp
                    </Button>
                  ) : (
                    <Button
                      variant="contained"
                      onClick={handleCompleteContract}
                      disabled={submittingContract}
                      sx={{ alignSelf: "flex-start" }}
                    >
                      {submittingContract ? "Đang hoàn thiện..." : "Hoàn thiện hợp đồng vay"}
                    </Button>
                  )}
                </>
              )}
              {detail.status === "CONTRACTED" && detail.contract && (
                <>
                  <Divider />
                  <Alert severity="info">
                    Hồ sơ đã có hợp đồng hiệu lực nhưng chưa giải ngân. Sau khi giải ngân, khách hàng mới được phép thanh toán.
                  </Alert>
                  {submitError && <Alert severity="error">{submitError}</Alert>}
                  {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                  <Button
                    variant="contained"
                    onClick={handleDisburse}
                    disabled={submittingDisbursement}
                    sx={{ alignSelf: "flex-start" }}
                  >
                    {submittingDisbursement ? "Đang giải ngân..." : "Giải ngân khoản vay"}
                  </Button>
                </>
              )}
            </InfoCard>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Chứng từ hồ sơ vay">
              {!detail.documents?.length && (
                <Typography variant="body2" color="text.secondary">
                  Chưa có chứng từ đính kèm.
                </Typography>
              )}
              {detail.documents?.map((document) => (
                <Stack
                  key={document.documentType}
                  direction={{ xs: "column", sm: "row" }}
                  spacing={1}
                  alignItems={{ sm: "center" }}
                >
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    {labelLoanDocumentType(document.documentType)}: {document.fileName}
                    {document.fileSize != null ? ` (${formatFileSize(document.fileSize)})` : ""}
                  </Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => handleDownloadDocument(document)}
                    disabled={downloadingDocument === document.documentType}
                  >
                    {downloadingDocument === document.documentType ? "Đang tải..." : "Tải xuống"}
                  </Button>
                </Stack>
              ))}
            </InfoCard>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Kết quả DSS">
              {!detail.dss && (
                <Alert severity="warning">
                  Không tìm thấy bản ghi DSS cho hồ sơ này.
                </Alert>
              )}
              {detail.dss && (
                <>
                  <Typography variant="body2">Điểm tín dụng: {detail.dss.creditScore}</Typography>
                  <Typography variant="body2">Hạng rủi ro: {labelRiskRank(detail.dss.riskRank)}</Typography>
                  <Typography variant="body2">Phân khúc: {labelCustomerSegment(detail.dss.customerSegment)}</Typography>
                  <Typography variant="body2">Khuyến nghị: {labelDssRecommendation(detail.dss.recommendation)}</Typography>
                  <Alert severity="info" sx={{ mt: 1 }}>
                    {detail.dss.explanation}
                  </Alert>
                </>
              )}
            </InfoCard>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Xác minh và rủi ro">
              {!detail.verification && (
                <Typography variant="body2" color="text.secondary">
                  Chưa có dữ liệu xác minh.
                </Typography>
              )}
              {detail.verification && (
                <>
                  <Typography variant="body2">Giấy tờ: {labelVerificationStatus(detail.verification.documentStatus)}</Typography>
                  <Typography variant="body2">Định danh: {labelVerificationStatus(detail.verification.identityStatus)}</Typography>
                  <Typography variant="body2">Đối khớp khuôn mặt: {labelVerificationStatus(detail.verification.faceMatchStatus)}</Typography>
                  <Typography variant="body2">Thu nhập: {labelVerificationStatus(detail.verification.incomeStatus)}</Typography>
                  <Typography variant="body2">KYC: {labelVerificationStatus(detail.verification.kycStatus)}</Typography>
                  <Typography variant="body2">AML: {labelVerificationStatus(detail.verification.amlStatus)}</Typography>
                  <Typography variant="body2">
                    Cờ gian lận: {detail.verification.fraudFlag ? "Có" : "Không"}
                  </Typography>
                  {detail.verification.note && <Alert severity="info">{detail.verification.note}</Alert>}
                  <Divider />
                  <Stack spacing={2} component="form" onSubmit={handleSaveVerification}>
                    <Typography variant="subtitle2">Cập nhật từng bước xác minh</Typography>
                    <Grid container spacing={1.5}>
                      {[
                        ["documentStatus", "Giấy tờ"],
                        ["identityStatus", "Định danh"],
                        ["faceMatchStatus", "Đối khớp khuôn mặt"],
                        ["incomeStatus", "Thu nhập"],
                        ["kycStatus", "KYC"],
                        ["amlStatus", "AML"]
                      ].map(([field, label]) => (
                        <Grid item xs={12} sm={6} key={field}>
                          <TextField
                            select
                            size="small"
                            label={label}
                            value={verificationForm[field]}
                            onChange={handleVerificationChange(field)}
                            fullWidth
                            disabled={submittingVerification}
                            {...fieldErrorProps(verificationFieldErrors, field)}
                          >
                            {VERIFICATION_STATUSES.map((status) => (
                              <MenuItem key={status} value={status}>{labelVerificationStatus(status)}</MenuItem>
                            ))}
                          </TextField>
                        </Grid>
                      ))}
                      <Grid item xs={12} sm={6}>
                        <TextField
                          select
                          size="small"
                          label="Cờ gian lận"
                          value={String(verificationForm.fraudFlag)}
                          onChange={handleVerificationChange("fraudFlag")}
                          fullWidth
                          disabled={submittingVerification}
                        >
                          <MenuItem value="false">Không</MenuItem>
                          <MenuItem value="true">Có</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12}>
                        <TextField
                          size="small"
                          label="Ghi chú xác minh"
                          value={verificationForm.note}
                          onChange={handleVerificationChange("note")}
                          fullWidth
                          multiline
                          minRows={2}
                          disabled={submittingVerification}
                          {...fieldErrorProps(verificationFieldErrors, "note")}
                        />
                      </Grid>
                      <Grid item xs={12}>
                        <Button type="submit" variant="outlined" disabled={submittingVerification}>
                          {submittingVerification ? "Đang lưu..." : "Lưu xác minh"}
                        </Button>
                      </Grid>
                    </Grid>
                  </Stack>
                </>
              )}
              <Divider />
              {!detail.risk && (
                <Typography variant="body2" color="text.secondary">
                  Không tìm thấy bản ghi đánh giá rủi ro.
                </Typography>
              )}
              {detail.risk && (
                <>
                  <Typography variant="body2">Mức rủi ro tổng: {labelRiskLevel(detail.risk.overallRiskLevel)}</Typography>
                  <Typography variant="body2">Rủi ro tín dụng: {detail.risk.creditRiskScore}</Typography>
                  <Typography variant="body2">Rủi ro gian lận: {detail.risk.fraudRiskScore}</Typography>
                  <Typography variant="body2">Rủi ro vận hành: {detail.risk.operationalRiskScore}</Typography>
                  <Alert severity={detail.risk.overallRiskLevel === "HIGH" ? "warning" : "info"}>
                    {detail.risk.riskReasons}
                  </Alert>
                </>
              )}
            </InfoCard>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Quyết định cuối cùng">
              <Stack spacing={2} component="form" onSubmit={handleSubmitDecision}>
                {submitError && <Alert severity="error">{submitError}</Alert>}
                {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                {finalized && (
                  <Alert severity="info">
                    Hồ sơ này đã chốt kết quả. Không thể gửi thêm quyết định.
                  </Alert>
                )}
                <TextField
                  select
                  label="Hành động"
                  value={decision.action}
                  onChange={handleDecisionChange("action")}
                  disabled={submitting || finalized}
                  {...fieldErrorProps(decisionFieldErrors, "action")}
                >
                  <MenuItem value="">
                    <em>Chọn hành động</em>
                  </MenuItem>
                  <MenuItem value="APPROVE">Duyệt</MenuItem>
                  <MenuItem value="REJECT">Từ chối</MenuItem>
                </TextField>
                {showApprovalFields && (
                  <>
                    <Grid container spacing={1.5}>
                      <Grid item xs={12} sm={4}>
                        <TextField
                          label="Số tiền phê duyệt"
                          type="text"
                          value={decision.approvedAmount}
                          onChange={handleApprovedAmountChange}
                          disabled={submitting || finalized}
                          fullWidth
                          inputProps={{ inputMode: "numeric" }}
                          {...fieldErrorProps(decisionFieldErrors, "approvedAmount")}
                        />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField
                          label="Kỳ hạn phê duyệt"
                          type="number"
                          value={decision.approvedTermMonths}
                          onChange={handleDecisionChange("approvedTermMonths")}
                          disabled={submitting || finalized}
                          fullWidth
                          inputProps={{ min: 1, step: 1 }}
                          {...fieldErrorProps(decisionFieldErrors, "approvedTermMonths")}
                        />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField
                          label="Lãi suất năm"
                          type="number"
                          value={decision.approvedAnnualRate}
                          onChange={handleDecisionChange("approvedAnnualRate")}
                          disabled={submitting || finalized}
                          fullWidth
                          inputProps={{ min: 0, max: 1, step: 0.001 }}
                          {...fieldErrorProps(decisionFieldErrors, "approvedAnnualRate", "Nhập dạng thập phân, ví dụ 0.12")}
                        />
                      </Grid>
                    </Grid>
                  </>
                )}
                {showAppointmentFields && (
                  <>
                    <TextField
                      label="Lịch hẹn gặp mặt"
                      type="datetime-local"
                      required
                      value={decision.scheduledAt}
                      onChange={handleDecisionChange("scheduledAt")}
                      disabled={submitting || finalized}
                      InputLabelProps={{ shrink: true }}
                      {...fieldErrorProps(decisionFieldErrors, "scheduledAt", "Chọn thời điểm khách hàng đến gặp trực tiếp.")}
                    />
                    <TextField
                      label="Ghi chú lịch hẹn"
                      multiline
                      rows={3}
                      value={decision.appointmentNote}
                      onChange={handleDecisionChange("appointmentNote")}
                      disabled={submitting || finalized}
                      placeholder="Nhắc khách hàng mang bản gốc CCCD, giấy tờ xe và hồ sơ tài sản bảo đảm."
                      {...fieldErrorProps(decisionFieldErrors, "appointmentNote")}
                    />
                  </>
                )}
                {showRejectReasonField && (
                  <TextField
                    label="Lý do từ chối"
                    multiline
                    rows={3}
                    required
                    value={decision.rejectionReason}
                    onChange={handleDecisionChange("rejectionReason")}
                    disabled={submitting || finalized}
                    placeholder="Nhập lý do từ chối hồ sơ."
                    {...fieldErrorProps(decisionFieldErrors, "rejectionReason")}
                  />
                )}
                <Button type="submit" variant="contained" disabled={submitting || finalized || !hasSelectedAction}>
                  {submitting ? "Đang gửi..." : "Gửi quyết định"}
                </Button>
              </Stack>
            </InfoCard>
          </Grid>
          <Grid item xs={12}>
            <InfoCard title="Lịch sử quyết định">
              {!detail.decisionAudits?.length && (
                <Typography variant="body2" color="text.secondary">
                  Chưa có bản ghi quyết định.
                </Typography>
              )}
              {detail.decisionAudits?.length > 0 && (
                <Paper variant="outlined" sx={{ overflowX: "auto" }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Thời gian</TableCell>
                        <TableCell>Nhân viên</TableCell>
                        <TableCell>Hành động</TableCell>
                        <TableCell>Ghi chú xử lý</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {detail.decisionAudits.map((audit) => (
                        <TableRow key={audit.id}>
                          <TableCell>{new Date(audit.createdAt).toLocaleString()}</TableCell>
                          <TableCell>{audit.staffEmail}</TableCell>
                          <TableCell>{labelStaffAction(audit.action)}</TableCell>
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
