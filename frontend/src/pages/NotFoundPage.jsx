import { Button, Paper, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <Paper sx={{ p: 4 }}>
      <Stack spacing={2}>
        <Typography variant="h5">Page not found</Typography>
        <Typography color="text.secondary">
          The requested page does not exist.
        </Typography>
        <Button variant="contained" component={RouterLink} to="/">
          Back to home
        </Button>
      </Stack>
    </Paper>
  );
}
