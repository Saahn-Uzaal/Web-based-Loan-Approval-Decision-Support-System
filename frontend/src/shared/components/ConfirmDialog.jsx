import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";

export default function ConfirmDialog({ open, title, message, confirmText, cancelText, onConfirm, onCancel }) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title || "Xác nhận"}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} color="inherit">
          {cancelText || "Hủy"}
        </Button>
        <Button onClick={onConfirm} variant="contained" color="error" autoFocus>
          {confirmText || "Xác nhận"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

