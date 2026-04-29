import { apiRequest, downloadFile } from "@/shared/api/http";

export function createLoanApi(token, payload, files = {}) {
  const hasFiles = Object.values(files).some(Boolean);
  if (hasFiles) {
    const formData = new FormData();
    formData.append(
      "loan",
      new Blob([JSON.stringify(payload)], {
        type: "application/json"
      })
    );

    Object.entries(files).forEach(([key, file]) => {
      if (file) {
        formData.append(key, file);
      }
    });

    return apiRequest("/api/customer/loans", {
      method: "POST",
      token,
      body: formData
    });
  }

  return apiRequest("/api/customer/loans", {
    method: "POST",
    token,
    body: payload
  });
}

export function getMyLoansApi(token) {
  return apiRequest("/api/customer/loans", {
    token
  });
}

export function getLoanDetailApi(token, id) {
  return apiRequest(`/api/customer/loans/${id}`, {
    token
  });
}

export function downloadLoanDocumentApi(token, loanId, documentType, fileName) {
  return downloadFile(`/api/customer/loans/${loanId}/documents/${documentType}`, {
    token,
    fileName
  });
}
