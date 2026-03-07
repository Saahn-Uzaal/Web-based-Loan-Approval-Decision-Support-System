import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Paper,
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
import { getMyLoansApi } from "@/features/customer/api/loanApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelLoanPurpose, labelLoanStatus } from "@/shared/utils/labels";

function StatusChip({ status }) {
  const colorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return <Chip size="small" color={colorMap[status] || "default"} label={labelLoanStatus(status)} />;
}

export default function CustomerLoansPage() {
  const { accessToken } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadLoans() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getMyLoansApi(accessToken);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được hồ sơ vay của bạn");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadLoans();
    return () => {
      active = false;
    };
  }, [accessToken]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hồ sơ vay của tôi</Typography>
      <Typography color="text.secondary">
        Theo dõi trạng thái và xem quyết định cuối cùng kèm lý do.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải hồ sơ vay...</Typography>
          </Stack>
        </Paper>
      )}
      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Chưa có hồ sơ vay. Hãy tạo hồ sơ đầu tiên ở mục "Tạo hồ sơ vay".
          </Typography>
        </Paper>
      )}
      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã hồ sơ</TableCell>
              <TableCell>Số tiền</TableCell>
              <TableCell>Kỳ hạn</TableCell>
              <TableCell>Mục đích</TableCell>
              <TableCell>Trạng thái</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell>#{row.id}</TableCell>
                <TableCell>{formatVnd(row.amount)}</TableCell>
                <TableCell>{row.termMonths} tháng</TableCell>
                <TableCell>{labelLoanPurpose(row.purpose)}</TableCell>
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
                    Xem
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
