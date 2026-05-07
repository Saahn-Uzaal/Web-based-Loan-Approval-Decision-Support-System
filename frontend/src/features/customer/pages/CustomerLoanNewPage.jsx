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
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { getMyInformationVerificationApi } from "@/features/customer/api/informationVerificationApi";
import { createLoanApi, getMyLoansApi } from "@/features/customer/api/loanApi";
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
  idCardFront: null,
  idCardBack: null,
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
  idCardFront: ["cccd mặt trước", "mặt trước cccd", "id card front"],
  idCardBack: ["cccd mặt sau", "mặt sau cccd", "id card back"],
  faceCapture: ["ảnh khuôn mặt", "khuôn mặt", "face"]
};

function FilePicker({ label, file, disabled, onChange, error, helperText }) {
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
          {file ? "Đổi ảnh" : "Chọn ảnh"}
          <input hidden type="file" accept={LOAN_IMAGE_ACCEPT} onChange={onChange} />
        </Button>
        {file && (
          <Typography variant="body2" color="text.secondary">
            {file.name} ({formatFileSize(file.size)})
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

export default function CustomerLoanNewPage() {
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);

  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraError, setCameraError] = useState("");
  const [error, setError] = useState("");
  const [verificationError, setVerificationError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [informationVerification, setInformationVerification] = useState(null);
  const [profile, setProfile] = useState(null);
  const [existingOpenLoan, setExistingOpenLoan] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [files, setFiles] = useState(emptyFiles);

  useEffect(() => {
    let active = true;

    async function loadData() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setVerificationError("");
      try {
        const profilePromise = getMyProfileApi(accessToken).catch((err) => {
          const message = String(err.message || "");
          if (/kh(?:ông|ong)\s+t(?:ì|i)m\s+th(?:ấy|ay)/i.test(message)) {
            return null;
          }
          throw err;
        });
        const [profileResponse, verificationResponse, loansResponse] = await Promise.all([
          profilePromise,
          getMyInformationVerificationApi(accessToken),
          getMyLoansApi(accessToken)
        ]);
        if (!active) {
          return;
        }
        setProfile(profileResponse);
        setInformationVerification(verificationResponse);
        setExistingOpenLoan(
          (Array.isArray(loansResponse) ? loansResponse : []).find(
            (loan) => !["CLOSED", "REJECTED", "WITHDRAWN"].includes(loan.status)
          ) || null
        );
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
  }, [accessToken]);

  useEffect(() => {
    if (videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [cameraActive]);

  const collateralValue = parseVndInput(form.collateralValue);
  const verificationStatus = informationVerification?.status || "PENDING";
  const blockedByExistingLoan = Boolean(existingOpenLoan);
  const displayedIncome = profile?.verifiedMonthlyIncome ?? profile?.monthlyIncome ?? null;
  const usesVerifiedIncome = profile?.verifiedMonthlyIncome != null;

  const loanTypeHelp = useMemo(() => {
    if (form.loanType === "SECURED") {
      return "Vay bằng giấy tờ xe, cần ảnh giấy tờ xe và ảnh biển số xe tương ứng. Nhân viên sẽ liên hệ đặt lịch hẹn sau khi tiếp nhận.";
    }
    return "Vay tín chấp cần CCCD hai mặt và ảnh khuôn mặt chụp trực tiếp bằng máy ảnh trên trình duyệt.";
  }, [form.loanType]);

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
    if (!value) {
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

  const validateSubmit = () => {
    if (blockedByExistingLoan) {
      return `Bạn đang có hồ sơ vay #${existingOpenLoan.id} chưa kết thúc. Mỗi khách hàng chỉ được có 1 hồ sơ vay tại một thời điểm.`;
    }
    const amountValue = parseVndInput(form.amount);
    const termMonthsValue = Number(form.termMonths);
    if (amountValue == null || amountValue <= 0) {
      return "Vui lòng nhập số tiền vay hợp lệ.";
    }
    if (!Number.isFinite(termMonthsValue) || termMonthsValue <= 0) {
      return "Vui lòng nhập kỳ hạn hợp lệ.";
    }
    if (form.loanType === "SECURED") {
      if (collateralValue == null || collateralValue <= 0) {
        return "Vui lòng nhập giá trị tài sản bảo đảm.";
      }
      if (!files.vehicleRegistration) {
        return "Vui lòng chụp hoặc tải ảnh giấy tờ xe.";
      }
      if (!files.licensePlateImage) {
        return "Vui lòng chụp hoặc tải ảnh biển số xe.";
      }
      return "";
    }
    if (!files.idCardFront) {
      return "Vui lòng tải ảnh CCCD mặt trước.";
    }
    if (!files.idCardBack) {
      return "Vui lòng tải ảnh CCCD mặt sau.";
    }
    if (!files.faceCapture) {
      return "Vui lòng chụp ảnh khuôn mặt hiện tại bằng máy ảnh.";
    }
    return "";
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setFieldErrors({});
    setSuccessMessage("");

    const validationError = validateSubmit();
    if (validationError) {
      setError(validationError);
      setFieldErrors(mapFieldErrors(validationError, loanFieldKeywords));
      return;
    }

    const amountValue = parseVndInput(form.amount);
    const termMonthsValue = Number(form.termMonths);
    setSubmitting(true);
    try {
      const created = await createLoanApi(
        accessToken,
        {
          loanType: form.loanType,
          amount: amountValue,
          termMonths: termMonthsValue,
          purpose: form.purpose,
          collateralType: form.loanType === "SECURED" ? form.collateralType : null,
          collateralValue: form.loanType === "SECURED" ? collateralValue : null
        },
        form.loanType === "SECURED"
          ? {
              vehicleRegistration: files.vehicleRegistration,
              licensePlateImage: files.licensePlateImage
            }
          : {
              idCardFront: files.idCardFront,
              idCardBack: files.idCardBack,
              faceCapture: files.faceCapture
            }
      );
      setSuccessMessage(
        form.loanType === "SECURED"
          ? `Đã tiếp nhận hồ sơ vay thế chấp #${created.id}. Nhân viên sẽ liên hệ để đặt lịch hẹn.`
          : `Đã gửi hồ sơ vay tín chấp #${created.id}. Vui lòng chờ kết quả thẩm định.`
      );
      setForm(emptyForm);
      setFiles(emptyFiles);
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
      <Typography variant="h4">Tạo hồ sơ vay</Typography>
      <Typography color="text.secondary">
        Kiểm tra thông tin cá nhân đã lưu, sau đó chọn vay thế chấp hoặc vay tín chấp để nộp chứng từ tương ứng.
      </Typography>

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
                <Typography variant="body2">
                  Thu nhập dùng để đối chiếu: {displayedIncome != null ? formatVnd(displayedIncome) : "-"}
                  {usesVerifiedIncome ? " (đã xác minh)" : ""}
                </Typography>
              </Grid>
            </Grid>
          ) : (
            <Alert severity="info">
              Bạn cần hoàn thiện hồ sơ cá nhân và phiếu lương trước khi tạo hồ sơ vay.
            </Alert>
          )}
          {informationVerification && (
            <Alert severity={verificationStatus === "PASSED" ? "success" : verificationStatus === "FAILED" ? "warning" : "info"}>
              Trạng thái xác minh thông tin hồ sơ cá nhân: {labelVerificationStatus(verificationStatus)}.
              {informationVerification.rejectionReason ? ` Lý do: ${informationVerification.rejectionReason}.` : ""}
              {verificationStatus === "PASSED"
                ? " Hệ thống sẽ ưu tiên dữ liệu đã xác minh khi thẩm định hồ sơ vay."
                : " Bạn vẫn có thể nộp hồ sơ vay; nhân viên sẽ tiếp tục rà soát trong từng hồ sơ cụ thể."}
            </Alert>
          )}
          {existingOpenLoan && (
            <Alert severity="warning">
              Bạn đang có hồ sơ vay #{existingOpenLoan.id} ở trạng thái {labelLoanStatus(existingOpenLoan.status)}. Mỗi khách hàng chỉ được phép có 1 hồ sơ vay tại một thời điểm.
            </Alert>
          )}
          {(!profile || verificationStatus === "FAILED") && (
            <Button component={RouterLink} to="/customer/profile" variant="outlined" sx={{ alignSelf: "flex-start" }}>
              Đến hồ sơ của tôi
            </Button>
          )}
          {existingOpenLoan && (
            <Button
              component={RouterLink}
              to={`/customer/loans/${existingOpenLoan.id}`}
              variant="outlined"
              sx={{ alignSelf: "flex-start" }}
            >
              Xem hồ sơ hiện tại
            </Button>
          )}
        </Stack>
      </Paper>

      <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
        <Stack spacing={2.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}

          <Stack spacing={1}>
            <Typography variant="h6">Chọn loại vay</Typography>
            <ToggleButtonGroup
              exclusive
              value={form.loanType}
              onChange={handleLoanTypeChange}
              disabled={submitting || loading}
              sx={{ alignSelf: "flex-start" }}
            >
              <ToggleButton value="UNSECURED">{labelLoanType("UNSECURED")}</ToggleButton>
              <ToggleButton value="SECURED">{labelLoanType("SECURED")}</ToggleButton>
            </ToggleButtonGroup>
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
                disabled={submitting || loading}
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
                disabled={submitting || loading}
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
                disabled={submitting || loading}
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
                    disabled={submitting || loading}
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
                    disabled={submitting}
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
                    disabled={submitting || loading}
                    onChange={handleFileChange("vehicleRegistration")}
                    error={Boolean(fieldErrors.vehicleRegistration)}
                    helperText={fieldErrors.vehicleRegistration}
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <FilePicker
                    label={labelLoanDocumentType("LICENSE_PLATE_IMAGE")}
                    file={files.licensePlateImage}
                    disabled={submitting || loading}
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
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <FilePicker
                    label={labelLoanDocumentType("ID_CARD_FRONT")}
                    file={files.idCardFront}
                    disabled={submitting || loading}
                    onChange={handleFileChange("idCardFront")}
                    error={Boolean(fieldErrors.idCardFront)}
                    helperText={fieldErrors.idCardFront}
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <FilePicker
                    label={labelLoanDocumentType("ID_CARD_BACK")}
                    file={files.idCardBack}
                    disabled={submitting || loading}
                    onChange={handleFileChange("idCardBack")}
                    error={Boolean(fieldErrors.idCardBack)}
                    helperText={fieldErrors.idCardBack}
                  />
                </Grid>
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
                            disabled={submitting || loading}
                          >
                            Mở máy ảnh
                          </Button>
                        )}
                        {cameraActive && (
                          <>
                            <Button variant="contained" onClick={captureFace} disabled={submitting}>
                              Chụp ảnh
                            </Button>
                            <Button variant="outlined" color="inherit" onClick={stopCamera} disabled={submitting}>
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

          <Button
            type="submit"
            variant="contained"
            disabled={submitting || blockedByExistingLoan || loading || !profile}
            sx={{ alignSelf: "flex-start" }}
          >
            {submitting ? "Đang gửi..." : "Gửi hồ sơ vay"}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
