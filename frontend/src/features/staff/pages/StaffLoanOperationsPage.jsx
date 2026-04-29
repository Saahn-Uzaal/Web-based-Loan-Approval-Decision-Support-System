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
import { getStaffLoanOperationsApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelLoanStatus, labelLoanType } from "@/shared/utils/labels";

const OPERATION_STATUSES = [
  "APPOINTMENT_SCHEDULED",
  "APPROVED",
  "CONTRACTED",
  "DISBURSED",
  "ACTIVE"
];

function StatusChip({ status }) {
  const colorMap = {
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    DISBURSED: "primary",
    ACTIVE: "primary"
  };

  return <Chip size="small" label={labelLoanStatus(status)} color={colorMap[status] || "default"} />;
}

export default function StaffLoanOperationsPage() {
  const { accessToken } = useAuth();
  const [status, setStatus] = useState("");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const statusLabel = status ? labelLoanStatus(status) : "tất cả trạng thái vận hành";

  useEffect(() => {
    let active = true;

    async function loadOperations() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffLoanOperationsApi(accessToken, status);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được danh sách vận hành khoản vay");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadOperations();
    return () => {
      active = false;
    };
  }, [accessToken, status]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Vận hành khoản vay</Typography>
      <Typography color="text.secondary">
        Theo dõi các hồ sơ đã qua bước thẩm định để xử lý lịch hẹn, hợp đồng, giải ngân và khoản vay đang hoạt động.
      </Typography>

      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 260 }}>
          <InputLabel id="operation-status-filter-label">Lọc trạng thái</InputLabel>
          <Select
            labelId="operation-status-filter-label"
            value={status}
            label="Lọc trạng thái"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="">Tất cả vận hành</MenuItem>
            {OPERATION_STATUSES.map((item) => (
              <MenuItem key={item} value={item}>
                {labelLoanStatus(item)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải danh sách vận hành...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có hồ sơ nào ở bộ lọc {statusLabel}.
          </Typography>
        </Paper>
      )}

      {!loading && rows.length > 0 && (
        <Paper sx={{ overflowX: "auto" }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Mã hồ sơ</TableCell>
                <TableCell>Loại vay</TableCell>
                <TableCell>Khách hàng</TableCell>
                <TableCell>Số tiền duyệt</TableCell>
                <TableCell>Góp hằng tháng</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>#{row.id}</TableCell>
                  <TableCell>{labelLoanType(row.loanType)}</TableCell>
                  <TableCell>{row.customerName || row.customerEmail}</TableCell>
                  <TableCell>{formatVnd(row.approvedAmount ?? row.amount)}</TableCell>
                  <TableCell>{row.approvedMonthlyPayment != null ? formatVnd(row.approvedMonthlyPayment) : "-"}</TableCell>
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
                      Mở hồ sơ
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Stack>
  );
}
