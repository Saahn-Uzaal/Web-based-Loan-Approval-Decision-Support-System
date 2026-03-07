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
import { useEffect, useMemo, useState } from "react";
import { createDebtApi, deleteDebtApi, getDebtMetricsApi, getMyDebtsApi } from "@/features/customer/api/debtApi";
import { getMyProfileApi, upsertMyProfileApi } from "@/features/customer/api/profileApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import ConfirmDialog from "@/shared/components/ConfirmDialog";

function normalizeNumberInput(value) {
  if (value === "" || value == null) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeIntegerInput(value) {
  const number = normalizeNumberInput(value);
  if (number == null) {
    return null;
  }
  return Math.trunc(number);
}

const emptyProfileForm = {
  fullName: "",
  phone: "",
  dateOfBirth: "",
  monthlyIncome: "",
  employmentStatus: "",
  employmentStartDate: "",
  creditHistoryScore: ""
};

const emptyDebtForm = {
  debtType: "",
  monthlyPayment: "",
  remainingBalance: "",
  lenderName: ""
};

export default function CustomerProfilePage() {
  const { accessToken } = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [debtSubmitting, setDebtSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [debtError, setDebtError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [debtSuccess, setDebtSuccess] = useState("");
  const [paymentRating, setPaymentRating] = useState(0);
  const [form, setForm] = useState(emptyProfileForm);
  const [debtForm, setDebtForm] = useState(emptyDebtForm);
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
          if (
            String(err.message).includes("Profile not found") ||
            String(err.message).includes("Không tìm thấy hồ sơ")
          ) {
            return null;
          }
          throw err;
        });

        const [profile, debtList, metrics] = await Promise.all([
          profilePromise,
          getMyDebtsApi(accessToken),
          getDebtMetricsApi(accessToken)
        ]);

        if (!active) {
          return;
        }

        if (profile) {
          setForm({
            fullName: profile.fullName ?? "",
            phone: profile.phone ?? "",
            dateOfBirth: profile.dateOfBirth ?? "",
            monthlyIncome: profile.monthlyIncome == null ? "" : formatVndInput(profile.monthlyIncome),
            employmentStatus: profile.employmentStatus ?? "",
            employmentStartDate: profile.employmentStartDate ?? "",
            creditHistoryScore: profile.creditHistoryScore ?? ""
          });
          setPaymentRating(Number(profile.paymentRating || 0));
        } else {
          setForm(emptyProfileForm);
          setPaymentRating(0);
        }

        setDebts(Array.isArray(debtList) ? debtList : []);
        setDebtMetrics(metrics ?? null);
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

  const refreshDebtData = async () => {
    if (!accessToken) {
      return;
    }
    const [debtList, metrics] = await Promise.all([
      getMyDebtsApi(accessToken),
      getDebtMetricsApi(accessToken)
    ]);
    setDebts(Array.isArray(debtList) ? debtList : []);
    setDebtMetrics(metrics ?? null);
  };

  const dtiDisplay = useMemo(() => {
    if (debtMetrics?.debtToIncomeRatio == null) {
      return "Chưa đủ dữ liệu";
    }
    return `${Number(debtMetrics.debtToIncomeRatio).toFixed(2)}%`;
  }, [debtMetrics]);

  const handleChange = (name) => (event) => {
    setForm((prev) => ({
      ...prev,
      [name]: event.target.value
    }));
  };

  const handleMoneyChange = (name) => (event) => {
    setForm((prev) => ({
      ...prev,
      [name]: formatVndInput(event.target.value)
    }));
  };

  const handleDebtChange = (field) => (event) => {
    setDebtForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleDebtMoneyChange = (field) => (event) => {
    setDebtForm((prev) => ({
      ...prev,
      [field]: formatVndInput(event.target.value)
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSuccessMessage("");
    setSaving(true);
    try {
      const payload = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        dateOfBirth: form.dateOfBirth || null,
        monthlyIncome: parseVndInput(form.monthlyIncome),
        debtToIncomeRatio: debtMetrics?.debtToIncomeRatio ?? null,
        employmentStatus: form.employmentStatus.trim() || null,
        employmentStartDate: form.employmentStartDate || null,
        creditHistoryScore: normalizeIntegerInput(form.creditHistoryScore)
      };
      const profile = await upsertMyProfileApi(accessToken, payload);
      setForm({
        fullName: profile.fullName ?? "",
        phone: profile.phone ?? "",
        dateOfBirth: profile.dateOfBirth ?? "",
        monthlyIncome: profile.monthlyIncome == null ? "" : formatVndInput(profile.monthlyIncome),
        employmentStatus: profile.employmentStatus ?? "",
        employmentStartDate: profile.employmentStartDate ?? "",
        creditHistoryScore: profile.creditHistoryScore ?? ""
      });
      setPaymentRating(Number(profile.paymentRating || 0));
      await refreshDebtData();
      setSuccessMessage("Lưu hồ sơ thành công.");
    } catch (err) {
      setError(err.message || "Không lưu được hồ sơ");
    } finally {
      setSaving(false);
    }
  };

  const handleCreateDebt = async (event) => {
    event.preventDefault();
    setDebtError("");
    setDebtSuccess("");

    const monthlyPayment = parseVndInput(debtForm.monthlyPayment);
    const remainingBalance = parseVndInput(debtForm.remainingBalance);

    if (!debtForm.debtType.trim()) {
      setDebtError("Vui lòng nhập tên khoản nợ.");
      return;
    }
    if (monthlyPayment == null || monthlyPayment <= 0) {
      setDebtError("Vui lòng nhập số tiền trả hàng tháng hợp lệ.");
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
      await refreshDebtData();
      setDebtSuccess("Đã thêm khoản nợ.");
    } catch (err) {
      setDebtError(err.message || "Không thêm được khoản nợ");
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
      await refreshDebtData();
      setDebtSuccess("Đã xóa khoản nợ.");
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
        Cập nhật thông tin cá nhân. DTI được tự tính từ danh sách khoản nợ và thu nhập.
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Stack spacing={2} component="form" onSubmit={handleSubmit}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}

          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="subtitle2">Điểm tín nhiệm thanh toán</Typography>
            <Chip
              size="small"
              label={paymentRating}
              color={paymentRating >= 20 ? "success" : paymentRating >= 0 ? "info" : "error"}
            />
            <Chip size="small" label={`DTI: ${dtiDisplay}`} color="default" />
          </Stack>

          <TextField
            label="Họ và tên"
            value={form.fullName}
            onChange={handleChange("fullName")}
            required
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Số điện thoại"
            value={form.phone}
            onChange={handleChange("phone")}
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Ngày sinh"
            type="date"
            value={form.dateOfBirth}
            onChange={handleChange("dateOfBirth")}
            fullWidth
            disabled={loading || saving}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Thu nhập hàng tháng"
            type="text"
            value={form.monthlyIncome}
            onChange={handleMoneyChange("monthlyIncome")}
            fullWidth
            disabled={loading || saving}
            inputProps={{ inputMode: "numeric" }}
          />
          <TextField
            label="Tình trạng việc làm"
            value={form.employmentStatus}
            onChange={handleChange("employmentStatus")}
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Ngày bắt đầu làm việc"
            type="date"
            value={form.employmentStartDate}
            onChange={handleChange("employmentStartDate")}
            fullWidth
            disabled={loading || saving}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Điểm lịch sử tín dụng (0-100)"
            type="number"
            value={form.creditHistoryScore}
            onChange={handleChange("creditHistoryScore")}
            fullWidth
            disabled={loading || saving}
          />
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
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  label="Đơn vị cho vay"
                  value={debtForm.lenderName}
                  onChange={handleDebtChange("lenderName")}
                  fullWidth
                  disabled={debtSubmitting}
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
