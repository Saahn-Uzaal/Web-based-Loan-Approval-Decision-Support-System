import { apiRequest, downloadFile, getFileObjectUrl } from "@/shared/api/http";
import { buildQueryString, fetchAllPageResponse } from "@/shared/api/paged";

function buildDemoHeaders(demoNow) {
  if (!demoNow) {
    return undefined;
  }
  return {
    "X-Demo-Now": demoNow
  };
}

export function getStaffRequestsApi(token, status) {
  return fetchAllPageResponse("/api/staff/requests/paged", {
    token,
    params: { status }
  }).then((response) => (Array.isArray(response?.content) ? response.content : []));
}

export function getStaffRequestsPagedApi(token, { status, page = 0, size = 10 } = {}) {
  return apiRequest(`/api/staff/requests/paged${buildQueryString({ status, page, size })}`, {
    token
  });
}

export function getStaffLoanOperationsApi(token, status) {
  return fetchAllPageResponse("/api/staff/requests/operations/paged", {
    token,
    params: { status }
  }).then((response) => (Array.isArray(response?.content) ? response.content : []));
}

export function getStaffLoanOperationsPagedApi(token, { status, page = 0, size = 10 } = {}) {
  return apiRequest(`/api/staff/requests/operations/paged${buildQueryString({ status, page, size })}`, {
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

export function assignStaffCaseApi(token, id) {
  return apiRequest(`/api/staff/requests/${id}/assign`, {
    method: "POST",
    token
  });
}

export function releaseStaffCaseApi(token, id) {
  return apiRequest(`/api/staff/requests/${id}/release`, {
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

export function updateStaffRequestVerificationApi(token, loanId, payload) {
  return apiRequest(`/api/staff/requests/${loanId}/verification`, {
    method: "PUT",
    token,
    body: payload
  });
}

export function downloadStaffLoanDocumentApi(token, loanId, documentId, fileName) {
  return downloadFile(`/api/staff/requests/${loanId}/documents/${documentId}`, {
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

export function rescheduleStaffSecuredAppointmentApi(token, loanId, payload) {
  return apiRequest(`/api/staff/secured-procedures/${loanId}/appointments/reschedule`, {
    method: "POST",
    token,
    body: payload
  });
}

export function cancelStaffSecuredAppointmentApi(token, loanId) {
  return apiRequest(`/api/staff/secured-procedures/${loanId}/appointments/cancel`, {
    method: "POST",
    token
  });
}

export function noShowStaffSecuredAppointmentApi(token, loanId) {
  return apiRequest(`/api/staff/secured-procedures/${loanId}/appointments/no-show`, {
    method: "POST",
    token
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
