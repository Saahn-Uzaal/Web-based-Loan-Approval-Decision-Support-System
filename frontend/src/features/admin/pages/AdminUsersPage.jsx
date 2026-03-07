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
import { useCallback, useEffect, useState } from "react";
import { deleteManagedUserApi, getManagedUsersApi } from "@/features/admin/api/adminUserApi";
import { useAuth } from "@/features/auth/context/AuthContext";
import { labelRole } from "@/shared/utils/labels";
import ConfirmDialog from "@/shared/components/ConfirmDialog";

function RoleChip({ role }) {
  return (
    <Chip
      size="small"
      label={labelRole(role)}
      color={role === "STAFF" ? "primary" : "default"}
    />
  );
}

export default function AdminUsersPage() {
  const { accessToken } = useAuth();
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [deletingIds, setDeletingIds] = useState([]);
  const [confirmDeleteUser, setConfirmDeleteUser] = useState(null);

  const loadUsers = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const users = await getManagedUsersApi(accessToken, roleFilter);
      setRows(Array.isArray(users) ? users : []);
    } catch (err) {
      setError(err.message || "Không tải được danh sách người dùng");
    } finally {
      setLoading(false);
    }
  }, [accessToken, roleFilter]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const handleDelete = (user) => {
    setConfirmDeleteUser(user);
  };

  const handleConfirmDelete = async () => {
    const user = confirmDeleteUser;
    setConfirmDeleteUser(null);
    if (!user) {
      return;
    }

    setError("");
    setSuccess("");
    setDeletingIds((prev) => [...prev, user.id]);
    try {
      await deleteManagedUserApi(accessToken, user.id);
      setRows((prev) => prev.filter((item) => item.id !== user.id));
      setSuccess(`Đã xóa tài khoản: ${user.email}`);
    } catch (err) {
      setError(err.message || "Không xóa được người dùng");
    } finally {
      setDeletingIds((prev) => prev.filter((id) => id !== user.id));
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Quản lý người dùng</Typography>
      <Typography color="text.secondary">
        Quản trị viên có thể xem và xóa tài khoản khách hàng/nhân viên.
      </Typography>

      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="admin-role-filter-label">Lọc vai trò</InputLabel>
          <Select
            labelId="admin-role-filter-label"
            value={roleFilter}
            label="Lọc vai trò"
            onChange={(event) => setRoleFilter(event.target.value)}
          >
            <MenuItem value="ALL">Tất cả</MenuItem>
            <MenuItem value="CUSTOMER">Khách hàng</MenuItem>
            <MenuItem value="STAFF">Nhân viên</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}
      {success && <Alert severity="success">{success}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Đang tải người dùng...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            Không tìm thấy người dùng với bộ lọc này.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Mã người dùng</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Vai trò</TableCell>
              <TableCell>Ngày tạo</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const deleting = deletingIds.includes(row.id);
              return (
                <TableRow key={row.id} hover>
                  <TableCell>#{row.id}</TableCell>
                  <TableCell>{row.email}</TableCell>
                  <TableCell>
                    <RoleChip role={row.role} />
                  </TableCell>
                  <TableCell>
                    {row.createdAt ? new Date(row.createdAt).toLocaleString() : "-"}
                  </TableCell>
                  <TableCell align="right">
                    <Button
                      color="error"
                      variant="outlined"
                      size="small"
                      disabled={deleting}
                      onClick={() => handleDelete(row)}
                    >
                      {deleting ? "Đang xóa..." : "Xóa"}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>

      <ConfirmDialog
        open={confirmDeleteUser != null}
        title="Xóa tài khoản"
        message={
          confirmDeleteUser
            ? `Xóa tài khoản ${labelRole(confirmDeleteUser.role)} ${confirmDeleteUser.email}? Hành động này không thể hoàn tác.`
            : ""
        }
        confirmText="Xóa"
        cancelText="Hủy"
        onConfirm={handleConfirmDelete}
        onCancel={() => setConfirmDeleteUser(null)}
      />
    </Stack>
  );
}
