import { apiRequest } from "./http";

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
