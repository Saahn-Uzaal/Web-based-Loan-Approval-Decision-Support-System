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
import {
  downloadInformationVerificationPayslipApi,
  getInformationVerificationsApi
} from "@/features/staff/api/informationVerificationApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { labelVerificationStatus } from "@/shared/utils/labels";

function StatusChip({ status }) {
  const colorMap = {
    PENDING: "warning",
    PASSED: "success",
    FAILED: "error"
  };

  return <Chip size="small" label={labelVerificationStatus(status)} color={colorMap[status] || "default"} />;
}

export default function StaffInformationVerificationsPage() {
  const { accessToken } = useAuth();
  const [status, setStatus] = useState("PENDING");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [downloadingId, setDownloadingId] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadRows() {
      if (!accessToken) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getInformationVerificationsApi(accessToken, status);
        if (!active) {
          return;
        }
        setRows(Array.isArray(response) ? response : []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được danh sách xác minh thông tin");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadRows();
    return () => {
      active = false;
    };
  }, [accessToken, status]);

  const handleDownloadPayslip = async (row) => {
    if (!row?.payslipFileName) {
      return;
    }
    setDownloadingId(row.customerId);
    setError("");
    try {
      await downloadInformationVerificationPayslipApi(accessToken, row.customerId, row.payslipFileName);
    } catch (err) {
      setError(err.message || "Không tải được phiếu lương");
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Xác minh thông tin</Typography>
      <Typography color="text.secondary">
        Nhân viên xem thông tin khai báo của khách hàng và quyết định chấp thuận hoặc từ chối cho các lần tạo hồ sơ vay sau.
      </Typography>

      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="info-verification-filter-label">Lọc trạng thái</InputLabel>
          <Select
            labelId="info-verification-filter-label"
            value={status}
            label="Lọc trạng thái"
            onChange={(event) => setStatus(event.target.value)}
          >
            <MenuItem value="PENDING">Chờ xác minh</MenuItem>
            <MenuItem value="PASSED">Đã chấp thuận</MenuItem>
            <MenuItem value="FAILED">Đã từ chối</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải danh sách xác minh...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có khách hàng nào ở trạng thái {labelVerificationStatus(status)}.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã khách hàng</TableCell>
              <TableCell>Khách hàng</TableCell>
              <TableCell>Phiếu lương</TableCell>
              <TableCell>Tải lên lúc</TableCell>
              <TableCell>Trạng thái</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.customerId} hover>
                <TableCell>#{row.customerId}</TableCell>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography variant="body2">{row.fullName || row.email}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {row.email}
                    </Typography>
                    {!row.hasProfile && (
                      <Typography variant="caption" color="warning.main">
                        Chưa có hồ sơ để đối chiếu.
                      </Typography>
                    )}
                  </Stack>
                </TableCell>
                <TableCell>
                  {row.payslipFileName ? (
                    <Stack spacing={0.5} alignItems="flex-start">
                      <Typography variant="body2">{row.payslipFileName}</Typography>
                      <Button
                        size="small"
                        variant="text"
                        onClick={() => handleDownloadPayslip(row)}
                        disabled={downloadingId === row.customerId}
                      >
                        {downloadingId === row.customerId ? "Đang tải..." : "Tải xuống"}
                      </Button>
                    </Stack>
                  ) : (
                    "-"
                  )}
                </TableCell>
                <TableCell>
                  {row.payslipUploadedAt ? new Date(row.payslipUploadedAt).toLocaleString() : "-"}
                </TableCell>
                <TableCell>
                  <StatusChip status={row.status} />
                </TableCell>
                <TableCell align="right">
                  <Button
                    component={RouterLink}
                    to={`/staff/information-verifications/${row.customerId}`}
                    variant="outlined"
                    size="small"
                  >
                    Xem và xử lý
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
