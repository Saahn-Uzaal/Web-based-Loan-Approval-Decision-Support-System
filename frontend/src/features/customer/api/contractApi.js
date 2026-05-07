import { apiRequest } from "@/shared/api/http";

export function getMyLoanContractApi(token, loanRequestId) {
  return apiRequest(`/api/customer/contracts/${loanRequestId}`, {
    token
  });
}
