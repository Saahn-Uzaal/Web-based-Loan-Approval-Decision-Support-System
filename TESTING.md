# HÆ°á»›ng dáº«n Test

## 1. Unit Tests (khÃ´ng cáº§n Docker, khÃ´ng cáº§n DB)

```powershell
cd backend
mvn test
```

Káº¿t quáº£ mong Ä‘á»£i:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Cháº¡y má»™t test cá»¥ thá»ƒ:
```powershell
mvn test -Dtest=DecisionEngineServiceTest#shouldBoostScoreWithPositivePaymentRating
```

---

## 2. Khá»Ÿi Ä‘á»™ng toÃ n bá»™ stack (cáº§n Docker Desktop)

```powershell
# BÆ°á»›c 1: copy env
Copy-Item .env.example .env

# BÆ°á»›c 2: build & cháº¡y
docker compose up --build
```

Chá» Ä‘áº¿n khi tháº¥y log `Started LoanDssApplication` thÃ¬ backend sáºµn sÃ ng.

Kiá»ƒm tra health:
```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

---

## 3. Test API thá»§ cÃ´ng (dÃ¹ng PowerShell)

### 3.1 ÄÄƒng kÃ½ tÃ i khoáº£n customer
```powershell
$body = '{"email":"customer1@test.com","password":"pass123","role":"CUSTOMER"}'
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" -Body $body
```

### 3.2 ÄÄƒng nháº­p â€“ láº¥y token
```powershell
$loginBody = '{"email":"customer1@test.com","password":"pass123"}'
$loginRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $loginBody
$token = $loginRes.token
Write-Host "Token: $token"
```

### 3.3 Táº¡o há»“ sÆ¡ khÃ¡ch hÃ ng
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

### 3.4 Táº¡o yÃªu cáº§u vay â€“ kiá»ƒm tra DSS scoring cÃ³ payment rating
```powershell
$loanBody = '{"amount":10000,"termMonths":12,"purpose":"PERSONAL"}'
$loanRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/loans `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $loanBody
Write-Host "Loan ID: $($loanRes.id), Status: $($loanRes.status)"
```

### 3.5 Xem danh sÃ¡ch khoáº£n vay (cÃ³ pagination)
```powershell
# Táº¥t cáº£ (khÃ´ng phÃ¢n trang)
Invoke-RestMethod -Uri http://localhost:8080/api/customer/loans `
  -Headers @{Authorization="Bearer $token"}

# PhÃ¢n trang â€“ trang 0, 5 items má»—i trang
Invoke-RestMethod -Uri "http://localhost:8080/api/customer/loans/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $token"}
```

### 3.6 Táº¡o repayment â€“ test fix logic ON_TIME/LATE

```powershell
$loanId = $loanRes.id

# Tráº£ Ä‘Ãºng sá»‘ tiá»n â†’ ON_TIME
$repayBody = @{
  loanRequestId = $loanId
  amountPaid    = 833.33      # â‰ˆ 10000/12
  dueDate       = "2026-04-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody

# Tráº£ vÆ°á»£t sá»‘ tiá»n â†’ cÅ©ng ON_TIME (fix má»›i)
$repayBody2 = @{
  loanRequestId = $loanId
  amountPaid    = 1200        # nhiá»u hÆ¡n ká»³ háº¡n â†’ váº«n ON_TIME
  dueDate       = "2026-05-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody2

# Tráº£ thiáº¿u â†’ LATE
$repayBody3 = @{
  loanRequestId = $loanId
  amountPaid    = 100         # ráº¥t Ã­t â†’ LATE
  dueDate       = "2026-06-01"
} | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/payments `
  -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $repayBody3
```

### 3.7 Xem lá»‹ch sá»­ thanh toÃ¡n (cÃ³ pagination)
```powershell
# KhÃ´ng phÃ¢n trang
Invoke-RestMethod -Uri http://localhost:8080/api/customer/payments `
  -Headers @{Authorization="Bearer $token"}

# PhÃ¢n trang
Invoke-RestMethod -Uri "http://localhost:8080/api/customer/payments/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $token"}
```

### 3.8 Login staff vÃ  test queue cÃ³ pagination
```powershell
# ÄÄƒng nháº­p admin
$adminLogin = '{"email":"admin@gmail.com","password":"123456"}'
$adminRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $adminLogin
$adminToken = $adminRes.token

# Táº¡o tÃ i khoáº£n staff
$staffBody = '{"email":"staff1@test.com","password":"pass123","role":"STAFF"}'
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" -Body $staffBody

# Login staff
$staffLogin = '{"email":"staff1@test.com","password":"pass123"}'
$staffRes = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $staffLogin
$staffToken = $staffRes.token

# Xem review queue (táº¥t cáº£)
Invoke-RestMethod -Uri http://localhost:8080/api/staff/requests `
  -Headers @{Authorization="Bearer $staffToken"}

# Xem review queue (phÃ¢n trang)
Invoke-RestMethod -Uri "http://localhost:8080/api/staff/requests/paged?page=0&size=5" `
  -Headers @{Authorization="Bearer $staffToken"}

# PhÃª duyá»‡t má»™t loan request (thay LoanID bÃªn dÆ°á»›i)
$decisionBody = '{"action":"APPROVE","reason":"Good credit profile"}'
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/staff/requests/$($loanRes.id)/decision" `
  -ContentType "application/json" -Headers @{Authorization="Bearer $staffToken"} -Body $decisionBody
```

---

## 4. Test DSS Scoring cÃ³ payment rating

Ká»‹ch báº£n: táº¡o 2 customer cÃ³ cÃ¹ng profile, nhÆ°ng khÃ¡c payment history â†’ credit score khÃ¡c nhau.

```powershell
# Customer A: khÃ´ng cÃ³ lá»‹ch sá»­ (rating = 0)
# â†’ táº¡o loan ngay sau khi Ä‘Äƒng kÃ½

# Customer B: cÃ³ lá»‹ch sá»­ tá»‘t (rating cao)
# â†’ tráº£ nhiá»u láº§n ON_TIME trÆ°á»›c, rá»“i táº¡o loan má»›i
# Quan sÃ¡t explanation trong response DSS:
# "Score=XXX (base=YYY, paymentBonus=+ZZ, DTI ...)"
```

Khi táº¡o loan, field `explanation` trong DSS result sáº½ cho tháº¥y:
```
Score=790 (base=770, paymentBonus=+20, DTI 20.0%, income 5000, ...)
```

---

## 5. Validation Test â€“ kiá»ƒm tra Bean Validation tráº£ lá»—i Ä‘Ãºng

```powershell
# Thiáº¿u required field â†’ 400 Bad Request
$badLoan = '{"amount":-1,"termMonths":0}'
try {
  Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/customer/loans `
    -ContentType "application/json" -Headers @{Authorization="Bearer $token"} -Body $badLoan
} catch {
  $_.Exception.Response.StatusCode  # pháº£i lÃ  400
  $_.ErrorDetails.Message           # tháº¥y field + lá»—i cá»¥ thá»ƒ
}

# Password quÃ¡ ngáº¯n â†’ 400
$badRegister = '{"email":"x@x.com","password":"abc"}'
try {
  Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
    -ContentType "application/json" -Body $badRegister
} catch {
  $_.ErrorDetails.Message
}
```

---

## 6. Test nhanh báº±ng frontend (http://localhost:5173)

| BÆ°á»›c | Trang | Kiá»ƒm tra |
|------|-------|----------|
| 1 | `/login` | ÄÄƒng nháº­p customer |
| 2 | `/customer/profile` | Äiá»n income, DTI, employment |
| 3 | `/customer/loans/new` | Táº¡o khoáº£n vay â†’ xem DSS result trong detail |
| 4 | `/customer/payments` | Ghi repayment, kiá»ƒm tra rating thay Ä‘á»•i |
| 5 | `/login` â†’ staff | ÄÄƒng nháº­p staff, vÃ o queue, approve/reject |
| 6 | `/customer/loans` | Xem status Ä‘Ã£ cáº­p nháº­t |


