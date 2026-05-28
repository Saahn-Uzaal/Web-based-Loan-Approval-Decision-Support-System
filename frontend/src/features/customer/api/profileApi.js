import { apiRequest, downloadFile } from "@/shared/api/http";

export function getMyProfileApi(token) {
  return apiRequest("/api/customer/profile", {
    token
  });
}

export function upsertMyProfileApi(token, payload, payslipFile, idCardFrontFile, idCardBackFile) {
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
  if (idCardFrontFile) {
    formData.append("idCardFront", idCardFrontFile);
  }
  if (idCardBackFile) {
    formData.append("idCardBack", idCardBackFile);
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

export function downloadMyIdentityCardApi(token, side, fileName) {
  return downloadFile(`/api/customer/profile/id-card/${side}`, {
    token,
    fileName
  });
}
