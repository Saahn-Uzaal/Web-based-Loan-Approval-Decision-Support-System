const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

function buildErrorMessage(status, payload) {
  if (payload?.message) {
    return payload.message;
  }
  if (payload?.error) {
    return payload.error;
  }
  return `Yêu cầu thất bại với mã trạng thái ${status}`;
}

function isFormData(value) {
  return typeof FormData !== "undefined" && value instanceof FormData;
}

function parseFileName(contentDisposition) {
  if (!contentDisposition) {
    return null;
  }

  const utfMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utfMatch?.[1]) {
    return decodeURIComponent(utfMatch[1]);
  }

  const asciiMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
  return asciiMatch?.[1] || null;
}

export async function apiRequest(path, { method = "GET", body, token, headers: extraHeaders } = {}) {
  const headers = {
    Accept: "application/json",
    ...(extraHeaders || {})
  };

  if (body !== undefined && !isFormData(body)) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : isFormData(body) ? body : JSON.stringify(body)
  });

  const hasJson = response.headers.get("content-type")?.includes("application/json");
  const payload = hasJson ? await response.json() : null;

  if (!response.ok) {
    throw new Error(buildErrorMessage(response.status, payload));
  }

  return payload;
}

export async function downloadFile(path, { token, fileName } = {}) {
  const headers = {};

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "GET",
    headers
  });

  const hasJson = response.headers.get("content-type")?.includes("application/json");
  if (!response.ok) {
    const payload = hasJson ? await response.json() : null;
    throw new Error(buildErrorMessage(response.status, payload));
  }

  const blob = await response.blob();
  const resolvedName = fileName || parseFileName(response.headers.get("content-disposition")) || "tệp-tin";
  const blobUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = blobUrl;
  anchor.download = resolvedName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(blobUrl);
}

export async function getFileObjectUrl(path, { token } = {}) {
  const headers = {};

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "GET",
    headers
  });

  const hasJson = response.headers.get("content-type")?.includes("application/json");
  if (!response.ok) {
    const payload = hasJson ? await response.json() : null;
    throw new Error(buildErrorMessage(response.status, payload));
  }

  const blob = await response.blob();
  return {
    objectUrl: window.URL.createObjectURL(blob),
    contentType: blob.type || response.headers.get("content-type") || "application/octet-stream",
    fileName: parseFileName(response.headers.get("content-disposition")) || "xem-trước"
  };
}
