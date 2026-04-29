import { apiRequest, downloadFile, getFileObjectUrl } from "@/shared/api/http";

function buildDemoHeaders(demoNow) {
  if (!demoNow) {
    return undefined;
  }
  return {
    "X-Demo-Now": demoNow
  };
}

export function getStaffRequestsApi(token, status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest(`/api/staff/requests${query}`, {
    token
  });
}

export function getStaffRequestDetailApi(token, id) {
  return apiRequest(`/api/staff/requests/${id}`, {
    token
  });
}

export function submitStaffDecisionApi(token, id, payload) {
  return apiRequest(`/api/staff/requests/${id}/decision`, {
    method: "POST",
    token,
    body: payload
  });
}

export function completeStaffContractApi(token, id) {
  return apiRequest(`/api/staff/requests/${id}/complete-contract`, {
    method: "POST",
    token
  });
}

export function disburseStaffLoanApi(token, id) {
  return apiRequest(`/api/staff/requests/${id}/disburse`, {
    method: "POST",
    token
  });
}

export function updateStaffCustomerVerificationApi(token, customerId, payload) {
  return apiRequest(`/api/staff/verifications/${customerId}`, {
    method: "PUT",
    token,
    body: payload
  });
}

export function downloadStaffLoanDocumentApi(token, loanId, documentType, fileName) {
  return downloadFile(`/api/staff/requests/${loanId}/documents/${documentType}`, {
    token,
    fileName
  });
}

export function getStaffSecuredProceduresApi(token) {
  return apiRequest("/api/staff/secured-procedures", {
    token
  });
}

export function getStaffSecuredProcedureDetailApi(token, loanId) {
  return apiRequest(`/api/staff/secured-procedures/${loanId}`, {
    token
  });
}

export function saveStaffSecuredProcedureApi(token, loanId, payload, demoNow) {
  return apiRequest(`/api/staff/secured-procedures/${loanId}`, {
    method: "PUT",
    token,
    body: payload,
    headers: buildDemoHeaders(demoNow)
  });
}

export function getStaffPaymentConfirmationsApi(token, status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest(`/api/staff/payment-confirmations${query}`, {
    token
  });
}

export function getStaffPaymentConfirmationDetailApi(token, id) {
  return apiRequest(`/api/staff/payment-confirmations/${id}`, {
    token
  });
}

export function reviewStaffPaymentConfirmationApi(token, id, payload) {
  return apiRequest(`/api/staff/payment-confirmations/${id}/review`, {
    method: "POST",
    token,
    body: payload
  });
}

export function getStaffPaymentProofObjectUrlApi(token, id) {
  return getFileObjectUrl(`/api/staff/payment-confirmations/${id}/proof`, {
    token
  });
}

export function downloadStaffPaymentProofApi(token, id, fileName) {
  return downloadFile(`/api/staff/payment-confirmations/${id}/proof`, {
    token,
    fileName
  });
}
