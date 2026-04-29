import { apiRequest, downloadFile } from "@/shared/api/http";

export function getMyPaymentsApi(token) {
  return apiRequest("/api/customer/payments", {
    token
  });
}

export function createPaymentConfirmationApi(token, { loanRequestId, note, proof }) {
  const formData = new FormData();
  formData.append("loanRequestId", String(loanRequestId));
  if (note) {
    formData.append("note", note);
  }
  formData.append("proof", proof);

  return apiRequest("/api/customer/payments/confirmations", {
    method: "POST",
    token,
    body: formData
  });
}

export function downloadPaymentProofApi(token, confirmationId, fileName) {
  return downloadFile(`/api/customer/payments/confirmations/${confirmationId}/proof`, {
    token,
    fileName
  });
}
