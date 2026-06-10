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

export function verifyEmailApi(token) {
  return apiRequest(`/api/auth/verify-email?token=${encodeURIComponent(token)}`);
}

export function resendVerificationApi(payload) {
  return apiRequest("/api/auth/resend-verification", {
    method: "POST",
    body: payload
  });
}

export function meApi(token) {
  return apiRequest("/api/auth/me", {
    token
  });
}

