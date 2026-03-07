import { apiRequest } from "@/shared/api/http";

export function createLoanApi(token, payload) {
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

