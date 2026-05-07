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
  TablePagination,
  TableRow,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { getStaffLoanOperationsPagedApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelLoanStatus, labelLoanType } from "@/shared/utils/labels";

const SECTION_CONFIGS = [
  {
    key: "approved",
    title: "Chờ khách chấp nhận",
    description: "Bao gồm hồ sơ đã được duyệt và đã có điều khoản, đang chờ khách hàng chấp nhận hợp đồng.",
    status: "APPROVED"
  },
  {
    key: "contracted",
    title: "Chờ kích hoạt khoản vay",
    description: "Bao gồm hồ sơ đã có hợp đồng hiệu lực nhưng còn chờ bước kích hoạt khoản vay.",
    status: "CONTRACTED"
  },
  {
    key: "servicing",
    title: "Khoản vay đang theo dõi",
    description: "Bao gồm các khoản vay đã kích hoạt và đang ở giai đoạn theo dõi thanh toán định kỳ.",
    status: "ACTIVE"
  },
  {
    key: "collections",
    title: "Khoản vay quá hạn",
    description: "Bao gồm các khoản vay đã quá hạn và cần theo dõi thu hồi nợ.",
    status: "OVERDUE"
  }
];

function StatusChip({ status }) {
  const colorMap = {
    APPROVED: "success",
    CONTRACTED: "info",
    ACTIVE: "primary",
    OVERDUE: "error"
  };

  return <Chip size="small" label={labelLoanStatus(status)} color={colorMap[status] || "default"} />;
}

function OperationTable({ title, description, status, accessToken }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [totalRows, setTotalRows] = useState(0);

  useEffect(() => {
    let active = true;

    async function loadSection() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffLoanOperationsPagedApi(accessToken, {
          status,
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
        setError(err.message || "Không tải được nhóm vận hành này");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadSection();
    return () => {
      active = false;
    };
  }, [accessToken, page, rowsPerPage, status]);

  return (
    <Paper sx={{ p: 2.5 }}>
      <Stack spacing={2}>
        <Stack spacing={0.5}>
          <Typography variant="h6">{title}</Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        </Stack>

        {error && <Alert severity="error">{error}</Alert>}

        {loading ? (
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={18} />
            <Typography variant="body2" color="text.secondary">
              Đang tải danh sách...
            </Typography>
          </Stack>
        ) : totalRows === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Không có hồ sơ nào trong nhóm này.
          </Typography>
        ) : (
          <Paper variant="outlined" sx={{ overflowX: "auto" }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Mã hồ sơ</TableCell>
                  <TableCell>Loại vay</TableCell>
                  <TableCell>Khách hàng</TableCell>
                  <TableCell>Phụ trách</TableCell>
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
                    <TableCell>{row.assignedStaffEmail || "Chưa có người phụ trách"}</TableCell>
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
        )}
      </Stack>
    </Paper>
  );
}

export default function StaffLoanOperationsPage() {
  const { accessToken } = useAuth();
  return (
    <Stack spacing={2}>
      <Typography variant="h4">Vận hành khoản vay</Typography>
      <Typography color="text.secondary">
        Tách riêng các giai đoạn sau phê duyệt, khoản vay đang theo dõi và khoản vay quá hạn. Hồ sơ thế chấp đã lên lịch hẹn được xử lý ở màn thủ tục vay thế chấp.
      </Typography>

      {SECTION_CONFIGS.map((section) => (
        <OperationTable
          key={section.key}
          title={section.title}
          description={section.description}
          status={section.status}
          accessToken={accessToken}
        />
      ))}
    </Stack>
  );
}
