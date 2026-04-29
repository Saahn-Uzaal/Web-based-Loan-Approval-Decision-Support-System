# Tóm tắt các cải thiện đã thực hiện

## 🔴 Bảo mật (3 sửa đổi)

### 1. Bộ lọc JWT - Sửa nuốt lỗi im lặng
**File:** `JwtAuthenticationFilter.java`
- **Trước:** `catch (Exception ignored)` — nuốt mọi lỗi, không biết token hết hạn hay bị giả mạo
- **Sau:** Ghi log `ExpiredJwtException` riêng ở mức DEBUG, ghi log lỗi JWT khác ở mức DEBUG. Thêm import `ExpiredJwtException` và `Logger`

### 2. Xử lý `AccessDeniedException` và ngoại lệ tổng quát
**File:** `ApiExceptionHandler.java`
- **Thêm:** `@ExceptionHandler(AccessDeniedException.class)` → trả 403 JSON thay vì lỗi HTML mặc định
- **Thêm:** `@ExceptionHandler(Exception.class)` → bắt mọi lỗi không xử lý, trả 500 JSON và ghi log ERROR
- **Thêm:** `Logger` để ghi log lỗi không mong đợi

### 3. `docker-compose` thêm bí mật JWT
**File:** `docker-compose.yml`
- **Thêm:** `APP_JWT_SECRET: ${APP_JWT_SECRET:-}` vào environment của backend để môi trường production PHẢI cấu hình secret riêng

---

## 🟡 Tính đúng đắn và toàn vẹn dữ liệu (3 sửa đổi)

### 4. Sửa tính toán sai số tiền đến hạn hàng tháng trong `RepaymentService`
**File:** `RepaymentService.java`
- **Trước:** `calculateExpectedMonthlyDue()` dùng `amount / termMonths` (chia đơn giản, không tính lãi suất)
- **Sau:** Tra cứu `LoanContract.monthlyPayment` từ `LoanContractService` (EMI đã tính lãi suất đúng). Chỉ dùng phép chia đơn giản khi chưa có hợp đồng
- **Thêm:** Inject `LoanContractService` vào constructor

### 5. Thêm `@Transactional` cho `CustomerProfileService.upsert()`
**File:** `CustomerProfileService.java`
- **Trước:** `upsert()` gọi `customerProfileRepository.upsert()` rồi `customerDebtService.recalculateAndSyncDti()` — 2 thao tác DB không atom
- **Sau:** Thêm `@Transactional` để đảm bảo cả 2 cùng thành công hoặc rollback cùng nhau

### 6. Thêm chỉ mục cơ sở dữ liệu cho `loan_requests`
**File mới:** `V5__add_loan_requests_indexes.sql`
- `idx_loan_requests_customer_id` — tăng tốc truy vấn theo khách hàng
- `idx_loan_requests_status` — tăng tốc lọc theo trạng thái
- `idx_loan_requests_customer_status` — chỉ mục tổng hợp
- `idx_loan_repayments_loan_customer` — tăng tốc tra cứu thanh toán

---

## 🟢 Chất lượng mã nguồn (4 sửa đổi)

### 7. Loại bỏ `RowMapper` trùng lặp trong `LoanRepository`
**File:** `LoanRepository.java`
- **Trước:** Copy/paste cùng một lambda `(rs, rowNum) -> new LoanRecord(...)` trong 4 phương thức
- **Sau:** Trích xuất thành `private static final RowMapper<LoanRecord> LOAN_ROW_MAPPER`. Đổi `toInstant()` thành `static`

### 8. Trích xuất hằng số có tên rõ ràng trong `DecisionEngineService`
**File:** `DecisionEngineService.java`
- **Trước:** Magic number xuất hiện khắp nơi: `0.23`, `0.18`, `780`, `700`, `620`, `75`, `55`, `45`, `35`, `60`
- **Sau:** 15 hằng số rõ nghĩa:
  - Trọng số: `WEIGHT_DTI`, `WEIGHT_INCOME`, `WEIGHT_CREDIT_HISTORY`...
  - Ngưỡng điểm: `RANK_A_THRESHOLD`, `RANK_B_THRESHOLD`, `RANK_C_THRESHOLD`
  - Ngưỡng DTI: `DTI_LOW_THRESHOLD`, `DTI_EXTREME_THRESHOLD`, `DTI_REJECT_THRESHOLD`...
  - Phạm vi điểm: `SCORE_MIN`, `SCORE_MAX`, `SCORE_MULTIPLIER`

### 9. Thêm ghi log vào 5 service quan trọng
**Các file:** `AuthService`, `CustomerLoanService`, `StaffReviewService`, `RepaymentService`, `DecisionEngineService`
- Đăng ký: log `userId`, `email`, `role`
- Đăng nhập thành công/thất bại: log `email`
- Tạo hồ sơ vay: log `loanId`, `customerId`, `amount`
- Quyết định DSS: log `customerId`, `score`, `rank`, `recommendation`
- Quyết định nhân viên: log `loanRequestId`, `staffUserId`, `action`, `newStatus`
- Thanh toán: log `loanRequestId`, `amountPaid`, `status`, `ratingDelta`

---

## 🔵 Frontend (4 sửa đổi)

### 10. Thêm ranh giới bắt lỗi giao diện
**File mới:** `shared/components/ErrorBoundary.jsx`
- Class component với `getDerivedStateFromError` và `componentDidCatch`
- Hiển thị trang lỗi MUI với nút "Tải lại trang" và "Về trang chủ"
- Bao toàn bộ ứng dụng trong `main.jsx`

### 11. Tải lười cho toàn bộ route
**File:** `router.jsx`
- **Trước:** 12 trang được import eager (tất cả tải cùng lúc ban đầu)
- **Sau:** Dùng `React.lazy()` + `Suspense` bao cho mỗi route
- **Kết quả:** Mỗi trang là một khối JS riêng (xác nhận qua build output)

### 12. Thay `window.confirm` bằng hộp thoại xác nhận MUI
**File mới:** `shared/components/ConfirmDialog.jsx`
- Component `Dialog` của MUI có thể tái sử dụng: `title`, `message`, `confirmText`, `cancelText`
- **Cập nhật `AdminUsersPage.jsx`:** Thay `window.confirm` → `ConfirmDialog` cho thao tác xóa người dùng
- **Cập nhật `CustomerProfilePage.jsx`:** Thay `window.confirm` → `ConfirmDialog` cho thao tác xóa khoản nợ

---

## ✅ Kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Backend unit tests (5 tests) | ✅ PASS |
| Backend compile (91 source files) | ✅ THÀNH CÔNG |
| Frontend build | ✅ THÀNH CÔNG (28 chunks, 2.51s) |
| IDE error check (tất cả file đã sửa) | ✅ Không có lỗi |

---

## 📋 Vấn đề còn tồn đọng (chưa sửa, ưu tiên thấp hơn)

1. **Giới hạn tốc độ** trên `/api/auth/login` — cần thêm dependency hoặc filter phức tạp
2. **Làm mới token** — cần cả endpoint backend mới và interceptor frontend
3. **Token trong `localStorage`** → nên chuyển sang cookie `httpOnly` (cần thay đổi lớn)
4. **Tách `CustomerPaymentsPage.jsx`** (507 dòng) thành các thành phần con
5. **`employmentStatus` nhập tự do** → nên chuyển sang enum (cần Flyway migration và cập nhật frontend)
6. **Hiển thị khung tải dữ liệu** thay vì spinner đơn giản
7. **Phân trang cho API quản lý người dùng của quản trị**
