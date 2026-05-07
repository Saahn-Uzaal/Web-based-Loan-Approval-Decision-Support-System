import { apiRequest } from "@/shared/api/http";

export function buildQueryString(params = {}) {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value == null || value === "") {
      return;
    }
    searchParams.set(key, String(value));
  });

  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

export async function fetchAllPageResponse(path, { token, params = {}, pageSize = 100 } = {}) {
  const content = [];
  let page = 0;
  let totalPages = 1;
  let firstResponse = null;

  while (page < totalPages) {
    const response = await apiRequest(
      `${path}${buildQueryString({
        ...params,
        page,
        size: pageSize
      })}`,
      { token }
    );

    if (!firstResponse) {
      firstResponse = response;
    }

    content.push(...(Array.isArray(response?.content) ? response.content : []));

    if (response?.last === true) {
      break;
    }

    totalPages = Math.max(Number(response?.totalPages || 0), 1);
    page += 1;
  }

  return {
    ...firstResponse,
    content
  };
}
