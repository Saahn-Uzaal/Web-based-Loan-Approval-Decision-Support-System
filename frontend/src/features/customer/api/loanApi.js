import { apiRequest, downloadFile } from "@/shared/api/http";
import { buildQueryString, fetchAllPageResponse } from "@/shared/api/paged";

function buildLoanRequestBody(payload, files = {}, options = {}) {
  const hasFiles = Object.values(files).some(Boolean);
  if (hasFiles || options.forceMultipart) {
    const formData = new FormData();
    formData.append(
      "loan",
      new Blob([JSON.stringify(payload)], {
        type: "application/json"
      })
    );

    Object.entries(files).forEach(([key, file]) => {
      if (file) {
        formData.append(key, file);
      }
    });

    return formData;
  }

  return payload;
}

function mutateLoan(path, { method = "POST", token, payload, files, forceMultipart = false } = {}) {
  return apiRequest(path, {
    method,
    token,
    body: buildLoanRequestBody(payload, files, { forceMultipart })
  });
}

export function createLoanApi(token, payload, files = {}) {
  return mutateLoan("/api/customer/loans", {
    method: "POST",
    token,
    payload,
    files,
    forceMultipart: true
  });
}

export function createLoanDraftApi(token, payload, files = {}) {
  return mutateLoan("/api/customer/loans/drafts", {
    method: "POST",
    token,
    payload,
    files
  });
}

export function updateLoanDraftApi(token, id, payload, files = {}) {
  return mutateLoan(`/api/customer/loans/${id}/draft`, {
    method: "PUT",
    token,
    payload,
    files
  });
}

export function submitLoanDraftApi(token, id, payload, files = {}) {
  return mutateLoan(`/api/customer/loans/${id}/submit`, {
    method: "POST",
    token,
    payload,
    files,
    forceMultipart: true
  });
}

export function getMyLoansApi(token) {
  return fetchAllPageResponse("/api/customer/loans/paged", { token })
    .then((response) => (Array.isArray(response?.content) ? response.content : []));
}

export function getMyLoansPagedApi(token, { page = 0, size = 10 } = {}) {
  return apiRequest(`/api/customer/loans/paged${buildQueryString({ page, size })}`, {
    token
  });
}

export function getLoanDetailApi(token, id) {
  return apiRequest(`/api/customer/loans/${id}`, {
    token
  });
}

export function getLoanAcceptanceChallengeApi(token, id) {
  return apiRequest(`/api/customer/loans/${id}/acceptance-challenge`, {
    token
  });
}

export function acceptLoanApi(token, id, payload) {
  return apiRequest(`/api/customer/loans/${id}/accept`, {
    method: "POST",
    token,
    body: payload
  });
}

export function withdrawLoanApi(token, id) {
  return apiRequest(`/api/customer/loans/${id}/withdraw`, {
    method: "POST",
    token
  });
}

export function resubmitLoanApi(token, id, files = {}) {
  const supplementalDocuments = Array.isArray(files.supplementalDocuments)
    ? files.supplementalDocuments.filter(Boolean)
    : [];
  const hasFiles = supplementalDocuments.length > 0;
  if (hasFiles) {
    const formData = new FormData();
    supplementalDocuments.forEach((file) => {
      formData.append("supplementalDocuments", file);
    });
    return apiRequest(`/api/customer/loans/${id}/resubmit`, {
      method: "POST",
      token,
      body: formData
    });
  }

  return apiRequest(`/api/customer/loans/${id}/resubmit`, {
    method: "POST",
    token
  });
}

export function downloadLoanDocumentApi(token, loanId, documentId, fileName) {
  return downloadFile(`/api/customer/loans/${loanId}/documents/${documentId}`, {
    token,
    fileName
  });
}
