const ROLE_LABELS = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  ADMIN: "Quản trị",
  GUEST: "Khách"
};

const LOAN_STATUS_LABELS = {
  PENDING: "Chờ xử lý",
  APPOINTMENT_SCHEDULED: "Đã lên lịch hẹn",
  APPROVED: "Đã duyệt",
  CONTRACTED: "Đã ký hợp đồng",
  DISBURSED: "Đã giải ngân",
  ACTIVE: "Đang vay",
  CLOSED: "Đã tất toán",
  REJECTED: "Từ chối"
};

const LOAN_PURPOSE_LABELS = {
  PERSONAL: "Tiêu dùng",
  HOME: "Mua nhà",
  EDUCATION: "Học tập",
  BUSINESS: "Kinh doanh"
};

const LOAN_TYPE_LABELS = {
  SECURED: "Vay thế chấp",
  UNSECURED: "Vay tín chấp"
};

const COLLATERAL_TYPE_LABELS = {
  VEHICLE_REGISTRATION: "Giấy tờ xe"
};

const LOAN_DOCUMENT_TYPE_LABELS = {
  VEHICLE_REGISTRATION: "Giấy tờ xe",
  LICENSE_PLATE_IMAGE: "Ảnh biển số xe",
  ID_CARD_FRONT: "CCCD mặt trước",
  ID_CARD_BACK: "CCCD mặt sau",
  FACE_CAPTURE: "Ảnh khuôn mặt hiện tại"
};

const DSS_RECOMMENDATION_LABELS = {
  APPROVE_RECOMMENDED: "Đề xuất duyệt",
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
  REJECT: "Từ chối"
};

const SECURED_PROCEDURE_STATUS_LABELS = {
  DRAFT: "Chưa xử lý",
  IN_PROGRESS: "Đang xử lý",
  COMPLETED: "Hoàn tất"
};

const PAYMENT_CONFIRMATION_STATUS_LABELS = {
  PENDING_REVIEW: "Chờ đối chiếu",
  CONFIRMED: "Đã xác nhận",
  REJECTED: "Bị từ chối"
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

export function labelLoanType(value) {
  return LOAN_TYPE_LABELS[value] || fallback(value);
}

export function labelCollateralType(value) {
  return COLLATERAL_TYPE_LABELS[value] || fallback(value);
}

export function labelLoanDocumentType(value) {
  return LOAN_DOCUMENT_TYPE_LABELS[value] || fallback(value);
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

export function labelPaymentConfirmationStatus(value) {
  return PAYMENT_CONFIRMATION_STATUS_LABELS[value] || fallback(value);
}

export function labelStaffAction(value) {
  return STAFF_ACTION_LABELS[value] || fallback(value);
}

export function labelSecuredProcedureStatus(value) {
  return SECURED_PROCEDURE_STATUS_LABELS[value] || fallback(value);
}
