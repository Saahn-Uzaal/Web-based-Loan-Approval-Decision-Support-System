import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated, user } = useAuth();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("CUSTOMER");

  if (isAuthenticated) {
    const next = user.role === "STAFF" ? "/staff/requests" : "/customer/loans";
    return <Navigate to={next} replace />;
  }

  const handleSubmit = (event) => {
    event.preventDefault();
    login({ email, role });
    const fallback = role === "STAFF" ? "/staff/requests" : "/customer/loans";
    const from = location.state?.from?.pathname || fallback;
    navigate(from, { replace: true });
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
        <Stack spacing={2}>
          <Typography variant="h4">Sign In</Typography>
          <Typography color="text.secondary">
            Dev login for route scaffolding. API integration comes next.
          </Typography>
          <Alert severity="info">
            Role controls access: CUSTOMER or STAFF.
          </Alert>
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
                select
                label="Role"
                value={role}
                onChange={(event) => setRole(event.target.value)}
                fullWidth
              >
                <MenuItem value="CUSTOMER">CUSTOMER</MenuItem>
                <MenuItem value="STAFF">STAFF</MenuItem>
              </TextField>
              <Button type="submit" variant="contained" size="large">
                Continue
              </Button>
            </Stack>
          </Box>
        </Stack>
      </Paper>
    </Box>
  );
}
