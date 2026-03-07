import { apiRequest } from "@/shared/api/http";

export function getMyDebtsApi(token) {
  return apiRequest("/api/customer/debts", {
    token
  });
}

export function getDebtMetricsApi(token) {
  return apiRequest("/api/customer/debts/metrics", {
    token
  });
}

export function createDebtApi(token, payload) {
  return apiRequest("/api/customer/debts", {
    method: "POST",
    token,
    body: payload
  });
}

export function deleteDebtApi(token, debtId) {
  return apiRequest(`/api/customer/debts/${debtId}`, {
    method: "DELETE",
    token
  });
}
