import { apiRequest } from "@/shared/api/http";
import { buildQueryString, fetchAllPageResponse } from "@/shared/api/paged";

export function getManagedUsersApi(token, role) {
  return fetchAllPageResponse("/api/admin/users/paged", {
    token,
    params: { role: role && role !== "ALL" ? role : undefined }
  }).then((response) => (Array.isArray(response?.content) ? response.content : []));
}

export function getManagedUsersPagedApi(token, { role, page = 0, size = 10 } = {}) {
  return apiRequest(
    `/api/admin/users/paged${buildQueryString({
      role: role && role !== "ALL" ? role : undefined,
      page,
      size
    })}`,
    {
      token
    }
  );
}

export function deleteManagedUserApi(token, userId) {
  return apiRequest(`/api/admin/users/${userId}`, {
    method: "DELETE",
    token
  });
}

export function createManagedUserApi(token, payload) {
  return apiRequest("/api/admin/users", {
    method: "POST",
    token,
    body: payload
  });
}

