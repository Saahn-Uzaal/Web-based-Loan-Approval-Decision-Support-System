import { Chip, Divider, Grid, Paper, Stack, Typography } from "@mui/material";
import { useParams } from "react-router-dom";

function KeyValue({ label, value }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      {typeof value === "string" ? <Typography>{value}</Typography> : value}
    </Stack>
  );
}

export default function CustomerLoanDetailPage() {
  const { id } = useParams();

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Loan Request #{id}</Typography>
      <Typography color="text.secondary">
        Final decision and reason will be visible here for customers.
      </Typography>
      <Paper sx={{ p: 3 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <KeyValue label="Status" value={<Chip label="PENDING" color="warning" size="small" />} />
          </Grid>
          <Grid item xs={12} md={4}>
            <KeyValue label="Amount" value="$15,000" />
          </Grid>
          <Grid item xs={12} md={4}>
            <KeyValue label="Term" value="24 months" />
          </Grid>
        </Grid>
        <Divider sx={{ my: 2 }} />
        <Stack spacing={1}>
          <Typography variant="subtitle2">Final Decision Reason</Typography>
          <Typography color="text.secondary">
            Waiting for staff review. Decision reason will be shown after finalization.
          </Typography>
        </Stack>
      </Paper>
    </Stack>
  );
}
