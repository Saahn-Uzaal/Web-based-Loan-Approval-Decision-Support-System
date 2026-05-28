import {
  Alert,
  Button,
  Chip,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import { useEffect, useRef, useState } from "react";
import { getMyInformationVerificationApi } from "@/features/customer/api/informationVerificationApi";
import {
  downloadMyIdentityCardApi,
  downloadMyPayslipApi,
  getMyProfileApi,
  upsertMyProfileApi
} from "@/features/customer/api/profileApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import {
  IDENTITY_CARD_ACCEPT,
  PAYSLIP_ACCEPT,
  formatFileSize,
  isAcceptedIdentityCardFile,
  isAcceptedPayslipFile
} from "@/shared/utils/files";
import {
  labelCreditBureauStatus,
  labelEmploymentStatus,
  labelVerificationStatus
} from "@/shared/utils/labels";

const emptyProfileForm = {
  fullName: "",
  phone: "",
  identityNumber: "",
  dateOfBirth: "",
  monthlyIncome: "",
  verifiedMonthlyIncome: null,
  employmentStatus: "",
  employmentStartDate: "",
  bankAccountNumber: "",
  bankName: ""
};

const profileFieldKeywords = {
  fullName: ["họ và tên", "họ tên", "full name", "fullName"],
  phone: ["số điện thoại", "điện thoại", "phone"],
  identityNumber: ["cccd", "căn cước", "identityNumber"],
  dateOfBirth: ["ngày sinh", "date of birth"],
  monthlyIncome: ["thu nhập", "lương", "monthlyIncome"],
  bankAccountNumber: ["số tài khoản", "tài khoản ngân hàng", "bank account", "bankAccountNumber"],
  bankName: ["tên ngân hàng", "ngân hàng", "bank name", "bankName"],
  payslip: ["phiếu lương", "payslip", "file"],
  idCardFront: ["cccd mặt trước", "mặt trước cccd", "id card front"],
  idCardBack: ["cccd mặt sau", "mặt sau cccd", "id card back"]
};

const employmentStatusOptions = [
  "EMPLOYED",
  "SELF_EMPLOYED",
  "BUSINESS_OWNER",
  "PART_TIME",
  "CONTRACTOR",
  "UNEMPLOYED",
  "STUDENT",
  "RETIRED",
  "OTHER"
];

function normalizeEmploymentStatusValue(value) {
  return employmentStatusOptions.includes(value) ? value : "";
}

function verificationSeverity(status) {
  if (status === "PASSED") {
    return "success";
  }
  if (status === "FAILED") {
    return "warning";
  }
  return "info";
}

function verificationChipColor(status) {
  if (status === "PASSED") {
    return "success";
  }
  if (status === "FAILED") {
    return "error";
  }
  return "warning";
}

function toPayslipSummary(profile) {
  if (!profile?.payslipFileName) {
    return null;
  }

  return {
    fileName: profile.payslipFileName,
    fileSize: profile.payslipFileSize ?? null,
    uploadedAt: profile.payslipUploadedAt ?? null
  };
}

function toIdentityCardSummary(profile, side) {
  if (side === "front") {
    if (!profile?.identityCardFrontFileName) {
      return null;
    }
    return {
      fileName: profile.identityCardFrontFileName,
      fileSize: profile.identityCardFrontFileSize ?? null,
      uploadedAt: profile.identityCardFrontUploadedAt ?? null
    };
  }

  if (!profile?.identityCardBackFileName) {
    return null;
  }
  return {
    fileName: profile.identityCardBackFileName,
    fileSize: profile.identityCardBackFileSize ?? null,
    uploadedAt: profile.identityCardBackUploadedAt ?? null
  };
}

function creditCheckSeverity(creditCheck) {
  if (creditCheck?.hardReject) {
    return "error";
  }
  if (creditCheck?.manualReviewRequired) {
    return "warning";
  }
  return "info";
}

function creditCheckChipColor(creditCheck) {
  if (creditCheck?.hardReject) {
    return "error";
  }
  if (creditCheck?.manualReviewRequired) {
    return "warning";
  }
  if (creditCheck?.bureauStatus === "CLEAR" || creditCheck?.bureauStatus === "NO_HIT") {
    return "success";
  }
  return "default";
}

export default function CustomerProfilePage() {
  const { accessToken } = useAuth();
  const fileInputRef = useRef(null);
  const idCardFrontInputRef = useRef(null);
  const idCardBackInputRef = useRef(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [downloadingPayslip, setDownloadingPayslip] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [creditHistoryScore, setCreditHistoryScore] = useState(null);
  const [creditCheck, setCreditCheck] = useState(null);
  const [paymentRating, setPaymentRating] = useState(0);
  const [debtToIncomeRatio, setDebtToIncomeRatio] = useState(null);
  const [informationVerification, setInformationVerification] = useState(null);
  const [form, setForm] = useState(emptyProfileForm);
  const [profileFieldErrors, setProfileFieldErrors] = useState({});
  const [selectedPayslip, setSelectedPayslip] = useState(null);
  const [currentPayslip, setCurrentPayslip] = useState(null);
  const [selectedIdentityCardFront, setSelectedIdentityCardFront] = useState(null);
  const [selectedIdentityCardBack, setSelectedIdentityCardBack] = useState(null);
  const [currentIdentityCardFront, setCurrentIdentityCardFront] = useState(null);
  const [currentIdentityCardBack, setCurrentIdentityCardBack] = useState(null);
  const [downloadingIdentityCard, setDownloadingIdentityCard] = useState("");

  useEffect(() => {
    let active = true;

    async function loadData() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const profilePromise = getMyProfileApi(accessToken).catch((err) => {
          const message = String(err.message || "");
          if (
            message.includes("Không tìm thấy hồ sơ khách hàng") ||
            message.includes("Không tìm thấy hồ sơ khách hàng")
          ) {
            return null;
          }
          throw err;
        });

        const [profile, verification] = await Promise.all([
          profilePromise,
          getMyInformationVerificationApi(accessToken)
        ]);

        if (!active) {
          return;
        }

        if (profile) {
          setForm({
            fullName: profile.fullName ?? "",
            phone: profile.phone ?? "",
            identityNumber: profile.identityNumber ?? "",
            dateOfBirth: profile.dateOfBirth ?? "",
            monthlyIncome: profile.monthlyIncome != null ? formatVndInput(profile.monthlyIncome) : "",
            verifiedMonthlyIncome: profile.verifiedMonthlyIncome ?? null,
            employmentStatus: normalizeEmploymentStatusValue(profile.employmentStatus),
            employmentStartDate: profile.employmentStartDate ?? "",
            bankAccountNumber: profile.bankAccountNumber ?? "",
            bankName: profile.bankName ?? ""
          });
          setCreditHistoryScore(profile.creditHistoryScore ?? null);
          setCreditCheck(profile.creditCheck ?? null);
          setPaymentRating(Number(profile.paymentRating || 0));
          setDebtToIncomeRatio(profile.debtToIncomeRatio ?? null);
          setCurrentPayslip(toPayslipSummary(profile));
          setCurrentIdentityCardFront(toIdentityCardSummary(profile, "front"));
          setCurrentIdentityCardBack(toIdentityCardSummary(profile, "back"));
        } else {
          setForm(emptyProfileForm);
          setCreditHistoryScore(null);
          setCreditCheck(null);
          setPaymentRating(0);
          setDebtToIncomeRatio(null);
          setCurrentPayslip(null);
          setCurrentIdentityCardFront(null);
          setCurrentIdentityCardBack(null);
        }

        setSelectedPayslip(null);
        setSelectedIdentityCardFront(null);
        setSelectedIdentityCardBack(null);
        if (fileInputRef.current) {
          fileInputRef.current.value = "";
        }
        if (idCardFrontInputRef.current) {
          idCardFrontInputRef.current.value = "";
        }
        if (idCardBackInputRef.current) {
          idCardBackInputRef.current.value = "";
        }
        setInformationVerification(verification ?? null);
      } catch (err) {
        if (active) {
          setError(err.message || "Không tải được hồ sơ của bạn");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadData();
    return () => {
      active = false;
    };
  }, [accessToken]);

  const refreshAuxiliaryData = async () => {
    if (!accessToken) {
      return null;
    }
    const verification = await getMyInformationVerificationApi(accessToken);
    setInformationVerification(verification ?? null);
    return verification ?? null;
  };

  const dtiDisplay = (() => {
    if (debtToIncomeRatio == null) {
      return "Chưa đủ dữ liệu";
    }
    return `${Number(debtToIncomeRatio).toFixed(2)}%`;
  })();

  const handleChange = (name) => (event) => {
    setProfileFieldErrors((prev) => clearFieldError(prev, name));
    setForm((prev) => ({
      ...prev,
      [name]: event.target.value
    }));
  };

  const handleProfileMoneyChange = (name) => (event) => {
    setProfileFieldErrors((prev) => clearFieldError(prev, name));
    setForm((prev) => ({
      ...prev,
      [name]: formatVndInput(event.target.value)
    }));
  };

  const clearSelectedPayslip = () => {
    setSelectedPayslip(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const clearSelectedIdentityCard = (side) => {
    if (side === "front") {
      setSelectedIdentityCardFront(null);
      if (idCardFrontInputRef.current) {
        idCardFrontInputRef.current.value = "";
      }
      return;
    }
    setSelectedIdentityCardBack(null);
    if (idCardBackInputRef.current) {
      idCardBackInputRef.current.value = "";
    }
  };

  const handlePayslipChange = (event) => {
    const nextFile = event.target.files?.[0] ?? null;
    setProfileFieldErrors((prev) => clearFieldError(prev, "payslip"));
    if (!nextFile) {
      setSelectedPayslip(null);
      return;
    }

    if (!isAcceptedPayslipFile(nextFile)) {
      const message = "Chỉ chấp nhận file phiếu lương dạng PDF, Word hoặc Excel.";
      setError(message);
      setProfileFieldErrors({ payslip: message });
      event.target.value = "";
      return;
    }

    setError("");
    setSelectedPayslip(nextFile);
  };

  const handleIdentityCardChange = (side) => (event) => {
    const nextFile = event.target.files?.[0] ?? null;
    const fieldName = side === "front" ? "idCardFront" : "idCardBack";
    setProfileFieldErrors((prev) => clearFieldError(prev, fieldName));
    if (!nextFile) {
      if (side === "front") {
        setSelectedIdentityCardFront(null);
      } else {
        setSelectedIdentityCardBack(null);
      }
      return;
    }

    if (!isAcceptedIdentityCardFile(nextFile)) {
      const message = "Chỉ chấp nhận ảnh CCCD dạng JPG, JPEG, PNG hoặc WEBP.";
      setError(message);
      setProfileFieldErrors({ [fieldName]: message });
      event.target.value = "";
      return;
    }

    setError("");
    if (side === "front") {
      setSelectedIdentityCardFront(nextFile);
    } else {
      setSelectedIdentityCardBack(nextFile);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setProfileFieldErrors({});
    setSuccessMessage("");

    if (!selectedPayslip && !currentPayslip) {
      const message = "Vui lòng chọn phiếu lương trong tháng gần nhất.";
      setError(message);
      setProfileFieldErrors({ payslip: message });
      return;
    }
    if (!selectedIdentityCardFront && !currentIdentityCardFront) {
      const message = "Vui lòng tải ảnh CCCD mặt trước.";
      setError(message);
      setProfileFieldErrors({ idCardFront: message });
      return;
    }
    if (!selectedIdentityCardBack && !currentIdentityCardBack) {
      const message = "Vui lòng tải ảnh CCCD mặt sau.";
      setError(message);
      setProfileFieldErrors({ idCardBack: message });
      return;
    }

    const monthlyIncome = parseVndInput(form.monthlyIncome);
    const bankAccountNumber = form.bankAccountNumber.replace(/\s+/g, "").trim();
    const bankName = form.bankName.trim();
    if (monthlyIncome == null || monthlyIncome <= 0) {
      const message = "Vui lòng nhập thu nhập hàng tháng hợp lệ để tính hạn mức vay.";
      setError(message);
      setProfileFieldErrors({ monthlyIncome: message });
      return;
    }
    if ((bankAccountNumber && !bankName) || (!bankAccountNumber && bankName)) {
      const message = "Vui lòng nhập đầy đủ số tài khoản và tên ngân hàng để phục vụ giải ngân.";
      setError(message);
      setProfileFieldErrors({
        bankAccountNumber: message,
        bankName: message
      });
      return;
    }
    if (bankAccountNumber && !/^\d{6,30}$/.test(bankAccountNumber)) {
      const message = "Số tài khoản ngân hàng phải gồm từ 6 đến 30 chữ số.";
      setError(message);
      setProfileFieldErrors({ bankAccountNumber: message });
      return;
    }

    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        identityNumber: form.identityNumber.replace(/\s+/g, "").trim(),
        dateOfBirth: form.dateOfBirth || null,
        monthlyIncome,
        employmentStatus: form.employmentStatus.trim() || null,
        employmentStartDate: form.employmentStartDate || null,
        bankAccountNumber: bankAccountNumber || null,
        bankName: bankName || null
      };

      const profile = await upsertMyProfileApi(
        accessToken,
        payload,
        selectedPayslip,
        selectedIdentityCardFront,
        selectedIdentityCardBack
      );
      setForm({
        fullName: profile.fullName ?? "",
        phone: profile.phone ?? "",
        identityNumber: profile.identityNumber ?? "",
        dateOfBirth: profile.dateOfBirth ?? "",
        monthlyIncome: profile.monthlyIncome != null ? formatVndInput(profile.monthlyIncome) : "",
        verifiedMonthlyIncome: profile.verifiedMonthlyIncome ?? null,
        employmentStatus: normalizeEmploymentStatusValue(profile.employmentStatus),
        employmentStartDate: profile.employmentStartDate ?? "",
        bankAccountNumber: profile.bankAccountNumber ?? "",
        bankName: profile.bankName ?? ""
      });
      setCreditHistoryScore(profile.creditHistoryScore ?? null);
      setCreditCheck(profile.creditCheck ?? null);
      setPaymentRating(Number(profile.paymentRating || 0));
      setDebtToIncomeRatio(profile.debtToIncomeRatio ?? null);
      setCurrentPayslip(toPayslipSummary(profile));
      setCurrentIdentityCardFront(toIdentityCardSummary(profile, "front"));
      setCurrentIdentityCardBack(toIdentityCardSummary(profile, "back"));
      clearSelectedPayslip();
      clearSelectedIdentityCard("front");
      clearSelectedIdentityCard("back");
      const refreshedVerification = await refreshAuxiliaryData();
      setSuccessMessage(
        refreshedVerification?.status === "PENDING"
          ? "Lưu hồ sơ thành công. Các thay đổi liên quan đến định danh hoặc thu nhập đã đưa trạng thái xác minh về chờ đối chiếu lại."
          : "Lưu hồ sơ thành công."
      );
    } catch (err) {
      const message = err.message || "Không lưu được hồ sơ";
      setError(message);
      setProfileFieldErrors(mapFieldErrors(message, profileFieldKeywords));
    } finally {
      setSaving(false);
    }
  };

  const handleDownloadPayslip = async () => {
    if (!currentPayslip?.fileName) {
      return;
    }
    setDownloadingPayslip(true);
    setError("");
    try {
      await downloadMyPayslipApi(accessToken, currentPayslip.fileName);
    } catch (err) {
      setError(err.message || "Không tải được phiếu lương");
    } finally {
      setDownloadingPayslip(false);
    }
  };

  const handleDownloadIdentityCard = async (side) => {
    const currentFile = side === "front" ? currentIdentityCardFront : currentIdentityCardBack;
    if (!currentFile?.fileName) {
      return;
    }
    setDownloadingIdentityCard(side);
    setError("");
    try {
      await downloadMyIdentityCardApi(accessToken, side, currentFile.fileName);
    } catch (err) {
      setError(err.message || "Không tải được ảnh CCCD");
    } finally {
      setDownloadingIdentityCard("");
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hồ sơ của tôi</Typography>
      <Typography color="text.secondary">
        Cập nhật thông tin cá nhân, tài khoản nhận giải ngân và phiếu lương gần nhất để dữ liệu thẩm định hồ sơ vay được đối chiếu chính xác hơn.
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Stack spacing={2} component="form" onSubmit={handleSubmit}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
          {informationVerification && (
            <Alert severity={verificationSeverity(informationVerification.status)}>
              Trạng thái xác minh thông tin: {labelVerificationStatus(informationVerification.status)}.
              {informationVerification.rejectionReason ? ` Lý do: ${informationVerification.rejectionReason}` : ""}
            </Alert>
          )}

          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography variant="subtitle2">Chỉ số hiện tại</Typography>
            <Chip
              size="small"
              label={`Điểm thanh toán: ${paymentRating}`}
              color={paymentRating >= 20 ? "success" : paymentRating >= 0 ? "info" : "error"}
            />
            <Chip
              size="small"
              label={`Điểm tín dụng: ${creditHistoryScore ?? "-"}`}
              color={creditHistoryScore != null && creditHistoryScore >= 70 ? "success" : creditHistoryScore != null && creditHistoryScore >= 50 ? "warning" : "default"}
            />
            <Chip size="small" label={`DTI: ${dtiDisplay}`} color="default" />
            <Chip
              size="small"
              label={`Xác minh: ${labelVerificationStatus(informationVerification?.status || "PENDING")}`}
              color={verificationChipColor(informationVerification?.status || "PENDING")}
            />
            {creditCheck && (
              <Chip
                size="small"
                label={`Tra cứu tín dụng: ${labelCreditBureauStatus(creditCheck.bureauStatus)}`}
                color={creditCheckChipColor(creditCheck)}
              />
            )}
          </Stack>

          {creditCheck && (
            <Alert severity={creditCheckSeverity(creditCheck)}>
              Kết quả tra cứu tín dụng nội bộ theo CCCD: {labelCreditBureauStatus(creditCheck.bureauStatus)}.
              {creditCheck.creditScore != null ? ` Điểm tín dụng nội bộ hiện tại: ${creditCheck.creditScore}.` : ""}
              {creditCheck.manualReviewRequired ? " Hồ sơ này sẽ bị đẩy sang thẩm định thủ công." : ""}
              {creditCheck.hardReject ? " Dữ liệu tín dụng đang có cờ từ chối cứng." : ""}
              {creditCheck.riskNote ? ` Ghi chú: ${creditCheck.riskNote}` : ""}
            </Alert>
          )}

          <TextField
            label="Họ và tên"
            value={form.fullName}
            onChange={handleChange("fullName")}
            required
            fullWidth
            disabled={loading || saving}
            {...fieldErrorProps(profileFieldErrors, "fullName")}
          />
          <TextField
            label="Số điện thoại"
            value={form.phone}
            onChange={handleChange("phone")}
            fullWidth
            disabled={loading || saving}
            {...fieldErrorProps(profileFieldErrors, "phone")}
          />
          <TextField
            label="Số CCCD"
            value={form.identityNumber}
            onChange={handleChange("identityNumber")}
            required
            fullWidth
            disabled={loading || saving}
            inputProps={{ inputMode: "numeric", maxLength: 12 }}
            {...fieldErrorProps(
              profileFieldErrors,
              "identityNumber",
              "Nhập đúng 12 chữ số, dùng để tra cứu dữ liệu tín dụng nội bộ."
            )}
          />
          <TextField
            label="Ngày sinh"
            type="date"
            value={form.dateOfBirth}
            onChange={handleChange("dateOfBirth")}
            fullWidth
            disabled={loading || saving}
            InputLabelProps={{ shrink: true }}
            {...fieldErrorProps(profileFieldErrors, "dateOfBirth")}
          />
          <TextField
            label="Thu nhập hàng tháng theo phiếu lương"
            type="text"
            value={form.monthlyIncome}
            onChange={handleProfileMoneyChange("monthlyIncome")}
            required
            fullWidth
            disabled={loading || saving}
            inputProps={{ inputMode: "numeric" }}
            {...fieldErrorProps(profileFieldErrors, "monthlyIncome")}
          />
          <TextField
            label="Tình trạng việc làm"
            select
            value={form.employmentStatus}
            onChange={handleChange("employmentStatus")}
            fullWidth
            disabled={loading || saving}
            {...fieldErrorProps(profileFieldErrors, "employmentStatus")}
          >
            <MenuItem value="">
              <em>Để trống</em>
            </MenuItem>
            {employmentStatusOptions.map((status) => (
              <MenuItem key={status} value={status}>
                {labelEmploymentStatus(status)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Ngày bắt đầu công việc"
            type="date"
            value={form.employmentStartDate}
            onChange={handleChange("employmentStartDate")}
            fullWidth
            disabled={loading || saving}
            InputLabelProps={{ shrink: true }}
            {...fieldErrorProps(profileFieldErrors, "employmentStartDate")}
          />
          <TextField
            label="Số tài khoản ngân hàng nhận giải ngân"
            value={form.bankAccountNumber}
            onChange={handleChange("bankAccountNumber")}
            fullWidth
            disabled={loading || saving}
            inputProps={{ inputMode: "numeric", maxLength: 30 }}
            {...fieldErrorProps(
              profileFieldErrors,
              "bankAccountNumber",
              "Có thể để trống lúc này, nhưng bắt buộc phải có trước khi nhân viên giải ngân."
            )}
          />
          <TextField
            label="Tên ngân hàng"
            value={form.bankName}
            onChange={handleChange("bankName")}
            fullWidth
            disabled={loading || saving}
            {...fieldErrorProps(
              profileFieldErrors,
              "bankName",
              "Ví dụ: Vietcombank, BIDV, Techcombank."
            )}
          />
          {!(form.bankAccountNumber.trim() && form.bankName.trim()) && (
            <Alert severity="warning">
              Bạn chưa khai báo đầy đủ thông tin tài khoản nhận giải ngân. Hồ sơ vẫn lưu được, nhưng khoản vay sẽ chưa thể giải ngân cho tới khi bổ sung đủ số tài khoản và tên ngân hàng.
            </Alert>
          )}
          {form.verifiedMonthlyIncome != null && (
            <Alert severity="info">
              Thu nhập đã xác minh bởi nhân viên: {formatVnd(form.verifiedMonthlyIncome)}.
              Giá trị này sẽ được ưu tiên dùng khi tính hạn mức vay.
            </Alert>
          )}

          <Paper
            variant="outlined"
            sx={{
              p: 2,
              borderStyle: "dashed",
              borderColor:
                profileFieldErrors.idCardFront || profileFieldErrors.idCardBack ? "error.main" : "divider",
              borderWidth: profileFieldErrors.idCardFront || profileFieldErrors.idCardBack ? 2 : 1
            }}
          >
            <Stack spacing={1.5}>
              <Typography variant="subtitle1">Ảnh CCCD đã đăng ký</Typography>
              <Typography variant="body2" color="text.secondary">
                Chấp nhận JPG, JPEG, PNG, WEBP. Hệ thống dùng dữ liệu này làm hồ sơ định danh gốc và tái sử dụng ở các lần tạo hồ sơ vay sau.
              </Typography>

              <Stack spacing={2}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">CCCD mặt trước</Typography>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ sm: "center" }}>
                    <Button component="label" variant="outlined" disabled={loading || saving}>
                      {selectedIdentityCardFront ? "Đổi ảnh" : currentIdentityCardFront ? "Chọn ảnh mới" : "Chọn ảnh"}
                      <input
                        ref={idCardFrontInputRef}
                        hidden
                        type="file"
                        accept={IDENTITY_CARD_ACCEPT}
                        onChange={handleIdentityCardChange("front")}
                      />
                    </Button>
                    {selectedIdentityCardFront && (
                      <Button variant="text" color="inherit" onClick={() => clearSelectedIdentityCard("front")} disabled={loading || saving}>
                        Bỏ chọn
                      </Button>
                    )}
                    {currentIdentityCardFront?.fileName && (
                      <Button
                        variant="text"
                        onClick={() => handleDownloadIdentityCard("front")}
                        disabled={loading || saving || downloadingIdentityCard === "front"}
                      >
                        {downloadingIdentityCard === "front" ? "Đang tải..." : "Tải ảnh đã nộp"}
                      </Button>
                    )}
                  </Stack>
                  {selectedIdentityCardFront && (
                    <Alert severity="info">
                      Đã chọn: {selectedIdentityCardFront.name} ({formatFileSize(selectedIdentityCardFront.size)})
                    </Alert>
                  )}
                  {!selectedIdentityCardFront && currentIdentityCardFront?.fileName && (
                    <Alert severity="info">
                      File hiện tại: {currentIdentityCardFront.fileName}
                      {currentIdentityCardFront.fileSize != null ? ` (${formatFileSize(currentIdentityCardFront.fileSize)})` : ""}
                      {currentIdentityCardFront.uploadedAt ? ` - tải lên lúc ${new Date(currentIdentityCardFront.uploadedAt).toLocaleString()}` : ""}
                    </Alert>
                  )}
                  {profileFieldErrors.idCardFront && <Alert severity="error">{profileFieldErrors.idCardFront}</Alert>}
                </Stack>

                <Stack spacing={1}>
                  <Typography variant="subtitle2">CCCD mặt sau</Typography>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ sm: "center" }}>
                    <Button component="label" variant="outlined" disabled={loading || saving}>
                      {selectedIdentityCardBack ? "Đổi ảnh" : currentIdentityCardBack ? "Chọn ảnh mới" : "Chọn ảnh"}
                      <input
                        ref={idCardBackInputRef}
                        hidden
                        type="file"
                        accept={IDENTITY_CARD_ACCEPT}
                        onChange={handleIdentityCardChange("back")}
                      />
                    </Button>
                    {selectedIdentityCardBack && (
                      <Button variant="text" color="inherit" onClick={() => clearSelectedIdentityCard("back")} disabled={loading || saving}>
                        Bỏ chọn
                      </Button>
                    )}
                    {currentIdentityCardBack?.fileName && (
                      <Button
                        variant="text"
                        onClick={() => handleDownloadIdentityCard("back")}
                        disabled={loading || saving || downloadingIdentityCard === "back"}
                      >
                        {downloadingIdentityCard === "back" ? "Đang tải..." : "Tải ảnh đã nộp"}
                      </Button>
                    )}
                  </Stack>
                  {selectedIdentityCardBack && (
                    <Alert severity="info">
                      Đã chọn: {selectedIdentityCardBack.name} ({formatFileSize(selectedIdentityCardBack.size)})
                    </Alert>
                  )}
                  {!selectedIdentityCardBack && currentIdentityCardBack?.fileName && (
                    <Alert severity="info">
                      File hiện tại: {currentIdentityCardBack.fileName}
                      {currentIdentityCardBack.fileSize != null ? ` (${formatFileSize(currentIdentityCardBack.fileSize)})` : ""}
                      {currentIdentityCardBack.uploadedAt ? ` - tải lên lúc ${new Date(currentIdentityCardBack.uploadedAt).toLocaleString()}` : ""}
                    </Alert>
                  )}
                  {profileFieldErrors.idCardBack && <Alert severity="error">{profileFieldErrors.idCardBack}</Alert>}
                </Stack>
              </Stack>
            </Stack>
          </Paper>

          <Paper
            variant="outlined"
            sx={{
              p: 2,
              borderStyle: "dashed",
              borderColor: profileFieldErrors.payslip ? "error.main" : "divider",
              borderWidth: profileFieldErrors.payslip ? 2 : 1
            }}
          >
            <Stack spacing={1.5}>
              <Typography variant="subtitle1">Phiếu lương trong tháng gần nhất</Typography>
              <Typography variant="body2" color="text.secondary">
                Chấp nhận PDF, DOC, DOCX, XLS, XLSX. Kích thước tối đa 10MB.
              </Typography>

              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ sm: "center" }}>
                <Button component="label" variant="outlined" disabled={loading || saving}>
                  {selectedPayslip ? "Đổi file" : currentPayslip ? "Chọn file mới" : "Chọn file"}
                  <input
                    ref={fileInputRef}
                    hidden
                    type="file"
                    accept={PAYSLIP_ACCEPT}
                    onChange={handlePayslipChange}
                  />
                </Button>
                {selectedPayslip && (
                  <Button variant="text" color="inherit" onClick={clearSelectedPayslip} disabled={loading || saving}>
                    Bỏ chọn
                  </Button>
                )}
                {currentPayslip?.fileName && (
                  <Button
                    variant="text"
                    onClick={handleDownloadPayslip}
                    disabled={loading || saving || downloadingPayslip}
                  >
                    {downloadingPayslip ? "Đang tải..." : "Tải file đã nộp"}
                  </Button>
                )}
              </Stack>

              {selectedPayslip && (
                <Alert severity="info">
                  Đã chọn: {selectedPayslip.name} ({formatFileSize(selectedPayslip.size)})
                </Alert>
              )}

              {!selectedPayslip && currentPayslip?.fileName && (
                <Alert severity="info">
                  File hiện tại: {currentPayslip.fileName}
                  {currentPayslip.fileSize != null ? ` (${formatFileSize(currentPayslip.fileSize)})` : ""}
                  {currentPayslip.uploadedAt ? ` - tải lên lúc ${new Date(currentPayslip.uploadedAt).toLocaleString()}` : ""}
                </Alert>
              )}
              {profileFieldErrors.payslip && <Alert severity="error">{profileFieldErrors.payslip}</Alert>}
            </Stack>
          </Paper>

          <Button type="submit" variant="contained" disabled={loading || saving}>
            {saving ? "Đang lưu..." : "Lưu hồ sơ"}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
