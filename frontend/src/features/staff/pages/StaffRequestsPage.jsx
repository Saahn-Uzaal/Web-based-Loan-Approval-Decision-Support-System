import {
  Alert,
  Button,
  Chip,
  CircularProgress,
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
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { getStaffRequestsApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelDssRecommendation, labelLoanStatus } from "@/shared/utils/labels";

function StatusChip({ status }) {
  const colorMap = {
    PENDING: "warning",
    WAITING_SUPERVISOR: "info",
    APPROVED: "success",
    REJECTED: "error"
  };

  return <Chip size="small" label={labelLoanStatus(status)} color={colorMap[status] || "default"} />;
}

export default function StaffRequestsPage() {
  const { accessToken } = useAuth();
  const [status, setStatus] = useState("PENDING");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadQueue() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffRequestsApi(accessToken, status);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được hàng đợi thẩm định");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadQueue();
    return () => {
      active = false;
    };
  }, [accessToken, status]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hàng đợi thẩm định</Typography>
      <Typography color="text.secondary">
        Thẩm định hồ sơ và xem khuyến nghị từ DSS.
      </Typography>
      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="status-filter-label">Lọc trạng thái</InputLabel>
          <Select
            labelId="status-filter-label"
            value={status}
            label="Lọc trạng thái"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="PENDING">Chờ xử lý</MenuItem>
            <MenuItem value="WAITING_SUPERVISOR">Chờ quản lý duyệt</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải hàng đợi thẩm định...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có hồ sơ nào ở trạng thái {labelLoanStatus(status)}.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã hồ sơ</TableCell>
              <TableCell>Khách hàng</TableCell>
              <TableCell>Số tiền</TableCell>
              <TableCell>Khuyến nghị DSS</TableCell>
              <TableCell>Trạng thái</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>{row.customerName || row.customerEmail}</TableCell>
                <TableCell>{formatVnd(row.amount)}</TableCell>
                <TableCell>
                  {labelDssRecommendation(row.dssRecommendation)}
                </TableCell>
                <TableCell>
                  <StatusChip status={row.status} />
                </TableCell>
                <TableCell align="right">
                  <Button
                    component={RouterLink}
                    to={`/staff/requests/${row.id}`}
                    variant="outlined"
                    size="small"
                  >
                    Thẩm định
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
