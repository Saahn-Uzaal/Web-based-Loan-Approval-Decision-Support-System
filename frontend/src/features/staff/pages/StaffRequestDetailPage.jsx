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
import {
  downloadInformationVerificationIdentityCardApi,
  downloadInformationVerificationPayslipApi
} from "@/features/staff/api/informationVerificationApi";
import {
  assignStaffCaseApi,
  disburseStaffLoanApi,
  downloadStaffLoanDocumentApi,
  getStaffRequestDetailApi,
  releaseStaffCaseApi,
  resolveOverdueLoanApi,
  submitStaffDecisionApi,
  updateStaffRequestVerificationApi
} from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import {
  DEFAULT_ADDITIONAL_INFO_DEADLINE_DAYS,
  MAX_ADDITIONAL_INFO_REQUESTS
} from "@/shared/constants/loanApplicationPolicy";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { formatFileSize } from "@/shared/utils/files";
import { formatPercentInputFromFraction, normalizePercentInput, percentInputToFraction } from "@/shared/utils/percent";
import {
  labelContractStatus,
  labelCollateralType,
  labelCreditBureauStatus,
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

function defaultAdditionalInfoDeadlineInputValue() {
  const date = new Date();
  date.setDate(date.getDate() + DEFAULT_ADDITIONAL_INFO_DEADLINE_DAYS);
  date.setHours(17, 0, 0, 0);
  return toDateTimeLocalValue(date);
}

function toIsoInstant(localDateTime) {
  return localDateTime ? new Date(localDateTime).toISOString() : null;
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("vi-VN");
}

const VERIFICATION_STATUSES = ["PENDING", "PASSED", "FAILED"];
const DECISION_EDITABLE_STATUSES = ["PENDING"];
const VERIFICATION_EDITABLE_STATUSES = ["PENDING", "NEEDS_MORE_INFO"];

const decisionFieldKeywords = {
  action: ["hành động", "quyết định", "action"],
  approvedAmount: ["số tiền phê duyệt", "số tiền", "approvedAmount"],
  approvedTermMonths: ["kỳ hạn phê duyệt", "kỳ hạn", "approvedTermMonths"],
  approvedAnnualRate: ["lãi suất", "approvedAnnualRate"],
  scheduledAt: ["lịch hẹn", "thời điểm", "scheduledAt"],
  appointmentNote: ["ghi chú lịch hẹn", "appointmentNote"],
  additionalInfoRequestNote: ["nội dung bổ sung", "bổ sung hồ sơ", "additionalInfoRequestNote"],
  additionalInfoDeadlineAt: ["hạn bổ sung", "deadline", "additionalInfoDeadlineAt"],
  rejectionReason: ["lý do từ chối", "từ chối", "rejectionReason"]
};

const verificationStepFieldKeywords = {
  documentStatus: ["giấy tờ", "documentStatus"],
  identityStatus: ["định danh", "identityStatus"],
  faceMatchStatus: ["khuôn mặt", "faceMatchStatus"],
  incomeStatus: ["thu nhập", "incomeStatus"],
  verifiedMonthlyIncome: ["thu nhập đã xác minh", "verifiedMonthlyIncome"],
  kycStatus: ["kyc"],
  amlStatus: ["aml"],
  note: ["ghi chú xác minh", "note"]
};

const overdueResolutionFieldKeywords = {
  extensionDays: ["gia hạn", "số ngày", "extensionDays"],
  waivedLateFeeAmount: ["phí chậm trả", "late fee", "waivedLateFeeAmount"],
  reason: ["lý do xử lý", "reason"]
};

function resolveWorkflowStage(status) {
  if (status === "PENDING" || status === "NEEDS_MORE_INFO") {
    return {
      title: "Thẩm định hồ sơ",
      description: "Rà soát hồ sơ vay, cập nhật xác minh trước phê duyệt và đưa ra quyết định thẩm định ban đầu."
    };
  }
  if (status === "APPOINTMENT_SCHEDULED") {
    return {
      title: "Thủ tục vay thế chấp",
      description: "Hồ sơ đã qua quyết định ban đầu và đang chờ gặp mặt trực tiếp để đối chiếu tài sản bảo đảm."
    };
  }
  if (status === "APPROVED" || status === "CONTRACTED") {
    return {
      title: "Tiền giải ngân",
      description: "Hồ sơ đang ở giai đoạn hoàn tất điều khoản, hiệu lực hợp đồng và chuẩn bị giải ngân."
    };
  }
  if (status === "ACTIVE") {
    return {
      title: "Theo dõi khoản vay",
      description: "Khoản vay đã kích hoạt. Màn hình này dùng để tra cứu hồ sơ, hợp đồng và lịch sử quyết định."
    };
  }
  if (status === "OVERDUE") {
    return {
      title: "Xử lý khoản vay quá hạn",
      description: "Khoản vay đã quá hạn. Nhân viên có thể xem hồ sơ gốc, snapshot công nợ hiện tại và thực hiện gia hạn hoặc miễn phí chậm trả ngay tại đây."
    };
  }
  return {
    title: "Chi tiết hồ sơ vay",
    description: "Theo dõi hồ sơ vay và toàn bộ thông tin đã được lưu trong quá trình xử lý."
  };
}

export default function StaffRequestDetailPage() {
  const { id } = useParams();
  const { accessToken, user } = useAuth();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submittingDisbursement, setSubmittingDisbursement] = useState(false);
  const [submittingOverdueResolution, setSubmittingOverdueResolution] = useState(false);
  const [submittingVerification, setSubmittingVerification] = useState(false);
  const [claimingCase, setClaimingCase] = useState(false);
  const [releasingCase, setReleasingCase] = useState(false);
  const [downloadingPayslip, setDownloadingPayslip] = useState(false);
  const [downloadingIdentityCard, setDownloadingIdentityCard] = useState("");
  const [downloadingDocument, setDownloadingDocument] = useState("");
  const [decisionFieldErrors, setDecisionFieldErrors] = useState({});
  const [verificationFieldErrors, setVerificationFieldErrors] = useState({});
  const [overdueResolutionFieldErrors, setOverdueResolutionFieldErrors] = useState({});
  const [decision, setDecision] = useState({
    action: "",
    scheduledAt: defaultAppointmentInputValue(),
    appointmentNote: "",
    additionalInfoRequestNote: "",
    additionalInfoDeadlineAt: defaultAdditionalInfoDeadlineInputValue(),
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
    verifiedMonthlyIncome: "",
    kycStatus: "PENDING",
    amlStatus: "PENDING",
    fraudFlag: false,
    note: ""
  });
  const [overdueResolution, setOverdueResolution] = useState({
    extensionDays: "",
    waivedLateFeeAmount: "",
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
      setDecisionFieldErrors({});
      setVerificationFieldErrors({});
      setOverdueResolutionFieldErrors({});
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
          verifiedMonthlyIncome:
            response?.verification?.verifiedMonthlyIncome != null
              ? formatVndInput(response.verification.verifiedMonthlyIncome)
              : response?.customerProfile?.verifiedMonthlyIncome != null
                ? formatVndInput(response.customerProfile.verifiedMonthlyIncome)
                : response?.customerProfile?.monthlyIncome != null
                  ? formatVndInput(response.customerProfile.monthlyIncome)
                  : "",
          kycStatus: response?.verification?.kycStatus || "PENDING",
          amlStatus: response?.verification?.amlStatus || "PENDING",
          fraudFlag: Boolean(response?.verification?.fraudFlag),
          note: response?.verification?.note || ""
        });
        setDecision((prev) => {
          if (prev.action || prev.appointmentNote || prev.additionalInfoRequestNote || prev.rejectionReason) {
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
            approvedAnnualRate: formatPercentInputFromFraction(response?.approvedAnnualRate, 3)
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
  const verificationEditable = detail ? VERIFICATION_EDITABLE_STATUSES.includes(detail.status) : false;
  const assignmentOwnedByCurrentUser = Boolean(user?.id && detail?.assignment?.staffUserId === user.id);
  const assignmentBlockedByOtherStaff = Boolean(detail?.assignment?.staffUserId && !assignmentOwnedByCurrentUser);
  const stage = resolveWorkflowStage(detail?.status);
  const isSecuredLoan = detail?.loanType === "SECURED";
  const faceMatchApplicable = !isSecuredLoan;
  const showApprovalFields = decision.action === "APPROVE";
  const showAppointmentFields = showApprovalFields && isSecuredLoan;
  const showRejectReasonField = decision.action === "REJECT";
  const showMoreInfoReasonField = decision.action === "REQUEST_MORE_INFO";
  const hasSelectedAction = ["APPROVE", "REJECT", "REQUEST_MORE_INFO"].includes(decision.action);
  const additionalInfoRequestCount = Number(detail?.additionalInfoRequestCount || 0);
  const additionalInfoRequestLimitReached = additionalInfoRequestCount >= MAX_ADDITIONAL_INFO_REQUESTS;
  const hasDisbursementAccount = Boolean(
    detail?.customerProfile?.bankAccountNumber && detail?.customerProfile?.bankName
  );
  const repayment = detail?.repayment || null;
  const verificationStepFields = [
    ["documentStatus", "Giấy tờ"],
    ["identityStatus", "Định danh"],
    ...(faceMatchApplicable ? [["faceMatchStatus", "Đối khớp khuôn mặt"]] : []),
    ["incomeStatus", "Thu nhập"],
    ["kycStatus", "KYC"],
    ["amlStatus", "AML"]
  ];

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

  const handleApprovedAnnualRateChange = (event) => {
    setDecisionFieldErrors((prev) => clearFieldError(prev, "approvedAnnualRate"));
    setDecision((prev) => ({
      ...prev,
      approvedAnnualRate: normalizePercentInput(event.target.value)
    }));
  };

  const handleVerificationChange = (field) => (event) => {
    const value = field === "fraudFlag"
      ? event.target.value === "true"
      : field === "verifiedMonthlyIncome"
        ? formatVndInput(event.target.value)
        : event.target.value;
    setVerificationFieldErrors((prev) => clearFieldError(prev, field));
    setVerificationForm((prev) => ({
      ...prev,
      [field]: value
    }));
  };

  const handleOverdueResolutionChange = (field) => (event) => {
    setOverdueResolutionFieldErrors((prev) => clearFieldError(prev, field));
    setOverdueResolution((prev) => ({
      ...prev,
      [field]: field === "waivedLateFeeAmount"
        ? formatVndInput(event.target.value)
        : event.target.value
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
      await updateStaffRequestVerificationApi(accessToken, detail.id, {
        ...verificationForm,
        verifiedMonthlyIncome:
          verificationForm.incomeStatus === "PASSED"
            ? parseVndInput(verificationForm.verifiedMonthlyIncome)
            : null
      });
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setVerificationForm({
        documentStatus: refreshed?.verification?.documentStatus || "PENDING",
        identityStatus: refreshed?.verification?.identityStatus || "PENDING",
        faceMatchStatus: refreshed?.verification?.faceMatchStatus || "PENDING",
        incomeStatus: refreshed?.verification?.incomeStatus || "PENDING",
        verifiedMonthlyIncome:
          refreshed?.verification?.verifiedMonthlyIncome != null
            ? formatVndInput(refreshed.verification.verifiedMonthlyIncome)
            : refreshed?.customerProfile?.verifiedMonthlyIncome != null
              ? formatVndInput(refreshed.customerProfile.verifiedMonthlyIncome)
              : refreshed?.customerProfile?.monthlyIncome != null
                ? formatVndInput(refreshed.customerProfile.monthlyIncome)
                : "",
        kycStatus: refreshed?.verification?.kycStatus || "PENDING",
        amlStatus: refreshed?.verification?.amlStatus || "PENDING",
        fraudFlag: Boolean(refreshed?.verification?.fraudFlag),
        note: refreshed?.verification?.note || ""
      });
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
    if (showMoreInfoReasonField && !decision.additionalInfoRequestNote.trim()) {
      const message = "Vui lòng nhập nội dung cần khách hàng bổ sung.";
      setSubmitError(message);
      setDecisionFieldErrors({ additionalInfoRequestNote: message });
      return;
    }
    if (showMoreInfoReasonField && !decision.additionalInfoDeadlineAt) {
      const message = "Vui lòng chọn hạn bổ sung hồ sơ.";
      setSubmitError(message);
      setDecisionFieldErrors({ additionalInfoDeadlineAt: message });
      return;
    }
    if (showMoreInfoReasonField && additionalInfoRequestLimitReached) {
      const message = `Hồ sơ này đã đạt giới hạn ${MAX_ADDITIONAL_INFO_REQUESTS} lần yêu cầu bổ sung.`;
      setSubmitError(message);
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
        appointmentNote: shouldSchedule
          ? decision.appointmentNote.trim()
          : decision.action === "REJECT"
            ? decision.rejectionReason.trim()
            : null,
        additionalInfoRequestNote: decision.action === "REQUEST_MORE_INFO"
          ? decision.additionalInfoRequestNote.trim()
          : null,
        additionalInfoDeadlineAt: decision.action === "REQUEST_MORE_INFO"
          ? toIsoInstant(decision.additionalInfoDeadlineAt)
          : null,
        approvedAmount: decision.action === "APPROVE" ? parseVndInput(decision.approvedAmount) : null,
        approvedTermMonths: decision.action === "APPROVE" && decision.approvedTermMonths !== "" ? Number(decision.approvedTermMonths) : null,
        approvedAnnualRate: decision.action === "APPROVE" ? percentInputToFraction(decision.approvedAnnualRate) : null
      });
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess(`Gửi quyết định thành công. Trạng thái hiện tại: ${labelLoanStatus(refreshed.status)}.`);
      setDecision((prev) => ({
        ...prev,
        appointmentNote: "",
        additionalInfoRequestNote: "",
        additionalInfoDeadlineAt: defaultAdditionalInfoDeadlineInputValue(),
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

  const handleDownloadIdentityCard = async (side) => {
    const fileName = side === "front"
      ? detail?.customerProfile?.identityCardFrontFileName
      : detail?.customerProfile?.identityCardBackFileName;
    if (!detail?.customer?.id || !fileName) {
      return;
    }
    setDownloadingIdentityCard(side);
    setError("");
    try {
      await downloadInformationVerificationIdentityCardApi(accessToken, detail.customer.id, side, fileName);
    } catch (err) {
      setError(err.message || "Không tải được ảnh CCCD");
    } finally {
      setDownloadingIdentityCard("");
    }
  };

  const handleDownloadDocument = async (document) => {
    if (!detail?.id || !document?.id) {
      return;
    }
    setDownloadingDocument(String(document.id));
    setError("");
    try {
      await downloadStaffLoanDocumentApi(accessToken, detail.id, document.id, document.fileName);
    } catch (err) {
      setError(err.message || "Không tải được chứng từ hồ sơ vay");
    } finally {
      setDownloadingDocument("");
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
      setSubmitSuccess("Đã giải ngân và kích hoạt khoản vay. Khách hàng có thể bắt đầu thanh toán.");
    } catch (err) {
      setSubmitError(err.message || "Không giải ngân được khoản vay");
    } finally {
      setSubmittingDisbursement(false);
    }
  };

  const handleAssignCase = async () => {
    if (!detail?.id) {
      return;
    }
    setClaimingCase(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      const refreshed = await assignStaffCaseApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess("Bạn đã nhận phụ trách hồ sơ này.");
    } catch (err) {
      setSubmitError(err.message || "Không nhận được phụ trách hồ sơ");
    } finally {
      setClaimingCase(false);
    }
  };

  const handleReleaseCase = async () => {
    if (!detail?.id) {
      return;
    }
    setReleasingCase(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      const refreshed = await releaseStaffCaseApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess("Bạn đã bỏ nhận hồ sơ này.");
    } catch (err) {
      setSubmitError(err.message || "Không thể bỏ nhận hồ sơ");
    } finally {
      setReleasingCase(false);
    }
  };

  const handleResolveOverdueLoan = async (event) => {
    event.preventDefault();
    if (!detail?.id) {
      return;
    }
    setSubmittingOverdueResolution(true);
    setSubmitError("");
    setSubmitSuccess("");
    setOverdueResolutionFieldErrors({});
    try {
      const refreshed = await resolveOverdueLoanApi(accessToken, detail.id, {
        extensionDays: overdueResolution.extensionDays !== "" ? Number(overdueResolution.extensionDays) : 0,
        waivedLateFeeAmount: parseVndInput(overdueResolution.waivedLateFeeAmount) || 0,
        reason: overdueResolution.reason.trim()
      });
      setDetail(refreshed);
      setOverdueResolution({
        extensionDays: "",
        waivedLateFeeAmount: "",
        reason: ""
      });
      setSubmitSuccess(
        refreshed.status === "OVERDUE"
          ? "Đã cập nhật phương án xử lý nợ quá hạn. Khoản vay vẫn còn quá hạn và sẽ tiếp tục được theo dõi."
          : `Đã xử lý khoản vay quá hạn. Trạng thái hiện tại: ${labelLoanStatus(refreshed.status)}.`
      );
    } catch (err) {
      const message = err.message || "Không xử lý được khoản vay quá hạn";
      setSubmitError(message);
      setOverdueResolutionFieldErrors(mapFieldErrors(message, overdueResolutionFieldKeywords));
    } finally {
      setSubmittingOverdueResolution(false);
    }
  };

  const statusColorMap = {
    DRAFT: "default",
    NEEDS_MORE_INFO: "warning",
    WITHDRAWN: "default",
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    ACTIVE: "primary",
    OVERDUE: "error",
    CLOSED: "default",
    REJECTED: "error",
    PENDING: "warning"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">{stage.title} #{id}</Typography>
      <Typography color="text.secondary">
        {stage.description}
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
        <>
          {(detail.status === "PENDING" && detail.reviewDeadlineAt) && (
            <Alert severity="info">
              SLA thẩm định của hồ sơ này đến {formatDateTime(detail.reviewDeadlineAt)}. Quá hạn này mà chưa có quyết định,
              hệ thống sẽ tự hủy hồ sơ.
            </Alert>
          )}
          {(detail.status === "APPROVED" && detail.contractAcceptanceDeadlineAt) && (
            <Alert severity="warning">
              Hồ sơ đang chờ khách hàng chấp nhận hợp đồng đến {formatDateTime(detail.contractAcceptanceDeadlineAt)}.
              Nếu khách hàng không xác nhận trước hạn, hệ thống sẽ tự hủy hồ sơ và đóng hợp đồng chờ ký.
            </Alert>
          )}
          <Grid container spacing={2}>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Stack
                direction={{ xs: "column", md: "row" }}
                spacing={1.5}
                justifyContent="space-between"
                alignItems={{ xs: "flex-start", md: "center" }}
              >
                <Stack spacing={0.5}>
                  <Typography variant="subtitle1">Phân công xử lý hồ sơ</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {detail.assignment?.staffEmail
                      ? `Đang do ${detail.assignment.staffEmail} phụ trách${detail.assignment.assignedAt ? ` từ ${new Date(detail.assignment.assignedAt).toLocaleString()}` : ""}.`
                      : "Hồ sơ này chưa có người phụ trách."}
                  </Typography>
                  {assignmentBlockedByOtherStaff && (
                    <Alert severity="info">
                      Bạn đang ở chế độ chỉ xem. Chỉ nhân viên đang phụ trách mới được cập nhật xác minh, ra quyết định hoặc giải ngân.
                    </Alert>
                  )}
                </Stack>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  {!detail.assignment && (
                    <Button variant="outlined" onClick={handleAssignCase} disabled={claimingCase}>
                      {claimingCase ? "Đang nhận..." : "Nhận phụ trách"}
                    </Button>
                  )}
                  {assignmentOwnedByCurrentUser && (
                    <Button variant="text" color="inherit" onClick={handleReleaseCase} disabled={releasingCase}>
                      {releasingCase ? "Đang bỏ nhận..." : "Bỏ nhận hồ sơ"}
                    </Button>
                  )}
                </Stack>
              </Stack>
            </Paper>
          </Grid>
          <Grid item xs={12} md={6}>
            <InfoCard title="Tóm tắt hồ sơ khách hàng">
              <Typography variant="body2">Mã khách hàng: #{detail.customer?.id}</Typography>
              <Typography variant="body2">Email: {detail.customer?.email || "-"}</Typography>
              <Typography variant="body2">Họ tên: {detail.customerProfile?.fullName || "-"}</Typography>
              <Typography variant="body2">Số CCCD: {detail.customerProfile?.identityNumber || "-"}</Typography>
              <Typography variant="body2">
                Số tài khoản nhận giải ngân: {detail.customerProfile?.bankAccountNumber || "-"}
              </Typography>
              <Typography variant="body2">
                Ngân hàng nhận giải ngân: {detail.customerProfile?.bankName || "-"}
              </Typography>
              {!hasDisbursementAccount && (
                <Alert severity="warning">
                  Khách hàng chưa cập nhật đủ thông tin tài khoản nhận giải ngân. Không thể giải ngân cho tới khi hồ sơ có số tài khoản và tên ngân hàng.
                </Alert>
              )}
              <Typography variant="body2">
                Thu nhập hàng tháng: {detail.customerProfile?.monthlyIncome != null ? formatVnd(detail.customerProfile.monthlyIncome) : "-"}
              </Typography>
              <Typography variant="body2">
                Thu nhập đã xác minh sẵn có: {detail.customerProfile?.verifiedMonthlyIncome != null ? formatVnd(detail.customerProfile.verifiedMonthlyIncome) : "-"}
              </Typography>
              <Typography variant="body2">
                DTI: {detail.customerProfile?.debtToIncomeRatio != null ? `${detail.customerProfile.debtToIncomeRatio}%` : "-"}
              </Typography>
              <Typography variant="body2">Điểm tín dụng nội bộ: {detail.customerProfile?.creditHistoryScore ?? "-"}</Typography>
              {detail.customerProfile?.creditCheck && (
                <Alert severity={detail.customerProfile.creditCheck.hardReject ? "error" : detail.customerProfile.creditCheck.manualReviewRequired ? "warning" : "info"}>
                  Tra cứu tín dụng nội bộ theo CCCD: {labelCreditBureauStatus(detail.customerProfile.creditCheck.bureauStatus)}.
                  {detail.customerProfile.creditCheck.creditScore != null ? ` Điểm: ${detail.customerProfile.creditCheck.creditScore}.` : ""}
                  {detail.customerProfile.creditCheck.manualReviewRequired ? " Cần thẩm định thủ công." : ""}
                  {detail.customerProfile.creditCheck.hardReject ? " Có cờ từ chối cứng." : ""}
                  {detail.customerProfile.creditCheck.riskNote ? ` Ghi chú: ${detail.customerProfile.creditCheck.riskNote}` : ""}
                </Alert>
              )}
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
              <Typography variant="body2">
                CCCD mặt trước: {detail.customerProfile?.identityCardFrontFileName || "-"}
                {detail.customerProfile?.identityCardFrontFileSize != null
                  ? ` (${formatFileSize(detail.customerProfile.identityCardFrontFileSize)})`
                  : ""}
              </Typography>
              <Typography variant="body2">
                Tải lên lúc: {detail.customerProfile?.identityCardFrontUploadedAt ? new Date(detail.customerProfile.identityCardFrontUploadedAt).toLocaleString() : "-"}
              </Typography>
              {detail.customerProfile?.identityCardFrontFileName && (
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
                CCCD mặt sau: {detail.customerProfile?.identityCardBackFileName || "-"}
                {detail.customerProfile?.identityCardBackFileSize != null
                  ? ` (${formatFileSize(detail.customerProfile.identityCardBackFileSize)})`
                  : ""}
              </Typography>
              <Typography variant="body2">
                Tải lên lúc: {detail.customerProfile?.identityCardBackUploadedAt ? new Date(detail.customerProfile.identityCardBackUploadedAt).toLocaleString() : "-"}
              </Typography>
              {detail.customerProfile?.identityCardBackFileName && (
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
                Hạn mức đánh giá nội bộ: {detail.eligibleLimit != null ? formatVnd(detail.eligibleLimit) : "-"}
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
              <Typography variant="body2">
                Số lần yêu cầu bổ sung: {additionalInfoRequestCount}/{MAX_ADDITIONAL_INFO_REQUESTS}
              </Typography>
              <Typography variant="body2">
                Yêu cầu bổ sung gần nhất: {detail.additionalInfoLastRequestedAt ? new Date(detail.additionalInfoLastRequestedAt).toLocaleString() : "-"}
              </Typography>
              <Typography variant="body2">
                Hạn bổ sung hiện tại: {detail.additionalInfoRequestDeadline ? new Date(detail.additionalInfoRequestDeadline).toLocaleString() : "-"}
              </Typography>
              {detail.intakeNote && <Alert severity="info">{detail.intakeNote}</Alert>}
              {detail.additionalInfoRequestNote && (
                <Alert severity="warning">
                  Nội dung yêu cầu bổ sung hiện tại: {detail.additionalInfoRequestNote}
                </Alert>
              )}
              {additionalInfoRequestLimitReached && (
                <Alert severity="warning">
                  Hồ sơ đã đạt giới hạn {MAX_ADDITIONAL_INFO_REQUESTS} lần yêu cầu bổ sung. Lần tiếp theo nên chuyển sang từ chối hoặc luồng xử lý khác.
                </Alert>
              )}
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
                  {detail.loanType !== "SECURED" && (
                    <Alert severity="info">
                      Cho khách hàng chấp nhận điều khoản/ký hợp đồng trên màn hình khách hàng trước khi giải ngân.
                    </Alert>
                  )}
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
                    <Alert severity="info">
                      Hợp đồng tín chấp được tạo để khách hàng tự xem và chấp nhận điều khoản trên màn hình khách hàng trước khi giải ngân.
                    </Alert>
                  )}
                </>
              )}
              {detail.status === "APPROVED" && detail.contract && (
                <>
                  <Divider />
                  <Alert severity="info">
                    Hợp đồng đã được tạo và đang chờ khách hàng xem, chấp nhận điều khoản trên màn hình khách hàng trước khi chuyển sang trạng thái đã ký hợp đồng.
                  </Alert>
                </>
              )}
              {detail.status === "CONTRACTED" && detail.contract && (
                <>
                  <Divider />
                  <Alert severity="info">
                    Hồ sơ đã có hợp đồng hiệu lực nhưng chưa giải ngân. Sau khi giải ngân, khách hàng mới được phép thanh toán.
                  </Alert>
                  {!hasDisbursementAccount && (
                    <Alert severity="warning">
                      Thiếu thông tin nhận giải ngân của khách hàng. Hãy yêu cầu khách hàng cập nhật số tài khoản và tên ngân hàng trong hồ sơ gốc trước khi tiếp tục.
                    </Alert>
                  )}
                  {submitError && <Alert severity="error">{submitError}</Alert>}
                  {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                  {detail.loanType === "SECURED" && (
                    <Button
                      component={RouterLink}
                      to={`/staff/secured-procedures/${detail.id}`}
                      variant="outlined"
                      sx={{ alignSelf: "flex-start" }}
                    >
                      Mở lại thủ tục vay thế chấp
                    </Button>
                  )}
                  <Button
                    variant="contained"
                    onClick={handleDisburse}
                    disabled={submittingDisbursement || assignmentBlockedByOtherStaff || !hasDisbursementAccount}
                    sx={{ alignSelf: "flex-start" }}
                  >
                    {submittingDisbursement ? "Đang giải ngân..." : "Giải ngân khoản vay"}
                  </Button>
                </>
              )}
              {detail.status === "ACTIVE" && (
                <>
                  <Divider />
                  <Alert severity="success">
                    Khoản vay đã kích hoạt ngay sau giải ngân và đang ở giai đoạn theo dõi thanh toán định kỳ.
                  </Alert>
                </>
              )}
              {detail.status === "OVERDUE" && (
                <>
                  <Divider />
                  <Alert severity="warning">
                    Khoản vay đang quá hạn. Bạn có thể xử lý trực tiếp ở card công nợ bên dưới bằng cách gia hạn các kỳ còn mở và/hoặc miễn một phần phí chậm trả.
                  </Alert>
                </>
              )}
            </InfoCard>
          </Grid>
          {repayment && (
            <Grid item xs={12} md={6}>
              <InfoCard title={detail.status === "OVERDUE" ? "Xử lý khoản vay quá hạn" : "Trạng thái trả nợ hiện tại"}>
                <Typography variant="body2">
                  Dư nợ còn lại: {formatVnd(repayment.remainingRepayableAmount)}
                </Typography>
                <Typography variant="body2">
                  Đã thanh toán: {formatVnd(repayment.totalPaidAmount)}
                </Typography>
                <Typography variant="body2">
                  Kỳ đang mở: {repayment.installmentNumber != null ? `#${repayment.installmentNumber}` : "-"}
                </Typography>
                <Typography variant="body2">
                  Đến hạn: {repayment.dueDate || "-"}
                </Typography>
                <Typography variant="body2">
                  Số tiền phải xử lý hiện tại: {formatVnd(repayment.currentAmountDue)}
                </Typography>
                <Typography variant="body2">
                  Gốc hiện tại: {formatVnd(repayment.currentPrincipalDue)}
                </Typography>
                <Typography variant="body2">
                  Lãi hiện tại: {formatVnd(repayment.currentInterestDue)}
                </Typography>
                <Typography variant="body2">
                  Phí chậm trả còn lại: {formatVnd(repayment.currentLateFeeDue)}
                </Typography>
                <Typography variant="body2">
                  Trạng thái: {repayment.overdue ? `Quá hạn ${repayment.overdueDays} ngày` : repayment.fullyPaid ? "Đã tất toán" : "Đang theo dõi bình thường"}
                </Typography>
                {detail.status === "OVERDUE" && (
                  <>
                    <Divider />
                    {submitError && <Alert severity="error">{submitError}</Alert>}
                    {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                    {assignmentBlockedByOtherStaff && (
                      <Alert severity="info">
                        Hồ sơ này đang do nhân viên khác phụ trách nên bạn chỉ có thể tra cứu, chưa thể xử lý gia hạn hoặc miễn phí chậm trả.
                      </Alert>
                    )}
                    <Stack spacing={2} component="form" onSubmit={handleResolveOverdueLoan}>
                      <TextField
                        label="Gia hạn thêm (ngày)"
                        type="number"
                        value={overdueResolution.extensionDays}
                        onChange={handleOverdueResolutionChange("extensionDays")}
                        fullWidth
                        inputProps={{ min: 0, max: 180, step: 1 }}
                        disabled={submittingOverdueResolution || assignmentBlockedByOtherStaff}
                        {...fieldErrorProps(
                          overdueResolutionFieldErrors,
                          "extensionDays",
                          "Nếu nhập lớn hơn 0, hệ thống sẽ dời ngày đến hạn của kỳ đang mở và các kỳ chưa thanh toán phía sau."
                        )}
                      />
                      <TextField
                        label="Miễn / giảm phí chậm trả"
                        value={overdueResolution.waivedLateFeeAmount}
                        onChange={handleOverdueResolutionChange("waivedLateFeeAmount")}
                        fullWidth
                        inputProps={{ inputMode: "numeric" }}
                        disabled={submittingOverdueResolution || assignmentBlockedByOtherStaff}
                        {...fieldErrorProps(
                          overdueResolutionFieldErrors,
                          "waivedLateFeeAmount",
                          `Tối đa phần phí chậm trả còn lại của kỳ hiện tại: ${formatVnd(repayment.currentLateFeeDue || 0)}.`
                        )}
                      />
                      <TextField
                        label="Lý do xử lý"
                        value={overdueResolution.reason}
                        onChange={handleOverdueResolutionChange("reason")}
                        fullWidth
                        multiline
                        minRows={3}
                        disabled={submittingOverdueResolution || assignmentBlockedByOtherStaff}
                        placeholder="Ví dụ: gia hạn do khách hàng bổ sung hồ sơ chứng minh khó khăn tài chính, miễn một phần phí theo phê duyệt nội bộ"
                        {...fieldErrorProps(overdueResolutionFieldErrors, "reason")}
                      />
                      <Button
                        type="submit"
                        variant="contained"
                        disabled={submittingOverdueResolution || assignmentBlockedByOtherStaff}
                      >
                        {submittingOverdueResolution ? "Đang xử lý..." : "Lưu phương án xử lý quá hạn"}
                      </Button>
                    </Stack>
                  </>
                )}
              </InfoCard>
            </Grid>
          )}
          <Grid item xs={12} md={6}>
            <InfoCard title="Chứng từ hồ sơ vay">
              {!detail.documents?.length && (
                <Typography variant="body2" color="text.secondary">
                  Chưa có chứng từ đính kèm.
                </Typography>
              )}
              {detail.documents?.map((document) => (
                <Stack
                  key={document.id}
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
                    disabled={downloadingDocument === String(document.id)}
                  >
                    {downloadingDocument === String(document.id) ? "Đang tải..." : "Tải xuống"}
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
                  <Typography variant="body2">
                    Đối khớp khuôn mặt: {faceMatchApplicable
                      ? labelVerificationStatus(detail.verification.faceMatchStatus)
                      : "N/A ở bước đầu"}
                  </Typography>
                  <Typography variant="body2">Thu nhập: {labelVerificationStatus(detail.verification.incomeStatus)}</Typography>
                  {detail.verification.verifiedMonthlyIncome != null && (
                    <Typography variant="body2">
                      Thu nhập xác minh: {formatVnd(detail.verification.verifiedMonthlyIncome)}
                    </Typography>
                  )}
                  <Typography variant="body2">KYC: {labelVerificationStatus(detail.verification.kycStatus)}</Typography>
                  <Typography variant="body2">AML: {labelVerificationStatus(detail.verification.amlStatus)}</Typography>
                  <Typography variant="body2">
                    Cờ gian lận: {detail.verification.fraudFlag ? "Có" : "Không"}
                  </Typography>
                  {detail.verification.note && <Alert severity="info">{detail.verification.note}</Alert>}
                  {!verificationEditable && (
                    <Alert severity="info">
                      Bằng chứng xác minh đã bị khóa vì hồ sơ đã qua bước thẩm định ban đầu. Nếu cần rà soát lại, hãy đưa hồ sơ về một luồng tái thẩm định riêng.
                    </Alert>
                  )}
                  {assignmentBlockedByOtherStaff && verificationEditable && (
                    <Alert severity="info">
                      Hồ sơ này đang do nhân viên khác phụ trách nên bạn không thể cập nhật xác minh ở màn hình này.
                    </Alert>
                  )}
                  {detail.customerProfile?.verifiedMonthlyIncome != null && (
                    <Alert severity="info">
                      Thu nhập đã xác minh từ hồ sơ khách hàng gốc đã được nạp sẵn. Bạn chỉ cần nhập lại nếu muốn điều chỉnh theo bộ chứng từ của riêng hồ sơ vay này.
                    </Alert>
                  )}
                  <Divider />
                  <Stack spacing={2} component="form" onSubmit={handleSaveVerification}>
                    <Typography variant="subtitle2">Cập nhật từng bước xác minh</Typography>
                    <Grid container spacing={1.5}>
                      {verificationStepFields.map(([field, label]) => (
                        <Grid item xs={12} sm={6} key={field}>
                          <TextField
                            select
                            size="small"
                            label={label}
                            value={verificationForm[field]}
                            onChange={handleVerificationChange(field)}
                            fullWidth
                            disabled={submittingVerification || !verificationEditable || assignmentBlockedByOtherStaff}
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
                          size="small"
                          label="Thu nhập đã xác minh"
                          value={verificationForm.verifiedMonthlyIncome}
                          onChange={handleVerificationChange("verifiedMonthlyIncome")}
                          fullWidth
                          inputProps={{ inputMode: "numeric" }}
                          disabled={
                            submittingVerification
                            || !verificationEditable
                            || assignmentBlockedByOtherStaff
                            || verificationForm.incomeStatus !== "PASSED"
                          }
                          {...fieldErrorProps(
                            verificationFieldErrors,
                            "verifiedMonthlyIncome",
                            "Bắt buộc khi bước thu nhập được đánh dấu là đạt."
                          )}
                        />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField
                          select
                          size="small"
                          label="Cờ gian lận"
                          value={String(verificationForm.fraudFlag)}
                          onChange={handleVerificationChange("fraudFlag")}
                          fullWidth
                          disabled={submittingVerification || !verificationEditable || assignmentBlockedByOtherStaff}
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
                          disabled={submittingVerification || !verificationEditable || assignmentBlockedByOtherStaff}
                          {...fieldErrorProps(verificationFieldErrors, "note")}
                        />
                      </Grid>
                      <Grid item xs={12}>
                        <Button
                          type="submit"
                          variant="outlined"
                          disabled={submittingVerification || !verificationEditable || assignmentBlockedByOtherStaff}
                        >
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
            <InfoCard title={finalized ? "Thông tin bước thẩm định" : "Quyết định thẩm định ban đầu"}>
              <Stack spacing={2} component="form" onSubmit={handleSubmitDecision}>
                {submitError && <Alert severity="error">{submitError}</Alert>}
                {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
                {finalized && (
                  <Alert severity="info">
                    Hồ sơ này đã qua bước thẩm định ban đầu. Màn hình quyết định được giữ ở chế độ tra cứu.
                  </Alert>
                )}
                {assignmentBlockedByOtherStaff && !finalized && (
                  <Alert severity="info">
                    Hồ sơ này đang do nhân viên khác phụ trách nên bạn không thể gửi quyết định ở màn hình này.
                  </Alert>
                )}
                {additionalInfoRequestLimitReached && !finalized && (
                  <Alert severity="warning">
                    Hồ sơ đã dùng hết {MAX_ADDITIONAL_INFO_REQUESTS} lần yêu cầu bổ sung. Vui lòng chuyển sang quyết định khác.
                  </Alert>
                )}
                <TextField
                  select
                  label="Hành động"
                  value={decision.action}
                  onChange={handleDecisionChange("action")}
                  disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                  {...fieldErrorProps(decisionFieldErrors, "action")}
                >
                  <MenuItem value="">
                    <em>Chọn hành động</em>
                  </MenuItem>
                  <MenuItem value="APPROVE">Duyệt</MenuItem>
                  <MenuItem value="REJECT">Từ chối</MenuItem>
                  <MenuItem value="REQUEST_MORE_INFO" disabled={additionalInfoRequestLimitReached}>
                    Yêu cầu bổ sung hồ sơ
                  </MenuItem>
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
                          disabled={submitting || finalized || assignmentBlockedByOtherStaff}
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
                          disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                          fullWidth
                          inputProps={{ min: 1, step: 1 }}
                          {...fieldErrorProps(decisionFieldErrors, "approvedTermMonths")}
                        />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField
                          label="Lãi suất năm (%/năm)"
                          type="text"
                          value={decision.approvedAnnualRate}
                          onChange={handleApprovedAnnualRateChange}
                          disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                          fullWidth
                          inputProps={{ inputMode: "decimal" }}
                          {...fieldErrorProps(decisionFieldErrors, "approvedAnnualRate", "Nhập theo phần trăm, ví dụ 10,5 cho 10,5%/năm.")}
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
                      disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                      InputLabelProps={{ shrink: true }}
                      {...fieldErrorProps(decisionFieldErrors, "scheduledAt", "Chọn thời điểm khách hàng đến gặp trực tiếp.")}
                    />
                    <TextField
                      label="Ghi chú lịch hẹn"
                      multiline
                      rows={3}
                      value={decision.appointmentNote}
                      onChange={handleDecisionChange("appointmentNote")}
                      disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                      placeholder="Nhắc khách hàng mang bản gốc CCCD, giấy tờ xe và hồ sơ tài sản bảo đảm."
                      {...fieldErrorProps(decisionFieldErrors, "appointmentNote")}
                    />
                  </>
                )}
                {showMoreInfoReasonField && (
                  <>
                    <TextField
                      label="Nội dung cần bổ sung"
                      multiline
                      rows={3}
                      required
                      value={decision.additionalInfoRequestNote}
                      onChange={handleDecisionChange("additionalInfoRequestNote")}
                      disabled={submitting || finalized || assignmentBlockedByOtherStaff || additionalInfoRequestLimitReached}
                      placeholder="Nhập danh sách giấy tờ/thông tin khách hàng cần bổ sung."
                      {...fieldErrorProps(decisionFieldErrors, "additionalInfoRequestNote")}
                    />
                    <TextField
                      label="Hạn bổ sung"
                      type="datetime-local"
                      required
                      value={decision.additionalInfoDeadlineAt}
                      onChange={handleDecisionChange("additionalInfoDeadlineAt")}
                      disabled={submitting || finalized || assignmentBlockedByOtherStaff || additionalInfoRequestLimitReached}
                      InputLabelProps={{ shrink: true }}
                      {...fieldErrorProps(decisionFieldErrors, "additionalInfoDeadlineAt", "Quá hạn này hệ thống sẽ tự từ chối hồ sơ nếu khách hàng chưa gửi lại.")}
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
                    disabled={submitting || finalized || assignmentBlockedByOtherStaff}
                    placeholder="Nhập lý do từ chối hồ sơ."
                    {...fieldErrorProps(decisionFieldErrors, "rejectionReason")}
                  />
                )}
                <Button
                  type="submit"
                  variant="contained"
                  disabled={submitting || finalized || assignmentBlockedByOtherStaff || !hasSelectedAction || (showMoreInfoReasonField && additionalInfoRequestLimitReached)}
                >
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
        </>
      )}
      <Divider />
    </Stack>
  );
}
