import {
  Alert,
  Button,
  Chip,
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
import { useState } from "react";
import { registerApi } from "@/features/auth/api/authApi";
import { labelRole } from "@/shared/utils/labels";

export default function StaffUserCreatePage() {
  const [form, setForm] = useState({
    email: "",
    password: "",
    role: "CUSTOMER"
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [createdAccounts, setCreatedAccounts] = useState([]);

  const handleChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setSuccess("");
    try {
      const payload = await registerApi({
        email: form.email,
        password: form.password,
        role: form.role
      });
      const createdUser = payload?.user;
      if (createdUser) {
        setCreatedAccounts((prev) => [
          {
            id: createdUser.id,
            email: createdUser.email,
            role: createdUser.role
          },
          ...prev
        ]);
      }
      setSuccess(`Đã tạo tài khoản ${labelRole(form.role)}: ${form.email}`);
      setForm((prev) => ({
        ...prev,
        email: "",
        password: ""
      }));
    } catch (err) {
      setError(err.message || "Không tạo được tài khoản");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Tạo tài khoản người dùng</Typography>
      <Typography color="text.secondary">
        Tạo nhiều tài khoản để kiểm thử nhân viên và khách hàng.
      </Typography>

      <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          {success && <Alert severity="success">{success}</Alert>}

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <TextField
                label="Email"
                type="email"
                value={form.email}
                onChange={handleChange("email")}
                required
                fullWidth
                disabled={submitting}
                placeholder="staff01@example.com"
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                label="Mật khẩu"
                type="password"
                value={form.password}
                onChange={handleChange("password")}
                required
                fullWidth
                disabled={submitting}
                inputProps={{ minLength: 6 }}
                placeholder="Tối thiểu 6 ký tự"
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                label="Vai trò"
                value={form.role}
                onChange={handleChange("role")}
                fullWidth
                disabled={submitting}
              >
                <MenuItem value="CUSTOMER">Khách hàng</MenuItem>
                <MenuItem value="STAFF">Nhân viên</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <Button type="submit" variant="contained" disabled={submitting}>
                {submitting ? "Đang tạo..." : "Tạo tài khoản"}
              </Button>
            </Grid>
          </Grid>
        </Stack>
      </Paper>

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã người dùng</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Vai trò</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {createdAccounts.map((account) => (
              <TableRow key={`${account.id}-${account.email}`} hover>
                <TableCell>#{account.id}</TableCell>
                <TableCell>{account.email}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={labelRole(account.role)}
                    color={account.role === "STAFF" ? "primary" : "default"}
                  />
                </TableCell>
              </TableRow>
            ))}
            {createdAccounts.length === 0 && (
              <TableRow>
                <TableCell colSpan={3}>
                  <Typography variant="body2" color="text.secondary">
                    Chưa tạo tài khoản nào trong phiên này.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}
