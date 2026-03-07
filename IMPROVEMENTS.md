# Tóm tắt các cải thiện đã thực hiện

## 🔴 Bảo mật (3 sửa đổi)

### 1. JWT Filter - Sửa nuốt lỗi im lặng
**File:** `JwtAuthenticationFilter.java`
- **Trước:** `catch (Exception ignored)` — nuốt mọi lỗi, không biết token hết hạn hay bị giả mạo
- **Sau:** Log `ExpiredJwtException` riêng ở mức DEBUG, log lỗi JWT khác ở mức DEBUG. Thêm import `ExpiredJwtException` và `Logger`

### 2. Xử lý `AccessDeniedException` + Generic Exception
**File:** `ApiExceptionHandler.java`
- **Thêm:** `@ExceptionHandler(AccessDeniedException.class)` → trả 403 JSON thay vì lỗi HTML mặc định
- **Thêm:** `@ExceptionHandler(Exception.class)` → bắt mọi lỗi không xử lý, trả 500 JSON + log ERROR
- **Thêm:** Logger để ghi log lỗi không mong đợi

### 3. Docker-compose thêm JWT Secret
**File:** `docker-compose.yml`
- **Thêm:** `APP_JWT_SECRET: ${APP_JWT_SECRET:-}` vào backend environment để production PHẢI cấu hình secret riêng

---

## 🟡 Tính đúng đắn & Toàn vẹn dữ liệu (3 sửa đổi)

### 4. Sửa tính toán sai monthly due trong RepaymentService
**File:** `RepaymentService.java`
- **Trước:** `calculateExpectedMonthlyDue()` dùng `amount / termMonths` (chia đơn giản, không tính lãi suất)
- **Sau:** Tra cứu `LoanContract.monthlyPayment` từ `LoanContractService` (EMI đã tính lãi suất đúng). Chỉ fallback về phép chia khi chưa có contract
- **Thêm:** Inject `LoanContractService` vào constructor

### 5. Thêm `@Transactional` cho `CustomerProfileService.upsert()`
**File:** `CustomerProfileService.java`
- **Trước:** `upsert()` gọi `customerProfileRepository.upsert()` rồi `customerDebtService.recalculateAndSyncDti()` — 2 thao tác DB không atomic
- **Sau:** Thêm `@Transactional` đảm bảo cả 2 hoặc thành công hoặc rollback cùng nhau

### 6. Thêm database indexes cho `loan_requests`
**File mới:** `V5__add_loan_requests_indexes.sql`
- `idx_loan_requests_customer_id` — tăng tốc truy vấn theo khách hàng
- `idx_loan_requests_status` — tăng tốc lọc theo trạng thái
- `idx_loan_requests_customer_status` — composite index
- `idx_loan_repayments_loan_customer` — tăng tốc tra cứu thanh toán

---

## 🟢 Chất lượng code (4 sửa đổi)

### 7. Loại bỏ duplicate RowMapper trong LoanRepository
**File:** `LoanRepository.java`
- **Trước:** Copy/paste cùng 1 lambda `(rs, rowNum) -> new LoanRecord(...)` trong 4 phương thức
- **Sau:** Trích xuất thành `private static final RowMapper<LoanRecord> LOAN_ROW_MAPPER`. Đổi `toInstant()` thành `static`

### 8. Trích xuất named constants trong DecisionEngineService
**File:** `DecisionEngineService.java`
- **Trước:** Magic numbers khắp nơi: `0.23`, `0.18`, `780`, `700`, `620`, `75`, `55`, `45`, `35`, `60`
- **Sau:** 15 hằng số rõ tên:
  - Trọng số: `WEIGHT_DTI`, `WEIGHT_INCOME`, `WEIGHT_CREDIT_HISTORY`...
  - Ngưỡng điểm: `RANK_A_THRESHOLD`, `RANK_B_THRESHOLD`, `RANK_C_THRESHOLD`
  - Ngưỡng DTI: `DTI_LOW_THRESHOLD`, `DTI_EXTREME_THRESHOLD`, `DTI_REJECT_THRESHOLD`...
  - Phạm vi điểm: `SCORE_MIN`, `SCORE_MAX`, `SCORE_MULTIPLIER`

### 9. Thêm logging vào 5 service quan trọng
**Các file:** `AuthService`, `CustomerLoanService`, `StaffReviewService`, `RepaymentService`, `DecisionEngineService`
- Đăng ký: log userId, email, role
- Đăng nhập thành công/thất bại: log email
- Tạo hồ sơ vay: log loanId, customerId, amount
- Quyết định DSS: log customerId, score, rank, recommendation
- Quyết định nhân viên: log loanRequestId, staffUserId, action, newStatus
- Thanh toán: log loanRequestId, amountPaid, status, ratingDelta

---

## 🔵 Frontend (4 sửa đổi)

### 10. Thêm Error Boundary
**File mới:** `shared/components/ErrorBoundary.jsx`
- Class component với `getDerivedStateFromError` + `componentDidCatch`
- Hiển thị trang lỗi MUI đẹp với nút "Tải lại trang" và "Về trang chủ"
- Wrap toàn bộ app trong `main.jsx`

### 11. Lazy loading tất cả routes
**File:** `router.jsx`
- **Trước:** 12 page import eager (tất cả load cùng lúc ban đầu)
- **Sau:** Dùng `React.lazy()` + `Suspense` wrapper cho mỗi route
- **Kết quả:** Mỗi page là 1 chunk JS riêng biệt (verified qua build output)

### 12. Thay `window.confirm` bằng MUI ConfirmDialog
**File mới:** `shared/components/ConfirmDialog.jsx`
- Component MUI Dialog tái sử dụng: title, message, confirmText, cancelText
- **Cập nhật `AdminUsersPage.jsx`:** Thay `window.confirm` → ConfirmDialog cho xóa user
- **Cập nhật `CustomerProfilePage.jsx`:** Thay `window.confirm` → ConfirmDialog cho xóa khoản nợ

---

## ✅ Kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Backend unit tests (5 tests) | ✅ PASS |
| Backend compile (91 source files) | ✅ SUCCESS |
| Frontend build | ✅ SUCCESS (28 chunks, 2.51s) |
| IDE error check (tất cả files đã sửa) | ✅ No errors |

---

## 📋 Vấn đề còn tồn đọng (chưa sửa, ưu tiên thấp hơn)

1. **Rate limiting** trên `/api/auth/login` — cần thêm dependency hoặc filter phức tạp
2. **Token refresh** — cần cả backend endpoint mới + frontend interceptor
3. **Token trong localStorage** → nên chuyển sang httpOnly cookie (cần thay đổi lớn)
4. **Tách `CustomerPaymentsPage.jsx`** (507 dòng) thành sub-components
5. **`employmentStatus` free-text** → nên chuyển sang enum (cần Flyway migration + frontend update)
6. **Skeleton loading** thay vì spinner đơn giản
7. **Pagination cho admin users API**

