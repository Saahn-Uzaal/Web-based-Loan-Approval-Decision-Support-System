import {
  Alert,
  Button,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { createLoanApi } from "@/features/customer/api/loanApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVndInput, parseVndInput } from "@/shared/utils/currency";

export default function CustomerLoanNewPage() {
  const navigate = useNavigate();
  const { accessToken } = useAuth();

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [form, setForm] = useState({
    amount: "",
    termMonths: "",
    purpose: "PERSONAL",
    collateralValue: ""
  });

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

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSuccessMessage("");
    const amountValue = parseVndInput(form.amount);
    const termMonthsValue = Number(form.termMonths);
    const collateralValue = parseVndInput(form.collateralValue);

    if (amountValue == null || amountValue <= 0) {
      setError("Vui lòng nhập số tiền vay hợp lệ.");
      return;
    }
    if (!Number.isFinite(termMonthsValue) || termMonthsValue <= 0) {
      setError("Vui lòng nhập kỳ hạn hợp lệ.");
      return;
    }

    setSubmitting(true);
    try {
      const created = await createLoanApi(accessToken, {
        amount: amountValue,
        termMonths: termMonthsValue,
        purpose: form.purpose,
        collateralValue: form.collateralValue === "" ? null : collateralValue
      });
      setSuccessMessage(`Tạo hồ sơ vay #${created.id} thành công.`);
      setForm({
        amount: "",
        termMonths: "",
        purpose: "PERSONAL",
        collateralValue: ""
      });
      navigate(`/customer/loans/${created.id}`);
    } catch (err) {
      setError(err.message || "Không tạo được hồ sơ vay");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Tạo hồ sơ vay</Typography>
      <Typography color="text.secondary">
        Nhập số tiền, kỳ hạn, mục đích vay. Trạng thái ban đầu: Chờ xử lý.
      </Typography>
      <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
        <Stack spacing={2} sx={{ mb: 2 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField
              label="Số tiền vay"
              type="text"
              value={form.amount}
              onChange={handleMoneyChange("amount")}
              required
              fullWidth
              disabled={submitting}
              inputProps={{ inputMode: "numeric" }}
            />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField
              label="Kỳ hạn (tháng)"
              type="number"
              value={form.termMonths}
              onChange={handleChange("termMonths")}
              required
              fullWidth
              disabled={submitting}
            />
          </Grid>
          <Grid item xs={12}>
            <TextField
              select
              label="Mục đích"
              fullWidth
              value={form.purpose}
              onChange={handleChange("purpose")}
              disabled={submitting}
            >
              <MenuItem value="PERSONAL">Tiêu dùng cá nhân</MenuItem>
              <MenuItem value="HOME">Mua nhà</MenuItem>
              <MenuItem value="EDUCATION">Học tập</MenuItem>
              <MenuItem value="BUSINESS">Kinh doanh</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12}>
            <TextField
              label="Giá trị tài sản đảm bảo (tùy chọn)"
              type="text"
              value={form.collateralValue}
              onChange={handleMoneyChange("collateralValue")}
              fullWidth
              disabled={submitting}
              inputProps={{ inputMode: "numeric" }}
            />
          </Grid>
          <Grid item xs={12}>
            <Button type="submit" variant="contained" disabled={submitting}>
              {submitting ? "Đang gửi..." : "Gửi hồ sơ vay"}
            </Button>
          </Grid>
        </Grid>
      </Paper>
    </Stack>
  );
}
