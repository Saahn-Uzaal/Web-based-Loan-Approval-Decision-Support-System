import { apiRequest, downloadFile } from "@/shared/api/http";
import { buildQueryString } from "@/shared/api/paged";

export function getMyPaymentsApi(token) {
  return getMyPaymentsPagedApi(token, { page: 0, size: 100 }).then(async (firstPage) => {
    const items = Array.isArray(firstPage?.items) ? [...firstPage.items] : [];
    const totalPages = Math.max(Number(firstPage?.totalPages || 0), 1);

    for (let page = 1; page < totalPages; page += 1) {
      const nextPage = await getMyPaymentsPagedApi(token, { page, size: 100 });
      items.push(...(Array.isArray(nextPage?.items) ? nextPage.items : []));
      if (nextPage?.last) {
        break;
      }
    }

    return {
      ...firstPage,
      items
    };
  });
}

export function getMyPaymentsPagedApi(token, { page = 0, size = 10 } = {}) {
  return apiRequest(`/api/customer/payments/paged${buildQueryString({ page, size })}`, {
    token
  });
}

export function createPaymentConfirmationApi(token, { loanRequestId, note, proof }) {
  const formData = new FormData();
  formData.append("loanRequestId", String(loanRequestId));
  if (note) {
    formData.append("note", note);
  }
  formData.append("proof", proof);
  formData.append("idempotencyKey", buildPaymentIdempotencyKey({ loanRequestId, note, proof }));

  return apiRequest("/api/customer/payments/confirmations", {
    method: "POST",
    token,
    body: formData
  });
}

export function cancelPaymentConfirmationApi(token, confirmationId) {
  return apiRequest(`/api/customer/payments/confirmations/${confirmationId}/cancel`, {
    method: "POST",
    token
  });
}

export function replacePaymentConfirmationApi(token, confirmationId, { note, proof }) {
  const formData = new FormData();
  if (note) {
    formData.append("note", note);
  }
  formData.append("proof", proof);

  return apiRequest(`/api/customer/payments/confirmations/${confirmationId}/replace`, {
    method: "POST",
    token,
    body: formData
  });
}

export function downloadPaymentProofApi(token, confirmationId, fileName) {
  return downloadFile(`/api/customer/payments/confirmations/${confirmationId}/proof`, {
    token,
    fileName
  });
}

function buildPaymentIdempotencyKey({ loanRequestId, note, proof }) {
  const payload = [
    loanRequestId,
    note?.trim() || "",
    proof?.name || "",
    proof?.size || 0,
    proof?.lastModified || 0,
    proof?.type || ""
  ].join("|");

  return `pc-${loanRequestId}-${hashString(payload)}`;
}

function hashString(value) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16);
}
