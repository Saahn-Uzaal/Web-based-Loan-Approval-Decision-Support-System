import { apiRequest } from "@/shared/api/http";

export function getNotificationsApi(token, limit = 20) {
  const query = `?limit=${encodeURIComponent(limit)}`;
  return apiRequest(`/api/notifications${query}`, {
    token
  });
}

export function markNotificationReadApi(token, notificationId) {
  return apiRequest(`/api/notifications/${notificationId}/read`, {
    method: "POST",
    token
  });
}

export function markAllNotificationsReadApi(token) {
  return apiRequest("/api/notifications/read-all", {
    method: "POST",
    token
  });
}
