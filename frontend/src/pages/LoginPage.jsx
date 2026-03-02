import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography
} from "@mui/material";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register, isAuthenticated, user, isInitializing } = useAuth();

  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("CUSTOMER");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!isInitializing && isAuthenticated) {
    const next = user.role === "STAFF" ? "/staff/requests" : "/customer/loans";
    return <Navigate to={next} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const authUser = isRegisterMode
        ? await register({ email, password, role })
        : await login({ email, password });
      const fallback = authUser.role === "STAFF" ? "/staff/requests" : "/customer/loans";
      const from = location.state?.from?.pathname || fallback;
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || "Authentication failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        px: 2,
        background: "linear-gradient(135deg, #f0f4fb 0%, #dce7fb 100%)"
      }}
    >
      <Paper sx={{ width: "100%", maxWidth: 420, p: 4 }}>
        {isInitializing && <LinearProgress sx={{ mb: 2 }} />}
        <Stack spacing={2}>
          <Typography variant="h4">{isRegisterMode ? "Register" : "Sign In"}</Typography>
          <Typography color="text.secondary">
            Authentication now uses backend API with JWT.
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={isRegisterMode}
                onChange={(event) => setIsRegisterMode(event.target.checked)}
              />
            }
            label={isRegisterMode ? "Register mode" : "Login mode"}
          />
          {isRegisterMode && (
            <Alert severity="info">You can register CUSTOMER or STAFF for demo RBAC testing.</Alert>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          <Box component="form" onSubmit={handleSubmit}>
            <Stack spacing={2}>
              <TextField
                label="Email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                placeholder="user@example.com"
                required
                fullWidth
              />
              <TextField
                label="Password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                placeholder="At least 6 characters"
                required
                fullWidth
              />
              {isRegisterMode && (
                <TextField
                  select
                  label="Role"
                  value={role}
                  onChange={(event) => setRole(event.target.value)}
                  fullWidth
                >
                  <MenuItem value="CUSTOMER">CUSTOMER</MenuItem>
                  <MenuItem value="STAFF">STAFF</MenuItem>
                </TextField>
              )}
              <Button type="submit" variant="contained" size="large" disabled={submitting || isInitializing}>
                {submitting ? "Please wait..." : isRegisterMode ? "Register" : "Login"}
              </Button>
            </Stack>
          </Box>
        </Stack>
      </Paper>
    </Box>
  );
}
