# Web-based Loan Approval Decision Support System

Ứng dụng web hỗ trợ thẩm định và vận hành khoản vay. Hệ thống mô phỏng đầy đủ các bước từ khách hàng khai báo hồ sơ, DSS chấm điểm và khuyến nghị, nhân viên thẩm định, xử lý thủ tục thế chấp, ký hợp đồng, giải ngân, đến xác nhận thanh toán và theo dõi điểm tín nhiệm.

## Tính năng chính

- Đăng ký, đăng nhập bằng JWT và phân quyền theo vai trò `CUSTOMER`, `STAFF`, `ADMIN`.
- Khách hàng cập nhật hồ sơ cá nhân, thu nhập, phiếu lương và danh sách khoản nợ.
- Nhân viên xác minh thông tin khách hàng trước khi khách được tạo hồ sơ vay mới.
- Khách hàng tạo hồ sơ vay tín chấp hoặc vay thế chấp kèm chứng từ.
- DSS tính điểm tín dụng, DTI, DSCR, xếp hạng rủi ro và khuyến nghị duyệt/từ chối/xem xét thêm.
- Hàng đợi thẩm định tách riêng với hàng đợi vận hành khoản vay.
- Luồng vay thế chấp có lịch hẹn, mẫu xử lý thủ tục, tái thẩm định DSS và hợp đồng không lệch điều khoản.
- Hợp đồng và lịch trả nợ dùng điều khoản đã được DSS xác nhận lại.
- Khách hàng gửi biên lai thanh toán, nhân viên đối chiếu rồi hệ thống mới ghi nhận thanh toán.
- Thông báo trong ứng dụng cho các sự kiện nghiệp vụ quan trọng.
- Giao diện customer/staff hiển thị lỗi ngay tại field bằng viền đỏ và helper text.

## Vai trò

`CUSTOMER`

- Quản lý hồ sơ cá nhân, thu nhập, phiếu lương và khoản nợ.
- Theo dõi trạng thái xác minh thông tin.
- Tạo hồ sơ vay tín chấp hoặc thế chấp.
- Xem hợp đồng, lịch trả nợ và gửi biên lai xác nhận thanh toán.

`STAFF`

- Xác minh hồ sơ khách hàng và thu nhập đã đối chiếu.
- Xem hàng đợi thẩm định chỉ gồm hồ sơ đang chờ xử lý.
- Duyệt, từ chối hoặc lên lịch hẹn cho khoản vay thế chấp.
- Xử lý thủ tục thế chấp, hoàn tất hợp đồng, giải ngân.
- Đối chiếu biên lai thanh toán và ghi nhận kết quả đúng hạn/trễ hạn.

`ADMIN`

- Quản lý tài khoản người dùng.
- Tạo tài khoản nhân viên/khách hàng.
- Xóa người dùng cùng dữ liệu nghiệp vụ liên quan theo logic hệ thống.

## Luồng nghiệp vụ

1. Khách hàng đăng ký hoặc đăng nhập.
2. Khách hàng hoàn thiện hồ sơ cá nhân, thu nhập, phiếu lương và danh sách khoản nợ.
3. Nhân viên xác minh thông tin khách hàng. Khi khách sửa hồ sơ hoặc chứng từ, thu nhập đã xác minh bị vô hiệu hóa và trạng thái quay về chờ xác minh.
4. Khách hàng tạo hồ sơ vay.
5. DSS chấm điểm tín dụng, tính DTI/DSCR, đánh giá rủi ro và sinh khuyến nghị.
6. Nhân viên thẩm định hồ sơ trong hàng đợi thẩm định.
7. Với vay thế chấp, nhân viên lên lịch hẹn và hoàn thiện mẫu thủ tục thế chấp.
8. Khi hoàn tất thủ tục thế chấp, hệ thống tái thẩm định DSS bằng dữ liệu sau thẩm định và chỉ cho hoàn tất nếu khoản thanh toán hằng tháng khớp DSS.
9. Hợp đồng, lịch trả nợ và điều khoản thanh toán được sinh từ kết quả DSS đã xác nhận.
10. Sau giải ngân, khách hàng gửi biên lai. Nhân viên đối chiếu rồi hệ thống ghi nhận thanh toán và cập nhật điểm tín nhiệm.

## Kiến trúc

Backend:

- Java 17
- Spring Boot 3.5
- Spring Security
- Spring Validation
- Spring JDBC
- Flyway
- MySQL 8.4
- JJWT

Frontend:

- React 18
- Vite 5
- Material UI 5
- React Router 6

Hạ tầng local:

- Docker Compose gồm `mysql`, `backend`, `frontend`
- File upload được lưu vào volume/container storage cho phiếu lương, chứng từ vay và biên lai thanh toán.

## Cấu trúc thư mục

```text
.
|- backend
|  |- src/main/java/com/loanapproval/dss
|  |  |- admin
|  |  |- auth
|  |  |- compliance
|  |  |- contract
|  |  |- customerinfo
|  |  |- debt
|  |  |- demo
|  |  |- dss
|  |  |- health
|  |  |- loan
|  |  |- notification
|  |  |- profile
|  |  |- repayment
|  |  |- risk
|  |  |- security
|  |  |- staff
|  |  |- verification
|  |  `- shared
|  |- src/main/resources/db/migration
|  |- src/test
|  |- Dockerfile
|  `- pom.xml
|- frontend
|  |- src
|  |  |- app
|  |  |- features
|  |  |  |- admin
|  |  |  |- auth
|  |  |  |- customer
|  |  |  `- staff
|  |  `- shared
|  |- Dockerfile
|  |- package.json
|  `- vite.config.js
|- docker-compose.yml
|- .env.example
`- README.md
```

## Database và migration

Flyway migration hiện có từ `V1` đến `V18`, bao gồm:

- Schema người dùng, hồ sơ khách hàng, khoản nợ, hồ sơ vay.
- Vai trò admin và bootstrap tài khoản.
- Thanh toán, điểm tín nhiệm và lịch trả nợ.
- Thông tin xác minh khách hàng, phiếu lương và chứng từ vay.
- Loại hồ sơ vay tín chấp/thế chấp.
- Lịch hẹn và thủ tục thế chấp.
- Điều khoản hợp đồng, lịch thanh toán và tái thẩm định DSS.
- Yêu cầu xác nhận biên lai thanh toán.
- Thông báo trong ứng dụng.

## API chính

Auth:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

Admin:

- `GET /api/admin/users`
- `POST /api/admin/users`
- `DELETE /api/admin/users/{id}`

Customer:

- `GET /api/customer/profile`
- `PUT /api/customer/profile`
- `GET /api/customer/profile/payslip`
- `GET /api/customer/information-verification`
- `GET /api/customer/debts`
- `GET /api/customer/debts/metrics`
- `POST /api/customer/debts`
- `DELETE /api/customer/debts/{id}`
- `POST /api/customer/loans`
- `GET /api/customer/loans`
- `GET /api/customer/loans/paged`
- `GET /api/customer/loans/{id}`
- `GET /api/customer/loans/{id}/documents/{documentType}`
- `GET /api/customer/contracts/{loanRequestId}`
- `GET /api/customer/payments`
- `GET /api/customer/payments/paged`
- `POST /api/customer/payments/confirmations`
- `GET /api/customer/payments/confirmations/{confirmationId}/proof`

Staff:

- `GET /api/staff/information-verifications`
- `GET /api/staff/information-verifications/{customerId}`
- `GET /api/staff/information-verifications/{customerId}/payslip`
- `POST /api/staff/information-verifications/{customerId}/decision`
- `GET /api/staff/requests`
- `GET /api/staff/requests/paged`
- `GET /api/staff/requests/operations`
- `GET /api/staff/requests/operations/paged`
- `GET /api/staff/requests/{id}`
- `POST /api/staff/requests/{id}/decision`
- `POST /api/staff/requests/{id}/complete-contract`
- `POST /api/staff/requests/{id}/disburse`
- `GET /api/staff/requests/{id}/documents/{documentType}`
- `GET /api/staff/secured-procedures`
- `GET /api/staff/secured-procedures/{loanRequestId}`
- `PUT /api/staff/secured-procedures/{loanRequestId}`
- `GET /api/staff/payment-confirmations`
- `GET /api/staff/payment-confirmations/{confirmationId}`
- `POST /api/staff/payment-confirmations/{confirmationId}/review`
- `GET /api/staff/payment-confirmations/{confirmationId}/proof`
- `GET /api/staff/verifications/{customerId}`
- `PUT /api/staff/verifications/{customerId}`

Notifications:

- `GET /api/notifications`
- `POST /api/notifications/{id}/read`
- `POST /api/notifications/read-all`

Health:

- `GET /api/health`
- `GET /actuator/health`
- `GET /actuator/info`

## Chạy bằng Docker

Tạo file môi trường:

```powershell
Copy-Item .env.example .env
```

Build và chạy:

```powershell
docker compose up --build -d
```

Truy cập:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- MySQL: `localhost:3306`

Tài khoản bootstrap mặc định:

- Admin: `admin@gmail.com` / `123456`
- Staff demo: `staff.demo@loan.local` / `123456`
- Customer demo: `customer.demo@loan.local` / `123456`
- Customer pending: `customer.pending@loan.local` / `123456`
- Customer failed: `customer.failed@loan.local` / `123456`

## Chạy local không Docker

Yêu cầu:

- Java 17
- Maven
- Node.js
- MySQL 8.x

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

## Biến môi trường thường dùng

```text
MYSQL_DATABASE=loan_dss
MYSQL_USER=loan_user
MYSQL_PASSWORD=loan_password
MYSQL_ROOT_PASSWORD=root_password
MYSQL_PORT=3306
SPRING_PROFILES_ACTIVE=dev
BACKEND_PORT=8080
FRONTEND_PORT=5173
APP_JWT_SECRET=...
APP_BOOTSTRAP_ADMIN_ENABLED=true
APP_BOOTSTRAP_ADMIN_EMAIL=admin@gmail.com
APP_BOOTSTRAP_ADMIN_PASSWORD=123456
APP_BOOTSTRAP_DEMO_ENABLED=true
APP_BOOTSTRAP_DEMO_PASSWORD=123456
```

## Build và test

Backend tests:

```powershell
cd backend
mvn test
```

Frontend build:

```powershell
cd frontend
npm run build
```

Backend package:

```powershell
cd backend
mvn -DskipTests clean package
```

## Ghi chú triển khai

- `.md` ngoài `README.md` không được đưa lên repository.
- `.env` không được commit.
- `frontend/dist`, `frontend/node_modules`, `backend/target` là output build/local dependency và không nên commit.
- API frontend dùng `VITE_API_BASE_URL`; khi chạy Docker giá trị mặc định trỏ tới backend local.
