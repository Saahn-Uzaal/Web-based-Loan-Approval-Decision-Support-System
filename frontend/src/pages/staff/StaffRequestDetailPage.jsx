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
import { useParams } from "react-router-dom";

function InfoCard({ title, children }) {
  return (
    <Paper sx={{ p: 2, height: "100%" }}>
      <Stack spacing={1}>
        <Typography variant="subtitle1">{title}</Typography>
        {children}
      </Stack>
    </Paper>
  );
}

export default function StaffRequestDetailPage() {
  const { id } = useParams();

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Request Review #{id}</Typography>
      <Typography color="text.secondary">
        Staff view: customer profile, loan details, DSS output, and final action.
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <InfoCard title="Customer Profile Summary">
            <Typography variant="body2">Income: $3,000 / month</Typography>
            <Typography variant="body2">DTI: 43%</Typography>
            <Typography variant="body2">Repayment history: Average</Typography>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Loan Details">
            <Typography variant="body2">Amount: $15,000</Typography>
            <Typography variant="body2">Term: 24 months</Typography>
            <Typography variant="body2">Purpose: Personal</Typography>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={7}>
          <InfoCard title="DSS Output">
            <Typography variant="body2">Credit score: 678</Typography>
            <Typography variant="body2">Risk rating: B</Typography>
            <Typography variant="body2">Recommendation: ESCALATE</Typography>
            <Alert severity="info" sx={{ mt: 1 }}>
              Explanation: DTI is high and repayment history is mixed.
            </Alert>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={5}>
          <InfoCard title="Final Decision">
            <Stack spacing={2} component="form">
              <TextField select label="Action" defaultValue="ESCALATE">
                <MenuItem value="APPROVE">APPROVE</MenuItem>
                <MenuItem value="REJECT">REJECT</MenuItem>
                <MenuItem value="ESCALATE">ESCALATE</MenuItem>
              </TextField>
              <TextField
                label="Reason"
                multiline
                rows={4}
                required
                placeholder="Reason is mandatory for final action."
              />
              <Button variant="contained">Submit Decision</Button>
            </Stack>
          </InfoCard>
        </Grid>
      </Grid>
    </Stack>
  );
}
