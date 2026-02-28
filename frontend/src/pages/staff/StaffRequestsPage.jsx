import {
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";

const demoRows = [
  { id: 202, customer: "CUST-17", amount: 15000, status: "PENDING" },
  { id: 203, customer: "CUST-03", amount: 40000, status: "WAITING_SUPERVISOR" },
  { id: 204, customer: "CUST-11", amount: 8000, status: "PENDING" }
];

export default function StaffRequestsPage() {
  const [status, setStatus] = useState("PENDING");
  const filteredRows = demoRows.filter((row) => row.status === status);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Staff Review Queue</Typography>
      <Typography color="text.secondary">
        Review incoming requests and DSS recommendations.
      </Typography>
      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="status-filter-label">Status filter</InputLabel>
          <Select
            labelId="status-filter-label"
            value={status}
            label="Status filter"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="PENDING">PENDING</MenuItem>
            <MenuItem value="WAITING_SUPERVISOR">WAITING_SUPERVISOR</MenuItem>
          </Select>
        </FormControl>
      </Paper>
      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Request ID</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredRows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>{row.customer}</TableCell>
                <TableCell>${row.amount.toLocaleString()}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={row.status}
                    color={row.status === "PENDING" ? "warning" : "info"}
                  />
                </TableCell>
                <TableCell align="right">
                  <Button
                    component={RouterLink}
                    to={`/staff/requests/${row.id}`}
                    variant="outlined"
                    size="small"
                  >
                    Review
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
