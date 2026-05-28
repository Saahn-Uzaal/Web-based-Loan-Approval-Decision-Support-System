export const PAYSLIP_ACCEPT =
  ".pdf,.doc,.docx,.xls,.xlsx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

export const IDENTITY_CARD_ACCEPT = ".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp";
export const LOAN_IMAGE_ACCEPT = ".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp";
export const SUPPLEMENTAL_DOCUMENT_ACCEPT =
  ".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx,.xls,.xlsx,image/jpeg,image/png,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
export const PAYMENT_PROOF_ACCEPT = LOAN_IMAGE_ACCEPT;

export function isAcceptedPayslipFile(file) {
  if (!file?.name) {
    return false;
  }
  return /\.(pdf|doc|docx|xls|xlsx)$/i.test(file.name);
}

export function isAcceptedLoanImageFile(file) {
  if (!file?.name) {
    return false;
  }
  return /\.(jpg|jpeg|png|webp)$/i.test(file.name);
}

export function isAcceptedIdentityCardFile(file) {
  return isAcceptedLoanImageFile(file);
}

export function isAcceptedSupplementalDocumentFile(file) {
  if (!file?.name) {
    return false;
  }
  return /\.(jpg|jpeg|png|webp|pdf|doc|docx|xls|xlsx)$/i.test(file.name);
}

export function isAcceptedPaymentProofFile(file) {
  return isAcceptedLoanImageFile(file);
}

export function formatFileSize(bytes) {
  const size = Number(bytes);
  if (!Number.isFinite(size) || size < 0) {
    return "-";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}
