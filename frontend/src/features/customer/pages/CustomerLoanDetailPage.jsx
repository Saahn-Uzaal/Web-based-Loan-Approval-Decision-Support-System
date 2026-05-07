import {
  Alert,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { getMyLoanContractApi } from "@/features/customer/api/contractApi";
import { useParams } from "react-router-dom";
import {
  acceptLoanApi,
  downloadLoanDocumentApi,
  getLoanDetailApi,
  resubmitLoanApi,
  withdrawLoanApi
} from "@/features/customer/api/loanApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { formatVnd } from "@/shared/utils/currency";
import {
  formatFileSize,
  isAcceptedSupplementalDocumentFile,
  SUPPLEMENTAL_DOCUMENT_ACCEPT
} from "@/shared/utils/files";
import {
  labelAppointmentStatus,
  labelCollateralType,
  labelContractStatus,
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

function formatFinalReason(reason) {
  if (!reason) {
    return reason;
  }
  const prefix = "Lịch hẹn gặp mặt:";
  if (!reason.includes(prefix)) {
    return reason;
  }
  const [firstPart, ...restParts] = reason.split(";");
  const rawDate = firstPart.replace(prefix, "").trim();
  const parsedDate = new Date(rawDate);
  if (Number.isNaN(parsedDate.getTime())) {
    return reason;
  }
  const formattedFirstPart = `${prefix} ${parsedDate.toLocaleString("vi-VN")}`;
  const tail = restParts
    .map((part) => part.trim())
    .filter(Boolean);
  return tail.length > 0 ? [formattedFirstPart, ...tail].join("; ") : formattedFirstPart;
}

export default function CustomerLoanDetailPage() {
  const { id } = useParams();
  const { accessToken } = useAuth();
  const [loan, setLoan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState("");
  const [downloadingDocument, setDownloadingDocument] = useState("");
  const [error, setError] = useState("");
  const [supplementalDocuments, setSupplementalDocuments] = useState([]);
  const [supplementalError, setSupplementalError] = useState("");
  const [contract, setContract] = useState(null);
  const [contractLoading, setContractLoading] = useState(false);
  const [contractError, setContractError] = useState("");
  const [reviewConfirmed, setReviewConfirmed] = useState(false);

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

  const shouldLoadContract = useMemo(
    () =>
      ["APPROVED", "CONTRACTED", "ACTIVE", "OVERDUE", "CLOSED"].includes(loan?.status),
    [loan?.status]
  );

  useEffect(() => {
    let active = true;

    async function loadContract() {
      if (!accessToken || !loan?.id || !shouldLoadContract) {
        setContractLoading(false);
        setContract(null);
        setContractError("");
        setReviewConfirmed(false);
        return;
      }
      setContractLoading(true);
      setContractError("");
      try {
        const response = await getMyLoanContractApi(accessToken, loan.id);
        if (!active) {
          return;
        }
        setContract(response);
      } catch (err) {
        if (!active) {
          return;
        }
        setContract(null);
        setContractError(err.message || "Không tải được hợp đồng vay");
      } finally {
        if (active) {
          setContractLoading(false);
        }
      }
    }

    loadContract();
    return () => {
      active = false;
    };
  }, [accessToken, loan?.id, shouldLoadContract]);

  useEffect(() => {
    setReviewConfirmed(false);
  }, [loan?.id, loan?.status, contract?.id]);

  const statusColorMap = {
    DRAFT: "default",
    NEEDS_MORE_INFO: "warning",
    APPOINTMENT_SCHEDULED: "info",
    APPROVED: "success",
    CONTRACTED: "info",
    ACTIVE: "primary",
    OVERDUE: "error",
    CLOSED: "default",
    REJECTED: "error",
    PENDING: "warning",
    WITHDRAWN: "default"
  };

  const handleDownloadDocument = async (document) => {
    if (!document?.id || !loan?.id) {
      return;
    }
    setDownloadingDocument(String(document.id));
    setError("");
    try {
      await downloadLoanDocumentApi(accessToken, loan.id, document.id, document.fileName);
    } catch (err) {
      setError(err.message || "Không tải được chứng từ hồ sơ vay");
    } finally {
      setDownloadingDocument("");
    }
  };

  const handleLoanAction = async (action, apiCall) => {
    if (!loan?.id) {
      return;
    }
    setActionLoading(action);
    setError("");
    try {
      const updated = await apiCall(accessToken, loan.id);
      setLoan(updated);
    } catch (err) {
      setError(err.message || "Không thực hiện được thao tác hồ sơ vay");
    } finally {
      setActionLoading("");
    }
  };

  const handleAcceptLoan = async () => {
    if (!loan?.id || !contract) {
      return;
    }
    setActionLoading("accept");
    setError("");
    try {
      const updated = await acceptLoanApi(accessToken, loan.id);
      setLoan(updated);
      const refreshedContract = await getMyLoanContractApi(accessToken, loan.id);
      setContract(refreshedContract);
      setReviewConfirmed(false);
    } catch (err) {
      setError(err.message || "Không thực hiện được thao tác hồ sơ vay");
    } finally {
      setActionLoading("");
    }
  };

  const handleSupplementalFileChange = (event) => {
    const nextFiles = Array.from(event.target.files || []);
    if (nextFiles.some((file) => !isAcceptedSupplementalDocumentFile(file))) {
      setSupplementalError("Giấy tờ bổ sung chỉ hỗ trợ JPG, JPEG, PNG, WEBP, PDF, DOC, DOCX, XLS và XLSX.");
      event.target.value = "";
      return;
    }
    setSupplementalDocuments(nextFiles);
    setSupplementalError("");
  };

  const handleResubmitLoan = async () => {
    if (!loan?.id) {
      return;
    }
    if (supplementalDocuments.length === 0) {
      setSupplementalError("Vui lòng đính kèm ít nhất một giấy tờ bổ sung trước khi gửi lại hồ sơ.");
      return;
    }
    setActionLoading("resubmit");
    setError("");
    setSupplementalError("");
    try {
      const updated = await resubmitLoanApi(accessToken, loan.id, { supplementalDocuments });
      setLoan(updated);
      setSupplementalDocuments([]);
    } catch (err) {
      setError(err.message || "Không thực hiện được thao tác hồ sơ vay");
    } finally {
      setActionLoading("");
    }
  };

  const canWithdraw = ["DRAFT", "PENDING", "NEEDS_MORE_INFO", "APPOINTMENT_SCHEDULED", "APPROVED"].includes(loan?.status);

  const finalReasonText = formatFinalReason(loan?.finalReason);

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

          {loan.appointment && (
            <Alert severity={loan.appointment.status === "SCHEDULED" ? "info" : loan.appointment.status === "COMPLETED" ? "success" : "warning"} sx={{ mt: 2 }}>
              Lịch hẹn gặp mặt hiện tại: {loan.appointment.scheduledAt ? new Date(loan.appointment.scheduledAt).toLocaleString("vi-VN") : "-"}.
              {" "}Trạng thái: {labelAppointmentStatus(loan.appointment.status)}.
              {loan.appointment.location ? ` Địa điểm: ${loan.appointment.location}.` : ""}
              {loan.appointment.note ? ` Ghi chú: ${loan.appointment.note}.` : ""}
            </Alert>
          )}

          {shouldLoadContract && (
            <>
              <Divider sx={{ my: 2 }} />
              <Stack spacing={1.25}>
                <Typography variant="subtitle2">Hợp đồng vay</Typography>
                {contractLoading && (
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CircularProgress size={18} />
                    <Typography variant="body2" color="text.secondary">
                      Đang tải hợp đồng vay...
                    </Typography>
                  </Stack>
                )}
                {!contractLoading && contractError && (
                  <Alert severity={loan.status === "APPROVED" ? "warning" : "error"}>{contractError}</Alert>
                )}
                {contract && (
                  <Stack spacing={2}>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Trạng thái hợp đồng" value={labelContractStatus(contract.status)} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Số tiền gốc" value={formatVnd(contract.principalAmount)} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue
                          label="Lãi suất"
                          value={`${(Number(contract.annualInterestRate || 0) * 100).toFixed(2)}%/năm`}
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Kỳ hạn hợp đồng" value={`${contract.termMonths} tháng`} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Thanh toán hàng tháng" value={formatVnd(contract.monthlyPayment)} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Tổng lãi dự kiến" value={formatVnd(contract.totalInterest)} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Ngày bắt đầu" value={contract.startDate || "-"} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Ngày thanh toán đầu tiên" value={contract.firstPaymentDate || "-"} />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <KeyValue label="Ngày thanh toán cuối cùng" value={contract.finalPaymentDate || "-"} />
                      </Grid>
                    </Grid>
                    {Array.isArray(contract.installments) && contract.installments.length > 0 && (
                      <Paper variant="outlined" sx={{ overflowX: "auto" }}>
                        <Stack sx={{ p: 2, pb: 0 }}>
                          <Typography variant="subtitle2">Lịch trả nợ theo từng kỳ</Typography>
                          <Typography variant="body2" color="text.secondary">
                            Lịch này được materialize từ backend và sẽ cập nhật khi có thanh toán một phần hoặc tất toán.
                          </Typography>
                        </Stack>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Kỳ</TableCell>
                              <TableCell>Đến hạn</TableCell>
                              <TableCell>Gốc đầu kỳ</TableCell>
                              <TableCell>Gốc kỳ này</TableCell>
                              <TableCell>Lãi kỳ này</TableCell>
                              <TableCell>Tổng kỳ này</TableCell>
                              <TableCell>Đã trả</TableCell>
                              <TableCell>Còn lại</TableCell>
                              <TableCell>Trạng thái</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {contract.installments.map((installment) => (
                              <TableRow key={installment.installmentNumber}>
                                <TableCell>#{installment.installmentNumber}</TableCell>
                                <TableCell>{installment.dueDate || "-"}</TableCell>
                                <TableCell>{formatVnd(installment.openingPrincipal)}</TableCell>
                                <TableCell>{formatVnd(installment.scheduledPrincipal)}</TableCell>
                                <TableCell>{formatVnd(installment.scheduledInterest)}</TableCell>
                                <TableCell>{formatVnd(installment.scheduledAmount)}</TableCell>
                                <TableCell>{formatVnd(installment.paidAmount)}</TableCell>
                                <TableCell>{formatVnd(installment.remainingAmount)}</TableCell>
                                <TableCell>{labelInstallmentStatus(installment.status)}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </Paper>
                    )}
                  </Stack>
                )}
                {loan.status === "APPROVED" && contract && (
                  <>
                    <Alert severity="info">
                      Hồ sơ đã được duyệt nhưng hợp đồng chỉ có hiệu lực sau khi bạn xem kỹ các điều khoản và xác nhận chấp nhận.
                    </Alert>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={reviewConfirmed}
                          onChange={(event) => setReviewConfirmed(event.target.checked)}
                        />
                      }
                      label="Tôi đã xem các điều khoản hợp đồng và đồng ý tiếp tục ký/chấp nhận."
                    />
                  </>
                )}
              </Stack>
            </>
          )}

          {(loan.status === "APPROVED" || loan.status === "NEEDS_MORE_INFO" || canWithdraw) && (
            <Stack spacing={1} sx={{ mt: 2 }}>
              {loan.status === "NEEDS_MORE_INFO" && (
                <Stack spacing={1}>
                  <Typography variant="subtitle2">Giấy tờ bổ sung</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Đính kèm các tài liệu bổ sung mà nhân viên đã yêu cầu. Bạn có thể gửi nhiều file trong một lần bổ sung.
                  </Typography>
                  {supplementalError && <Alert severity="error">{supplementalError}</Alert>}
                  <Button variant="outlined" component="label" sx={{ alignSelf: "flex-start" }}>
                    {supplementalDocuments.length > 0 ? "Đổi bộ giấy tờ bổ sung" : "Chọn giấy tờ bổ sung"}
                    <input
                      hidden
                      multiple
                      type="file"
                      accept={SUPPLEMENTAL_DOCUMENT_ACCEPT}
                      onChange={handleSupplementalFileChange}
                    />
                  </Button>
                  {supplementalDocuments.length > 0 && (
                    <Paper variant="outlined" sx={{ p: 1.5 }}>
                      <Stack spacing={0.75}>
                        {supplementalDocuments.map((file) => (
                          <Typography key={`${file.name}-${file.size}-${file.lastModified}`} variant="body2" color="text.secondary">
                            {file.name} ({formatFileSize(file.size)})
                          </Typography>
                        ))}
                      </Stack>
                    </Paper>
                  )}
                </Stack>
              )}
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                {loan.status === "APPROVED" && (
                  <Button
                    variant="contained"
                    onClick={handleAcceptLoan}
                    disabled={Boolean(actionLoading) || !contract || !reviewConfirmed}
                  >
                    {actionLoading === "accept" ? "Đang xử lý..." : "Chấp nhận hợp đồng"}
                  </Button>
                )}
                {loan.status === "NEEDS_MORE_INFO" && (
                  <Button
                    variant="outlined"
                    onClick={handleResubmitLoan}
                    disabled={Boolean(actionLoading)}
                  >
                    {actionLoading === "resubmit" ? "Đang gửi..." : "Gửi lại sau khi bổ sung"}
                  </Button>
                )}
                {canWithdraw && (
                  <Button
                    variant="text"
                    color="error"
                    onClick={() => handleLoanAction("withdraw", withdrawLoanApi)}
                    disabled={Boolean(actionLoading)}
                  >
                    {actionLoading === "withdraw" ? "Đang rút..." : "Rút hồ sơ"}
                  </Button>
                )}
              </Stack>
            </Stack>
          )}

          <Divider sx={{ my: 2 }} />
          <Stack spacing={1}>
            <Typography variant="subtitle2">Lý do quyết định cuối cùng</Typography>
            <Typography color="text.secondary">
              {finalReasonText || "Đang chờ nhân viên thẩm định. Lý do sẽ hiển thị sau khi hoàn tất."}
            </Typography>
          </Stack>

          {loan.documents?.length > 0 && (
            <>
              <Divider sx={{ my: 2 }} />
              <Stack spacing={1.25}>
                <Typography variant="subtitle2">Chứng từ đã nộp</Typography>
                {loan.documents.map((document) => (
                  <Stack
                    key={document.id}
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
                      disabled={downloadingDocument === String(document.id)}
                    >
                      {downloadingDocument === String(document.id) ? "Đang tải..." : "Tải xuống"}
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

function labelInstallmentStatus(status) {
  if (status === "PAID") {
    return "Đã trả";
  }
  if (status === "PARTIALLY_PAID") {
    return "Trả một phần";
  }
  return "Chờ thanh toán";
}
