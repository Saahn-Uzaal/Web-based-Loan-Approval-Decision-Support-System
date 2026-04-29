import { apiRequest } from "@/shared/api/http";

export function getMyInformationVerificationApi(token) {
  return apiRequest("/api/customer/information-verification", {
    token
  });
}
