import { createTheme } from "@mui/material";

export const appTheme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1f4b99"
    },
    secondary: {
      main: "#118a71"
    },
    background: {
      default: "#f3f5f8",
      paper: "#ffffff"
    }
  },
  shape: {
    borderRadius: 10
  },
  typography: {
    fontFamily: "'Segoe UI', 'Helvetica Neue', Arial, sans-serif",
    h4: {
      fontWeight: 700
    },
    h5: {
      fontWeight: 700
    }
  }
});
