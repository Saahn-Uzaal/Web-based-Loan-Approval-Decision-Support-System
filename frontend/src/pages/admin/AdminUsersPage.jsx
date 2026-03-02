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
import { deleteManagedUserApi, getManagedUsersApi } from "../../api/adminUserApi";
import { useAuth } from "../../auth/AuthContext";

function RoleChip({ role }) {
  return (
    <Chip
      size="small"
      label={role}
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
      setError(err.message || "Failed to load users");
    } finally {
      setLoading(false);
    }
  }, [accessToken, roleFilter]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const handleDelete = async (user) => {
    const confirmed = window.confirm(
      `Delete ${user.role} account ${user.email}? This action cannot be undone.`
    );
    if (!confirmed) {
      return;
    }

    setError("");
    setSuccess("");
    setDeletingIds((prev) => [...prev, user.id]);
    try {
      await deleteManagedUserApi(accessToken, user.id);
      setRows((prev) => prev.filter((item) => item.id !== user.id));
      setSuccess(`Deleted account: ${user.email}`);
    } catch (err) {
      setError(err.message || "Failed to delete user");
    } finally {
      setDeletingIds((prev) => prev.filter((id) => id !== user.id));
    }
  };

  return (
    <Stack spacing={2}>
      <Typography variant="h4">User Management</Typography>
      <Typography color="text.secondary">
        Admin can review and delete CUSTOMER / STAFF accounts.
      </Typography>

      <Paper sx={{ p: 2 }}>
        <FormControl sx={{ minWidth: 240 }}>
          <InputLabel id="admin-role-filter-label">Role filter</InputLabel>
          <Select
            labelId="admin-role-filter-label"
            value={roleFilter}
            label="Role filter"
            onChange={(event) => setRoleFilter(event.target.value)}
          >
            <MenuItem value="ALL">ALL</MenuItem>
            <MenuItem value="CUSTOMER">CUSTOMER</MenuItem>
            <MenuItem value="STAFF">STAFF</MenuItem>
          </Select>
        </FormControl>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}
      {success && <Alert severity="success">{success}</Alert>}

      {loading && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CircularProgress size={20} />
            <Typography variant="body2">Loading users...</Typography>
          </Stack>
        </Paper>
      )}

      {!loading && rows.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary">
            No users found for this filter.
          </Typography>
        </Paper>
      )}

      <Paper sx={{ overflowX: "auto" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>User ID</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Created At</TableCell>
              <TableCell align="right">Action</TableCell>
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
                      {deleting ? "Deleting..." : "Delete"}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}
