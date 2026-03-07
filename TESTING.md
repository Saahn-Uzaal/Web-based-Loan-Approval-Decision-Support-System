# Hướng dẫn Test

## 1. Unit Tests (không cần Docker, không cần DB)

```powershell
cd backend
mvn test
```

Kết quả mong đợi:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Chạy một test cụ thể:
```powershell
mvn test -Dtest=DecisionEngineServiceTest#shouldBoostScoreWithPositivePaymentRating
```

---

## 2. Khởi động toàn bộ stack (cần Docker Desktop)

```powershell
# Bước 1: copy env
Copy-Item .env.example .env

# Bước 2: build & chạy
docker compose up --build
```

Chờ đến khi thấy log `Started LoanDssApplication` thì backend sẵn sàng.

Kiểm tra health:
```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

---

## 3. Test API thủ công (dùng PowerShell)

### 3.1 Đăng ký tài khoản customer
```powershell
$body = '{"email":"customer1@test.com","password":"pass123","role":"CUSTOMER"}'
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" -Body $body
```

### 3.2 Đăng nhập – lấy token
```powershell
$loginBody = '{"email":"customer1@test.com","password":"pass123"}'
$loginRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $loginBody
$token = $loginRes.token
Write-Host "Token: $token"
```

### 3.3 Tạo hồ sơ khách hàng
```powershell
$profileBody = @{
  fullName = "Nguyen Van A"
  phone = "0901234567"
  monthlyIncome = 5000
  debtToIncomeRatio = 20
  employmentStatus = "Permanent"
} | ConvertTo-Json
Invoke-RestMethod -Method PUT -Uri http://localhost:8080/api/customer/profile `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $profileBody
```

### 3.4 Tạo yêu cầu vay – kiểm tra DSS scoring có payment rating
```powershell
$loanBody = '{"amount":10000,"termMonths":12,"purpose":"PERSONAL"}'
$loanRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/loans `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $loanBody
Write-Host "Loan ID: $($loanRes.id), Status: $($loanRes.status)"
```

### 3.5 Xem danh sách khoản vay (có pagination)
```powershell
# Tất cả (không phân trang)
Invoke-RestMethod -Uri http://localhost:8080/api/customer/loans `
  -Headers @{Authorization="Bearer $token"}

# Phân trang – trang 0, 5 items mỗi trang
Invoke-RestMethod -Uri "http://localhost:8080/api/customer/loans/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $token"}
```

### 3.6 Tạo repayment – test fix logic ON_TIME/LATE

```powershell
$loanId = $loanRes.id

# Trả đúng số tiền → ON_TIME
$repayBody = @{
  loanRequestId = $loanId
  amountPaid    = 833.33      # ≈ 10000/12
  dueDate       = "2026-04-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody

# Trả vượt số tiền → cũng ON_TIME (fix mới)
$repayBody2 = @{
  loanRequestId = $loanId
  amountPaid    = 1200        # nhiều hơn kỳ hạn → vẫn ON_TIME
  dueDate       = "2026-05-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody2

# Trả thiếu → LATE
$repayBody3 = @{
  loanRequestId = $loanId
  amountPaid    = 100         # rất ít → LATE
  dueDate       = "2026-06-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody3
```

### 3.7 Xem lịch sử thanh toán (có pagination)
```powershell
# Không phân trang
Invoke-RestMethod -Uri http://localhost:8080/api/customer/payments `
  -Headers @{Authorization="Bearer $token"}

# Phân trang
Invoke-RestMethod -Uri "http://localhost:8080/api/customer/payments/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $token"}
```

### 3.8 Login staff và test queue có pagination
```powershell
# Đăng nhập admin
$adminLogin = '{"email":"admin@loan.local","password":"Admin123!"}'
$adminRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $adminLogin
$adminToken = $adminRes.token

# Tạo tài khoản staff
$staffBody = '{"email":"staff1@test.com","password":"pass123","role":"STAFF"}'
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" -Body $staffBody

# Login staff
$staffLogin = '{"email":"staff1@test.com","password":"pass123"}'
$staffRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $staffLogin
$staffToken = $staffRes.token

# Xem review queue (tất cả)
Invoke-RestMethod -Uri http://localhost:8080/api/staff/requests `
  -Headers @{Authorization="Bearer $staffToken"}

# Xem review queue (phân trang)
Invoke-RestMethod -Uri "http://localhost:8080/api/staff/requests/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $staffToken"}

# Phê duyệt một loan request (thay LoanID bên dưới)
$decisionBody = '{"action":"APPROVE","reason":"Good credit profile"}'
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/staff/requests/$($loanRes.id)/decision" `
  -ContentType "application/json" -Headers @{Authorization="Bearer $staffToken"} -Body $decisionBody
```

---

## 4. Test DSS Scoring có payment rating

Kịch bản: tạo 2 customer có cùng profile, nhưng khác payment history → credit score khác nhau.

```powershell
# Customer A: không có lịch sử (rating = 0)
# → tạo loan ngay sau khi đăng ký

# Customer B: có lịch sử tốt (rating cao)
# → trả nhiều lần ON_TIME trước, rồi tạo loan mới
# Quan sát explanation trong response DSS:
# "Score=XXX (base=YYY, paymentBonus=+ZZ, DTI ...)"
```

Khi tạo loan, field `explanation` trong DSS result sẽ cho thấy:
```
Score=790 (base=770, paymentBonus=+20, DTI 20.0%, income 5000, ...)
```

---

## 5. Validation Test – kiểm tra Bean Validation trả lỗi đúng

```powershell
# Thiếu required field → 400 Bad Request
$badLoan = '{"amount":-1,"termMonths":0}'
try {
  Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/loans `
    -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $badLoan
} catch {
  $_.Exception.Response.StatusCode  # phải là 400
  $_.ErrorDetails.Message           # thấy field + lỗi cụ thể
}

# Password quá ngắn → 400
$badRegister = '{"email":"x@x.com","password":"abc"}'
try {
  Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
    -ContentType "application/json" -Body $badRegister
} catch {
  $_.ErrorDetails.Message
}
```

---

## 6. Test nhanh bằng frontend (http://localhost:5173)

| Bước | Trang | Kiểm tra |
|------|-------|----------|
| 1 | `/login` | Đăng nhập customer |
| 2 | `/customer/profile` | Điền income, DTI, employment |
| 3 | `/customer/loans/new` | Tạo khoản vay → xem DSS result trong detail |
| 4 | `/customer/payments` | Ghi repayment, kiểm tra rating thay đổi |
| 5 | `/login` → staff | Đăng nhập staff, vào queue, approve/reject |
| 6 | `/customer/loans` | Xem status đã cập nhật |

