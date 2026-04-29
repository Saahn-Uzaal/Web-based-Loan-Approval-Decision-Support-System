# Getting Started

Tài liệu này dành cho người mới clone project và muốn chạy được hệ thống trên máy cá nhân. Cách khuyến nghị là Docker Compose vì không cần tự cài MySQL, Java, Maven và Node.js để chạy thử.

## 1. Clone project

```powershell
git clone https://github.com/Saahn-Uzaal/Web-based-Loan-Approval-Decision-Support-System.git
cd Web-based-Loan-Approval-Decision-Support-System
```

Nếu đã có source trên máy:

```powershell
git pull origin main
```

## 2. Chạy nhanh bằng Docker

### Yêu cầu

- Git
- Docker Desktop

### Tạo file môi trường

```powershell
Copy-Item .env.example .env
```

Các port mặc định:

- MySQL: `3306`
- Backend: `8080`
- Frontend: `5173`

Nếu máy đã dùng một trong các port trên, sửa trong `.env` trước khi chạy.

### Build và chạy

```powershell
docker compose up --build -d
```

Kiểm tra container:

```powershell
docker compose ps
```

Xem log khi cần debug:

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
```

### Truy cập

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`

### Tài khoản demo

Mật khẩu mặc định của các tài khoản bootstrap là `123456`.

| Vai trò | Email |
| --- | --- |
| Admin | `admin@gmail.com` |
| Staff | `staff.demo@loan.local` |
| Customer đã xác minh | `customer.demo@loan.local` |
| Customer chờ xác minh | `customer.pending@loan.local` |
| Customer bị từ chối xác minh | `customer.failed@loan.local` |

Khách hàng mới cũng có thể tự đăng ký tại trang đăng nhập.

### Dừng project

```powershell
docker compose down
```

Xóa luôn dữ liệu MySQL trong Docker volume để chạy lại từ đầu:

```powershell
docker compose down -v
```

Sau đó chạy lại:

```powershell
docker compose up --build -d
```

## 3. Chạy local không Docker

Chỉ dùng cách này khi cần debug backend hoặc frontend trực tiếp.

### Yêu cầu

- Java 17
- Maven 3.9+
- Node.js 20+
- MySQL 8+

### Tạo database

Backend mặc định kết nối MySQL local với thông tin:

- Database: `loan_dss`
- Username: `loan_user`
- Password: `loan_password`

Tạo nhanh bằng SQL:

```sql
CREATE DATABASE loan_dss CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'loan_user'@'localhost' IDENTIFIED BY 'loan_password';
GRANT ALL PRIVILEGES ON loan_dss.* TO 'loan_user'@'localhost';
FLUSH PRIVILEGES;
```

Flyway sẽ tự chạy migration khi backend khởi động.

### Chạy backend

```powershell
cd backend
mvn spring-boot:run
```

Backend chạy tại `http://localhost:8080`.

Nếu cần đổi cấu hình local, set biến môi trường trước khi chạy:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/loan_dss?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME="loan_user"
$env:SPRING_DATASOURCE_PASSWORD="loan_password"
$env:APP_JWT_SECRET="bG9hbi1kc3MtZGV2LXNlY3JldC1rZXktdGhpcnR5LXR3by1ieXRlcw=="
mvn spring-boot:run
```

### Chạy frontend

Mở terminal khác:

```powershell
cd frontend
npm install
npm run dev
```

Frontend chạy tại `http://localhost:5173`.

Nếu backend không chạy port `8080`:

```powershell
$env:VITE_API_BASE_URL="http://localhost:8081"
npm run dev
```

## 4. Luồng kiểm tra nhanh sau khi chạy

1. Vào `http://localhost:5173/login`.
2. Đăng nhập staff bằng `staff.demo@loan.local` / `123456`.
3. Kiểm tra các màn:
   - `Xác minh thông tin`
   - `Thẩm định`
   - `Vận hành khoản vay`
   - `Thủ tục thế chấp`
   - `Xác nhận thanh toán`
4. Đăng nhập customer bằng `customer.demo@loan.local` / `123456`.
5. Kiểm tra các màn:
   - `Hồ sơ của tôi`
   - `Tạo hồ sơ vay`
   - `Khoản vay của tôi`
   - `Thanh toán`

## 5. Cập nhật source sau khi đã clone

```powershell
git pull origin main
```

Nếu chạy Docker:

```powershell
docker compose up --build -d
```

Nếu chạy local frontend:

```powershell
cd frontend
npm install
```

Nếu database schema thay đổi, backend sẽ tự chạy Flyway migration khi restart.

## 6. Build và test

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

## 7. Lỗi thường gặp

### Port đã được sử dụng

Sửa `.env`:

```text
MYSQL_PORT=3307
BACKEND_PORT=8081
FRONTEND_PORT=5174
```

Sau đó chạy lại:

```powershell
docker compose up --build -d
```

### Docker đã chạy nhưng web chưa vào được

Chờ thêm 10-20 giây ở lần chạy đầu vì backend phải đợi MySQL healthy và chạy Flyway migration. Nếu vẫn lỗi:

```powershell
docker compose ps
docker compose logs -f backend
```

### Backend báo lỗi kết nối MySQL khi chạy local

Kiểm tra MySQL đã chạy, database/user đã tạo đúng, và biến môi trường datasource khớp với cấu hình local.

### Frontend gọi sai backend

Kiểm tra `VITE_API_BASE_URL`. Với Docker mặc định là `http://localhost:8080`. Với local, set lại nếu backend dùng port khác.

### Muốn reset toàn bộ dữ liệu demo

Với Docker:

```powershell
docker compose down -v
docker compose up --build -d
```

Với local MySQL, drop database rồi tạo lại:

```sql
DROP DATABASE loan_dss;
CREATE DATABASE loan_dss CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 8. Ghi chú repository

- Chỉ `README.md` và `GETTING_STARTED.md` được commit trong nhóm Markdown.
- Không commit `.env`, cache Docker, database local, Maven cache, `node_modules`, `dist` hoặc `target`.
- Người mới chỉ cần source code, `.env.example`, Docker Compose hoặc các dependency chuẩn nêu ở trên.
