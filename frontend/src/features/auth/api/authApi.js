import { apiRequest } from "@/shared/api/http";

export function loginApi(payload) {
  return apiRequest("/api/auth/login", {
    method: "POST",
    body: payload
  });
}

export function registerApi(payload) {
  return apiRequest("/api/auth/register", {
    method: "POST",
    body: payload
  });
}

export function meApi(token) {
  return apiRequest("/api/auth/me", {
    token
  });
}

