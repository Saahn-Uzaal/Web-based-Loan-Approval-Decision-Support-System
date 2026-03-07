import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getStaffRequestDetailApi, submitStaffDecisionApi } from "@/features/staff/api/staffApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import {
  labelContractStatus,
  labelCustomerSegment,
  labelDssRecommendation,
  labelLoanPurpose,
  labelLoanStatus,
  labelRiskLevel,
  labelRiskRank,
  labelStaffAction,
  labelVerificationStatus
} from "@/shared/utils/labels";

function InfoCard({ title, children }) {
  return (
    <Paper sx={{ p: 2, height: "100%" }}>
      <Stack spacing={1}>
        <Typography variant="subtitle1">{title}</Typography>
        {children}
      </Stack>
    </Paper>
  );
}

function mapRecommendationToAction(recommendation) {
  if (recommendation === "APPROVE_RECOMMENDED") {
    return "APPROVE";
  }
  if (recommendation === "REJECT_RECOMMENDED") {
    return "REJECT";
  }
  return "ESCALATE";
}

export default function StaffRequestDetailPage() {
  const { id } = useParams();
  const { accessToken } = useAuth();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [submitSuccess, setSubmitSuccess] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [decision, setDecision] = useState({
    action: "ESCALATE",
    reason: ""
  });

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      if (!accessToken || !id) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await getStaffRequestDetailApi(accessToken, id);
        if (!active) {
          return;
        }
        setDetail(response);
        setDecision((prev) => ({
          action: prev.reason ? prev.action : mapRecommendationToAction(response?.dss?.recommendation),
          reason: prev.reason
        }));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(err.message || "Không tải được chi tiết hồ sơ");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadDetail();
    return () => {
      active = false;
    };
  }, [accessToken, id]);

  const finalized = detail?.status === "APPROVED" || detail?.status === "REJECTED";

  const handleDecisionChange = (field) => (event) => {
    setDecision((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  const handleSubmitDecision = async (event) => {
    event.preventDefault();
    if (!detail) {
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setSubmitSuccess("");
    try {
      await submitStaffDecisionApi(accessToken, detail.id, {
        action: decision.action,
        reason: decision.reason.trim()
      });
      const refreshed = await getStaffRequestDetailApi(accessToken, detail.id);
      setDetail(refreshed);
      setSubmitSuccess(`Gửi quyết định thành công. Trạng thái hiện tại: ${labelLoanStatus(refreshed.status)}.`);
      setDecision((prev) => ({
        ...prev,
        reason: ""
      }));
    } catch (err) {
      setSubmitError(err.message || "Không gửi được quyết định");
    } finally {
      setSubmitting(false);
    }
  };

  const statusColorMap = {
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    WAITING_SUPERVISOR: "info"
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Thẩm định hồ sơ #{id}</Typography>
      <Typography color="text.secondary">
        Màn hình nhân viên: hồ sơ khách hàng, thông tin khoản vay, kết quả DSS và quyết định cuối cùng.
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
      {!loading && !detail && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không tìm thấy hồ sơ vay.
          </Typography>
        </Paper>
      )}
      {detail && (
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <InfoCard title="Tóm tắt hồ sơ khách hàng">
            <Typography variant="body2">Mã khách hàng: #{detail.customer?.id}</Typography>
            <Typography variant="body2">Email: {detail.customer?.email || "-"}</Typography>
            <Typography variant="body2">Họ tên: {detail.customerProfile?.fullName || "-"}</Typography>
            <Typography variant="body2">
              Thu nhập: {detail.customerProfile?.monthlyIncome != null ? `${formatVnd(detail.customerProfile.monthlyIncome)} / tháng` : "-"}
            </Typography>
            <Typography variant="body2">
              DTI: {detail.customerProfile?.debtToIncomeRatio != null ? `${detail.customerProfile.debtToIncomeRatio}%` : "-"}
            </Typography>
            <Typography variant="body2">
              Việc làm: {detail.customerProfile?.employmentStatus || "-"}
            </Typography>
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Thông tin khoản vay">
            <Typography variant="body2">
              Trạng thái: <Chip size="small" color={statusColorMap[detail.status] || "default"} label={labelLoanStatus(detail.status)} />
            </Typography>
            <Typography variant="body2">Số tiền: {formatVnd(detail.amount)}</Typography>
            <Typography variant="body2">Kỳ hạn: {detail.termMonths} tháng</Typography>
            <Typography variant="body2">Mục đích: {labelLoanPurpose(detail.purpose)}</Typography>
            <Typography variant="body2">Ngày nộp: {new Date(detail.createdAt).toLocaleString()}</Typography>
            <Typography variant="body2">Lý do cuối cùng: {detail.finalReason || "-"}</Typography>
            {detail.contract && (
              <>
                <Divider />
                <Typography variant="body2">Trạng thái hợp đồng: {labelContractStatus(detail.contract.status)}</Typography>
                <Typography variant="body2">
                  Thanh toán hàng tháng: {formatVnd(detail.contract.monthlyPayment)}
                </Typography>
                <Typography variant="body2">
                  Lãi suất: {(Number(detail.contract.annualInterestRate || 0) * 100).toFixed(2)}%
                </Typography>
              </>
            )}
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Kết quả DSS">
            {!detail.dss && (
              <Alert severity="warning">
                Không tìm thấy bản ghi DSS cho hồ sơ này.
              </Alert>
            )}
            {detail.dss && (
              <>
                <Typography variant="body2">Điểm tín dụng: {detail.dss.creditScore}</Typography>
                <Typography variant="body2">Hạng rủi ro: {labelRiskRank(detail.dss.riskRank)}</Typography>
                <Typography variant="body2">Phân khúc: {labelCustomerSegment(detail.dss.customerSegment)}</Typography>
                <Typography variant="body2">Khuyến nghị: {labelDssRecommendation(detail.dss.recommendation)}</Typography>
                <Alert severity="info" sx={{ mt: 1 }}>
                  {detail.dss.explanation}
                </Alert>
              </>
            )}
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Xác minh và rủi ro">
            {!detail.verification && (
              <Typography variant="body2" color="text.secondary">
                Chưa có dữ liệu xác minh.
              </Typography>
            )}
            {detail.verification && (
              <>
                <Typography variant="body2">Giấy tờ: {labelVerificationStatus(detail.verification.documentStatus)}</Typography>
                <Typography variant="body2">Định danh: {labelVerificationStatus(detail.verification.identityStatus)}</Typography>
                <Typography variant="body2">Thu nhập: {labelVerificationStatus(detail.verification.incomeStatus)}</Typography>
                <Typography variant="body2">KYC: {labelVerificationStatus(detail.verification.kycStatus)}</Typography>
                <Typography variant="body2">AML: {labelVerificationStatus(detail.verification.amlStatus)}</Typography>
                <Typography variant="body2">
                  Cờ gian lận: {detail.verification.fraudFlag ? "Có" : "Không"}
                </Typography>
                {detail.verification.note && (
                  <Alert severity="info">{detail.verification.note}</Alert>
                )}
              </>
            )}
            <Divider />
            {!detail.risk && (
              <Typography variant="body2" color="text.secondary">
                Không tìm thấy bản ghi đánh giá rủi ro.
              </Typography>
            )}
            {detail.risk && (
              <>
                <Typography variant="body2">Mức rủi ro tổng: {labelRiskLevel(detail.risk.overallRiskLevel)}</Typography>
                <Typography variant="body2">Rủi ro tín dụng: {detail.risk.creditRiskScore}</Typography>
                <Typography variant="body2">Rủi ro gian lận: {detail.risk.fraudRiskScore}</Typography>
                <Typography variant="body2">Rủi ro vận hành: {detail.risk.operationalRiskScore}</Typography>
                <Alert severity={detail.risk.overallRiskLevel === "HIGH" ? "warning" : "info"}>
                  {detail.risk.riskReasons}
                </Alert>
              </>
            )}
          </InfoCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <InfoCard title="Quyết định cuối cùng">
            <Stack spacing={2} component="form" onSubmit={handleSubmitDecision}>
              {submitError && <Alert severity="error">{submitError}</Alert>}
              {submitSuccess && <Alert severity="success">{submitSuccess}</Alert>}
              {finalized && (
                <Alert severity="info">
                  Hồ sơ này đã chốt kết quả. Không thể gửi thêm quyết định.
                </Alert>
              )}
              <TextField
                select
                label="Hành động"
                value={decision.action}
                onChange={handleDecisionChange("action")}
                disabled={submitting || finalized}
              >
                <MenuItem value="APPROVE">Duyệt</MenuItem>
                <MenuItem value="REJECT">Từ chối</MenuItem>
                <MenuItem value="ESCALATE">Chuyển cấp cao hơn</MenuItem>
              </TextField>
              <TextField
                label="Lý do"
                multiline
                rows={4}
                required
                value={decision.reason}
                onChange={handleDecisionChange("reason")}
                disabled={submitting || finalized}
                placeholder="Lý do là bắt buộc khi gửi quyết định."
              />
              <Button type="submit" variant="contained" disabled={submitting || finalized}>
                {submitting ? "Đang gửi..." : "Gửi quyết định"}
              </Button>
            </Stack>
          </InfoCard>
        </Grid>
        <Grid item xs={12}>
          <InfoCard title="Lịch sử quyết định">
            {!detail.decisionAudits?.length && (
              <Typography variant="body2" color="text.secondary">
                Chưa có bản ghi quyết định.
              </Typography>
            )}
            {detail.decisionAudits?.length > 0 && (
              <Paper variant="outlined" sx={{ overflowX: "auto" }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Thời gian</TableCell>
                      <TableCell>Nhân viên</TableCell>
                      <TableCell>Hành động</TableCell>
                      <TableCell>Lý do</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.decisionAudits.map((audit) => (
                      <TableRow key={audit.id}>
                        <TableCell>{new Date(audit.createdAt).toLocaleString()}</TableCell>
                        <TableCell>{audit.staffEmail}</TableCell>
                        <TableCell>{labelStaffAction(audit.action)}</TableCell>
                        <TableCell>{audit.reason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            )}
          </InfoCard>
        </Grid>
      </Grid>
      )}
      <Divider />
    </Stack>
  );
}
