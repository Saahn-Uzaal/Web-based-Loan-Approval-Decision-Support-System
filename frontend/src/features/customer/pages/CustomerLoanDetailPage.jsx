import { Alert, Chip, CircularProgress, Divider, Grid, Paper, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getLoanDetailApi } from "@/features/customer/api/loanApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { labelLoanPurpose, labelLoanStatus } from "@/shared/utils/labels";

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
  const { accessToken } = useAuth();
  const [loan, setLoan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadLoanDetail() {
      if (!accessToken || !id) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const detail = await getLoanDetailApi(accessToken, id);
        if (!active) {
          return;
        }
        setLoan(detail);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được chi tiết hồ sơ vay");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadLoanDetail();
    return () => {
      active = false;
    };
  }, [accessToken, id]);

  const statusColorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hồ sơ vay #{id}</Typography>
      <Typography color="text.secondary">
        Quyết định cuối cùng và lý do sẽ xuất hiện tại đây.
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải chi tiết hồ sơ...</Typography>
          </Stack>
        </Paper>
      )}
      {!loading && !loan && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không tìm thấy hồ sơ vay.
          </Typography>
        </Paper>
      )}
      {loan && (
        <Paper sx={{ p: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Trạng thái"
                value={(
                  <Chip
                    label={labelLoanStatus(loan.status)}
                    color={statusColorMap[loan.status] || "default"}
                    size="small"
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Số tiền"
                value={formatVnd(loan.amount)}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Kỳ hạn" value={`${loan.termMonths} tháng`} />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Mục đích" value={labelLoanPurpose(loan.purpose)} />
            </Grid>
          </Grid>
          <Divider sx={{ my: 2 }} />
          <Stack spacing={1}>
            <Typography variant="subtitle2">Lý do quyết định cuối cùng</Typography>
            <Typography color="text.secondary">
              {loan.finalReason || "Đang chờ nhân viên thẩm định. Lý do sẽ hiển thị sau khi hoàn tất."}
            </Typography>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
