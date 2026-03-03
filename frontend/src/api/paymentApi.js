import { apiRequest } from "./http";

export function getMyPaymentsApi(token) {
  return apiRequest("/api/customer/payments", {
    token
  });
}

export function createPaymentApi(token, payload) {
  return apiRequest("/api/customer/payments", {
    method: "POST",
    token,
    body: payload
  });
}
