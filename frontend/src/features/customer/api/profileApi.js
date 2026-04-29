import { apiRequest, downloadFile } from "@/shared/api/http";

export function getMyProfileApi(token) {
  return apiRequest("/api/customer/profile", {
    token
  });
}

export function upsertMyProfileApi(token, payload, payslipFile) {
  const formData = new FormData();

  formData.append(
    "profile",
    new Blob([JSON.stringify(payload)], {
      type: "application/json"
    })
  );

  if (payslipFile) {
    formData.append("payslip", payslipFile);
  }

  return apiRequest("/api/customer/profile", {
    method: "PUT",
    token,
    body: formData
  });
}

export function downloadMyPayslipApi(token, fileName) {
  return downloadFile("/api/customer/profile/payslip", {
    token,
    fileName
  });
}
