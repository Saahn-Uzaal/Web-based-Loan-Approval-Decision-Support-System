import { apiRequest, downloadFile } from "@/shared/api/http";

export function getInformationVerificationsApi(token, status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest(`/api/staff/information-verifications${query}`, {
    token
  });
}

export function getInformationVerificationDetailApi(token, customerId) {
  return apiRequest(`/api/staff/information-verifications/${customerId}`, {
    token
  });
}

export function reviewInformationVerificationApi(token, customerId, payload) {
  return apiRequest(`/api/staff/information-verifications/${customerId}/decision`, {
    method: "POST",
    token,
    body: payload
  });
}

export function downloadInformationVerificationPayslipApi(token, customerId, fileName) {
  return downloadFile(`/api/staff/information-verifications/${customerId}/payslip`, {
    token,
    fileName
  });
}
