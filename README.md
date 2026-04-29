# Hệ thống hỗ trợ quyết định phê duyệt khoản vay trên nền web

Hệ thống web hỗ trợ quyết định phê duyệt khoản vay, bao gồm:
- Quy trình nộp hồ sơ vay của khách hàng.
- DSS chấm điểm tín dụng và khuyến nghị phê duyệt.
- Xác minh KYC / AML / gian lận cho nhân viên thẩm định.
- Quy trình duyệt hoặc từ chối hồ sơ vay.
- Sinh hợp đồng vay, theo dõi thanh toán và điểm tín nhiệm thanh toán.
- Nhật ký tuân thủ / kiểm toán cho các hành động quan trọng.

Tài liệu cài đặt nhanh trên máy mới: [GETTING_STARTED.md](GETTING_STARTED.md)

## 1. Tổng quan nghiệp vụ

Luồng xử lý chính:
1. Khách hàng tạo hoặc cập nhật hồ sơ tài chính.
2. Khách hàng khai báo danh sách các khoản nợ hiện tại.
3. Hệ thống tự tính DTI và DSCR từ thu nhập và các khoản nợ.
4. Khách hàng tạo hồ sơ vay.
5. DSS chấm điểm tín dụng, xếp hạng rủi ro và sinh khuyến nghị.
6. Hệ thống đánh giá rủi ro theo các nhóm tín dụng / gian lận / vận hành.
7. Nhân viên xác minh hồ sơ, KYC, AML, xác minh thu nhập và gian lận.
8. Nhân viên ra quyết định cuối: `APPROVE` hoặc `REJECT`.
9. Nếu được duyệt, hệ thống sinh hợp đồng vay và lịch trả nợ.
10. Khách hàng ghi nhận thanh toán, hệ thống cập nhật dư nợ và điểm tín nhiệm thanh toán.

## 2. Vai trò chính

### `CUSTOMER`
- Cập nhật hồ sơ cá nhân và tài chính.
- Quản lý các khoản nợ đang có.
- Tạo và theo dõi hồ sơ vay.
- Xem hợp đồng và thanh toán khoản vay đã duyệt.

### `STAFF`
- Xem hàng đợi thẩm định.
- Cập nhật trạng thái xác minh KYC / AML / Gian lận / Thu nhập.
- Xem chi tiết hồ sơ: hồ sơ khách hàng, DSS, rủi ro, xác minh, hợp đồng, kiểm toán.
- Gửi quyết định xử lý hồ sơ vay.

### `ADMIN`
- Quản lý tài khoản khách hàng và nhân viên.
- Xóa người dùng cùng dữ liệu nghiệp vụ liên quan theo logic hệ thống.

## 3. Điểm nổi bật đã hoàn thành

- Xác thực JWT phi trạng thái + RBAC theo vai trò.
- DSS có giải thích (`explanation`) và quy tắc khuyến nghị rõ ràng.
- Đánh giá rủi ro tổng hợp thành `LOW`, `MEDIUM`, `HIGH`.
- Mô-đun xác minh nghiệp vụ: giấy tờ, định danh, thu nhập, KYC, AML, gian lận.
- Mô-đun hợp đồng vay với công thức EMI.
- Mô-đun nhật ký kiểm toán tuân thủ.
- DTI không nhập tay trên giao diện, đồng bộ từ danh sách khoản nợ.
- Trang thanh toán có tính nợ còn lại và chặn thanh toán khi khoản vay đã tất toán.
- Frontend có trang giới thiệu công khai trước bước đăng nhập và bảng điều khiển được bảo vệ theo vai trò.
- Trang đăng nhập đã được làm lại để đồng bộ với trang giới thiệu và luồng điều hướng được bảo vệ.

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

## 7. DSS và logic rủi ro

Đầu vào DSS:
- Thu nhập tháng, DTI, tổng nợ hiện tại.
- Tuổi, thời gian làm việc, điểm lịch sử tín dụng.
- Mục đích vay, giá trị tài sản đảm bảo.
- Điểm tín nhiệm thanh toán.
- Trạng thái xác minh: KYC / AML / Thu nhập / Gian lận.

Đầu ra DSS:
- `creditScore` (`300-850`)
- `riskRank` (`A`, `B`, `C`, `D`)
- `customerSegment`
- `recommendation` (`APPROVE_RECOMMENDED`, `ESCALATE_RECOMMENDED`, `REJECT_RECOMMENDED`)
- `explanation`

Quy tắc chính:
- `A + low DTI` -> đề xuất duyệt.
- `D` hoặc không đạt tuân thủ nghiêm trọng -> đề xuất từ chối.
- `B/C + borderline` -> cần xem xét thêm trước khi ra quyết định.

Đánh giá rủi ro:
- Chấm 3 nhóm rủi ro: `tín dụng`, `gian lận`, `vận hành`.
- Tổng hợp thành `overallRiskLevel`: `LOW`, `MEDIUM`, `HIGH`.
- Lưu snapshot tại `risk_assessments`.

## 8. DTI, khoản nợ và hồ sơ

- Khách hàng không nhập DTI thủ công trên giao diện.
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

## 9. Thanh toán, nợ còn lại và điểm tín nhiệm

Nghiệp vụ thanh toán:
- Chỉ cho phép thanh toán với khoản vay `APPROVED`.
- Backend tính số tiền đến hạn theo hợp đồng vay; dùng phép chia đơn giản khi chưa có hợp đồng.
- Backend chặn thanh toán nếu khoản vay đã trả hết.
- Frontend hiển thị:
  - nợ còn lại hiện tại
  - nợ còn lại sau khi nhập số tiền trả
  - lịch sử thanh toán
- Nếu tất toán, khoản vay sẽ biến mất khỏi danh sách có thể thanh toán.

Điểm tín nhiệm:
- Trả đủ hoặc vượt mức đến hạn -> `ON_TIME`, cộng điểm.
- Trả thiếu -> `LATE`, trừ điểm.
- Điểm thanh toán lưu tại `customer_profiles.payment_rating`.

## 10. Tuân thủ và bảo mật

- Xác thực JWT phi trạng thái.
- Phân quyền bằng `@PreAuthorize`.
- CORS cho môi trường local.
- Băm mật khẩu bằng BCrypt.
- Nhật ký kiểm toán cho các hành động:
  - cập nhật xác minh
  - đánh giá hồ sơ
  - quyết định của nhân viên
  - tạo hợp đồng

## 11. Điều hướng frontend

Các đường dẫn công khai:
- `/`: trang giới thiệu chung trước bước đăng nhập.
- `/login`: trang đăng nhập / đăng ký.

Các đường dẫn được bảo vệ:
- `/dashboard`: trang chủ theo vai trò sau khi đăng nhập.
- `/customer/*`, `/staff/*`, `/admin/*`: các màn hình nghiệp vụ tương ứng.

Luồng hiện tại:
1. Người dùng vào trang giới thiệu tại `/`.
2. Chọn CTA để vào `/login`.
3. Sau khi xác thực thành công, hệ thống điều hướng sang `/dashboard` hoặc đường dẫn được bảo vệ đã yêu cầu trước đó.

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
- Body: `{ "action": "APPROVE|REJECT", "scheduledAt?": "...", "appointmentNote?": "..." }`

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
- Tiền tệ trên giao diện được chuẩn hóa theo `vi-VN`.
- Dự án ưu tiên luồng nghiệp vụ tín dụng thực tế hơn CRUD thuần túy.
