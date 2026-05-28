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
  FormControl,
  FormControlLabel,
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
import { useCallback, useEffect, useState } from "react";
import {
  createCreditBureauRecordApi,
  deleteCreditBureauRecordApi,
  getCreditBureauRecordsPagedApi,
  updateCreditBureauRecordApi
} from "@/features/creditcheck/api/creditBureauApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import ConfirmDialog from "@/shared/components/ConfirmDialog";
import { labelCreditBureauStatus } from "@/shared/utils/labels";

const STATUS_OPTIONS = [
  { value: "NO_HIT", label: "Không có dữ liệu xấu" },
  { value: "CLEAR", label: "Lịch sử sạch" },
  { value: "WATCHLIST", label: "Danh sách cần rà soát" },
  { value: "BAD_DEBT", label: "Có nợ xấu" },
  { value: "FRAUD_SUSPECT", label: "Nghi ngờ gian lận" }
];

function emptyForm() {
  return {
    identityNumber: "",
    borrowerName: "",
    bureauStatus: "BAD_DEBT",
    creditScore: 35,
    activeLoanCount: 0,
    daysPastDue: 0,
    manualReviewRequired: true,
    hardReject: false,
    riskNote: ""
  };
}

function defaultsForStatus(status) {
  if (status === "FRAUD_SUSPECT") {
    return { manualReviewRequired: true, hardReject: true };
  }
  if (status === "WATCHLIST" || status === "BAD_DEBT") {
    return { manualReviewRequired: true, hardReject: false };
  }
  return { manualReviewRequired: false, hardReject: false };
}

function StatusChip({ status }) {
  const colorMap = {
    NO_HIT: "default",
    CLEAR: "success",
    WATCHLIST: "warning",
    BAD_DEBT: "error",
    FRAUD_SUSPECT: "secondary"
  };

  return <Chip size="small" label={labelCreditBureauStatus(status)} color={colorMap[status] || "default"} />;
}

export default function CreditBureauRecordsPage() {
  const { accessToken, user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const [statusFilter, setStatusFilter] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalRows, setTotalRows] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingIdentityNumber, setEditingIdentityNumber] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [saving, setSaving] = useState(false);
  const [confirmDeleteIdentity, setConfirmDeleteIdentity] = useState(null);
  const [deletingIdentity, setDeletingIdentity] = useState("");

  const loadRecords = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const response = await getCreditBureauRecordsPagedApi(accessToken, {
        status: statusFilter || undefined,
        query: query || undefined,
        page,
        size: rowsPerPage
      });
      setRows(Array.isArray(response?.content) ? response.content : []);
      setTotalRows(Number(response?.totalElements || 0));
    } catch (err) {
      setError(err.message || "Không tải được danh sách nợ xấu");
    } finally {
      setLoading(false);
    }
  }, [accessToken, page, query, rowsPerPage, statusFilter]);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

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
      bureauStatus: row.bureauStatus,
      creditScore: row.creditScore ?? 0,
      activeLoanCount: row.activeLoanCount ?? 0,
      daysPastDue: row.daysPastDue ?? 0,
      manualReviewRequired: Boolean(row.manualReviewRequired),
      hardReject: Boolean(row.hardReject),
      riskNote: row.riskNote || ""
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
    if (field === "bureauStatus") {
      const nextDefaults = defaultsForStatus(value);
      setForm((prev) => ({
        ...prev,
        bureauStatus: value,
        manualReviewRequired: nextDefaults.manualReviewRequired,
        hardReject: nextDefaults.hardReject
      }));
      return;
    }
    setForm((prev) => ({
      ...prev,
      [field]: value
    }));
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
        ...form,
        identityNumber: form.identityNumber.trim(),
        borrowerName: form.borrowerName.trim(),
        creditScore: Number(form.creditScore),
        activeLoanCount: Number(form.activeLoanCount),
        daysPastDue: Number(form.daysPastDue),
        riskNote: form.riskNote.trim()
      };
      if (editingIdentityNumber) {
        await updateCreditBureauRecordApi(accessToken, editingIdentityNumber, payload);
        setSuccess(`Đã cập nhật bản ghi ${payload.identityNumber}.`);
      } else {
        await createCreditBureauRecordApi(accessToken, payload);
        setSuccess(`Đã thêm bản ghi ${payload.identityNumber}.`);
      }
      setDialogOpen(false);
      await loadRecords();
    } catch (err) {
      setError(err.message || "Không lưu được dữ liệu nợ xấu");
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
      setSuccess(`Đã xóa bản ghi ${identityNumber}.`);
      if (rows.length === 1 && page > 0) {
        setPage((prev) => prev - 1);
      } else {
        await loadRecords();
      }
    } catch (err) {
      setError(err.message || "Không xóa được dữ liệu nợ xấu");
    } finally {
      setDeletingIdentity("");
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Danh sách nợ xấu</Typography>
      <Typography color="text.secondary">
        Theo dõi dữ liệu tín dụng nội bộ theo CCCD. Nhân viên chỉ được xem, quản trị viên có thể thêm, sửa và xóa bản ghi.
      </Typography>

      {!isAdmin && (
        <Alert severity="info">
          Tài khoản nhân viên đang ở chế độ chỉ xem. Mọi thay đổi trên danh sách này phải do quản trị viên thực hiện.
        </Alert>
      )}

      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems={{ xs: "stretch", md: "center" }}>
          <TextField
            label="Tìm theo CCCD, tên hoặc ghi chú"
            value={queryInput}
            onChange={(event) => setQueryInput(event.target.value)}
            fullWidth
          />
          <FormControl sx={{ minWidth: { xs: "100%", md: 220 } }}>
            <InputLabel id="credit-bureau-status-filter-label">Lọc trạng thái</InputLabel>
            <Select
              labelId="credit-bureau-status-filter-label"
              value={statusFilter}
              label="Lọc trạng thái"
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
            <Button variant="contained" color="secondary" onClick={openCreateDialog}>
              Thêm dữ liệu
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
            <Typography variant="body2">Đang tải danh sách nợ xấu...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && totalRows === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không có bản ghi tín dụng nào phù hợp với bộ lọc hiện tại.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>CCCD</TableCell>
              <TableCell>Người vay</TableCell>
              <TableCell>Trạng thái</TableCell>
              <TableCell align="right">Điểm</TableCell>
              <TableCell align="right">Khoản vay hoạt động</TableCell>
              <TableCell align="right">Ngày quá hạn</TableCell>
              <TableCell>Cần rà soát</TableCell>
              <TableCell>Từ chối cứng</TableCell>
              <TableCell>Cập nhật</TableCell>
              {isAdmin && <TableCell align="right">Thao tác</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.identityNumber} hover>
                <TableCell>{row.identityNumber}</TableCell>
                <TableCell>
                  <Stack spacing={0.25}>
                    <Typography variant="body2">{row.borrowerName || "-"}</Typography>
                    {row.riskNote && (
                      <Typography variant="caption" color="text.secondary">
                        {row.riskNote}
                      </Typography>
                    )}
                  </Stack>
                </TableCell>
                <TableCell>
                  <StatusChip status={row.bureauStatus} />
                </TableCell>
                <TableCell align="right">{row.creditScore ?? "-"}</TableCell>
                <TableCell align="right">{row.activeLoanCount ?? 0}</TableCell>
                <TableCell align="right">{row.daysPastDue ?? 0}</TableCell>
                <TableCell>{row.manualReviewRequired ? "Có" : "Không"}</TableCell>
                <TableCell>{row.hardReject ? "Có" : "Không"}</TableCell>
                <TableCell>{row.updatedAt ? new Date(row.updatedAt).toLocaleString() : "-"}</TableCell>
                {isAdmin && (
                  <TableCell align="right">
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                      <Button
                        variant="outlined"
                        size="small"
                        onClick={() => openEditDialog(row)}
                      >
                        Sửa
                      </Button>
                      <Button
                        variant="outlined"
                        color="error"
                        size="small"
                        disabled={deletingIdentity === row.identityNumber}
                        onClick={() => setConfirmDeleteIdentity(row.identityNumber)}
                      >
                        {deletingIdentity === row.identityNumber ? "Đang xóa..." : "Xóa"}
                      </Button>
                    </Stack>
                  </TableCell>
                )}
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

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{editingIdentityNumber ? "Cập nhật dữ liệu nợ xấu" : "Thêm dữ liệu nợ xấu"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="CCCD"
              value={form.identityNumber}
              onChange={handleFormChange("identityNumber")}
              disabled={Boolean(editingIdentityNumber)}
              fullWidth
            />
            <TextField
              label="Tên người vay"
              value={form.borrowerName}
              onChange={handleFormChange("borrowerName")}
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="credit-bureau-status-label">Trạng thái tín dụng</InputLabel>
              <Select
                labelId="credit-bureau-status-label"
                value={form.bureauStatus}
                label="Trạng thái tín dụng"
                onChange={handleFormChange("bureauStatus")}
              >
                {STATUS_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Điểm tín dụng"
                type="number"
                value={form.creditScore}
                onChange={handleFormChange("creditScore")}
                fullWidth
              />
              <TextField
                label="Khoản vay hoạt động"
                type="number"
                value={form.activeLoanCount}
                onChange={handleFormChange("activeLoanCount")}
                fullWidth
              />
              <TextField
                label="Ngày quá hạn"
                type="number"
                value={form.daysPastDue}
                onChange={handleFormChange("daysPastDue")}
                fullWidth
              />
            </Stack>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={form.manualReviewRequired}
                    onChange={handleFormChange("manualReviewRequired")}
                  />
                }
                label="Cần rà soát thủ công"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={form.hardReject}
                    onChange={handleFormChange("hardReject")}
                  />
                }
                label="Cờ từ chối cứng"
              />
            </Stack>
            <TextField
              label="Ghi chú rủi ro"
              value={form.riskNote}
              onChange={handleFormChange("riskNote")}
              multiline
              minRows={3}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={closeDialog} disabled={saving}>
            Hủy
          </Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? "Đang lưu..." : editingIdentityNumber ? "Lưu thay đổi" : "Tạo bản ghi"}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmDeleteIdentity != null}
        title="Xóa dữ liệu nợ xấu"
        message={
          confirmDeleteIdentity
            ? `Xóa bản ghi tín dụng ${confirmDeleteIdentity}? Hành động này không thể hoàn tác.`
            : ""
        }
        confirmText="Xóa"
        cancelText="Hủy"
        onConfirm={handleDelete}
        onCancel={() => setConfirmDeleteIdentity(null)}
      />
    </Stack>
  );
}
