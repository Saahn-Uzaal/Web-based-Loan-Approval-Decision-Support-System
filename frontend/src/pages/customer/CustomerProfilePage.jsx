import { Alert, Button, Chip, Paper, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { getMyProfileApi, upsertMyProfileApi } from "../../api/profileApi";
import { useAuth } from "../../auth/AuthContext";

function normalizeNumberInput(value) {
  if (value === "" || value == null) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export default function CustomerProfilePage() {
  const { accessToken } = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [paymentRating, setPaymentRating] = useState(0);
  const [form, setForm] = useState({
    fullName: "",
    phone: "",
    monthlyIncome: "",
    debtToIncomeRatio: "",
    employmentStatus: ""
  });

  useEffect(() => {
    let active = true;

    async function loadProfile() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const profile = await getMyProfileApi(accessToken);
        if (!active) {
          return;
        }
        setForm({
          fullName: profile.fullName ?? "",
          phone: profile.phone ?? "",
          monthlyIncome: profile.monthlyIncome ?? "",
          debtToIncomeRatio: profile.debtToIncomeRatio ?? "",
          employmentStatus: profile.employmentStatus ?? ""
        });
        setPaymentRating(Number(profile.paymentRating || 0));
      } catch (err) {
        if (!active) {
          return;
        }
        if (!String(err.message).includes("Profile not found")) {
          setError(err.message || "Failed to load profile");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadProfile();
    return () => {
      active = false;
    };
  }, [accessToken]);

  const handleChange = (name) => (event) => {
    setForm((prev) => ({
      ...prev,
      [name]: event.target.value
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
        monthlyIncome: normalizeNumberInput(form.monthlyIncome),
        debtToIncomeRatio: normalizeNumberInput(form.debtToIncomeRatio),
        employmentStatus: form.employmentStatus.trim() || null
      };
      const profile = await upsertMyProfileApi(accessToken, payload);
      setForm({
        fullName: profile.fullName ?? "",
        phone: profile.phone ?? "",
        monthlyIncome: profile.monthlyIncome ?? "",
        debtToIncomeRatio: profile.debtToIncomeRatio ?? "",
        employmentStatus: profile.employmentStatus ?? ""
      });
      setPaymentRating(Number(profile.paymentRating || 0));
      setSuccessMessage("Profile saved successfully.");
    } catch (err) {
      setError(err.message || "Failed to save profile");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">My Profile</Typography>
      <Typography color="text.secondary">
        Update your personal and financial information used for loan assessment.
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Stack spacing={2} component="form" onSubmit={handleSubmit}>
          {error && <Alert severity="error">{error}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="subtitle2">Payment Rating</Typography>
            <Chip
              size="small"
              label={paymentRating}
              color={paymentRating >= 20 ? "success" : paymentRating >= 0 ? "info" : "error"}
            />
          </Stack>
          <TextField
            label="Full Name"
            value={form.fullName}
            onChange={handleChange("fullName")}
            required
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Phone"
            value={form.phone}
            onChange={handleChange("phone")}
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Monthly Income"
            type="number"
            value={form.monthlyIncome}
            onChange={handleChange("monthlyIncome")}
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Debt-to-Income Ratio (%)"
            type="number"
            value={form.debtToIncomeRatio}
            onChange={handleChange("debtToIncomeRatio")}
            fullWidth
            disabled={loading || saving}
          />
          <TextField
            label="Employment Status"
            value={form.employmentStatus}
            onChange={handleChange("employmentStatus")}
            fullWidth
            disabled={loading || saving}
          />
          <Button type="submit" variant="contained" disabled={loading || saving}>
            {saving ? "Saving..." : "Save Profile"}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
