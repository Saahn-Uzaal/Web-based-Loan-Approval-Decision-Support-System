const ROLE_LABELS = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  ADMIN: "Quản trị",
  GUEST: "Khách"
};

const LOAN_STATUS_LABELS = {
  DRAFT: "Bản nháp",
  NEEDS_MORE_INFO: "Cần bổ sung hồ sơ",
  WITHDRAWN: "Đã rút hồ sơ",
  PENDING: "Chờ xử lý",
  APPOINTMENT_SCHEDULED: "Đã lên lịch hẹn",
  APPROVED: "Đã duyệt",
  CONTRACTED: "Đã ký hợp đồng",
  ACTIVE: "Đang vay",
  OVERDUE: "Quá hạn",
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
  FACE_CAPTURE: "Ảnh khuôn mặt hiện tại",
  SUPPLEMENTAL_DOCUMENT: "Giấy tờ bổ sung"
};

const DSS_RECOMMENDATION_LABELS = {
  APPROVE_RECOMMENDED: "Đề xuất duyệt",
  MANUAL_REVIEW_RECOMMENDED: "Cần thẩm định thủ công",
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
  PENDING_ACCEPTANCE: "Chờ khách hàng chấp nhận",
  ACTIVE: "Đang hiệu lực",
  CLOSED: "Đã đóng",
  CANCELLED: "Đã hủy"
};

const EMPLOYMENT_STATUS_LABELS = {
  EMPLOYED: "Nhân viên chính thức",
  SELF_EMPLOYED: "Tự kinh doanh / tự do",
  BUSINESS_OWNER: "Chủ hộ kinh doanh / doanh nghiệp",
  PART_TIME: "Bán thời gian",
  CONTRACTOR: "Theo hợp đồng",
  UNEMPLOYED: "Thất nghiệp",
  STUDENT: "Sinh viên",
  RETIRED: "Đã nghỉ hưu",
  OTHER: "Khác"
};

const REPAYMENT_STATUS_LABELS = {
  EARLY: "Trả sớm",
  ON_TIME: "Đúng hạn",
  LATE: "Trễ hạn"
};

const STAFF_ACTION_LABELS = {
  REQUEST_MORE_INFO: "Yêu cầu bổ sung",
  APPROVE: "Duyệt",
  REJECT: "Từ chối"
};

const SECURED_PROCEDURE_STATUS_LABELS = {
  DRAFT: "Chưa xử lý",
  IN_PROGRESS: "Đang xử lý",
  COMPLETED: "Hoàn tất"
};

const APPOINTMENT_STATUS_LABELS = {
  SCHEDULED: "Đã lên lịch",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Khách vắng mặt"
};

const PAYMENT_CONFIRMATION_STATUS_LABELS = {
  PENDING_REVIEW: "Chờ đối chiếu",
  CANCELLED_BY_CUSTOMER: "Đã hủy bởi khách hàng",
  CONFIRMED: "Đã xác nhận",
  REJECTED: "Bị từ chối"
};

const CREDIT_BUREAU_STATUS_LABELS = {
  NO_HIT: "Chưa có quan hệ tín dụng",
  CLEAR: "Đang trả tốt",
  WATCHLIST: "Cần rà soát",
  BAD_DEBT: "Nợ xấu",
  FRAUD_SUSPECT: "Nghi ngờ gian lận"
};

const CREDIT_LOAN_SOURCE_TYPE_LABELS = {
  INTERNAL_SYSTEM: "Nội bộ từ app",
  PARTNER_NETWORK: "Đối tác / tổ chức khác",
  CUSTOMER_DECLARED: "Khách hàng tự khai"
};

const CREDIT_LOAN_ACCOUNT_STATUS_LABELS = {
  CURRENT: "Đang trả bình thường",
  OVERDUE: "Đang quá hạn",
  BAD_DEBT: "Nợ xấu",
  CLOSED: "Đã tất toán"
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

export function labelEmploymentStatus(value) {
  return EMPLOYMENT_STATUS_LABELS[value] || fallback(value);
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

export function labelAppointmentStatus(value) {
  return APPOINTMENT_STATUS_LABELS[value] || fallback(value);
}

export function labelCreditBureauStatus(value) {
  return CREDIT_BUREAU_STATUS_LABELS[value] || fallback(value);
}

export function labelCreditLoanSourceType(value) {
  return CREDIT_LOAN_SOURCE_TYPE_LABELS[value] || fallback(value);
}

export function labelCreditLoanAccountStatus(value) {
  return CREDIT_LOAN_ACCOUNT_STATUS_LABELS[value] || fallback(value);
}
