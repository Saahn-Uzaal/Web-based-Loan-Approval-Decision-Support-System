import { Paper, Stack, Typography } from "@mui/material";

export default function StaffDashboardPage() {
  return (
    <Stack spacing={2}>
      <Typography variant="h4">Staff Dashboard</Typography>
      <Typography color="text.secondary">
        Optional overview for pending workload and escalation trends.
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Typography variant="body2">
          Dashboard widgets will be added after backend metrics APIs are available.
        </Typography>
      </Paper>
    </Stack>
  );
}
