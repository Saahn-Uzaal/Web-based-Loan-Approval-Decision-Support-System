# Hướng dẫn pull và chạy project trên máy mới

Tài liệu này dành cho người mới clone project về máy và muốn chạy nhanh đúng luồng. Cách khuyến nghị là dùng Docker Compose vì không cần tự cài MySQL, Java, Maven, Node trên máy.

## 1. Cách nhanh nhất: chạy bằng Docker

### Yêu cầu

- Git
- Docker Desktop

### Bước 1: Clone project

```powershell
git clone https://github.com/Saahn-Uzaal/Web-based-Loan-Approval-Decision-Support-System.git
cd Web-based-Loan-Approval-Decision-Support-System
```

Nếu đã có source sẵn trên máy thì cập nhật bằng:

```powershell
git pull origin main
```

### Bước 2: Tạo file môi trường

```powershell
Copy-Item .env.example .env
```

Bạn có thể giữ nguyên file `.env` mặc định. Chỉ cần sửa nếu máy đang bị trùng port `3306`, `8080` hoặc `5173`.

### Bước 3: Build và chạy

```powershell
docker compose up --build -d
```

Nếu muốn xem trạng thái container:

```powershell
docker compose ps
```

Nếu muốn xem log khi có lỗi:

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
```

### Bước 4: Truy cập hệ thống

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`

Tài khoản admin mặc định:

- Email: `admin@gmail.com`
- Password: `123456`

Tài khoản customer có thể tạo trực tiếp trên trang `/login`.

### Bước 5: Dừng project

```powershell
docker compose down
```

Nếu muốn xóa luôn dữ liệu MySQL trong Docker volume để chạy lại từ đầu:

```powershell
docker compose down -v
```

## 2. Chạy local không dùng Docker

Chỉ dùng cách này nếu bạn muốn debug backend/frontend trực tiếp trên máy.

### Yêu cầu

- Java 17
- Maven 3.9+
- Node.js 20+
- MySQL 8+

### Bước 1: Tạo database local

Project mặc định sẽ tìm MySQL tại `localhost:3306` với:

- Database: `loan_dss`
- Username: `loan_user`
- Password: `loan_password`

Có thể tạo nhanh bằng SQL sau:

```sql
CREATE DATABASE loan_dss CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'loan_user'@'localhost' IDENTIFIED BY 'loan_password';
GRANT ALL PRIVILEGES ON loan_dss.* TO 'loan_user'@'localhost';
FLUSH PRIVILEGES;
```

Lưu ý:

- Flyway sẽ tự chạy migration khi backend start.
- File `.env` ở root dự án được Docker Compose dùng; khi chạy local, backend đọc biến môi trường hệ điều hành hoặc dùng giá trị mặc định trong `application.yml`.

### Bước 2: Chạy backend

```powershell
cd backend
mvn spring-boot:run
```

Backend mặc định chạy tại `http://localhost:8080`.

### Bước 3: Chạy frontend

Mở một terminal khác:

```powershell
cd frontend
npm install
npm run dev
```

Frontend mặc định chạy tại `http://localhost:5173`.

Nếu backend không dùng port `8080`, có thể set biến môi trường trước khi chạy frontend:

```powershell
$env:VITE_API_BASE_URL="http://localhost:8081"
npm run dev
```

## 3. Cập nhật project sau khi đã clone

Khi source code trên remote thay đổi:

```powershell
git pull origin main
docker compose up --build -d
```

Nếu chạy local không Docker:

```powershell
cd frontend
npm install
```

Backend dùng Maven nên thường không cần làm gì thêm ngoài việc chạy lại `mvn spring-boot:run`. Nếu `pom.xml` thay đổi, Maven sẽ tự tải thêm dependency.

## 4. Lỗi thường gặp

### Port đã được sử dụng

Sửa các giá trị sau trong file `.env` rồi chạy lại:

- `MYSQL_PORT`
- `BACKEND_PORT`
- `FRONTEND_PORT`

### Docker đã chạy nhưng không vào được web

Kiểm tra:

```powershell
docker compose ps
docker compose logs -f backend
```

Thử đợi thêm 10-20 giây ở lần chạy đầu vì backend cần chờ MySQL healthy và chạy Flyway migration.

### Frontend gọi API sai địa chỉ

Cần đảm bảo:

- Docker: `VITE_API_BASE_URL` đang trỏ tới `http://localhost:8080` hoặc port backend bạn đã đổi trong `.env`
- Local: backend đang chạy và frontend đang gọi đúng port backend
