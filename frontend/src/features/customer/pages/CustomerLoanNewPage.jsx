import {
  Alert,
  Box,
  Button,
  Divider,
  Grid,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography
} from "@mui/material";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link as RouterLink, useLocation, useNavigate, useParams } from "react-router-dom";
import { getMyInformationVerificationApi } from "@/features/customer/api/informationVerificationApi";
import {
  createLoanApi,
  createLoanDraftApi,
  getLoanDetailApi,
  getMyLoansApi,
  submitLoanDraftApi,
  updateLoanDraftApi
} from "@/features/customer/api/loanApi";
import { getMyProfileApi } from "@/features/customer/api/profileApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { LOAN_IMAGE_ACCEPT, formatFileSize, isAcceptedLoanImageFile } from "@/shared/utils/files";
import {
  labelCollateralType,
  labelLoanDocumentType,
  labelLoanStatus,
  labelLoanType,
  labelVerificationStatus
} from "@/shared/utils/labels";

const emptyFiles = {
  vehicleRegistration: null,
  licensePlateImage: null,
  faceCapture: null
};

const emptyForm = {
  loanType: "UNSECURED",
  amount: "",
  termMonths: "",
  purpose: "PERSONAL",
  collateralType: "VEHICLE_REGISTRATION",
  collateralValue: ""
};

const loanFieldKeywords = {
  amount: ["số tiền vay", "số tiền yêu cầu", "khoản vay"],
  termMonths: ["kỳ hạn", "thời hạn"],
  collateralValue: ["giá trị tài sản", "tài sản bảo đảm"],
  vehicleRegistration: ["giấy tờ xe", "đăng ký xe", "vehicle registration"],
  licensePlateImage: ["biển số xe", "license plate"],
  faceCapture: ["ảnh khuôn mặt", "khuôn mặt", "face"]
};

const BLOCKING_APPLICATION_STATUSES = [
  "DRAFT",
  "PENDING",
  "NEEDS_MORE_INFO",
  "APPOINTMENT_SCHEDULED",
  "APPROVED",
  "CONTRACTED"
];

function FilePicker({ label, file, currentFileName, disabled, onChange, error, helperText }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        height: "100%",
        borderColor: error ? "error.main" : "divider",
        borderWidth: error ? 2 : 1
      }}
    >
      <Stack spacing={1.25}>
        <Typography variant="subtitle2">{label}</Typography>
        <Button component="label" variant="outlined" disabled={disabled} sx={{ alignSelf: "flex-start" }}>
          {file ? "Đổi ảnh" : currentFileName ? "Đổi ảnh đã lưu" : "Chọn ảnh"}
          <input hidden type="file" accept={LOAN_IMAGE_ACCEPT} onChange={onChange} />
        </Button>
        {file && (
          <Typography variant="body2" color="text.secondary">
            Đã chọn: {file.name} ({formatFileSize(file.size)})
          </Typography>
        )}
        {!file && currentFileName && (
          <Typography variant="body2" color="text.secondary">
            Đã lưu: {currentFileName}
          </Typography>
        )}
        {helperText && (
          <Typography variant="caption" color={error ? "error" : "text.secondary"}>
            {helperText}
          </Typography>
        )}
      </Stack>
    </Paper>
  );
}

function buildStoredDocumentMap(documents) {
  return (Array.isArray(documents) ? documents : []).reduce((accumulator, document) => {
    accumulator[document.documentType] = document;
    return accumulator;
  }, {});
}

function buildFormFromLoan(loan) {
  return {
    loanType: loan?.loanType || "UNSECURED",
    amount: loan?.amount != null ? formatVndInput(loan.amount) : "",
    termMonths: loan?.termMonths != null ? String(loan.termMonths) : "",
    purpose: loan?.purpose || "PERSONAL",
    collateralType: loan?.collateralType || "VEHICLE_REGISTRATION",
    collateralValue: loan?.collateralValue != null ? formatVndInput(loan.collateralValue) : ""
  };
}

function existingBlockingLoan(loans, currentLoanId) {
  const currentId = currentLoanId != null ? String(currentLoanId) : null;
  return (Array.isArray(loans) ? loans : []).find((loan) => {
    if (!BLOCKING_APPLICATION_STATUSES.includes(loan.status)) {
      return false;
    }
    if (currentId != null && String(loan.id) === currentId) {
      return false;
    }
    return true;
  }) || null;
}

function requiredCurrentDocument(storedDocuments, file, documentType) {
  return Boolean(file) || Boolean(storedDocuments[documentType]);
}

function hasReusableIdentityProfile(profile) {
  return Boolean(
    profile?.identityNumber
      && profile?.payslipFileName
      && profile?.identityCardFrontFileName
      && profile?.identityCardBackFileName
  );
}

export default function CustomerLoanNewPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { id } = useParams();
  const { accessToken } = useAuth();
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);

  const isEditMode = Boolean(id);

  const [submitting, setSubmitting] = useState(false);
  const [savingDraft, setSavingDraft] = useState(false);
  const [loading, setLoading] = useState(true);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraError, setCameraError] = useState("");
  const [error, setError] = useState("");
  const [verificationError, setVerificationError] = useState("");
  const [successMessage, setSuccessMessage] = useState(location.state?.successMessage || "");
  const [informationVerification, setInformationVerification] = useState(null);
  const [profile, setProfile] = useState(null);
  const [blockingLoan, setBlockingLoan] = useState(null);
  const [repaymentLoans, setRepaymentLoans] = useState([]);
  const [draftLoan, setDraftLoan] = useState(null);
  const [storedDocuments, setStoredDocuments] = useState({});
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [files, setFiles] = useState(emptyFiles);

  useEffect(() => {
    setSuccessMessage(location.state?.successMessage || "");
  }, [id, location.state?.successMessage]);

  useEffect(() => {
    let active = true;

    async function loadData() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      setVerificationError("");
      try {
        const profilePromise = getMyProfileApi(accessToken).catch((err) => {
          const message = String(err.message || "");
          if (/kh(?:ông|ong)\s+t(?:ì|i)m\s+th(?:ấy|ay)/i.test(message)) {
            return null;
          }
          throw err;
        });
        const draftPromise = isEditMode ? getLoanDetailApi(accessToken, id) : Promise.resolve(null);
        const [profileResponse, verificationResponse, loansResponse, draftResponse] = await Promise.all([
          profilePromise,
          getMyInformationVerificationApi(accessToken),
          getMyLoansApi(accessToken),
          draftPromise
        ]);

        if (!active) {
          return;
        }

        const loans = Array.isArray(loansResponse) ? loansResponse : [];
        const nextBlockingLoan = existingBlockingLoan(loans, id);

        setProfile(profileResponse);
        setInformationVerification(verificationResponse);
        setBlockingLoan(nextBlockingLoan);
        setRepaymentLoans(loans.filter((loan) => ["ACTIVE", "OVERDUE"].includes(loan.status)));

        if (draftResponse) {
          if (draftResponse.status !== "DRAFT") {
            setError("Chỉ có thể tiếp tục chỉnh sửa hồ sơ đang ở trạng thái bản nháp.");
          }
          setDraftLoan(draftResponse);
          setForm(buildFormFromLoan(draftResponse));
          setStoredDocuments(buildStoredDocumentMap(draftResponse.documents));
        } else {
          setDraftLoan(null);
          setForm(emptyForm);
          setStoredDocuments({});
        }

        setFiles(emptyFiles);
      } catch (err) {
        if (!active) {
          return;
        }
        setVerificationError(err.message || "Không tải được trạng thái hồ sơ khách hàng");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadData();
    return () => {
      active = false;
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    };
  }, [accessToken, id, isEditMode]);

  useEffect(() => {
    if (videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [cameraActive]);

  const collateralValue = parseVndInput(form.collateralValue);
  const verificationStatus = informationVerification?.status || "PENDING";
  const blockedByExistingLoan = Boolean(blockingLoan);
  const displayedIncome = profile?.verifiedMonthlyIncome ?? profile?.monthlyIncome ?? null;
  const usesVerifiedIncome = profile?.verifiedMonthlyIncome != null;
  const busy = savingDraft || submitting;
  const draftEditable = !isEditMode || draftLoan?.status === "DRAFT";
  const creationBlocked = !isEditMode && blockedByExistingLoan;

  const loanTypeHelp = useMemo(() => {
    if (form.loanType === "SECURED") {
      return "Vay bằng giấy tờ xe, cần ảnh giấy tờ xe và ảnh biển số xe tương ứng. Nhân viên sẽ liên hệ đặt lịch hẹn sau khi tiếp nhận.";
    }
    return "Vay tín chấp sẽ dùng lại CCCD hai mặt từ hồ sơ khách hàng gốc, bạn chỉ cần chụp ảnh khuôn mặt hiện tại bằng máy ảnh trên trình duyệt.";
  }, [form.loanType]);

  const pageTitle = isEditMode ? `Hoàn thiện bản nháp #${id}` : "Tạo hồ sơ vay";
  const pageDescription = isEditMode
    ? "Bổ sung thông tin và chứng từ còn thiếu cho bản nháp, sau đó lưu tiếp hoặc gửi đi thẩm định."
    : "Kiểm tra thông tin cá nhân đã lưu, sau đó chọn vay thế chấp hoặc vay tín chấp để nộp chứng từ tương ứng.";

  const actionSuccessMessage = isEditMode
    ? "Đã lưu bản nháp. Bạn có thể tiếp tục chỉnh sửa hoặc gửi hồ sơ khi đã đủ chứng từ."
    : "Đã lưu bản nháp. Bạn có thể quay lại hoàn thiện rồi gửi hồ sơ sau.";

  const handleChange = (name) => (event) => {
    setFieldErrors((prev) => clearFieldError(prev, name));
    setForm((prev) => ({
      ...prev,
      [name]: event.target.value
    }));
  };

  const handleMoneyChange = (name) => (event) => {
    setFieldErrors((prev) => clearFieldError(prev, name));
    setForm((prev) => ({
      ...prev,
      [name]: formatVndInput(event.target.value)
    }));
  };

  const handleLoanTypeChange = (_event, value) => {
    if (!value || isEditMode) {
      return;
    }
    setForm((prev) => ({
      ...prev,
      loanType: value
    }));
    setFieldErrors({});
    setError("");
  };

  const handleFileChange = (name) => (event) => {
    setFieldErrors((prev) => clearFieldError(prev, name));
    const file = event.target.files?.[0] ?? null;
    if (!file) {
      setFiles((prev) => ({ ...prev, [name]: null }));
      return;
    }
    if (!isAcceptedLoanImageFile(file)) {
      const message = "Chỉ chấp nhận ảnh JPG, JPEG, PNG hoặc WEBP cho chứng từ hồ sơ vay.";
      setError(message);
      setFieldErrors({ [name]: message });
      event.target.value = "";
      return;
    }
    setError("");
    setFiles((prev) => ({ ...prev, [name]: file }));
  };

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraActive(false);
  };

  const startCamera = async () => {
    setCameraError("");
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user" },
        audio: false
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setCameraActive(true);
    } catch (err) {
      setCameraError(err.message || "Không mở được máy ảnh. Vui lòng cấp quyền máy ảnh để chụp ảnh khuôn mặt.");
    }
  };

  const captureFace = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) {
      return;
    }
    const width = video.videoWidth || 640;
    const height = video.videoHeight || 480;
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    context.drawImage(video, 0, 0, width, height);
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          setCameraError("Không chụp được ảnh khuôn mặt từ máy ảnh.");
          return;
        }
        const faceFile = new File([blob], `face-capture-${Date.now()}.jpg`, { type: "image/jpeg" });
        setFiles((prev) => ({ ...prev, faceCapture: faceFile }));
        setFieldErrors((prev) => clearFieldError(prev, "faceCapture"));
        setCameraError("");
        stopCamera();
      },
      "image/jpeg",
      0.92
    );
  };

  const validateBasePayload = () => {
    if (!draftEditable) {
      return "Hồ sơ này không còn ở trạng thái bản nháp nên không thể tiếp tục chỉnh sửa.";
    }
    if (creationBlocked) {
      return blockingLoan?.status === "DRAFT"
        ? `Bạn đang có bản nháp hồ sơ vay #${blockingLoan.id}. Hãy tiếp tục hoàn thiện bản nháp đó hoặc rút đi trước khi tạo hồ sơ mới.`
        : `Bạn đang có hồ sơ vay #${blockingLoan.id} ở trạng thái ${labelLoanStatus(blockingLoan.status)}. Vui lòng hoàn tất hồ sơ hiện tại trước khi tạo mới.`;
    }
    const amountValue = parseVndInput(form.amount);
    const termMonthsValue = Number(form.termMonths);
    if (amountValue == null || amountValue <= 0) {
      return "Vui lòng nhập số tiền vay hợp lệ.";
    }
    if (!Number.isFinite(termMonthsValue) || termMonthsValue <= 0) {
      return "Vui lòng nhập kỳ hạn hợp lệ.";
    }
    if (form.loanType === "SECURED" && (collateralValue == null || collateralValue <= 0)) {
      return "Vui lòng nhập giá trị tài sản bảo đảm.";
    }
    return "";
  };

  const validateFinalSubmit = () => {
    const baseValidationError = validateBasePayload();
    if (baseValidationError) {
      return baseValidationError;
    }
    if (!profile) {
      return "Bạn cần hoàn thiện hồ sơ cá nhân trước khi gửi hồ sơ vay đi thẩm định.";
    }
    if (!hasReusableIdentityProfile(profile)) {
      return "Bạn cần hoàn thiện hồ sơ cá nhân, số CCCD, ảnh CCCD 2 mặt và phiếu lương trước khi gửi hồ sơ vay đi thẩm định.";
    }
    if (form.loanType === "SECURED") {
      if (!requiredCurrentDocument(storedDocuments, files.vehicleRegistration, "VEHICLE_REGISTRATION")) {
        return "Vui lòng chụp hoặc tải ảnh giấy tờ xe.";
      }
      if (!requiredCurrentDocument(storedDocuments, files.licensePlateImage, "LICENSE_PLATE_IMAGE")) {
        return "Vui lòng chụp hoặc tải ảnh biển số xe.";
      }
      return "";
    }
    if (!requiredCurrentDocument(storedDocuments, files.faceCapture, "FACE_CAPTURE")) {
      return "Vui lòng chụp ảnh khuôn mặt hiện tại bằng máy ảnh.";
    }
    return "";
  };

  const buildPayload = () => ({
    loanType: form.loanType,
    amount: parseVndInput(form.amount),
    termMonths: Number(form.termMonths),
    purpose: form.purpose,
    collateralType: form.loanType === "SECURED" ? form.collateralType : null,
    collateralValue: form.loanType === "SECURED" ? collateralValue : null
  });

  const buildFilesPayload = () => (
    form.loanType === "SECURED"
      ? {
          vehicleRegistration: files.vehicleRegistration,
          licensePlateImage: files.licensePlateImage
        }
      : {
          faceCapture: files.faceCapture
        }
  );

  const handleSaveDraft = async () => {
    setError("");
    setFieldErrors({});
    setSuccessMessage("");

    const validationError = validateBasePayload();
    if (validationError) {
      setError(validationError);
      setFieldErrors(mapFieldErrors(validationError, loanFieldKeywords));
      return;
    }

    setSavingDraft(true);
    try {
      const payload = buildPayload();
      const nextFiles = buildFilesPayload();
      const saved = isEditMode
        ? await updateLoanDraftApi(accessToken, id, payload, nextFiles)
        : await createLoanDraftApi(accessToken, payload, nextFiles);

      if (!isEditMode) {
        navigate(`/customer/loans/${saved.id}/edit`, {
          state: { successMessage: actionSuccessMessage }
        });
        return;
      }

      setDraftLoan(saved);
      setStoredDocuments(buildStoredDocumentMap(saved.documents));
      setFiles(emptyFiles);
      setSuccessMessage(actionSuccessMessage);
    } catch (err) {
      const message = err.message || "Không lưu được bản nháp hồ sơ vay";
      setError(message);
      setFieldErrors(mapFieldErrors(message, loanFieldKeywords));
    } finally {
      setSavingDraft(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setFieldErrors({});
    setSuccessMessage("");

    const validationError = validateFinalSubmit();
    if (validationError) {
      setError(validationError);
      setFieldErrors(mapFieldErrors(validationError, loanFieldKeywords));
      return;
    }

    setSubmitting(true);
    try {
      const payload = buildPayload();
      const nextFiles = buildFilesPayload();
      const created = isEditMode
        ? await submitLoanDraftApi(accessToken, id, payload, nextFiles)
        : await createLoanApi(accessToken, payload, nextFiles);

      navigate(`/customer/loans/${created.id}`);
    } catch (err) {
      const message = err.message || "Không tạo được hồ sơ vay";
      setError(message);
      setFieldErrors(mapFieldErrors(message, loanFieldKeywords));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">{pageTitle}</Typography>
      <Typography color="text.secondary">{pageDescription}</Typography>

      <Paper sx={{ p: 3 }}>
        <Stack spacing={1.5}>
          <Typography variant="h6">Thông tin cá nhân</Typography>
          {loading && <LinearProgress />}
          {verificationError && <Alert severity="warning">{verificationError}</Alert>}
          {profile ? (
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">Họ tên: {profile.fullName || "-"}</Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">Số điện thoại: {profile.phone || "-"}</Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">Số CCCD: {profile.identityNumber || "-"}</Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">
                  Thu nhập dùng để đối chiếu: {displayedIncome != null ? formatVnd(displayedIncome) : "-"}
                  {usesVerifiedIncome ? " (đã xác minh)" : ""}
                </Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">
                  CCCD mặt trước: {profile.identityCardFrontFileName ? "Đã nộp" : "Thiếu"}
                </Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="body2">
                  CCCD mặt sau: {profile.identityCardBackFileName ? "Đã nộp" : "Thiếu"}
                </Typography>
              </Grid>
            </Grid>
          ) : (
            <Alert severity="info">
              Bạn nên hoàn thiện hồ sơ cá nhân, số CCCD, 2 ảnh CCCD và phiếu lương để việc thẩm định nhanh hơn. Nếu chưa sẵn sàng, bạn vẫn có thể lưu bản nháp trước.
            </Alert>
          )}
          {profile && !hasReusableIdentityProfile(profile) && (
            <Alert severity="warning">
              Hồ sơ khách hàng hiện chưa đủ dữ liệu định danh gốc. Hãy bổ sung số CCCD, ảnh CCCD hai mặt và phiếu lương trước khi gửi hồ sơ vay đi thẩm định.
            </Alert>
          )}
          {profile && !(profile.bankAccountNumber && profile.bankName) && (
            <Alert severity="info">
              Bạn chưa khai báo tài khoản nhận giải ngân. Hồ sơ vay vẫn có thể được nộp và thẩm định, nhưng sẽ phải bổ sung số tài khoản và tên ngân hàng trước khi nhân viên giải ngân.
            </Alert>
          )}
          {informationVerification && (
            <Alert severity={verificationStatus === "PASSED" ? "success" : verificationStatus === "FAILED" ? "warning" : "info"}>
              Trạng thái xác minh thông tin hồ sơ cá nhân: {labelVerificationStatus(verificationStatus)}.
              {informationVerification.rejectionReason ? ` Lý do: ${informationVerification.rejectionReason}.` : ""}
              {verificationStatus === "PASSED"
                ? " Hệ thống sẽ ưu tiên dữ liệu đã xác minh khi thẩm định hồ sơ vay."
                : " Bạn vẫn có thể lưu nháp hoặc nộp hồ sơ vay; nhân viên sẽ tiếp tục rà soát trong từng hồ sơ cụ thể."}
            </Alert>
          )}
          {blockingLoan && !isEditMode && (
            <Alert severity="warning">
              {blockingLoan.status === "DRAFT"
                ? `Bạn đang có bản nháp hồ sơ vay #${blockingLoan.id}. Hãy tiếp tục hoàn thiện bản nháp đó thay vì tạo mới.`
                : `Bạn đang có hồ sơ vay #${blockingLoan.id} ở trạng thái ${labelLoanStatus(blockingLoan.status)}. Mỗi khách hàng chỉ được phép có một hồ sơ đang xử lý tại cùng thời điểm.`}
            </Alert>
          )}
          {repaymentLoans.length > 0 && (
            <Alert severity="info">
              Bạn đang có {repaymentLoans.length} khoản vay còn hiệu lực. Nghĩa vụ trả nợ hiện tại của các khoản vay này sẽ được tính vào khả năng vay mới.
            </Alert>
          )}
          {!profile && (
            <Button component={RouterLink} to="/customer/profile" variant="outlined" sx={{ alignSelf: "flex-start" }}>
              Đến hồ sơ của tôi
            </Button>
          )}
          {blockingLoan && !isEditMode && (
            <Button
              component={RouterLink}
              to={blockingLoan.status === "DRAFT" ? `/customer/loans/${blockingLoan.id}/edit` : `/customer/loans/${blockingLoan.id}`}
              variant="outlined"
              sx={{ alignSelf: "flex-start" }}
            >
              {blockingLoan.status === "DRAFT" ? "Tiếp tục bản nháp hiện tại" : "Xem hồ sơ hiện tại"}
            </Button>
          )}
        </Stack>
      </Paper>

      <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
          {isEditMode && draftLoan?.status === "DRAFT" && (
            <Alert severity="info">
              Đây là bản nháp hồ sơ vay #{draftLoan.id}. Bạn có thể lưu lại nhiều lần trước khi gửi thẩm định.
            </Alert>
          )}

          <Stack spacing={1}>
            <Typography variant="h6">Chọn loại vay</Typography>
            <ToggleButtonGroup
              exclusive
              value={form.loanType}
              onChange={handleLoanTypeChange}
              disabled={busy || loading || isEditMode}
              sx={{ alignSelf: "flex-start" }}
            >
              <ToggleButton value="UNSECURED">{labelLoanType("UNSECURED")}</ToggleButton>
              <ToggleButton value="SECURED">{labelLoanType("SECURED")}</ToggleButton>
            </ToggleButtonGroup>
            {isEditMode && (
              <Typography variant="caption" color="text.secondary">
                Loại vay của bản nháp đã được khóa để tránh lẫn bộ chứng từ và logic thẩm định.
              </Typography>
            )}
            <Typography variant="body2" color="text.secondary">{loanTypeHelp}</Typography>
          </Stack>

          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField
                label="Số tiền vay"
                type="text"
                value={form.amount}
                onChange={handleMoneyChange("amount")}
                required
                fullWidth
                disabled={busy || loading || !draftEditable}
                inputProps={{ inputMode: "numeric" }}
                {...fieldErrorProps(fieldErrors, "amount")}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                label="Kỳ hạn (tháng)"
                type="number"
                value={form.termMonths}
                onChange={handleChange("termMonths")}
                required
                fullWidth
                disabled={busy || loading || !draftEditable}
                {...fieldErrorProps(fieldErrors, "termMonths")}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                select
                label="Nhu cầu vay"
                fullWidth
                value={form.purpose}
                onChange={handleChange("purpose")}
                disabled={busy || loading || !draftEditable}
              >
                <MenuItem value="PERSONAL">Tiêu dùng cá nhân</MenuItem>
                <MenuItem value="HOME">Mua nhà</MenuItem>
                <MenuItem value="EDUCATION">Học tập</MenuItem>
                <MenuItem value="BUSINESS">Kinh doanh</MenuItem>
              </TextField>
            </Grid>
          </Grid>

          <Alert severity="info">
            Hạn mức chính thức không được hiển thị trước trên màn này để tránh gây hiểu nhầm. Hệ thống và nhân viên sẽ xác định hồ sơ đủ điều kiện dựa trên thu nhập đã xác minh, nghĩa vụ nợ hiện tại, kỳ hạn vay, tài sản bảo đảm và kết quả kiểm tra rủi ro.
            {form.loanType === "SECURED" && collateralValue != null && collateralValue > 0
              ? ` Giá trị tài sản bạn đang kê khai là ${formatVnd(collateralValue)}, nhưng đây chỉ là một trong các căn cứ thẩm định.`
              : ""}
          </Alert>

          {form.loanType === "SECURED" && (
            <Stack spacing={2}>
              <Divider />
              <Typography variant="h6">Chứng từ vay thế chấp</Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    label="Tài sản bảo đảm"
                    fullWidth
                    value={form.collateralType}
                    onChange={handleChange("collateralType")}
                    disabled={busy || loading || !draftEditable}
                  >
                    <MenuItem value="VEHICLE_REGISTRATION">{labelCollateralType("VEHICLE_REGISTRATION")}</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField
                    label="Giá trị tài sản bảo đảm"
                    type="text"
                    value={form.collateralValue}
                    onChange={handleMoneyChange("collateralValue")}
                    fullWidth
                    required
                    disabled={busy || loading || !draftEditable}
                    inputProps={{ inputMode: "numeric" }}
                    placeholder="Ví dụ: 150.000.000"
                    {...fieldErrorProps(
                      fieldErrors,
                      "collateralValue",
                      "Nhập giá trị thị trường ước tính của tài sản"
                    )}
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <FilePicker
                    label={labelLoanDocumentType("VEHICLE_REGISTRATION")}
                    file={files.vehicleRegistration}
                    currentFileName={storedDocuments.VEHICLE_REGISTRATION?.fileName}
                    disabled={busy || loading || !draftEditable}
                    onChange={handleFileChange("vehicleRegistration")}
                    error={Boolean(fieldErrors.vehicleRegistration)}
                    helperText={fieldErrors.vehicleRegistration}
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <FilePicker
                    label={labelLoanDocumentType("LICENSE_PLATE_IMAGE")}
                    file={files.licensePlateImage}
                    currentFileName={storedDocuments.LICENSE_PLATE_IMAGE?.fileName}
                    disabled={busy || loading || !draftEditable}
                    onChange={handleFileChange("licensePlateImage")}
                    error={Boolean(fieldErrors.licensePlateImage)}
                    helperText={fieldErrors.licensePlateImage}
                  />
                </Grid>
              </Grid>
            </Stack>
          )}

          {form.loanType === "UNSECURED" && (
            <Stack spacing={2}>
              <Divider />
              <Typography variant="h6">Xác minh vay tín chấp</Typography>
              <Alert severity="info">
                CCCD hai mặt sẽ được dùng lại từ hồ sơ khách hàng gốc. Bước này chỉ yêu cầu ảnh khuôn mặt hiện tại để nhân viên đối chiếu với dữ liệu định danh đã lưu.
              </Alert>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      borderColor: fieldErrors.faceCapture ? "error.main" : "divider",
                      borderWidth: fieldErrors.faceCapture ? 2 : 1
                    }}
                  >
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle2">{labelLoanDocumentType("FACE_CAPTURE")}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Ảnh khuôn mặt phải được chụp trực tiếp bằng máy ảnh, không hỗ trợ tải tệp lên cho bước này.
                      </Typography>
                      {cameraError && <Alert severity="warning">{cameraError}</Alert>}
                      {fieldErrors.faceCapture && <Alert severity="error">{fieldErrors.faceCapture}</Alert>}
                      {files.faceCapture && (
                        <Alert severity="success">
                          Đã chụp ảnh khuôn mặt ({formatFileSize(files.faceCapture.size)}).
                        </Alert>
                      )}
                      {!files.faceCapture && storedDocuments.FACE_CAPTURE && (
                        <Alert severity="info">
                          Đã lưu ảnh khuôn mặt: {storedDocuments.FACE_CAPTURE.fileName}. Bạn chỉ cần chụp lại nếu muốn thay ảnh hiện tại.
                        </Alert>
                      )}
                      {cameraActive && (
                        <Box
                          component="video"
                          ref={videoRef}
                          autoPlay
                          playsInline
                          muted
                          sx={{
                            width: "100%",
                            maxWidth: 420,
                            borderRadius: 1,
                            border: "1px solid",
                            borderColor: "divider",
                            bgcolor: "grey.100"
                          }}
                        />
                      )}
                      <canvas ref={canvasRef} style={{ display: "none" }} />
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                        {!cameraActive && (
                          <Button
                            variant="outlined"
                            onClick={startCamera}
                            disabled={busy || loading || !draftEditable}
                          >
                            {storedDocuments.FACE_CAPTURE ? "Chụp lại ảnh khuôn mặt" : "Mở máy ảnh"}
                          </Button>
                        )}
                        {cameraActive && (
                          <>
                            <Button variant="contained" onClick={captureFace} disabled={busy || !draftEditable}>
                              Chụp ảnh
                            </Button>
                            <Button variant="outlined" color="inherit" onClick={stopCamera} disabled={busy || !draftEditable}>
                              Tắt máy ảnh
                            </Button>
                          </>
                        )}
                      </Stack>
                    </Stack>
                  </Paper>
                </Grid>
              </Grid>
            </Stack>
          )}

          <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
            <Button
              type="button"
              variant="outlined"
              onClick={handleSaveDraft}
              disabled={busy || loading || creationBlocked || !draftEditable}
            >
              {savingDraft ? "Đang lưu nháp..." : isEditMode ? "Lưu cập nhật bản nháp" : "Lưu nháp"}
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={busy || creationBlocked || loading || !draftEditable}
            >
              {submitting
                ? "Đang gửi..."
                : isEditMode
                  ? "Gửi hồ sơ từ bản nháp"
                  : "Gửi hồ sơ vay"}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
