const ROLE_LABELS = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  ADMIN: "Quản trị",
  GUEST: "Khách"
};

const LOAN_STATUS_LABELS = {
  PENDING: "Chờ xử lý",
  WAITING_SUPERVISOR: "Chờ quản lý duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối"
};

const LOAN_PURPOSE_LABELS = {
  PERSONAL: "Tiêu dùng",
  HOME: "Mua nhà",
  EDUCATION: "Học tập",
  BUSINESS: "Kinh doanh"
};

const DSS_RECOMMENDATION_LABELS = {
  APPROVE_RECOMMENDED: "Đề xuất duyệt",
  ESCALATE_RECOMMENDED: "Đề xuất chuyển cấp cao hơn",
  REJECT_RECOMMENDED: "Đề xuất từ chối"
};

const RISK_RANK_LABELS = {
  A: "Hạng A",
  B: "Hạng B",
  C: "Hạng C",
  D: "Hạng D"
};

const CUSTOMER_SEGMENT_LABELS = {
  LOW_RISK_HIGH_VALUE: "Rủi ro thấp - Giá trị cao",
  LOW_RISK_LOW_VALUE: "Rủi ro thấp - Giá trị thấp",
  HIGH_RISK_HIGH_VALUE: "Rủi ro cao - Giá trị cao",
  HIGH_RISK_LOW_VALUE: "Rủi ro cao - Giá trị thấp"
};

const VERIFICATION_STATUS_LABELS = {
  PENDING: "Chờ xác minh",
  PASSED: "Đạt",
  FAILED: "Không đạt"
};

const RISK_LEVEL_LABELS = {
  LOW: "Thấp",
  MEDIUM: "Trung bình",
  HIGH: "Cao"
};

const CONTRACT_STATUS_LABELS = {
  ACTIVE: "Đang hiệu lực",
  CLOSED: "Đã đóng"
};

const REPAYMENT_STATUS_LABELS = {
  ON_TIME: "Đúng hạn",
  LATE: "Trễ hạn"
};

const STAFF_ACTION_LABELS = {
  APPROVE: "Duyệt",
  REJECT: "Từ chối",
  ESCALATE: "Chuyển cấp cao hơn"
};

function fallback(value) {
  return value || "-";
}

export function labelRole(value) {
  return ROLE_LABELS[value] || fallback(value);
}

export function labelLoanStatus(value) {
  return LOAN_STATUS_LABELS[value] || fallback(value);
}

export function labelLoanPurpose(value) {
  return LOAN_PURPOSE_LABELS[value] || fallback(value);
}

export function labelDssRecommendation(value) {
  return DSS_RECOMMENDATION_LABELS[value] || fallback(value);
}

export function labelRiskRank(value) {
  return RISK_RANK_LABELS[value] || fallback(value);
}

export function labelCustomerSegment(value) {
  return CUSTOMER_SEGMENT_LABELS[value] || fallback(value);
}

export function labelVerificationStatus(value) {
  return VERIFICATION_STATUS_LABELS[value] || fallback(value);
}

export function labelRiskLevel(value) {
  return RISK_LEVEL_LABELS[value] || fallback(value);
}

export function labelContractStatus(value) {
  return CONTRACT_STATUS_LABELS[value] || fallback(value);
}

export function labelRepaymentStatus(value) {
  return REPAYMENT_STATUS_LABELS[value] || fallback(value);
}

export function labelStaffAction(value) {
  return STAFF_ACTION_LABELS[value] || fallback(value);
}
