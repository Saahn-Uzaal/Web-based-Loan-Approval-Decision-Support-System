import {
  Alert,
  Button,
  Chip,
  Grid,
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
import { useEffect, useMemo, useRef, useState } from "react";
import { createDebtApi, deleteDebtApi, getDebtMetricsApi, getMyDebtsApi } from "@/features/customer/api/debtApi";
import { getMyInformationVerificationApi } from "@/features/customer/api/informationVerificationApi";
import { downloadMyPayslipApi, getMyProfileApi, upsertMyProfileApi } from "@/features/customer/api/profileApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import { clearFieldError, fieldErrorProps, mapFieldErrors } from "@/shared/utils/formErrors";
import { PAYSLIP_ACCEPT, formatFileSize, isAcceptedPayslipFile } from "@/shared/utils/files";
import { labelVerificationStatus } from "@/shared/utils/labels";
import ConfirmDialog from "@/shared/components/ConfirmDialog";

const emptyProfileForm = {
  fullName: "",
  phone: "",
  dateOfBirth: "",
  monthlyIncome: "",
  verifiedMonthlyIncome: null
};

const emptyDebtForm = {
  debtType: "",
  monthlyPayment: "",
  remainingBalance: "",
  lenderName: ""
};

const profileFieldKeywords = {
  fullName: ["họ và tên", "họ tên", "full name", "fullName"],
  phone: ["số điện thoại", "điện thoại", "phone"],
  dateOfBirth: ["ngày sinh", "date of birth"],
  monthlyIncome: ["thu nhập", "lương", "monthlyIncome"],
  payslip: ["phiếu lương", "payslip", "file"]
};

const debtFieldKeywords = {
  debtType: ["tên khoản nợ", "khoản nợ", "debtType"],
  monthlyPayment: ["trả hàng tháng", "trả hằng tháng", "monthlyPayment"],
  remainingBalance: ["dư nợ", "remainingBalance"],
  lenderName: ["đơn vị cho vay", "lenderName"]
};

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

export default function CustomerProfilePage() {
  const { accessToken } = useAuth();
  const fileInputRef = useRef(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [debtSubmitting, setDebtSubmitting] = useState(false);
  const [downloadingPayslip, setDownloadingPayslip] = useState(false);
  const [error, setError] = useState("");
  const [debtError, setDebtError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [debtSuccess, setDebtSuccess] = useState("");
  const [paymentRating, setPaymentRating] = useState(0);
  const [informationVerification, setInformationVerification] = useState(null);
  const [form, setForm] = useState(emptyProfileForm);
  const [profileFieldErrors, setProfileFieldErrors] = useState({});
  const [selectedPayslip, setSelectedPayslip] = useState(null);
  const [currentPayslip, setCurrentPayslip] = useState(null);
  const [debtForm, setDebtForm] = useState(emptyDebtForm);
  const [debtFieldErrors, setDebtFieldErrors] = useState({});
  const [debts, setDebts] = useState([]);
  const [debtMetrics, setDebtMetrics] = useState(null);
  const [confirmDeleteDebt, setConfirmDeleteDebt] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadData() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      setDebtError("");
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

        const [profile, debtList, metrics, verification] = await Promise.all([
          profilePromise,
          getMyDebtsApi(accessToken),
          getDebtMetricsApi(accessToken),
          getMyInformationVerificationApi(accessToken)
        ]);

        if (!active) {
          return;
        }

        if (profile) {
          setForm({
            fullName: profile.fullName ?? "",
            phone: profile.phone ?? "",
            dateOfBirth: profile.dateOfBirth ?? "",
            monthlyIncome: profile.monthlyIncome != null ? formatVndInput(profile.monthlyIncome) : "",
            verifiedMonthlyIncome: profile.verifiedMonthlyIncome ?? null
          });
          setPaymentRating(Number(profile.paymentRating || 0));
          setCurrentPayslip(toPayslipSummary(profile));
        } else {
          setForm(emptyProfileForm);
          setPaymentRating(0);
          setCurrentPayslip(null);
        }

        setSelectedPayslip(null);
        if (fileInputRef.current) {
          fileInputRef.current.value = "";
        }
        setDebts(Array.isArray(debtList) ? debtList : []);
        setDebtMetrics(metrics ?? null);
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
      return;
    }
    const [debtList, metrics, verification] = await Promise.all([
      getMyDebtsApi(accessToken),
      getDebtMetricsApi(accessToken),
      getMyInformationVerificationApi(accessToken)
    ]);
    setDebts(Array.isArray(debtList) ? debtList : []);
    setDebtMetrics(metrics ?? null);
    setInformationVerification(verification ?? null);
  };

  const dtiDisplay = useMemo(() => {
    if (debtMetrics?.debtToIncomeRatio == null) {
      return "Chưa đủ dữ liệu";
    }
    return `${Number(debtMetrics.debtToIncomeRatio).toFixed(2)}%`;
  }, [debtMetrics]);

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

  const handleDebtChange = (field) => (event) => {
    setDebtFieldErrors((prev) => clearFieldError(prev, field));
    setDebtForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleDebtMoneyChange = (field) => (event) => {
    setDebtFieldErrors((prev) => clearFieldError(prev, field));
    setDebtForm((prev) => ({
      ...prev,
      [field]: formatVndInput(event.target.value)
    }));
  };

  const clearSelectedPayslip = () => {
    setSelectedPayslip(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
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

    const monthlyIncome = parseVndInput(form.monthlyIncome);
    if (monthlyIncome == null || monthlyIncome <= 0) {
      const message = "Vui lòng nhập thu nhập hàng tháng hợp lệ để tính hạn mức vay.";
      setError(message);
      setProfileFieldErrors({ monthlyIncome: message });
      return;
    }

    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        dateOfBirth: form.dateOfBirth || null,
        monthlyIncome
      };

      const profile = await upsertMyProfileApi(accessToken, payload, selectedPayslip);
      setForm({
        fullName: profile.fullName ?? "",
        phone: profile.phone ?? "",
        dateOfBirth: profile.dateOfBirth ?? "",
        monthlyIncome: profile.monthlyIncome != null ? formatVndInput(profile.monthlyIncome) : "",
        verifiedMonthlyIncome: profile.verifiedMonthlyIncome ?? null
      });
      setPaymentRating(Number(profile.paymentRating || 0));
      setCurrentPayslip(toPayslipSummary(profile));
      clearSelectedPayslip();
      await refreshAuxiliaryData();
      setSuccessMessage("Lưu hồ sơ thành công. Trạng thái xác minh sẽ quay về chờ đối chiếu lại.");
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

  const handleCreateDebt = async (event) => {
    event.preventDefault();
    setDebtError("");
    setDebtFieldErrors({});
    setDebtSuccess("");

    const monthlyPayment = parseVndInput(debtForm.monthlyPayment);
    const remainingBalance = parseVndInput(debtForm.remainingBalance);

    if (!debtForm.debtType.trim()) {
      const message = "Vui lòng nhập tên khoản nợ.";
      setDebtError(message);
      setDebtFieldErrors({ debtType: message });
      return;
    }
    if (monthlyPayment == null || monthlyPayment <= 0) {
      const message = "Vui lòng nhập số tiền trả hàng tháng hợp lệ.";
      setDebtError(message);
      setDebtFieldErrors({ monthlyPayment: message });
      return;
    }

    setDebtSubmitting(true);
    try {
      await createDebtApi(accessToken, {
        debtType: debtForm.debtType.trim(),
        monthlyPayment,
        remainingBalance: remainingBalance ?? 0,
        lenderName: debtForm.lenderName.trim() || null
      });
      setDebtForm(emptyDebtForm);
      await refreshAuxiliaryData();
      setDebtSuccess("Đã thêm khoản nợ. Trạng thái xác minh thông tin đã được đưa về chờ xác minh.");
    } catch (err) {
      const message = err.message || "Không thêm được khoản nợ";
      setDebtError(message);
      setDebtFieldErrors(mapFieldErrors(message, debtFieldKeywords));
    } finally {
      setDebtSubmitting(false);
    }
  };

  const handleDeleteDebt = async (debt) => {
    setConfirmDeleteDebt(debt);
  };

  const handleConfirmDelete = async () => {
    const debt = confirmDeleteDebt;
    setConfirmDeleteDebt(null);
    if (!debt) {
      return;
    }
    setDebtError("");
    setDebtSuccess("");
    setDebtSubmitting(true);
    try {
      await deleteDebtApi(accessToken, debt.id);
      await refreshAuxiliaryData();
      setDebtSuccess("Đã xóa khoản nợ. Trạng thái xác minh thông tin đã được đưa về chờ xác minh.");
    } catch (err) {
      setDebtError(err.message || "Không xóa được khoản nợ");
    } finally {
      setDebtSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hồ sơ của tôi</Typography>
      <Typography color="text.secondary">
        Cập nhật thông tin cá nhân, phiếu lương gần nhất và danh sách khoản nợ để nhân viên đối chiếu trước khi bạn tạo hồ sơ vay mới.
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
            <Chip size="small" label={`DTI: ${dtiDisplay}`} color="default" />
            <Chip
              size="small"
              label={`Xác minh: ${labelVerificationStatus(informationVerification?.status || "PENDING")}`}
              color={verificationChipColor(informationVerification?.status || "PENDING")}
            />
          </Stack>

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

      <Paper sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h6">Các khoản nợ hiện tại</Typography>
          <Typography variant="body2" color="text.secondary">
            Tổng nợ hàng tháng: {formatVnd(debtMetrics?.totalMonthlyDebt || 0)}. DTI hiện tại: {dtiDisplay}.
          </Typography>

          {debtError && <Alert severity="error">{debtError}</Alert>}
          {debtSuccess && <Alert severity="success">{debtSuccess}</Alert>}

          <Paper component="form" variant="outlined" onSubmit={handleCreateDebt} sx={{ p: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Tên khoản nợ"
                  value={debtForm.debtType}
                  onChange={handleDebtChange("debtType")}
                  fullWidth
                  required
                  disabled={debtSubmitting}
                  {...fieldErrorProps(debtFieldErrors, "debtType")}
                />
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField
                  label="Trả hàng tháng"
                  type="text"
                  value={debtForm.monthlyPayment}
                  onChange={handleDebtMoneyChange("monthlyPayment")}
                  fullWidth
                  required
                  disabled={debtSubmitting}
                  inputProps={{ inputMode: "numeric" }}
                  {...fieldErrorProps(debtFieldErrors, "monthlyPayment")}
                />
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField
                  label="Dư nợ còn lại"
                  type="text"
                  value={debtForm.remainingBalance}
                  onChange={handleDebtMoneyChange("remainingBalance")}
                  fullWidth
                  disabled={debtSubmitting}
                  inputProps={{ inputMode: "numeric" }}
                  {...fieldErrorProps(debtFieldErrors, "remainingBalance")}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  label="Đơn vị cho vay"
                  value={debtForm.lenderName}
                  onChange={handleDebtChange("lenderName")}
                  fullWidth
                  disabled={debtSubmitting}
                  {...fieldErrorProps(debtFieldErrors, "lenderName")}
                />
              </Grid>
              <Grid item xs={12}>
                <Button type="submit" variant="contained" disabled={debtSubmitting}>
                  {debtSubmitting ? "Đang thêm..." : "Thêm khoản nợ"}
                </Button>
              </Grid>
            </Grid>
          </Paper>

          <Paper sx={{ overflowX: "auto" }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Tên khoản nợ</TableCell>
                  <TableCell>Trả hàng tháng</TableCell>
                  <TableCell>Dư nợ còn lại</TableCell>
                  <TableCell>Đơn vị cho vay</TableCell>
                  <TableCell align="right">Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {debts.map((debt) => (
                  <TableRow key={debt.id} hover>
                    <TableCell>{debt.debtType}</TableCell>
                    <TableCell>{formatVnd(debt.monthlyPayment)}</TableCell>
                    <TableCell>{formatVnd(debt.remainingBalance)}</TableCell>
                    <TableCell>{debt.lenderName || "-"}</TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        color="error"
                        variant="outlined"
                        disabled={debtSubmitting}
                        onClick={() => handleDeleteDebt(debt)}
                      >
                        Xóa
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {debts.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography variant="body2" color="text.secondary">
                        Chưa có khoản nợ nào.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Paper>
        </Stack>
      </Paper>

      <ConfirmDialog
        open={confirmDeleteDebt != null}
        title="Xóa khoản nợ"
        message={confirmDeleteDebt ? `Bạn có chắc muốn xóa khoản nợ "${confirmDeleteDebt.debtType}"?` : ""}
        confirmText="Xóa"
        cancelText="Hủy"
        onConfirm={handleConfirmDelete}
        onCancel={() => setConfirmDeleteDebt(null)}
      />
    </Stack>
  );
}
