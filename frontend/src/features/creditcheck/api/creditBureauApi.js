import { apiRequest } from "@/shared/api/http";
import { buildQueryString } from "@/shared/api/paged";

export function getCreditBureauRecordsPagedApi(token, { status, query, page = 0, size = 10 } = {}) {
  return apiRequest(`/api/credit-bureau-records/paged${buildQueryString({ status, query, page, size })}`, {
    token
  });
}

export function getCreditBureauSummaryApi(token) {
  return apiRequest("/api/credit-bureau-records/summary", {
    token
  });
}

export function createCreditBureauRecordApi(token, payload) {
  return apiRequest("/api/credit-bureau-records", {
    method: "POST",
    token,
    body: payload
  });
}

export function updateCreditBureauRecordApi(token, identityNumber, payload) {
  return apiRequest(`/api/credit-bureau-records/${encodeURIComponent(identityNumber)}`, {
    method: "PUT",
    token,
    body: payload
  });
}

export function deleteCreditBureauRecordApi(token, identityNumber) {
  return apiRequest(`/api/credit-bureau-records/${encodeURIComponent(identityNumber)}`, {
    method: "DELETE",
    token
  });
}

export function syncCreditBureauInternalLoansApi(token) {
  return apiRequest("/api/credit-bureau-records/sync-internal-loans", {
    method: "POST",
    token
  });
}
