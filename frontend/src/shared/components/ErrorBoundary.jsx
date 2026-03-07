import { Component } from "react";
import { Alert, Box, Button, Container, Stack, Typography } from "@mui/material";

export class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error("ErrorBoundary caught:", error, errorInfo);
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  handleGoHome = () => {
    this.setState({ hasError: false, error: null });
    window.location.href = "/";
  };

  render() {
    if (this.state.hasError) {
      return (
        <Container maxWidth="sm" sx={{ py: 8 }}>
          <Stack spacing={3} alignItems="center">
            <Typography variant="h4" color="error">
              Đã xảy ra lỗi
            </Typography>
            <Alert severity="error" sx={{ width: "100%" }}>
              {this.state.error?.message || "Ứng dụng gặp lỗi không mong đợi."}
            </Alert>
            <Typography variant="body2" color="text.secondary">
              Vui lòng thử tải lại trang hoặc quay về trang chủ.
            </Typography>
            <Box sx={{ display: "flex", gap: 2 }}>
              <Button variant="contained" onClick={this.handleReload}>
                Tải lại trang
              </Button>
              <Button variant="outlined" onClick={this.handleGoHome}>
                Về trang chủ
              </Button>
            </Box>
          </Stack>
        </Container>
      );
    }
    return this.props.children;
  }
}

