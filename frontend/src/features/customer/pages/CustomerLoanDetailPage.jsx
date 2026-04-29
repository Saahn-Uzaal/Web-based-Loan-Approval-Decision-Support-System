import { Alert, Button, Chip, CircularProgress, Divider, Grid, Paper, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { downloadLoanDocumentApi, getLoanDetailApi } from "@/features/customer/api/loanApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import { formatFileSize } from "@/shared/utils/files";
import {
  labelCollateralType,
  labelLoanDocumentType,
  labelLoanPurpose,
  labelLoanStatus,
  labelLoanType
} from "@/shared/utils/labels";

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
  const [downloadingDocument, setDownloadingDocument] = useState("");
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
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    DISBURSED: "primary",
    ACTIVE: "primary",
    CLOSED: "default",
    REJECTED: "error",
    PENDING: "warning"
  };

  const handleDownloadDocument = async (document) => {
    if (!document?.documentType || !loan?.id) {
      return;
    }
    setDownloadingDocument(document.documentType);
    setError("");
    try {
      await downloadLoanDocumentApi(accessToken, loan.id, document.documentType, document.fileName);
    } catch (err) {
      setError(err.message || "Không tải được chứng từ hồ sơ vay");
    } finally {
      setDownloadingDocument("");
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Hồ sơ vay #{id}</Typography>
      <Typography color="text.secondary">
        Theo dõi trạng thái tiếp nhận, chứng từ đã nộp và quyết định cuối cùng của hồ sơ.
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
              <KeyValue label="Loại vay" value={labelLoanType(loan.loanType)} />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Số tiền" value={formatVnd(loan.amount)} />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Kỳ hạn" value={`${loan.termMonths} tháng`} />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue label="Mục đích" value={labelLoanPurpose(loan.purpose)} />
            </Grid>
            {loan.collateralType && (
              <Grid item xs={12} md={4}>
                <KeyValue label="Tài sản bảo đảm" value={labelCollateralType(loan.collateralType)} />
              </Grid>
            )}
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Hạn mức tạm tính"
                value={loan.eligibleLimit != null ? formatVnd(loan.eligibleLimit) : "-"}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Số tiền phê duyệt"
                value={loan.approvedAmount != null ? formatVnd(loan.approvedAmount) : "-"}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Kỳ hạn phê duyệt"
                value={loan.approvedTermMonths != null ? `${loan.approvedTermMonths} tháng` : "-"}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Góp hằng tháng"
                value={loan.approvedMonthlyPayment != null ? formatVnd(loan.approvedMonthlyPayment) : "-"}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Lãi suất phê duyệt"
                value={loan.approvedAnnualRate != null ? `${(Number(loan.approvedAnnualRate) * 100).toFixed(2)}%/năm` : "-"}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <KeyValue
                label="Phiên bản chính sách"
                value={loan.decisionPolicyVersion || "-"}
              />
            </Grid>
          </Grid>

          {loan.intakeNote && (
            <Alert severity="info" sx={{ mt: 2 }}>
              {loan.intakeNote}
            </Alert>
          )}

          <Divider sx={{ my: 2 }} />
          <Stack spacing={1}>
            <Typography variant="subtitle2">Lý do quyết định cuối cùng</Typography>
            <Typography color="text.secondary">
              {loan.finalReason || "Đang chờ nhân viên thẩm định. Lý do sẽ hiển thị sau khi hoàn tất."}
            </Typography>
          </Stack>

          {loan.documents?.length > 0 && (
            <>
              <Divider sx={{ my: 2 }} />
              <Stack spacing={1.25}>
                <Typography variant="subtitle2">Chứng từ đã nộp</Typography>
                {loan.documents.map((document) => (
                  <Stack
                    key={document.documentType}
                    direction={{ xs: "column", sm: "row" }}
                    spacing={1}
                    alignItems={{ sm: "center" }}
                  >
                    <Typography variant="body2" sx={{ flex: 1 }}>
                      {labelLoanDocumentType(document.documentType)}: {document.fileName}
                      {document.fileSize != null ? ` (${formatFileSize(document.fileSize)})` : ""}
                    </Typography>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleDownloadDocument(document)}
                      disabled={downloadingDocument === document.documentType}
                    >
                      {downloadingDocument === document.documentType ? "Đang tải..." : "Tải xuống"}
                    </Button>
                  </Stack>
                ))}
              </Stack>
            </>
          )}
        </Paper>
      )}
    </Stack>
  );
}
