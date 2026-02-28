import {
  Button,
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

const demoRows = [
  { id: 101, amount: 15000, termMonths: 24, status: "PENDING" },
  { id: 102, amount: 5000, termMonths: 12, status: "APPROVED" },
  { id: 103, amount: 30000, termMonths: 48, status: "WAITING_SUPERVISOR" }
];

function StatusChip({ status }) {
  const colorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return <Chip size="small" color={colorMap[status] || "default"} label={status} />;
}

export default function CustomerLoansPage() {
  return (
    <Stack spacing={2}>
      <Typography variant="h4">My Loan Requests</Typography>
      <Typography color="text.secondary">
        Track statuses and review final decisions with reasons.
      </Typography>
      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Request ID</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Term</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {demoRows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>${row.amount.toLocaleString()}</TableCell>
                <TableCell>{row.termMonths} months</TableCell>
                <TableCell>
                  <StatusChip status={row.status} />
                </TableCell>
                <TableCell align="right">
                  <Button
                    component={RouterLink}
                    to={`/customer/loans/${row.id}`}
                    variant="outlined"
                    size="small"
                  >
                    View
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}
