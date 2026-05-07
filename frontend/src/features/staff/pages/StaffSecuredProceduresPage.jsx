import {
  Alert,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  FormHelperText,
  FormControlLabel,
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
  assignStaffCaseApi,
  cancelStaffSecuredAppointmentApi,
  getStaffSecuredProcedureDetailApi,
  getStaffSecuredProceduresApi,
  noShowStaffSecuredAppointmentApi,
  releaseStaffCaseApi,
  rescheduleStaffSecuredAppointmentApi,
  saveStaffSecuredProcedureApi
} from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { formatPercentInputFromFraction, normalizePercentInput, percentInputToFraction } from "@/shared/utils/percent";
import { labelAppointmentStatus, labelLoanStatus, labelSecuredProcedureStatus } from "@/shared/utils/labels";

const DEMO_TIME_STORAGE_KEY_PREFIX = "loan_dss_demo_now_";
const MONEY_FIELDS = new Set(["appraisalValue", "monthlyPaymentAmount"]);

const securedProcedureFieldKeywords = {
  demoNow: ["ngày giờ giả lập", "thời gian giả lập", "demo now"],
  mortgageeName: ["bên nhận thế chấp", "tên bên nhận"],
  mortgageeAddress: ["địa chỉ bên nhận", "địa chỉ"],
  mortgageeBusinessCode: ["mã số doanh nghiệp"],
  mortgageePhone: ["số điện thoại"],
  contractNumber: ["số hợp đồng"],
  contractSignedDate: ["ngày ký hợp đồng", "ngày ký"],
  nationality: ["quốc tịch"],
  identityDocumentNumber: ["cmnd", "cccd", "hộ chiếu"],
  permanentAddress: ["địa chỉ hộ khẩu"],
  currentAddress: ["địa chỉ nơi ở hiện tại"],
  occupation: ["nghề nghiệp"],
  jobTitle: ["chức danh"],
  assetType: ["tài sản thế chấp", "loại tài sản"],
  assetManufacturer: ["nhà sản xuất"],
  engineNumber: ["số máy"],
  frameNumber: ["số khung"],
  collateralOwnerName: ["tên trên giấy đăng ký", "chủ sở hữu"],
  collateralIdentifier: ["biển số", "mã tài sản"],
  registrationNumber: ["số giấy đăng ký"],
  appraisalValue: ["giá trị thẩm định"],
  appraisalReportCode: ["mã biên bản thẩm định"],
  insurancePolicyNumber: ["hợp đồng bảo hiểm"],
  monthlyInterestRate: ["lãi suất thực tế hằng tháng", "lãi suất tháng", "lãi suất"],
  monthlyPaymentAmount: ["khoản thanh toán hằng tháng", "thanh toán hằng tháng", "monthly payment"],
  firstPaymentDate: ["ngày thanh toán đầu tiên"],
  monthlyPaymentDay: ["ngày thanh toán hằng tháng"],
  finalPaymentDate: ["ngày thanh toán cuối cùng"],
  status: ["trạng thái thủ tục"],
  note: ["ghi chú nghiệp vụ"]
};

const defaultForm = {
  mortgageeName: "",
  mortgageeAddress: "",
  mortgageeBusinessCode: "",
  mortgageePhone: "",
  contractNumber: "",
  contractSignedDate: "",
  nationality: "",
  identityDocumentNumber: "",
  permanentAddress: "",
  currentAddress: "",
  occupation: "",
  jobTitle: "",
  assetType: "",
  assetManufacturer: "",
  engineNumber: "",
  frameNumber: "",
  collateralOwnerName: "",
  collateralIdentifier: "",
  registrationNumber: "",
  appraisalValue: "",
  monthlyInterestRate: "",
  monthlyPaymentAmount: "",
  firstPaymentDate: "",
  monthlyPaymentDay: "",
  finalPaymentDate: "",
  appraisalReportCode: "",
  insurancePolicyNumber: "",
  originalCertificateReceived: false,
  certifiedCopyDelivered: false,
  collateralRegistrationCompleted: false,
  disputeChecked: false,
  seizureNoticeAcknowledged: false,
  documentsChecked: false,
  assetInspected: false,
  valuationApproved: false,
  contractSigned: false,
  collateralHandoverConfirmed: false,
  disbursementReady: false,
  status: "DRAFT",
  note: ""
};

const legalChecklist = [
  ["originalCertificateReceived", "Đã nhận bản chính giấy chứng nhận tài sản"],
  ["certifiedCopyDelivered", "Đã cấp bản sao chứng thực / giấy biên nhận thế chấp"],
  ["collateralRegistrationCompleted", "Đã đăng ký biện pháp bảo đảm"],
  ["disputeChecked", "Đã kiểm tra tình trạng tranh chấp, kê biên, phong tỏa"],
  ["seizureNoticeAcknowledged", "Khách hàng đã được giải thích điều khoản thu giữ / xử lý tài sản"],
  ["documentsChecked", "Đã đối chiếu CMND/CCCD và giấy tờ gốc của tài sản"]
];

const processingChecklist = [
  ["assetInspected", "Đã kiểm tra thực tế tài sản thế chấp"],
  ["valuationApproved", "Đã phê duyệt kết quả thẩm định giá"],
  ["contractSigned", "Khách hàng đã ký hồ sơ / hợp đồng"],
  ["collateralHandoverConfirmed", "Đã xác nhận bàn giao hoặc lưu giữ giấy tờ bảo đảm"],
  ["disbursementReady", "Hồ sơ đủ điều kiện giải ngân"]
];

function statusColor(status) {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "IN_PROGRESS") {
    return "info";
  }
  return "default";
}

function toDateInputValue(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toISOString().slice(0, 10);
}

function toDateTimeLocalValue(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const timezoneOffsetMs = date.getTimezoneOffset() * 60 * 1000;
  return new Date(date.getTime() - timezoneOffsetMs).toISOString().slice(0, 16);
}

function currentDateTimeLocalValue() {
  return toDateTimeLocalValue(new Date());
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

function plusMonths(baseDateString, months) {
  if (!baseDateString) {
    return "";
  }
  const date = new Date(baseDateString);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  date.setMonth(date.getMonth() + Number(months || 0));
  return date.toISOString().slice(0, 10);
}

function plusHoursDateTime(baseDateTimeValue, hours) {
  if (!baseDateTimeValue) {
    return "";
  }
  const date = new Date(baseDateTimeValue);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  date.setHours(date.getHours() + Number(hours || 0));
  return toDateTimeLocalValue(date);
}

function buildPaymentSchedule(baseDateString, termMonths) {
  if (!baseDateString) {
    return {
      firstPaymentDate: "",
      monthlyPaymentDay: "",
      finalPaymentDate: ""
    };
  }

  return {
    firstPaymentDate: baseDateString,
    monthlyPaymentDay: String(new Date(baseDateString).getDate()),
    finalPaymentDate: plusMonths(baseDateString, Math.max(Number(termMonths || 1) - 1, 0))
  };
}

function estimateMonthlyPayment(principal, termMonths, monthlyRate) {
  const amount = Number(principal || 0);
  const months = Number(termMonths || 0);
  const rate = Number(monthlyRate || 0);
  if (!amount || !months) {
    return "";
  }
  if (!rate) {
    return formatVndInput(Math.round(amount / months));
  }
  const numerator = amount * rate * Math.pow(1 + rate, months);
  const denominator = Math.pow(1 + rate, months) - 1;
  if (!denominator) {
    return "";
  }
  return formatVndInput(Math.round(numerator / denominator));
}

function toMoneyInputValue(value) {
  return value == null || value === "" ? "" : formatVndInput(value);
}

function toPercentInputValue(value, maximumFractionDigits = 4) {
  return value == null || value === "" ? "" : formatPercentInputFromFraction(value, maximumFractionDigits);
}

function buildInitialForm(detail) {
  const approvedAmount = detail.approvedAmount ?? detail.amount;
  const approvedTermMonths = detail.approvedTermMonths ?? detail.termMonths;
  const approvedMonthlyRate = detail.approvedAnnualRate != null ? Number(detail.approvedAnnualRate) / 12 : "";
  const signedDate =
    (detail.contractSignedDate && toDateInputValue(detail.contractSignedDate)) ||
    toDateInputValue(detail.appointment?.scheduledAt);
  const paymentSchedule = buildPaymentSchedule(signedDate, approvedTermMonths);

  return {
    mortgageeName: detail.mortgageeName || "",
    mortgageeAddress: detail.mortgageeAddress || "",
    mortgageeBusinessCode: detail.mortgageeBusinessCode || "",
    mortgageePhone: detail.mortgageePhone || "",
    contractNumber: detail.contractNumber || "",
    contractSignedDate: signedDate,
    nationality: detail.nationality || "",
    identityDocumentNumber: detail.identityDocumentNumber || "",
    permanentAddress: detail.permanentAddress || "",
    currentAddress: detail.currentAddress || "",
    occupation: detail.occupation || detail.customerEmploymentStatus || "",
    jobTitle: detail.jobTitle || "",
    assetType: detail.assetType || "",
    assetManufacturer: detail.assetManufacturer || "",
    engineNumber: detail.engineNumber || "",
    frameNumber: detail.frameNumber || "",
    collateralOwnerName: detail.collateralOwnerName || detail.customerName || "",
    collateralIdentifier: detail.collateralIdentifier || "",
    registrationNumber: detail.registrationNumber || "",
    appraisalValue: toMoneyInputValue(detail.appraisalValue ?? detail.declaredCollateralValue ?? approvedAmount),
    monthlyInterestRate: toPercentInputValue(detail.monthlyInterestRate ?? approvedMonthlyRate, 4),
    monthlyPaymentAmount: toMoneyInputValue(detail.monthlyPaymentAmount ?? detail.approvedMonthlyPayment),
    firstPaymentDate: detail.firstPaymentDate ? toDateInputValue(detail.firstPaymentDate) : paymentSchedule.firstPaymentDate,
    monthlyPaymentDay: detail.monthlyPaymentDay || paymentSchedule.monthlyPaymentDay,
    finalPaymentDate: detail.finalPaymentDate ? toDateInputValue(detail.finalPaymentDate) : paymentSchedule.finalPaymentDate,
    appraisalReportCode: detail.appraisalReportCode || "",
    insurancePolicyNumber: detail.insurancePolicyNumber || "",
    originalCertificateReceived: Boolean(detail.originalCertificateReceived),
    certifiedCopyDelivered: Boolean(detail.certifiedCopyDelivered),
    collateralRegistrationCompleted: Boolean(detail.collateralRegistrationCompleted),
    disputeChecked: Boolean(detail.disputeChecked),
    seizureNoticeAcknowledged: Boolean(detail.seizureNoticeAcknowledged),
    documentsChecked: Boolean(detail.documentsChecked),
    assetInspected: Boolean(detail.assetInspected),
    valuationApproved: Boolean(detail.valuationApproved),
    contractSigned: Boolean(detail.contractSigned),
    collateralHandoverConfirmed: Boolean(detail.collateralHandoverConfirmed),
    disbursementReady: Boolean(detail.disbursementReady),
    status: detail.loanStatus === "CONTRACTED" ? "COMPLETED" : detail.status || "DRAFT",
    note: detail.note || ""
  };
}

function toPayload(form) {
  const decimalFields = new Set(["monthlyInterestRate"]);

  return Object.fromEntries(
    Object.entries(form).map(([key, value]) => {
      if (MONEY_FIELDS.has(key)) {
        return [key, parseVndInput(value)];
      }
      if (decimalFields.has(key)) {
        return [key, percentInputToFraction(value)];
      }
      return [key, value === "" ? null : value];
    })
  );
}

function ReadOnlyField({ label, value }) {
  return (
    <TextField
      label={label}
      value={value ?? ""}
      InputProps={{ readOnly: true }}
      fullWidth
    />
  );
}

function SectionTitle({ index, title, description }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="h6">{index}. {title}</Typography>
      {description && (
        <Typography variant="body2" color="text.secondary">
          {description}
        </Typography>
      )}
    </Stack>
  );
}

function ProcedureList({ rows, loading, error }) {
  return (
    <Stack spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="h4">Thủ tục vay thế chấp</Typography>
        <Typography color="text.secondary">
          Danh sách hồ sơ vay thế chấp đang ở bước lịch hẹn hoặc đối chiếu tài sản. Hồ sơ đã hoàn tất thủ tục được xem từ chi tiết hồ sơ vay.
        </Typography>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải danh sách thủ tục...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Chưa có hồ sơ vay thế chấp cần xử lý.
          </Typography>
        </Paper>
      )}

      {!loading && rows.length > 0 && (
        <Paper sx={{ overflowX: "auto" }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Mã hồ sơ</TableCell>
                <TableCell>Khách hàng</TableCell>
                <TableCell>Phụ trách</TableCell>
                <TableCell>Số tiền</TableCell>
                <TableCell>Trạng thái hồ sơ</TableCell>
                <TableCell>Lịch hẹn</TableCell>
                <TableCell>Thủ tục</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.loanRequestId} hover>
                  <TableCell>#{row.loanRequestId}</TableCell>
                  <TableCell>{row.customerName || row.customerEmail}</TableCell>
                  <TableCell>{row.assignedStaffEmail || "Chưa có người phụ trách"}</TableCell>
                  <TableCell>{formatVnd(row.amount)}</TableCell>
                  <TableCell>{labelLoanStatus(row.loanStatus)}</TableCell>
                  <TableCell>
                    {row.appointmentScheduledAt
                      ? `${new Date(row.appointmentScheduledAt).toLocaleString()}${row.appointmentLocation ? ` - ${row.appointmentLocation}` : ""}`
                      : "-"}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={statusColor(row.procedureStatus)}
                      label={labelSecuredProcedureStatus(row.procedureStatus)}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Button
                      component={RouterLink}
                      to={`/staff/secured-procedures/${row.loanRequestId}`}
                      variant="outlined"
                      size="small"
                    >
                      Mở mẫu
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

export default function StaffSecuredProceduresPage() {
  const { loanRequestId } = useParams();
  const { accessToken, user } = useAuth();
  const [rows, setRows] = useState([]);
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(defaultForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [claimingCase, setClaimingCase] = useState(false);
  const [releasingCase, setReleasingCase] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [demoNow, setDemoNow] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [appointmentAction, setAppointmentAction] = useState("");
  const [appointmentForm, setAppointmentForm] = useState({
    scheduledAt: "",
    location: "",
    note: ""
  });

  useEffect(() => {
    let active = true;

    async function load() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      setSuccess("");
      setFieldErrors({});
      try {
        if (loanRequestId) {
          const response = await getStaffSecuredProcedureDetailApi(accessToken, loanRequestId);
          if (!active) {
            return;
          }
          setDetail(response);
          setForm(buildInitialForm(response));
          setAppointmentForm({
            scheduledAt: response.appointment?.scheduledAt ? toDateTimeLocalValue(response.appointment.scheduledAt) : "",
            location: response.appointment?.location || "",
            note: response.appointment?.note || ""
          });
          const storedDemoNow = localStorage.getItem(`${DEMO_TIME_STORAGE_KEY_PREFIX}${loanRequestId}`);
          if (storedDemoNow) {
            setDemoNow(storedDemoNow);
          } else if (response.appointment?.scheduledAt) {
            const suggestedDemoNow =
              new Date(response.appointment.scheduledAt).getTime() > Date.now()
                ? plusHoursDateTime(response.appointment.scheduledAt, 2)
                : toDateTimeLocalValue(response.appointment.scheduledAt);
            setDemoNow(suggestedDemoNow || currentDateTimeLocalValue());
          } else {
            setDemoNow(currentDateTimeLocalValue());
          }
        } else {
          const response = await getStaffSecuredProceduresApi(accessToken);
          if (!active) {
            return;
          }
          setRows(Array.isArray(response) ? response : []);
        }
      } catch (err) {
        if (active) {
          setError(err.message || "Không tải được dữ liệu thủ tục thế chấp");
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
  }, [accessToken, loanRequestId]);

  useEffect(() => {
    if (!loanRequestId || !demoNow) {
      return;
    }
    localStorage.setItem(`${DEMO_TIME_STORAGE_KEY_PREFIX}${loanRequestId}`, demoNow);
  }, [demoNow, loanRequestId]);

  const approvedAmount = detail?.approvedAmount ?? detail?.amount;
  const approvedTermMonths = detail?.approvedTermMonths ?? detail?.termMonths;

  const handleChange = (field) => (event) => {
    const value = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    const normalizedValue = MONEY_FIELDS.has(field)
      ? formatVndInput(value)
      : field === "monthlyInterestRate"
        ? normalizePercentInput(value)
        : value;
    setFieldErrors((prev) => clearFieldError(prev, field));
    setForm((prev) => {
      const next = {
        ...prev,
        [field]: normalizedValue
      };

      if (field === "contractSignedDate" && value && !prev.firstPaymentDate) {
        Object.assign(next, buildPaymentSchedule(value, approvedTermMonths));
      }

      if (field === "firstPaymentDate") {
        Object.assign(next, buildPaymentSchedule(value, approvedTermMonths));
      }

      if (field === "monthlyInterestRate") {
        next.monthlyPaymentAmount = normalizedValue === ""
          ? ""
          : estimateMonthlyPayment(
              approvedAmount,
              approvedTermMonths,
              percentInputToFraction(normalizedValue) ?? 0
            );
      }

      return next;
    });
  };

  const handleUseRealNow = () => {
    setFieldErrors((prev) => clearFieldError(prev, "demoNow"));
    setDemoNow(currentDateTimeLocalValue());
  };

  const handleUseAfterAppointment = () => {
    if (!detail?.appointment?.scheduledAt) {
      return;
    }
    setFieldErrors((prev) => clearFieldError(prev, "demoNow"));
    setDemoNow(plusHoursDateTime(detail.appointment.scheduledAt, 2));
  };

  const handleSave = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    setFieldErrors({});
    try {
      const response = await saveStaffSecuredProcedureApi(
        accessToken,
        loanRequestId,
        toPayload(form),
        toIsoInstant(demoNow)
      );
      setDetail(response);
      setForm(buildInitialForm(response));
      setSuccess("Đã lưu mẫu thủ tục thế chấp.");
    } catch (err) {
      const message = err.message || "Không lưu được thủ tục vay thế chấp";
      setError(message);
      setFieldErrors(mapFieldErrors(message, securedProcedureFieldKeywords));
    } finally {
      setSaving(false);
    }
  };

  const syncAppointmentForm = (response) => {
    setAppointmentForm({
      scheduledAt: response.appointment?.scheduledAt ? toDateTimeLocalValue(response.appointment.scheduledAt) : "",
      location: response.appointment?.location || "",
      note: response.appointment?.note || ""
    });
  };

  const handleAppointmentAction = async (action) => {
    if (!loanRequestId) {
      return;
    }
    setAppointmentAction(action);
    setError("");
    setSuccess("");
    try {
      let response;
      if (action === "reschedule") {
        response = await rescheduleStaffSecuredAppointmentApi(accessToken, loanRequestId, {
          scheduledAt: toIsoInstant(appointmentForm.scheduledAt),
          location: appointmentForm.location.trim() || null,
          note: appointmentForm.note.trim() || null
        });
      } else if (action === "cancel") {
        response = await cancelStaffSecuredAppointmentApi(accessToken, loanRequestId);
      } else {
        response = await noShowStaffSecuredAppointmentApi(accessToken, loanRequestId);
      }
      setDetail(response);
      setForm(buildInitialForm(response));
      syncAppointmentForm(response);
      setSuccess("Đã cập nhật lịch hẹn thế chấp.");
    } catch (err) {
      setError(err.message || "Không cập nhật được lịch hẹn thế chấp");
    } finally {
      setAppointmentAction("");
    }
  };

  const handleAssignCase = async () => {
    if (!loanRequestId) {
      return;
    }
    setClaimingCase(true);
    setError("");
    setSuccess("");
    try {
      await assignStaffCaseApi(accessToken, loanRequestId);
      const response = await getStaffSecuredProcedureDetailApi(accessToken, loanRequestId);
      setDetail(response);
      setSuccess("Bạn đã nhận phụ trách hồ sơ vay thế chấp này.");
    } catch (err) {
      setError(err.message || "Không nhận được phụ trách hồ sơ");
    } finally {
      setClaimingCase(false);
    }
  };

  const handleReleaseCase = async () => {
    if (!loanRequestId) {
      return;
    }
    setReleasingCase(true);
    setError("");
    setSuccess("");
    try {
      await releaseStaffCaseApi(accessToken, loanRequestId);
      const response = await getStaffSecuredProcedureDetailApi(accessToken, loanRequestId);
      setDetail(response);
      setSuccess("Bạn đã bỏ nhận hồ sơ vay thế chấp này.");
    } catch (err) {
      setError(err.message || "Không thể bỏ nhận hồ sơ");
    } finally {
      setReleasingCase(false);
    }
  };

  if (!loanRequestId) {
    return <ProcedureList rows={rows} loading={loading} error={error} />;
  }

  const appointmentScheduledAt = detail?.appointment?.scheduledAt || null;
  const simulatedNowIso = toIsoInstant(demoNow);
  const completionUnlocked =
    appointmentScheduledAt &&
    simulatedNowIso &&
    new Date(simulatedNowIso).getTime() >= new Date(appointmentScheduledAt).getTime() - 60 * 1000;
  const assignmentOwnedByCurrentUser = Boolean(user?.id && detail?.assignment?.staffUserId === user.id);
  const assignmentBlockedByOtherStaff = Boolean(detail?.assignment?.staffUserId && !assignmentOwnedByCurrentUser);

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", sm: "center" }}>
        <Stack spacing={0.5}>
          <Typography variant="h4">Biểu mẫu xử lý thủ tục thế chấp #{loanRequestId}</Typography>
          <Typography color="text.secondary">
            Bố cục biểu mẫu được tổ chức theo dạng hợp đồng thế chấp: bên thế chấp, bên nhận thế chấp, tài sản và khoản vay được bảo đảm.
          </Typography>
        </Stack>
        <Button component={RouterLink} to="/staff/secured-procedures" variant="outlined">
          Về danh sách
        </Button>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}
      {success && <Alert severity="success">{success}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải mẫu thủ tục...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && detail && (
        <Grid container spacing={2}>
          <Grid item xs={12} lg={4}>
            <Stack spacing={2}>
              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Tình trạng hồ sơ</Typography>
                  <Typography variant="body2">Khách hàng: {detail.customerName || detail.customerEmail}</Typography>
                  <Typography variant="body2">Điện thoại: {detail.customerPhone || "-"}</Typography>
                  <Typography variant="body2">
                    Nhân viên phụ trách: {detail.assignment?.staffEmail || "Chưa có người phụ trách"}
                  </Typography>
                  <Typography variant="body2">Số tiền phê duyệt: {formatVnd(approvedAmount)}</Typography>
                  <Typography variant="body2">Kỳ hạn phê duyệt: {approvedTermMonths} tháng</Typography>
                  <Typography variant="body2">Trạng thái hồ sơ: {labelLoanStatus(detail.loanStatus)}</Typography>
                  <Chip
                    size="small"
                    color={statusColor(detail.status || form.status)}
                    label={labelSecuredProcedureStatus(detail.status || form.status)}
                    sx={{ alignSelf: "flex-start" }}
                  />
                  {!detail.assignment && (
                    <Button
                      variant="outlined"
                      size="small"
                      onClick={handleAssignCase}
                      disabled={claimingCase}
                      sx={{ alignSelf: "flex-start" }}
                    >
                      {claimingCase ? "Đang nhận..." : "Nhận phụ trách"}
                    </Button>
                  )}
                  {assignmentOwnedByCurrentUser && (
                    <Button
                      variant="text"
                      color="inherit"
                      size="small"
                      onClick={handleReleaseCase}
                      disabled={releasingCase}
                      sx={{ alignSelf: "flex-start" }}
                    >
                      {releasingCase ? "Đang bỏ nhận..." : "Bỏ nhận hồ sơ"}
                    </Button>
                  )}
                  {assignmentBlockedByOtherStaff && (
                    <Alert severity="info">
                      Hồ sơ này hiện do {detail.assignment.staffEmail} phụ trách. Bạn chỉ có thể xem cho đến khi được bàn giao.
                    </Alert>
                  )}
                </Stack>
              </Paper>

              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Lịch hẹn gặp mặt</Typography>
                  <Typography variant="body2">
                    {detail.appointment?.scheduledAt ? new Date(detail.appointment.scheduledAt).toLocaleString() : "-"}
                  </Typography>
                  <Typography variant="body2">
                    Trạng thái lịch hẹn: {labelAppointmentStatus(detail.appointment?.status)}
                  </Typography>
                  <Typography variant="body2">{detail.appointment?.location || "-"}</Typography>
                  {detail.appointment?.note && <Alert severity="info">{detail.appointment.note}</Alert>}
                  <TextField
                    label="Đổi lịch hẹn"
                    type="datetime-local"
                    size="small"
                    value={appointmentForm.scheduledAt}
                    onChange={(event) => setAppointmentForm((prev) => ({ ...prev, scheduledAt: event.target.value }))}
                    InputLabelProps={{ shrink: true }}
                  />
                  <TextField
                    label="Địa điểm"
                    size="small"
                    value={appointmentForm.location}
                    onChange={(event) => setAppointmentForm((prev) => ({ ...prev, location: event.target.value }))}
                  />
                  <TextField
                    label="Ghi chú lịch hẹn"
                    size="small"
                    multiline
                    minRows={2}
                    value={appointmentForm.note}
                    onChange={(event) => setAppointmentForm((prev) => ({ ...prev, note: event.target.value }))}
                  />
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleAppointmentAction("reschedule")}
                      disabled={
                        Boolean(appointmentAction) ||
                        detail.appointment?.status !== "SCHEDULED" ||
                        assignmentBlockedByOtherStaff
                      }
                    >
                      {appointmentAction === "reschedule" ? "Đang lưu..." : "Đổi lịch"}
                    </Button>
                    <Button
                      size="small"
                      color="warning"
                      variant="outlined"
                      onClick={() => handleAppointmentAction("no-show")}
                      disabled={Boolean(appointmentAction) || detail.appointment?.status !== "SCHEDULED" || assignmentBlockedByOtherStaff}
                    >
                      Khách vắng mặt
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      variant="outlined"
                      onClick={() => handleAppointmentAction("cancel")}
                      disabled={Boolean(appointmentAction) || detail.appointment?.status !== "SCHEDULED" || assignmentBlockedByOtherStaff}
                    >
                      Hủy lịch
                    </Button>
                  </Stack>
                </Stack>
              </Paper>

              <Paper sx={{ p: 2 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Mốc pháp lý</Typography>
                  <Typography variant="body2">Ngày ký mẫu: {form.contractSignedDate || "-"}</Typography>
                  <Typography variant="body2">Ngày thanh toán đầu tiên: {form.firstPaymentDate || "-"}</Typography>
                  <Typography variant="body2">Ngày thanh toán cuối cùng: {form.finalPaymentDate || "-"}</Typography>
                  <Button
                    component={RouterLink}
                    to={`/staff/requests/${loanRequestId}`}
                    variant="outlined"
                    size="small"
                    sx={{ alignSelf: "flex-start" }}
                  >
                    Mở hồ sơ vay
                  </Button>
                </Stack>
              </Paper>
            </Stack>
          </Grid>

          <Grid item xs={12} lg={8}>
            <Paper sx={{ p: 3 }}>
              <Stack spacing={3} component="form" onSubmit={handleSave}>
                <Alert severity={completionUnlocked ? "success" : "info"}>
                  {completionUnlocked
                    ? `Chế độ demo đang mở khóa bước hoàn tất với ngày giờ giả lập ${new Date(simulatedNowIso).toLocaleString("vi-VN")}.`
                    : "Bạn có thể đặt ngày giờ giả lập sau lịch hẹn để demo bước hoàn tất mà không cần chờ tới giờ thực tế."}
                </Alert>

                <Stack
                  direction={{ xs: "column", md: "row" }}
                  spacing={1.5}
                  alignItems={{ xs: "stretch", md: "center" }}
                >
                  <TextField
                    label="Ngày giờ giả lập"
                    type="datetime-local"
                    value={demoNow}
                    onChange={(event) => {
                      setFieldErrors((prev) => clearFieldError(prev, "demoNow"));
                      setDemoNow(event.target.value);
                    }}
                    InputLabelProps={{ shrink: true }}
                    sx={{ minWidth: { xs: "100%", md: 280 } }}
                    {...fieldErrorProps(fieldErrors, "demoNow")}
                  />
                  <Button variant="outlined" onClick={handleUseRealNow}>
                    Dùng thời gian hiện tại
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={handleUseAfterAppointment}
                    disabled={!detail?.appointment?.scheduledAt}
                  >
                    Đặt sau lịch hẹn 2 giờ
                  </Button>
                </Stack>

                {detail.loanStatus !== "APPOINTMENT_SCHEDULED" && detail.loanStatus !== "CONTRACTED" && (
                  <Alert severity="warning">
                    Hồ sơ chưa ở bước đã lên lịch hẹn. Có thể lưu nháp, nhưng chỉ được hoàn tất sau khi đã có buổi gặp trực tiếp với khách hàng.
                  </Alert>
                )}

                <SectionTitle
                  index="1"
                  title="Thông tin Bên thế chấp"
                  description="Các trường này bám theo phần đầu của hợp đồng thế chấp mẫu."
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <ReadOnlyField label="1.1 Họ tên" value={detail.customerName} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <ReadOnlyField label="1.2 Ngày sinh" value={detail.customerDateOfBirth || ""} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField
                      label="1.3 Quốc tịch"
                      value={form.nationality}
                      onChange={handleChange("nationality")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "nationality")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="1.4 Số CMND/CCCD/Hộ chiếu"
                      value={form.identityDocumentNumber}
                      onChange={handleChange("identityDocumentNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "identityDocumentNumber")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="1.5 Địa chỉ hộ khẩu"
                      value={form.permanentAddress}
                      onChange={handleChange("permanentAddress")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "permanentAddress")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="1.6 Địa chỉ nơi ở hiện tại"
                      value={form.currentAddress}
                      onChange={handleChange("currentAddress")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "currentAddress")}
                    />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <ReadOnlyField label="1.7 Điện thoại di động" value={detail.customerPhone} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <ReadOnlyField label="1.8 Email" value={detail.customerEmail} />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="1.9 Nghề nghiệp"
                      value={form.occupation}
                      onChange={handleChange("occupation")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "occupation")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="1.10 Chức danh"
                      value={form.jobTitle}
                      onChange={handleChange("jobTitle")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "jobTitle")}
                    />
                  </Grid>
                </Grid>

                <Divider />
                <SectionTitle
                  index="2"
                  title="Thông tin Bên nhận thế chấp"
                  description="Nhân viên chủ động nhập thông tin đơn vị nhận thế chấp theo hồ sơ thực tế."
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="2.1 Tên bên nhận thế chấp"
                      value={form.mortgageeName}
                      onChange={handleChange("mortgageeName")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "mortgageeName")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="2.2 Địa chỉ"
                      value={form.mortgageeAddress}
                      onChange={handleChange("mortgageeAddress")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "mortgageeAddress")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="2.3 Mã số doanh nghiệp"
                      value={form.mortgageeBusinessCode}
                      onChange={handleChange("mortgageeBusinessCode")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "mortgageeBusinessCode")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="2.4 Số điện thoại"
                      value={form.mortgageePhone}
                      onChange={handleChange("mortgageePhone")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "mortgageePhone")}
                    />
                  </Grid>
                </Grid>

                <Divider />
                <SectionTitle
                  index="3"
                  title="Thông tin Tài sản thế chấp"
                  description="Khối này tương ứng với Mục 3 của hợp đồng."
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="3.1 Tài sản thế chấp"
                      value={form.assetType}
                      onChange={handleChange("assetType")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "assetType")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="3.2 Nhà sản xuất"
                      value={form.assetManufacturer}
                      onChange={handleChange("assetManufacturer")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "assetManufacturer")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Tên trên giấy đăng ký"
                      value={form.collateralOwnerName}
                      onChange={handleChange("collateralOwnerName")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "collateralOwnerName")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="3.3 Số máy"
                      value={form.engineNumber}
                      onChange={handleChange("engineNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "engineNumber")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="3.4 Số khung"
                      value={form.frameNumber}
                      onChange={handleChange("frameNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "frameNumber")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Biển số / mã tài sản"
                      value={form.collateralIdentifier}
                      onChange={handleChange("collateralIdentifier")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "collateralIdentifier")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Số giấy đăng ký"
                      value={form.registrationNumber}
                      onChange={handleChange("registrationNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "registrationNumber")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Giá trị thẩm định"
                      type="text"
                      value={form.appraisalValue}
                      onChange={handleChange("appraisalValue")}
                      inputProps={{ inputMode: "numeric" }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "appraisalValue")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Mã biên bản thẩm định"
                      value={form.appraisalReportCode}
                      onChange={handleChange("appraisalReportCode")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "appraisalReportCode")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="Số hợp đồng bảo hiểm"
                      value={form.insurancePolicyNumber}
                      onChange={handleChange("insurancePolicyNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "insurancePolicyNumber")}
                    />
                  </Grid>
                </Grid>

                <Divider />
                <SectionTitle
                  index="4"
                  title="Thông tin Khoản vay được bảo đảm"
                  description="Khối này mô phỏng Mục 4 của hợp đồng."
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Số hợp đồng"
                      value={form.contractNumber}
                      onChange={handleChange("contractNumber")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "contractNumber")}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Ngày ký hợp đồng"
                      type="date"
                      value={form.contractSignedDate}
                      onChange={handleChange("contractSignedDate")}
                      InputLabelProps={{ shrink: true }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "contractSignedDate")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <ReadOnlyField label="4.1 Khoản cấp vốn" value={formatVnd(approvedAmount)} />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <ReadOnlyField label="4.2 Thời hạn vay" value={`${approvedTermMonths} tháng`} />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="4.3 Lãi suất thực tế hàng tháng (%/tháng)"
                      type="text"
                      value={form.monthlyInterestRate}
                      onChange={handleChange("monthlyInterestRate")}
                      inputProps={{ inputMode: "decimal" }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "monthlyInterestRate", "Nhập theo phần trăm mỗi tháng, ví dụ 0,5 cho 0,5%/tháng.")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="4.4 Khoản thanh toán hàng tháng"
                      type="text"
                      value={form.monthlyPaymentAmount}
                      onChange={handleChange("monthlyPaymentAmount")}
                      inputProps={{ inputMode: "numeric" }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "monthlyPaymentAmount")}
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      label="4.5 Ngày thanh toán đầu tiên"
                      type="date"
                      value={form.firstPaymentDate}
                      onChange={handleChange("firstPaymentDate")}
                      InputLabelProps={{ shrink: true }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "firstPaymentDate")}
                    />
                  </Grid>
                  <Grid item xs={12} md={2}>
                    <TextField
                      label="4.6 Ngày thanh toán hàng tháng"
                      value={form.monthlyPaymentDay}
                      onChange={handleChange("monthlyPaymentDay")}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "monthlyPaymentDay")}
                    />
                  </Grid>
                  <Grid item xs={12} md={2}>
                    <TextField
                      label="4.7 Ngày thanh toán cuối cùng"
                      type="date"
                      value={form.finalPaymentDate}
                      onChange={handleChange("finalPaymentDate")}
                      InputLabelProps={{ shrink: true }}
                      fullWidth
                      {...fieldErrorProps(fieldErrors, "finalPaymentDate")}
                    />
                  </Grid>
                </Grid>

                <Divider />
                <SectionTitle
                  index="5"
                  title="Điều kiện, điều khoản và hồ sơ pháp lý"
                  description="Nhóm tickbox này bám theo các điều khoản vận hành trong mẫu hợp đồng."
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle1">5.1 Hồ sơ giấy tờ và quyền sở hữu</Typography>
                      {legalChecklist.map(([field, label]) => (
                        <FormControlLabel
                          key={field}
                          control={<Checkbox checked={form[field]} onChange={handleChange(field)} />}
                          label={label}
                        />
                      ))}
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle1">5.2 Thao tác xử lí nội bộ</Typography>
                      {processingChecklist.map(([field, label]) => (
                        <FormControlLabel
                          key={field}
                          control={<Checkbox checked={form[field]} onChange={handleChange(field)} />}
                          label={label}
                        />
                      ))}
                    </Stack>
                  </Grid>
                </Grid>

                <FormControl fullWidth error={Boolean(fieldErrors.status)}>
                  <InputLabel id="secured-procedure-status-label">Trạng thái thủ tục</InputLabel>
                  <Select
                    labelId="secured-procedure-status-label"
                    label="Trạng thái thủ tục"
                    value={form.status}
                    onChange={handleChange("status")}
                    disabled={detail?.loanStatus === "CONTRACTED"}
                  >
                    {detail?.loanStatus === "CONTRACTED" ? (
                      <MenuItem value="COMPLETED">Hoàn tất</MenuItem>
                    ) : (
                      [
                        <MenuItem key="draft" value="DRAFT">Chưa xử lý</MenuItem>,
                        <MenuItem key="in-progress" value="IN_PROGRESS">Đang xử lý</MenuItem>,
                        <MenuItem key="completed" value="COMPLETED">Hoàn tất</MenuItem>
                      ]
                    )}
                  </Select>
                  {fieldErrors.status && (
                    <FormHelperText>{fieldErrors.status}</FormHelperText>
                  )}
                </FormControl>

                <TextField
                  label="Ghi chú nghiệp vụ"
                  value={form.note}
                  onChange={handleChange("note")}
                  multiline
                  rows={4}
                  placeholder="Ghi lại các lưu ý phát sinh trong buổi gặp mặt, đối chiếu tài sản và hoàn thiện hồ sơ pháp lý."
                  {...fieldErrorProps(fieldErrors, "note")}
                />

                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <Button type="submit" variant="contained" disabled={saving || assignmentBlockedByOtherStaff}>
                    {saving ? "Đang lưu..." : "Lưu mẫu thủ tục"}
                  </Button>
                  <Button component={RouterLink} to={`/staff/requests/${loanRequestId}`} variant="outlined">
                    Mở hồ sơ vay
                  </Button>
                </Stack>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}
