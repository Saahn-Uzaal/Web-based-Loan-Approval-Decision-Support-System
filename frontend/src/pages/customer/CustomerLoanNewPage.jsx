import {
  Button,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";

export default function CustomerLoanNewPage() {
  return (
    <Stack spacing={2}>
      <Typography variant="h4">Create Loan Request</Typography>
      <Typography color="text.secondary">
        Submit amount, term, and purpose. Initial status: PENDING.
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField label="Loan Amount" type="number" fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Term (months)" type="number" fullWidth />
          </Grid>
          <Grid item xs={12}>
            <TextField select label="Purpose" fullWidth defaultValue="PERSONAL">
              <MenuItem value="PERSONAL">Personal</MenuItem>
              <MenuItem value="HOME">Home</MenuItem>
              <MenuItem value="EDUCATION">Education</MenuItem>
              <MenuItem value="BUSINESS">Business</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12}>
            <Button variant="contained">Submit Request</Button>
          </Grid>
        </Grid>
      </Paper>
    </Stack>
  );
}
