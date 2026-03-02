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
import { createLoanApi } from "../../api/loanApi";
import { useAuth } from "../../auth/AuthContext";

export default function CustomerLoanNewPage() {
  const navigate = useNavigate();
  const { accessToken } = useAuth();

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [form, setForm] = useState({
    amount: "",
    termMonths: "",
    purpose: "PERSONAL"
  });

  const handleChange = (name) => (event) => {
    setForm((prev) => ({
      ...prev,
      [name]: event.target.value
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setSuccessMessage("");
    try {
      const created = await createLoanApi(accessToken, {
        amount: Number(form.amount),
        termMonths: Number(form.termMonths),
        purpose: form.purpose
      });
      setSuccessMessage(`Loan request #${created.id} created successfully.`);
      setForm({
        amount: "",
        termMonths: "",
        purpose: "PERSONAL"
      });
      navigate(`/customer/loans/${created.id}`);
    } catch (err) {
      setError(err.message || "Failed to create loan request");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Create Loan Request</Typography>
      <Typography color="text.secondary">
        Submit amount, term, and purpose. Initial status: PENDING.
      </Typography>
      <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
        <Stack spacing={2} sx={{ mb: 2 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField
              label="Loan Amount"
              type="number"
              value={form.amount}
              onChange={handleChange("amount")}
              required
              fullWidth
              disabled={submitting}
            />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField
              label="Term (months)"
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
              label="Purpose"
              fullWidth
              value={form.purpose}
              onChange={handleChange("purpose")}
              disabled={submitting}
            >
              <MenuItem value="PERSONAL">Personal</MenuItem>
              <MenuItem value="HOME">Home</MenuItem>
              <MenuItem value="EDUCATION">Education</MenuItem>
              <MenuItem value="BUSINESS">Business</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12}>
            <Button type="submit" variant="contained" disabled={submitting}>
              {submitting ? "Submitting..." : "Submit Request"}
            </Button>
          </Grid>
        </Grid>
      </Paper>
    </Stack>
  );
}
