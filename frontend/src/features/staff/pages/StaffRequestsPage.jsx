import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  TablePagination,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { getStaffRequestsPagedApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelDssRecommendation, labelLoanStatus, labelLoanType } from "@/shared/utils/labels";

function StatusChip({ status }) {
  const colorMap = {
    PENDING: "warning",
    NEEDS_MORE_INFO: "warning",
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    ACTIVE: "primary",
    OVERDUE: "error",
    CLOSED: "default",
    REJECTED: "error"
  };

  return <Chip size="small" label={labelLoanStatus(status)} color={colorMap[status] || "default"} />;
}

export default function StaffRequestsPage() {
  const { accessToken } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalRows, setTotalRows] = useState(0);

  useEffect(() => {
    let active = true;

    async function loadQueue() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffRequestsPagedApi(accessToken, {
          page,
          size: rowsPerPage
        });
        if (!active) {
          return;
        }
        setRows(Array.isArray(response?.content) ? response.content : []);
        setTotalRows(Number(response?.totalElements || 0));
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
  }, [accessToken, page, rowsPerPage]);

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hàng đợi thẩm định</Typography>
      <Typography color="text.secondary">
        Hiển thị hồ sơ đang chờ quyết định thẩm định ban đầu và hồ sơ đã yêu cầu khách bổ sung thông tin.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải hàng đợi thẩm định...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && totalRows === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có hồ sơ nào đang chờ thẩm định hoặc chờ bổ sung thông tin.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã hồ sơ</TableCell>
              <TableCell>Loại vay</TableCell>
              <TableCell>Khách hàng</TableCell>
              <TableCell>Phụ trách</TableCell>
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
                <TableCell>{labelLoanType(row.loanType)}</TableCell>
                <TableCell>{row.customerName || row.customerEmail}</TableCell>
                <TableCell>{row.assignedStaffEmail || "Chưa có người phụ trách"}</TableCell>
                <TableCell>{formatVnd(row.amount)}</TableCell>
                <TableCell>{labelDssRecommendation(row.dssRecommendation)}</TableCell>
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
        <TablePagination
          component="div"
          count={totalRows}
          page={page}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(event) => {
            setRowsPerPage(Number(event.target.value));
            setPage(0);
          }}
          rowsPerPageOptions={[5, 10, 25]}
          labelRowsPerPage="Số dòng"
        />
      </Paper>
    </Stack>
  );
}
