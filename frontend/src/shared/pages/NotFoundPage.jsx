import { Button, Paper, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";

export default function NotFoundPage() {
  const { isAuthenticated } = useAuth();
  const homePath = isAuthenticated ? "/dashboard" : "/";

  return (
    <Paper sx={{ p: 4 }}>
      <Stack spacing={2}>
        <Typography variant="h5">Không tìm thấy trang</Typography>
        <Typography color="text.secondary">
          Trang bạn yêu cầu không tồn tại.
        </Typography>
        <Button variant="contained" component={RouterLink} to={homePath}>
          {isAuthenticated ? "Về bảng điều khiển" : "Về trang chủ"}
        </Button>
      </Stack>
    </Paper>
  );
}
