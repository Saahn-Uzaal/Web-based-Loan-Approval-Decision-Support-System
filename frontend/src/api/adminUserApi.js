import { apiRequest } from "./http";

export function getManagedUsersApi(token, role) {
  const query = role && role !== "ALL" ? `?role=${encodeURIComponent(role)}` : "";
  return apiRequest(`/api/admin/users${query}`, {
    token
  });
}

export function deleteManagedUserApi(token, userId) {
  return apiRequest(`/api/admin/users/${userId}`, {
    method: "DELETE",
    token
  });
}
