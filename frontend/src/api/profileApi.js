import { apiRequest } from "./http";

export function getMyProfileApi(token) {
  return apiRequest("/api/customer/profile", {
    token
  });
}

export function upsertMyProfileApi(token, payload) {
  return apiRequest("/api/customer/profile", {
    method: "PUT",
    token,
    body: payload
  });
}
