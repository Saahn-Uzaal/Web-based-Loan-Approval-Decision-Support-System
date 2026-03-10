# Web-based Loan Approval Decision Support System

Hệ thống web hỗ trợ quyết định phê duyệt khoản vay, bao gồm:
- Quy trình nộp hồ sơ vay của khách hàng.
- DSS chấm điểm tín dụng và khuyến nghị phê duyệt.
- Xác minh KYC / AML / Fraud cho nhân viên thẩm định.
- Quy trình duyệt, từ chối hoặc chuyển cấp phê duyệt.
- Sinh hợp đồng vay, theo dõi thanh toán và payment rating.
- Compliance / audit log cho các hành động quan trọng.

## 1. Tổng quan nghiệp vụ

Luồng xử lý chính:
1. Khách hàng tạo hoặc cập nhật hồ sơ tài chính.
2. Khách hàng khai báo danh sách các khoản nợ hiện tại.
3. Hệ thống tự tính DTI và DSCR từ thu nhập và các khoản nợ.
4. Khách hàng tạo hồ sơ vay.
5. DSS chấm điểm tín dụng, xếp hạng rủi ro và sinh khuyến nghị.
6. Hệ thống đánh giá Risk Assessment theo các nhóm credit / fraud / operational.
7. Nhân viên xác minh hồ sơ, KYC, AML, income verification và fraud.
8. Nhân viên ra quyết định cuối: `APPROVE`, `REJECT`, `ESCALATE`.
9. Nếu được duyệt, hệ thống sinh hợp đồng vay và lịch trả nợ.
10. Khách hàng ghi nhận thanh toán, hệ thống cập nhật dư nợ và payment rating.

## 2. Vai trò chính

### `CUSTOMER`
- Cập nhật hồ sơ cá nhân và tài chính.
- Quản lý các khoản nợ đang có.
- Tạo và theo dõi hồ sơ vay.
- Xem hợp đồng và thanh toán khoản vay đã duyệt.

### `STAFF`
- Xem hàng đợi thẩm định.
- Cập nhật trạng thái xác minh KYC / AML / Fraud / Income.
- Xem chi tiết hồ sơ: profile, DSS, risk, verification, contract, audit.
- Gửi quyết định xử lý hồ sơ vay.

### `ADMIN`
- Quản lý tài khoản customer và staff.
- Xóa người dùng cùng dữ liệu nghiệp vụ liên quan theo logic hệ thống.

## 3. Điểm nổi bật đã hoàn thành

- JWT stateless authentication + RBAC theo vai trò.
- DSS có giải thích (`explanation`) và rule khuyến nghị rõ ràng.
- Risk assessment tổng hợp thành `LOW`, `MEDIUM`, `HIGH`.
- Module xác minh nghiệp vụ: document, identity, income, KYC, AML, fraud.
- Module hợp đồng vay với công thức EMI.
- Module compliance audit log.
- DTI không nhập tay trên UI, đồng bộ từ danh sách khoản nợ.
- Trang thanh toán có tính nợ còn lại và chặn thanh toán khi khoản vay đã tất toán.
- Frontend có landing page public trước bước login và dashboard protected theo vai trò.
- Login page đã được làm lại để đồng bộ với landing page và luồng protected route.

## 4. Kiến trúc kỹ thuật

- Frontend: React 18 + Vite + MUI + React Router.
- Backend: Java 17 + Spring Boot 3.5 + Spring Security + Validation + JDBC.
- Database: MySQL 8.4.
- Migration: Flyway (`V1` -> `V5`).
- Container: Docker Compose (`mysql`, `backend`, `frontend`).

## 5. Cấu trúc thư mục

```text
.
|- backend
|  |- src/main/java/com/loanapproval/dss
|  |  |- auth, security, admin
|  |  |- profile, debt, loan, dss
|  |  |- verification, risk, contract, repayment
|  |  `- compliance, shared, health, staff
|  |- src/main/resources/db/migration
|  |- Dockerfile
|  `- pom.xml
|- frontend
|  |- src
|  |  |- app
|  |  |- features
|  |  |  |- auth, admin, customer, staff
|  |  `- shared
|  |- Dockerfile
|  `- vite.config.js
|- docker-compose.yml
|- TESTING.md
`- .env.example
```

## 6. Dữ liệu và schema chính

Các bảng cốt lõi:
- `users`
- `customer_profiles`
- `customer_debts`
- `loan_requests`
- `dss_results`
- `risk_assessments`
- `customer_verifications`
- `loan_contracts`
- `loan_repayments`
- `decision_audits`
- `compliance_audit_logs`

Migration hiện có:
- `V1__init_schema.sql`
- `V2__add_admin_role.sql`
- `V3__add_customer_repayments_and_rating.sql`
- `V4__add_blueprint_business_modules.sql`
- `V5__add_loan_requests_indexes.sql`

## 7. DSS và Risk logic

Đầu vào DSS:
- Thu nhập tháng, DTI, tổng nợ hiện tại.
- Tuổi, thời gian làm việc, điểm lịch sử tín dụng.
- Mục đích vay, giá trị tài sản đảm bảo.
- Payment rating.
- Trạng thái xác minh: KYC / AML / Income / Fraud.

Đầu ra DSS:
- `creditScore` (`300-850`)
- `riskRank` (`A`, `B`, `C`, `D`)
- `customerSegment`
- `recommendation` (`APPROVE_RECOMMENDED`, `ESCALATE_RECOMMENDED`, `REJECT_RECOMMENDED`)
- `explanation`

Rule chính:
- `A + low DTI` -> đề xuất duyệt.
- `D` hoặc hard-fail compliance -> đề xuất từ chối.
- `B/C + borderline` -> đề xuất chuyển cấp.

Risk Assessment:
- Chấm 3 nhóm rủi ro: `credit`, `fraud`, `operational`.
- Tổng hợp thành `overallRiskLevel`: `LOW`, `MEDIUM`, `HIGH`.
- Lưu snapshot tại `risk_assessments`.

## 8. DTI, Debt và Profile

- Khách hàng không nhập DTI thủ công trên UI.
- Khách hàng khai báo danh sách khoản nợ gồm:
  - tên khoản nợ
  - trả hàng tháng
  - dư nợ còn lại
  - đơn vị cho vay
- Hệ thống tự tính:
  - `totalMonthlyDebt`
  - `debtToIncomeRatio`
  - `debtServiceCoverageRatio`
- Khi thêm hoặc xóa khoản nợ, DTI trong hồ sơ được đồng bộ lại tự động.

## 9. Thanh toán, nợ còn lại và rating

Nghiệp vụ thanh toán:
- Chỉ cho phép thanh toán với khoản vay `APPROVED`.
- Backend tính số tiền đến hạn theo hợp đồng vay; fallback về phép chia đơn giản nếu chưa có contract.
- Backend chặn thanh toán nếu khoản vay đã trả hết.
- Frontend hiển thị:
  - nợ còn lại hiện tại
  - nợ còn lại sau khi nhập số tiền trả
  - lịch sử thanh toán
- Nếu tất toán, khoản vay sẽ biến mất khỏi danh sách có thể thanh toán.

Rating:
- Trả đủ hoặc vượt mức đến hạn -> `ON_TIME`, cộng điểm.
- Trả thiếu -> `LATE`, trừ điểm.
- Điểm thanh toán lưu tại `customer_profiles.payment_rating`.

## 10. Compliance và bảo mật

- JWT stateless authentication.
- Phân quyền bằng `@PreAuthorize`.
- CORS cho môi trường local.
- BCrypt password hashing.
- Audit log cho các hành động:
  - cập nhật xác minh
  - đánh giá hồ sơ
  - quyết định của staff
  - tạo hợp đồng

## 11. Frontend routing

Public routes:
- `/`: landing page chung trước bước đăng nhập.
- `/login`: trang đăng nhập / đăng ký.

Protected routes:
- `/dashboard`: trang chủ theo vai trò sau khi đăng nhập.
- `/customer/*`, `/staff/*`, `/admin/*`: các màn hình nghiệp vụ tương ứng.

Luồng hiện tại:
1. Người dùng vào landing page tại `/`.
2. Chọn CTA để vào `/login`.
3. Sau khi xác thực thành công, hệ thống điều hướng sang `/dashboard` hoặc route protected được yêu cầu trước đó.

## 12. API chính

Auth:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

Admin:
- `GET /api/admin/users?role=CUSTOMER|STAFF`
- `DELETE /api/admin/users/{id}`

Customer Profile + Debt:
- `GET /api/customer/profile`
- `PUT /api/customer/profile`
- `GET /api/customer/debts`
- `GET /api/customer/debts/metrics`
- `POST /api/customer/debts`
- `DELETE /api/customer/debts/{id}`

Customer Loan + Contract:
- `POST /api/customer/loans`
- `GET /api/customer/loans`
- `GET /api/customer/loans/paged?page=0&size=10`
- `GET /api/customer/loans/{id}`
- `GET /api/customer/contracts/{loanRequestId}`

Customer Payment:
- `GET /api/customer/payments`
- `GET /api/customer/payments/paged?page=0&size=10`
- `POST /api/customer/payments`
- Body: `{ "loanRequestId", "amountPaid", "dueDate", "paidAt?", "note?" }`

Staff:
- `GET /api/staff/requests?status=PENDING|WAITING_SUPERVISOR`
- `GET /api/staff/requests/paged?page=0&size=10&status=...`
- `GET /api/staff/requests/{id}`
- `POST /api/staff/requests/{id}/decision`
- Body: `{ "action": "APPROVE|REJECT|ESCALATE", "reason": "..." }`

Verification:
- `GET /api/staff/verifications/{customerId}`
- `PUT /api/staff/verifications/{customerId}`

Health:
- `GET /api/health`
- `GET /actuator/health`
- `GET /actuator/info`

## 13. Chạy nhanh bằng Docker

1. Tạo file môi trường:

```powershell
Copy-Item .env.example .env
```

2. Build và chạy:

```powershell
docker compose up --build -d
```

3. Truy cập:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- MySQL: `localhost:3306` hoặc theo `.env`

4. Tài khoản admin mặc định:
- Email: `admin@gmail.com`
- Password: `123456`

Lưu ý:
- Nếu database cũ đã từng seed `admin@loan.local`, backend sẽ tự migrate sang admin mặc định mới nếu email mới chưa tồn tại.

## 14. Chạy local không Docker

Backend:

```powershell
cd backend
mvn spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

## 15. Build và kiểm thử

Backend build:

```powershell
cd backend
mvn -B -DskipTests clean package
```

Frontend build:

```powershell
cd frontend
npm run build
```

Backend unit test:

```powershell
cd backend
mvn test
```

Kịch bản test chi tiết xem thêm tại `TESTING.md`.

## 16. Ghi chú phát triển

- Frontend tổ chức theo hướng feature-based (`features/*`, `shared/*`).
- API frontend dùng `VITE_API_BASE_URL`.
- Tiền tệ trên UI được chuẩn hóa theo `vi-VN`.
- Dự án ưu tiên luồng nghiệp vụ tín dụng thực tế hơn CRUD thuần túy.
