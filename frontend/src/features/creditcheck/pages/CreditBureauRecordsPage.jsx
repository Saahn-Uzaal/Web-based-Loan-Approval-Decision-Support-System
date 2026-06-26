import {
  Alert,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createCreditBureauRecordApi,
  deleteCreditBureauRecordApi,
  getCreditBureauRecordsPagedApi,
  getCreditBureauSummaryApi,
  syncCreditBureauInternalLoansApi,
  updateCreditBureauRecordApi
} from "@/features/creditcheck/api/creditBureauApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import ConfirmDialog from "@/shared/components/ConfirmDialog";
import { formatVnd, formatVndInput, parseVndInput } from "@/shared/utils/currency";
import {
  labelCreditBureauStatus,
  labelCreditLoanAccountStatus,
  labelCreditLoanSourceType
} from "@/shared/utils/labels";

const STATUS_OPTIONS = [
  { value: "NO_HIT", label: "Chưa có quan hệ tín dụng" },
  { value: "CLEAR", label: "Đang trả tốt" },
  { value: "WATCHLIST", label: "Cần rà soát" },
  { value: "BAD_DEBT", label: "Nợ xấu" },
  { value: "FRAUD_SUSPECT", label: "Nghi ngờ gian lận" }
];

const SOURCE_OPTIONS = [
  { value: "PARTNER_NETWORK", label: "Đối tác / tổ chức khác" },
  { value: "CUSTOMER_DECLARED", label: "Khách hàng tự khai" },
  { value: "INTERNAL_SYSTEM", label: "Nội bộ từ app" }
];

const LOAN_STATUS_OPTIONS = [
  { value: "CURRENT", label: "Đang trả bình thường" },
  { value: "OVERDUE", label: "Đang quá hạn" },
  { value: "BAD_DEBT", label: "Nợ xấu" },
  { value: "CLOSED", label: "Đã tất toán" }
];

const MONEY_LOAN_FIELDS = ["originalAmount", "outstandingBalance", "monthlyPayment"];

function emptyLoanAccount() {
  return {
    reportingInstitution: "",
    accountReference: "",
    sourceType: "PARTNER_NETWORK",
    loanCategory: "",
    accountStatus: "CURRENT",
    originalAmount: "0",
    outstandingBalance: "0",
    monthlyPayment: "0",
    daysPastDue: "0",
    note: ""
  };
}

function emptyForm() {
  return {
    identityNumber: "",
    borrowerName: "",
    consentGranted: true,
    fraudSuspect: false,
    riskNote: "",
    loanAccounts: [emptyLoanAccount()]
  };
}

function statusChipColor(status) {
  const colorMap = {
    NO_HIT: "default",
    CLEAR: "success",
    WATCHLIST: "warning",
    BAD_DEBT: "error",
    FRAUD_SUSPECT: "secondary"
  };
  return colorMap[status] || "default";
}

function loanStatusChipColor(status) {
  const colorMap = {
    CURRENT: "success",
    OVERDUE: "warning",
    BAD_DEBT: "error",
    CLOSED: "default"
  };
  return colorMap[status] || "default";
}

function summaryTone(status) {
  if (status === "FRAUD_SUSPECT" || status === "BAD_DEBT") {
    return "error";
  }
  if (status === "WATCHLIST") {
    return "warning";
  }
  if (status === "CLEAR") {
    return "success";
  }
  return "info";
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("vi-VN");
}

function parseMoneyValue(value) {
  const parsed = parseVndInput(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

function parseIntegerValue(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return Math.round(parsed);
}

function isActiveLoanStatus(status) {
  return status === "CURRENT" || status === "OVERDUE" || status === "BAD_DEBT";
}

function derivePreviewMetrics(form) {
  const institutions = new Set();
  let activeLoanCount = 0;
  let maxDaysPastDue = 0;
  let totalMonthlyObligation = 0;
  let totalOutstandingBalance = 0;
  let externalMonthlyObligation = 0;
  let externalOutstandingBalance = 0;
  let hasBadDebt = false;
  let hasOverdue = false;

  (form.loanAccounts || []).forEach((loan) => {
    const institution = String(loan.reportingInstitution || "").trim();
    if (institution) {
      institutions.add(institution);
    }
    if (!isActiveLoanStatus(loan.accountStatus)) {
      return;
    }

    const monthlyPayment = parseMoneyValue(loan.monthlyPayment);
    const outstandingBalance = parseMoneyValue(loan.outstandingBalance);
    const daysPastDue = parseIntegerValue(loan.daysPastDue);

    activeLoanCount += 1;
    maxDaysPastDue = Math.max(maxDaysPastDue, daysPastDue);
    totalMonthlyObligation += monthlyPayment;
    totalOutstandingBalance += outstandingBalance;

    if (loan.sourceType !== "INTERNAL_SYSTEM") {
      externalMonthlyObligation += monthlyPayment;
      externalOutstandingBalance += outstandingBalance;
    }

    if (loan.accountStatus === "BAD_DEBT" || daysPastDue >= 90) {
      hasBadDebt = true;
    } else if (loan.accountStatus === "OVERDUE" || daysPastDue > 0) {
      hasOverdue = true;
    }
  });

  let bureauStatus = "CLEAR";
  if (form.fraudSuspect) {
    bureauStatus = "FRAUD_SUSPECT";
  } else if (hasBadDebt) {
    bureauStatus = "BAD_DEBT";
  } else if (hasOverdue) {
    bureauStatus = "WATCHLIST";
  } else if (activeLoanCount === 0) {
    bureauStatus = "NO_HIT";
  }

  let creditScore = 72;
  if (bureauStatus === "FRAUD_SUSPECT") {
    creditScore = 15;
  } else if (bureauStatus === "BAD_DEBT") {
    creditScore = Math.max(20, 35 - Math.min(maxDaysPastDue, 180) / 18);
  } else if (bureauStatus === "WATCHLIST") {
    creditScore = Math.max(40, 58 - Math.min(maxDaysPastDue, 60) / 5);
  } else if (bureauStatus === "CLEAR") {
    creditScore = Math.max(68, 84 - Math.max(activeLoanCount - 1, 0) * 3);
  }

  return {
    bureauStatus,
    creditScore: Math.round(creditScore),
    activeLoanCount,
    maxDaysPastDue,
    totalMonthlyObligation,
    totalOutstandingBalance,
    externalMonthlyObligation,
    externalOutstandingBalance,
    reportingInstitutionCount: institutions.size
  };
}

function SummaryCard({ label, value, helper }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, height: "100%" }}>
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h6">{value}</Typography>
        {helper && (
          <Typography variant="body2" color="text.secondary">
            {helper}
          </Typography>
        )}
      </Stack>
    </Paper>
  );
}

export default function CreditBureauRecordsPage() {
  const { accessToken, user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const [statusFilter, setStatusFilter] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalRows, setTotalRows] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingIdentityNumber, setEditingIdentityNumber] = useState(null);
  const [detailRecord, setDetailRecord] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [saving, setSaving] = useState(false);
  const [confirmDeleteIdentity, setConfirmDeleteIdentity] = useState(null);
  const [deletingIdentity, setDeletingIdentity] = useState("");

  const preview = useMemo(() => derivePreviewMetrics(form), [form]);

  const loadData = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [summaryResponse, pagedResponse] = await Promise.all([
        getCreditBureauSummaryApi(accessToken),
        getCreditBureauRecordsPagedApi(accessToken, {
          status: statusFilter || undefined,
          query: query || undefined,
          page,
          size: rowsPerPage
        })
      ]);
      setSummary(summaryResponse || null);
      setRows(Array.isArray(pagedResponse?.content) ? pagedResponse.content : []);
      setTotalRows(Number(pagedResponse?.totalElements || 0));
    } catch (err) {
      setError(err.message || "Không tải được sổ tra cứu tín dụng");
    } finally {
      setLoading(false);
    }
  }, [accessToken, page, query, rowsPerPage, statusFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openCreateDialog = () => {
    setEditingIdentityNumber(null);
    setForm(emptyForm());
    setDialogOpen(true);
    setError("");
    setSuccess("");
  };

  const openEditDialog = (row) => {
    setEditingIdentityNumber(row.identityNumber);
    setForm({
      identityNumber: row.identityNumber,
      borrowerName: row.borrowerName || "",
      consentGranted: Boolean(row.consentGranted),
      fraudSuspect: row.bureauStatus === "FRAUD_SUSPECT",
      riskNote: row.riskNote || "",
      loanAccounts: Array.isArray(row.loanAccounts) && row.loanAccounts.length > 0
        ? row.loanAccounts.map((loan) => ({
            reportingInstitution: loan.reportingInstitution || "",
            accountReference: loan.accountReference || "",
            sourceType: loan.sourceType || "PARTNER_NETWORK",
            loanCategory: loan.loanCategory || "",
            accountStatus: loan.accountStatus || "CURRENT",
            originalAmount: formatVndInput(loan.originalAmount ?? 0),
            outstandingBalance: formatVndInput(loan.outstandingBalance ?? 0),
            monthlyPayment: formatVndInput(loan.monthlyPayment ?? 0),
            daysPastDue: String(loan.daysPastDue ?? 0),
            note: loan.note || ""
          }))
        : [emptyLoanAccount()]
    });
    setDialogOpen(true);
    setError("");
    setSuccess("");
  };

  const closeDialog = () => {
    if (saving) {
      return;
    }
    setDialogOpen(false);
  };

  const handleFormChange = (field) => (event) => {
    const value = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    setForm((prev) => ({
      ...prev,
      [field]: value
    }));
  };

  const handleLoanAccountChange = (index, field) => (event) => {
    const value = MONEY_LOAN_FIELDS.includes(field)
      ? formatVndInput(event.target.value)
      : event.target.value;
    setForm((prev) => ({
      ...prev,
      loanAccounts: prev.loanAccounts.map((loan, loanIndex) => (
        loanIndex === index ? { ...loan, [field]: value } : loan
      ))
    }));
  };

  const handleAddLoanAccount = () => {
    setForm((prev) => ({
      ...prev,
      loanAccounts: [...prev.loanAccounts, emptyLoanAccount()]
    }));
  };

  const handleRemoveLoanAccount = (index) => {
    setForm((prev) => {
      const nextLoans = prev.loanAccounts.filter((_, loanIndex) => loanIndex !== index);
      return {
        ...prev,
        loanAccounts: nextLoans.length > 0 ? nextLoans : [emptyLoanAccount()]
      };
    });
  };

  const handleSearchSubmit = () => {
    setPage(0);
    setQuery(queryInput.trim());
  };

  const handleClearFilters = () => {
    setStatusFilter("");
    setQueryInput("");
    setQuery("");
    setPage(0);
  };

  const handleSave = async () => {
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const payload = {
        identityNumber: form.identityNumber.trim(),
        borrowerName: form.borrowerName.trim(),
        consentGranted: Boolean(form.consentGranted),
        fraudSuspect: Boolean(form.fraudSuspect),
        riskNote: form.riskNote.trim(),
        loanAccounts: (form.loanAccounts || []).map((loan) => ({
          reportingInstitution: loan.reportingInstitution.trim(),
          accountReference: loan.accountReference.trim(),
          sourceType: loan.sourceType,
          loanCategory: loan.loanCategory.trim(),
          accountStatus: loan.accountStatus,
          originalAmount: parseMoneyValue(loan.originalAmount),
          outstandingBalance: parseMoneyValue(loan.outstandingBalance),
          monthlyPayment: parseMoneyValue(loan.monthlyPayment),
          daysPastDue: parseIntegerValue(loan.daysPastDue),
          note: loan.note.trim()
        }))
      };
      if (editingIdentityNumber) {
        await updateCreditBureauRecordApi(accessToken, editingIdentityNumber, payload);
        setSuccess(`Đã cập nhật hồ sơ tra cứu tín dụng cho CCCD ${payload.identityNumber}.`);
      } else {
        await createCreditBureauRecordApi(accessToken, payload);
        setSuccess(`Đã tạo hồ sơ tra cứu tín dụng cho CCCD ${payload.identityNumber}.`);
      }
      setDialogOpen(false);
      await loadData();
    } catch (err) {
      setError(err.message || "Không lưu được hồ sơ tra cứu tín dụng");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    const identityNumber = confirmDeleteIdentity;
    setConfirmDeleteIdentity(null);
    if (!identityNumber) {
      return;
    }
    setDeletingIdentity(identityNumber);
    setError("");
    setSuccess("");
    try {
      await deleteCreditBureauRecordApi(accessToken, identityNumber);
      setSuccess(`Đã xóa hồ sơ tra cứu tín dụng của CCCD ${identityNumber}.`);
      if (rows.length === 1 && page > 0) {
        setPage((prev) => prev - 1);
      } else {
        await loadData();
      }
    } catch (err) {
      setError(err.message || "Không xóa được hồ sơ tra cứu tín dụng");
    } finally {
      setDeletingIdentity("");
    }
  };

  const handleSyncInternalLoans = async () => {
    setSyncing(true);
    setError("");
    setSuccess("");
    try {
      const response = await syncCreditBureauInternalLoansApi(accessToken);
      setSuccess(
        `Đã đồng bộ ${response?.syncedLoanCount || 0} khoản vay nội bộ cho ${response?.borrowerCount || 0} khách hàng.`
        + `${Number(response?.skippedBorrowerCount || 0) > 0 ? ` Bỏ qua ${response.skippedBorrowerCount} khách hàng chưa có CCCD hợp lệ.` : ""}`
      );
      await loadData();
    } catch (err) {
      setError(err.message || "Không đồng bộ được khoản vay nội bộ");
    } finally {
      setSyncing(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Sổ tra cứu tín dụng liên tổ chức</Typography>
      <Typography color="text.secondary">
        Màn hình này gom dữ liệu khoản vay theo CCCD để phục vụ đối chiếu trước khi cho vay. Hồ sơ có thể chứa
        khoản vay nội bộ từ app, khoản vay do đối tác cung cấp hoặc khoản vay khách hàng tự khai chờ xác minh.
      </Typography>

      {!isAdmin && (
        <Alert severity="info">
          Tài khoản nhân viên đang ở chế độ chỉ xem. Quản trị viên mới có thể tạo, sửa, xóa hoặc đồng bộ dữ liệu nội bộ.
        </Alert>
      )}

      {summary && !loading && (
        <Grid container spacing={2}>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Khách hàng đang có hồ sơ"
              value={summary.borrowerCount}
              helper="Tính theo CCCD có trong sổ tra cứu"
            />
          </Grid>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Khách hàng nợ xấu"
              value={summary.badDebtCount}
              helper="Có ít nhất một khoản ở mức nợ xấu"
            />
          </Grid>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Hồ sơ cần rà soát"
              value={summary.watchlistCount}
              helper="Có quá hạn hoặc cần xem lại thủ công"
            />
          </Grid>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Khoản vay hiệu lực"
              value={summary.totalActiveLoanCount}
              helper="Tổng số khoản vay đang còn tác động đến nghĩa vụ nợ"
            />
          </Grid>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Nghĩa vụ hàng tháng"
              value={formatVnd(summary.totalMonthlyObligation)}
              helper="Tổng số tiền phải trả theo tháng trong sổ tra cứu"
            />
          </Grid>
          <Grid item xs={12} md={4} lg={2}>
            <SummaryCard
              label="Dư nợ còn lại"
              value={formatVnd(summary.totalOutstandingBalance)}
              helper="Tổng dư nợ còn mở trên toàn bộ hồ sơ"
            />
          </Grid>
        </Grid>
      )}

      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems={{ xs: "stretch", md: "center" }}>
          <TextField
            label="Tìm theo CCCD, người vay, đơn vị báo cáo hoặc ghi chú"
            value={queryInput}
            onChange={(event) => setQueryInput(event.target.value)}
            fullWidth
          />
          <FormControl sx={{ minWidth: { xs: "100%", md: 220 } }}>
            <InputLabel id="credit-bureau-status-filter-label">Lọc phân loại</InputLabel>
            <Select
              labelId="credit-bureau-status-filter-label"
              value={statusFilter}
              label="Lọc phân loại"
              onChange={(event) => {
                setStatusFilter(event.target.value);
                setPage(0);
              }}
            >
              <MenuItem value="">Tất cả</MenuItem>
              {STATUS_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button variant="contained" onClick={handleSearchSubmit}>
            Tìm
          </Button>
          <Button variant="outlined" color="inherit" onClick={handleClearFilters}>
            Xóa lọc
          </Button>
          {isAdmin && (
            <Button variant="outlined" onClick={handleSyncInternalLoans} disabled={syncing}>
              {syncing ? "Đang đồng bộ..." : "Đồng bộ khoản vay nội bộ"}
            </Button>
          )}
          {isAdmin && (
            <Button variant="contained" color="secondary" onClick={openCreateDialog}>
              Tạo hồ sơ
            </Button>
          )}
        </Stack>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}
      {success && <Alert severity="success">{success}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải sổ tra cứu tín dụng...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && totalRows === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Chưa có hồ sơ tín dụng nào phù hợp với bộ lọc hiện tại.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>CCCD</TableCell>
              <TableCell>Người vay</TableCell>
              <TableCell>Phân loại</TableCell>
              <TableCell align="right">Khoản vay hiệu lực</TableCell>
              <TableCell align="right">Nghĩa vụ / tháng</TableCell>
              <TableCell align="right">Dư nợ còn lại</TableCell>
              <TableCell align="right">Nguồn báo cáo</TableCell>
              <TableCell align="right">Quá hạn tối đa</TableCell>
              <TableCell>Cập nhật</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.identityNumber} hover>
                <TableCell>{row.identityNumber}</TableCell>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography variant="body2">{row.borrowerName || "-"}</Typography>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                      <Chip size="small" label={row.consentGranted ? "Đã có đồng ý chia sẻ" : "Chưa có đồng ý chia sẻ"} />
                      {row.riskNote && (
                        <Typography variant="caption" color="text.secondary">
                          {row.riskNote}
                        </Typography>
                      )}
                    </Stack>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={labelCreditBureauStatus(row.bureauStatus)}
                    color={statusChipColor(row.bureauStatus)}
                  />
                </TableCell>
                <TableCell align="right">{row.activeLoanCount ?? 0}</TableCell>
                <TableCell align="right">{formatVnd(row.totalMonthlyObligation || 0)}</TableCell>
                <TableCell align="right">{formatVnd(row.totalOutstandingBalance || 0)}</TableCell>
                <TableCell align="right">{row.reportingInstitutionCount ?? 0}</TableCell>
                <TableCell align="right">
                  {row.daysPastDue ? `${row.daysPastDue} ngày` : "-"}
                </TableCell>
                <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button variant="outlined" size="small" onClick={() => setDetailRecord(row)}>
                      Xem
                    </Button>
                    {isAdmin && (
                      <Button variant="outlined" size="small" onClick={() => openEditDialog(row)}>
                        Sửa
                      </Button>
                    )}
                    {isAdmin && (
                      <Button
                        variant="outlined"
                        color="error"
                        size="small"
                        disabled={deletingIdentity === row.identityNumber}
                        onClick={() => setConfirmDeleteIdentity(row.identityNumber)}
                      >
                        {deletingIdentity === row.identityNumber ? "Đang xóa..." : "Xóa"}
                      </Button>
                    )}
                  </Stack>
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

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="lg" fullWidth>
        <DialogTitle>{editingIdentityNumber ? "Cập nhật hồ sơ tra cứu tín dụng" : "Tạo hồ sơ tra cứu tín dụng"}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={3}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField
                  label="CCCD"
                  value={form.identityNumber}
                  onChange={handleFormChange("identityNumber")}
                  disabled={Boolean(editingIdentityNumber)}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={8}>
                <TextField
                  label="Tên người vay"
                  value={form.borrowerName}
                  onChange={handleFormChange("borrowerName")}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(form.consentGranted)}
                      onChange={handleFormChange("consentGranted")}
                    />
                  }
                  label="Đã có đồng ý chia sẻ dữ liệu tín dụng"
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(form.fraudSuspect)}
                      onChange={handleFormChange("fraudSuspect")}
                    />
                  }
                  label="Đánh dấu nghi ngờ gian lận"
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  label="Ghi chú rủi ro / nguồn xác minh"
                  value={form.riskNote}
                  onChange={handleFormChange("riskNote")}
                  fullWidth
                  multiline
                  minRows={2}
                />
              </Grid>
            </Grid>

            <Paper
              variant="outlined"
              sx={{
                p: 2,
                bgcolor: preview.bureauStatus === "CLEAR" ? "#f6fff8" : preview.bureauStatus === "NO_HIT" ? "#f8fbff" : "#fffaf3"
              }}
            >
              <Stack spacing={1.5}>
                <Typography variant="subtitle1">Kết quả phân loại tự suy ra từ các khoản vay báo cáo</Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip
                    label={labelCreditBureauStatus(preview.bureauStatus)}
                    color={statusChipColor(preview.bureauStatus)}
                  />
                  <Chip label={`Điểm tín dụng nội bộ: ${preview.creditScore}`} color="default" />
                  <Chip label={`Khoản vay hiệu lực: ${preview.activeLoanCount}`} color="default" />
                  <Chip label={`Nguồn báo cáo: ${preview.reportingInstitutionCount}`} color="default" />
                  <Chip label={`Quá hạn tối đa: ${preview.maxDaysPastDue} ngày`} color={preview.maxDaysPastDue > 0 ? "warning" : "default"} />
                </Stack>
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <SummaryCard
                      label="Nghĩa vụ hàng tháng"
                      value={formatVnd(preview.totalMonthlyObligation)}
                      helper="Tổng số tiền phải trả mỗi tháng từ các khoản vay còn hiệu lực"
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <SummaryCard
                      label="Dư nợ còn lại"
                      value={formatVnd(preview.totalOutstandingBalance)}
                      helper="Tổng dư nợ còn mở trên toàn bộ khoản vay đang tác động"
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <SummaryCard
                      label="Nghĩa vụ ngoài app"
                      value={formatVnd(preview.externalMonthlyObligation)}
                      helper="Phần nghĩa vụ hàng tháng đến từ đối tác hoặc khách tự khai"
                    />
                  </Grid>
                </Grid>
              </Stack>
            </Paper>

            <Divider />

            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} justifyContent="space-between" alignItems={{ xs: "stretch", sm: "center" }}>
              <Stack spacing={0.5}>
                <Typography variant="subtitle1">Danh sách khoản vay đã báo cáo</Typography>
                <Typography variant="body2" color="text.secondary">
                  Dữ liệu nội bộ từ app sẽ được ghi đè lại khi chạy đồng bộ. Các khoản bên ngoài có thể nhập tay để mô phỏng liên thông tín dụng.
                </Typography>
              </Stack>
              <Button variant="outlined" onClick={handleAddLoanAccount}>
                Thêm khoản vay
              </Button>
            </Stack>

            <Stack spacing={2}>
              {form.loanAccounts.map((loan, index) => {
                const internalAccount = loan.sourceType === "INTERNAL_SYSTEM";
                return (
                  <Paper key={`${loan.sourceType}-${index}`} variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={2}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1} justifyContent="space-between" alignItems={{ xs: "stretch", sm: "center" }}>
                        <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                          <Chip size="small" label={`Khoản vay #${index + 1}`} />
                          <Chip size="small" label={labelCreditLoanSourceType(loan.sourceType)} />
                          <Chip size="small" label={labelCreditLoanAccountStatus(loan.accountStatus)} color={loanStatusChipColor(loan.accountStatus)} />
                        </Stack>
                        {!internalAccount && (
                          <Button color="error" size="small" onClick={() => handleRemoveLoanAccount(index)}>
                            Xóa khoản này
                          </Button>
                        )}
                      </Stack>
                      {internalAccount && (
                        <Alert severity="info">
                          Khoản vay này đến từ dữ liệu nội bộ của app. Thông tin sẽ được cập nhật lại khi quản trị viên chạy đồng bộ.
                        </Alert>
                      )}
                      <Grid container spacing={2}>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Đơn vị báo cáo"
                            value={loan.reportingInstitution}
                            onChange={handleLoanAccountChange(index, "reportingInstitution")}
                            fullWidth
                            disabled={internalAccount}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Mã hợp đồng / mã khoản vay"
                            value={loan.accountReference}
                            onChange={handleLoanAccountChange(index, "accountReference")}
                            fullWidth
                            disabled={internalAccount}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <FormControl fullWidth>
                            <InputLabel id={`source-type-${index}`}>Nguồn dữ liệu</InputLabel>
                            <Select
                              labelId={`source-type-${index}`}
                              value={loan.sourceType}
                              label="Nguồn dữ liệu"
                              onChange={handleLoanAccountChange(index, "sourceType")}
                              disabled={internalAccount}
                            >
                              {SOURCE_OPTIONS.map((option) => (
                                <MenuItem key={option.value} value={option.value}>
                                  {option.label}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Nhóm khoản vay"
                            value={loan.loanCategory}
                            onChange={handleLoanAccountChange(index, "loanCategory")}
                            fullWidth
                            disabled={internalAccount}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <FormControl fullWidth>
                            <InputLabel id={`loan-status-${index}`}>Trạng thái khoản vay</InputLabel>
                            <Select
                              labelId={`loan-status-${index}`}
                              value={loan.accountStatus}
                              label="Trạng thái khoản vay"
                              onChange={handleLoanAccountChange(index, "accountStatus")}
                              disabled={internalAccount}
                            >
                              {LOAN_STATUS_OPTIONS.map((option) => (
                                <MenuItem key={option.value} value={option.value}>
                                  {option.label}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Ngày quá hạn"
                            type="number"
                            value={loan.daysPastDue}
                            onChange={handleLoanAccountChange(index, "daysPastDue")}
                            fullWidth
                            disabled={internalAccount}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Số tiền vay ban đầu"
                            value={loan.originalAmount}
                            onChange={handleLoanAccountChange(index, "originalAmount")}
                            fullWidth
                            disabled={internalAccount}
                            inputProps={{ inputMode: "numeric" }}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Dư nợ còn lại"
                            value={loan.outstandingBalance}
                            onChange={handleLoanAccountChange(index, "outstandingBalance")}
                            fullWidth
                            disabled={internalAccount}
                            inputProps={{ inputMode: "numeric" }}
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            label="Số tiền trả mỗi tháng"
                            value={loan.monthlyPayment}
                            onChange={handleLoanAccountChange(index, "monthlyPayment")}
                            fullWidth
                            disabled={internalAccount}
                            inputProps={{ inputMode: "numeric" }}
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <TextField
                            label="Ghi chú khoản vay"
                            value={loan.note}
                            onChange={handleLoanAccountChange(index, "note")}
                            fullWidth
                            disabled={internalAccount}
                          />
                        </Grid>
                      </Grid>
                    </Stack>
                  </Paper>
                );
              })}
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog} disabled={saving}>
            Hủy
          </Button>
          <Button onClick={handleSave} variant="contained" disabled={saving}>
            {saving ? "Đang lưu..." : "Lưu hồ sơ"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(detailRecord)}
        onClose={() => setDetailRecord(null)}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>Sơ đồ khoản vay theo CCCD {detailRecord?.identityNumber || ""}</DialogTitle>
        <DialogContent dividers>
          {detailRecord && (
            <Stack spacing={2}>
              <Alert severity={summaryTone(detailRecord.bureauStatus)}>
                Hồ sơ được phân loại là <strong>{labelCreditBureauStatus(detailRecord.bureauStatus)}</strong>.
                {detailRecord.riskNote ? ` Ghi chú: ${detailRecord.riskNote}` : ""}
              </Alert>
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <SummaryCard label="Người vay" value={detailRecord.borrowerName || "-"} helper={`Điểm: ${detailRecord.creditScore ?? "-"}`} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <SummaryCard label="Nghĩa vụ hàng tháng" value={formatVnd(detailRecord.totalMonthlyObligation || 0)} helper={`Nguồn ngoài app: ${formatVnd(detailRecord.externalMonthlyObligation || 0)}`} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <SummaryCard label="Dư nợ còn lại" value={formatVnd(detailRecord.totalOutstandingBalance || 0)} helper={`Nguồn ngoài app: ${formatVnd(detailRecord.externalOutstandingBalance || 0)}`} />
                </Grid>
              </Grid>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip label={`Khoản vay hiệu lực: ${detailRecord.activeLoanCount || 0}`} />
                <Chip label={`Nguồn báo cáo: ${detailRecord.reportingInstitutionCount || 0}`} />
                <Chip label={`Quá hạn tối đa: ${detailRecord.daysPastDue || 0} ngày`} />
                <Chip label={detailRecord.consentGranted ? "Đã có đồng ý chia sẻ" : "Chưa có đồng ý chia sẻ"} />
              </Stack>
              <Paper sx={{ overflowX: "auto" }}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Đơn vị báo cáo</TableCell>
                      <TableCell>Mã khoản vay</TableCell>
                      <TableCell>Nguồn</TableCell>
                      <TableCell>Nhóm vay</TableCell>
                      <TableCell>Trạng thái</TableCell>
                      <TableCell align="right">Tiền vay ban đầu</TableCell>
                      <TableCell align="right">Dư nợ còn lại</TableCell>
                      <TableCell align="right">Trả mỗi tháng</TableCell>
                      <TableCell align="right">Ngày quá hạn</TableCell>
                      <TableCell>Cập nhật</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(detailRecord.loanAccounts || []).map((loan, index) => (
                      <TableRow key={`${loan.accountReference || loan.reportingInstitution || "loan"}-${index}`}>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{loan.reportingInstitution || "-"}</Typography>
                            {loan.note && (
                              <Typography variant="caption" color="text.secondary">
                                {loan.note}
                              </Typography>
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell>{loan.accountReference || "-"}</TableCell>
                        <TableCell>{labelCreditLoanSourceType(loan.sourceType)}</TableCell>
                        <TableCell>{loan.loanCategory || "-"}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={labelCreditLoanAccountStatus(loan.accountStatus)}
                            color={loanStatusChipColor(loan.accountStatus)}
                          />
                        </TableCell>
                        <TableCell align="right">{formatVnd(loan.originalAmount || 0)}</TableCell>
                        <TableCell align="right">{formatVnd(loan.outstandingBalance || 0)}</TableCell>
                        <TableCell align="right">{formatVnd(loan.monthlyPayment || 0)}</TableCell>
                        <TableCell align="right">{loan.daysPastDue || 0}</TableCell>
                        <TableCell>{formatDateTime(loan.reportedAt || loan.updatedAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailRecord(null)}>Đóng</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(confirmDeleteIdentity)}
        title="Xóa hồ sơ tra cứu tín dụng"
        description={`Bạn có chắc muốn xóa hồ sơ tín dụng của CCCD ${confirmDeleteIdentity || ""}?`}
        confirmText="Xóa hồ sơ"
        cancelText="Hủy"
        confirmColor="error"
        onConfirm={handleDelete}
        onClose={() => setConfirmDeleteIdentity(null)}
      />
    </Stack>
  );
}
