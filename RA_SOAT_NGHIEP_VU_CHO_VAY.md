# Rà soát nghiệp vụ ứng dụng cho vay

Ngày rà soát: 2026-04-29  
Phạm vi: backend Spring Boot, frontend React, migration MySQL, Docker Compose và dữ liệu demo hiện có.

## 1. Kết luận nhanh

Project hiện đủ tốt để demo luồng cơ bản của một hệ thống hỗ trợ xét duyệt khoản vay: khách hàng khai báo hồ sơ, nhân viên xác minh, DSS chấm điểm, nhân viên phê duyệt, vay thế chấp có lịch hẹn và mẫu thủ tục, hợp đồng được tạo, khoản vay được giải ngân và khách hàng có thể ghi nhận thanh toán.

Nếu nhìn như một app cho vay thật, hệ thống vẫn còn một số điểm chưa ổn ở mức nghiệp vụ và kiểm soát rủi ro. Các vấn đề lớn nhất là: chế độ demo có thể tác động trực tiếp vào nghiệp vụ thật, khách hàng đang tự ghi nhận thanh toán, hợp đồng thế chấp vẫn có đường bypass điều khoản lãi/thanh toán, hàng đợi thẩm định đang lẫn hồ sơ đã qua thẩm định, audit/data retention chưa phù hợp với hệ thống tài chính, và logic trả nợ còn ở mức mô phỏng.

## 2. Luồng nghiệp vụ hiện tại

### 2.1. Hồ sơ khách hàng

Khách hàng cập nhật hồ sơ cá nhân ở `CustomerProfileService`.

Thông tin chính gồm họ tên, điện thoại, ngày sinh, thu nhập tự khai báo và phiếu lương. Sau mỗi lần cập nhật hồ sơ hoặc khoản nợ, hệ thống đưa trạng thái xác minh thông tin về `PENDING` qua `CustomerInformationVerificationService.markPending`.

Nhân viên xác minh thông tin ở `CustomerInformationVerificationService.review`. Nếu duyệt, nhân viên có thể nhập `verifiedMonthlyIncome`. DSS và các bước đánh giá sau đó dùng `profile.effectiveMonthlyIncome()`, tức ưu tiên thu nhập đã xác minh nếu có.

### 2.2. Khách hàng tạo yêu cầu vay

Khách hàng tạo hồ sơ vay qua `CustomerLoanController` và `CustomerLoanService`.

Trước khi tạo khoản vay, hệ thống bắt buộc thông tin khách hàng đã được nhân viên duyệt thông qua `assertApprovedForLoanCreation`. Sau đó DSS đánh giá rủi ro, hệ thống tính hạn mức sơ bộ bằng `LoanEligibilityService`, lưu kết quả DSS/risk assessment và quyết định trạng thái ban đầu.

Với vay tín chấp, hồ sơ rủi ro thấp có thể tự động được duyệt. Với vay thế chấp, hệ thống hiện không tự động duyệt nữa; hồ sơ phải đi qua nhân viên và lịch hẹn.

### 2.3. Nhân viên thẩm định

Nhân viên xem hồ sơ qua `StaffReviewController` và `StaffReviewService`.

Chỉ hồ sơ `PENDING` mới được ra quyết định phê duyệt/từ chối. Khi nhân viên thay đổi số tiền duyệt, kỳ hạn hoặc lãi suất ở bước thẩm định, hệ thống gọi lại `LoanApprovalReassessmentService` để chạy lại DSS và kiểm tra hạn mức an toàn.

Với vay tín chấp, nếu được duyệt thì chuyển sang `APPROVED`, nhân viên hoàn tất hợp đồng, sau đó giải ngân.

Với vay thế chấp, nếu được duyệt thì chuyển sang `APPOINTMENT_SCHEDULED`, bắt buộc có lịch hẹn gặp mặt trước khi hoàn tất thủ tục thế chấp.

### 2.4. Thủ tục thế chấp

Luồng thế chấp nằm ở `SecuredLoanProcedureController`, `SecuredLoanProcedureService` và `SecuredLoanProcedureRepository`.

Nhân viên nhập mẫu thủ tục, thông tin tài sản, giá trị thẩm định, ngày ký hợp đồng, lịch thanh toán và các checkbox pháp lý. Khi trạng thái thủ tục là `COMPLETED`, hệ thống kiểm tra lịch hẹn đã diễn ra, kiểm tra đủ trường bắt buộc, kiểm tra checklist pháp lý, chạy lại đánh giá với `appraisalValue`, tạo hợp đồng và chuyển hồ sơ sang `CONTRACTED`.

Frontend có nút giả lập ngày giờ để demo việc “buổi gặp đã diễn ra” mà không phải chờ thời gian thật.

### 2.5. Hợp đồng và giải ngân

Hợp đồng được tạo ở `LoanContractService`. Hợp đồng lưu số tiền gốc, lãi suất năm, kỳ hạn, ngày bắt đầu, ngày thanh toán đầu tiên, ngày thanh toán cuối cùng, số tiền trả hàng tháng và tổng lãi.

Nhân viên giải ngân qua `StaffReviewService.disburseLoan`. Sau khi giải ngân, hệ thống chuyển khoản vay sang `ACTIVE`.

### 2.6. Thanh toán

Khách hàng ghi nhận thanh toán ở `CustomerRepaymentController` và `RepaymentService`.

Hệ thống lấy hợp đồng, tính tổng phải trả bằng gốc cộng tổng lãi, cộng tổng số tiền đã thanh toán, suy ra dư nợ còn lại và kỳ thanh toán hiện tại qua `RepaymentScheduleService`.

Khách hàng chỉ được trả tối đa số tiền đến hạn kỳ hiện tại hoặc tất toán toàn bộ khoản vay. Nếu trả đủ kỳ hiện tại đúng hạn thì cộng điểm thanh toán; nếu trả đủ kỳ nhưng sau hạn thì trừ điểm. Nếu tất toán hết, hồ sơ chuyển `CLOSED` và hợp đồng đóng.

Frontend có ngày giờ giả lập thanh toán để demo đúng hạn/trễ hạn.

## 3. Những điểm hiện đã tương đối ổn

| Mảng | Nhận xét |
| --- | --- |
| Vay thế chấp không còn auto-approve | `CustomerLoanService` chỉ auto-approve cho khoản vay không phải `SECURED`. Vay thế chấp phải vào luồng nhân viên và lịch hẹn. |
| DTI ưu tiên thu nhập đã xác minh | `CustomerProfile.effectiveMonthlyIncome()` được dùng trong tạo khoản vay và đánh giá lại. Đây là hướng đúng hơn so với dùng thu nhập tự khai báo. |
| Nhân viên đổi điều khoản thì có đánh giá lại | `StaffReviewService` gọi `LoanApprovalReassessmentService` khi duyệt hồ sơ, tránh việc sửa số tiền/kỳ hạn/lãi suất mà không chạy lại DSS. |
| Thế chấp có bước thẩm định tài sản | `SecuredLoanProcedureService` yêu cầu `appraisalValue`, checklist pháp lý và lịch hẹn đã diễn ra trước khi tạo hợp đồng. |
| Khoản vay sau giải ngân chuyển sang đang vay | `StaffReviewService.disburseLoan` hiện chuyển hồ sơ sang `ACTIVE`, phù hợp hơn so với giữ ở `DISBURSED`. |
| Thanh toán không dùng `paidAt` trong body | Backend dùng thời gian server hoặc header demo, tránh việc khách hàng tự gửi thời điểm thanh toán trong body. |

## 4. Các vấn đề cần xử lý

### P0-01. Chế độ demo đang nằm trong API nghiệp vụ thật

Hiện trạng: `CustomerRepaymentController.createRepayment` và `SecuredLoanProcedureController.saveSecuredProcedure` đều nhận header `X-Demo-Now`. `DemoTimeResolver` luôn chấp nhận header này nếu format đúng ISO-8601.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/shared/DemoTimeResolver.java` | Nhận và parse thời gian giả lập. |
| `backend/src/main/java/com/loanapproval/dss/repayment/CustomerRepaymentController.java` | Cho khách hàng gửi `X-Demo-Now` khi ghi nhận thanh toán. |
| `backend/src/main/java/com/loanapproval/dss/staff/SecuredLoanProcedureController.java` | Cho nhân viên gửi `X-Demo-Now` khi hoàn tất thủ tục thế chấp. |
| `frontend/src/features/customer/pages/CustomerPaymentsPage.jsx` | UI giả lập ngày trả nợ. |
| `frontend/src/features/staff/pages/StaffSecuredProceduresPage.jsx` | UI giả lập thời điểm sau lịch hẹn. |

Vì sao không ổn: với app thật, khách hàng không được tự chọn thời điểm thanh toán để tạo trạng thái đúng hạn/trễ hạn. Nhân viên cũng không được dùng header để vượt qua kiểm tra thời gian gặp mặt nếu hệ thống chạy production.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Tách demo khỏi production | Thêm cấu hình `app.demo.enabled=false` mặc định. Chỉ bật trong profile demo/dev. |
| Chặn header khi không ở demo | `DemoTimeResolver` phải reject `X-Demo-Now` nếu `app.demo.enabled=false`. |
| Đổi tên UI | Ghi rõ “Chỉ dành cho demo” và ẩn hoàn toàn khi production. |
| Ghi audit | Nếu dùng thời gian giả lập, audit log phải ghi rõ actor, thời gian thật và thời gian giả lập. |

### P0-02. Khách hàng đang tự ghi nhận thanh toán

Hiện trạng: endpoint `POST /api/customer/payments` cho khách hàng tạo bản ghi thanh toán trực tiếp. Backend chỉ kiểm tra khoản vay thuộc khách hàng, số tiền không vượt quy tắc và thời điểm đúng/trễ hạn.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/repayment/CustomerRepaymentController.java` | Customer endpoint tạo payment. |
| `backend/src/main/java/com/loanapproval/dss/repayment/RepaymentService.java` | Tạo `loan_repayments` và cập nhật điểm thanh toán. |
| `backend/src/main/resources/db/migration/V3__add_customer_repayments_and_rating.sql` | Bảng `loan_repayments`. |

Vì sao không ổn: trong app cho vay thật, khách hàng có thể khởi tạo yêu cầu thanh toán nhưng không được tự xác nhận tiền đã vào hệ thống. Thanh toán phải được xác nhận bởi cổng thanh toán, ngân hàng, kế toán hoặc nhân viên có quyền.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Tách request và confirmation | Tạo `payment_intents` hoặc `payment_transactions` với trạng thái `PENDING`, `SUCCEEDED`, `FAILED`, `REVERSED`. |
| Không cho customer tự finalize | Customer chỉ tạo intent. Chỉ webhook/staff/accounting endpoint mới ghi nhận `SUCCEEDED`. |
| Chống double submit | Thêm `idempotency_key` hoặc `external_transaction_id` unique. |
| Giữ demo nhưng không lẫn nghiệp vụ | Nếu cần demo, tạo endpoint demo rõ tên như `/api/demo/payments/confirm`, chỉ bật trong dev. |

### P0-03. Thủ tục thế chấp vẫn có đường bypass DSS bằng điều khoản hợp đồng

Hiện trạng: khi hoàn tất thủ tục thế chấp, `SecuredLoanProcedureService` chạy lại đánh giá với `request.appraisalValue()`. Tuy nhiên các điều khoản hợp đồng thực tế như `monthlyInterestRate` và `monthlyPaymentAmount` từ mẫu thủ tục lại được đưa vào `LoanContractScheduleTerms` để tạo hợp đồng.

Điểm nguy hiểm: `LoanApprovalReassessmentService.reassessAndPersist` trong luồng này đang nhận lại `loan.approvedAmount()`, `loan.approvedTermMonths()` và `loan.approvedAnnualRate()`, không bắt buộc đánh giá lại theo `request.monthlyInterestRate()` và `request.monthlyPaymentAmount()`.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/staff/SecuredLoanProcedureService.java` | Hoàn tất thế chấp, chạy lại đánh giá, tạo hợp đồng. |
| `backend/src/main/java/com/loanapproval/dss/loan/LoanApprovalReassessmentService.java` | Đánh giá lại DSS/DTI theo điều khoản được truyền vào. |
| `backend/src/main/java/com/loanapproval/dss/contract/LoanContractService.java` | Tạo hợp đồng từ schedule terms. |

Vì sao không ổn: nhân viên có thể nhập lãi suất/thanh toán hàng tháng trong mẫu thủ tục khác với điều khoản đã được DSS đánh giá. Điều này làm DTI, khả năng trả nợ và tổng nghĩa vụ hợp đồng không còn nhất quán với quyết định phê duyệt.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Đánh giá lại đúng điều khoản hợp đồng | Khi `COMPLETED`, truyền annual rate từ `monthlyInterestRate * 12` và số tiền/kỳ hạn thực tế vào `reassessAndPersist`. |
| Kiểm tra `monthlyPaymentAmount` | Tính EMI từ approved amount, term, rate; chỉ cho sai số nhỏ nếu có lý do rounding. |
| Không để form tự quyết tổng nghĩa vụ | `monthlyPaymentAmount` nên là giá trị backend tính, frontend chỉ hiển thị hoặc đề xuất. |
| Lưu policy snapshot | Hợp đồng nên lưu `decision_policy_version`, projected DTI và DSS snapshot đã duyệt điều khoản cuối. |

### P0-04. Xóa tài khoản đang xóa cả dữ liệu nghiệp vụ và audit

Hiện trạng: `AdminUserRepository.deleteCustomerAndRelations` xóa hồ sơ vay, hợp đồng, thanh toán, DSS, risk assessment, decision audit và compliance audit liên quan đến khách hàng.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/admin/AdminUserRepository.java` | Xóa dữ liệu nghiệp vụ theo user. |
| `backend/src/main/java/com/loanapproval/dss/admin/AdminUserService.java` | Gọi delete customer/staff. |

Vì sao không ổn: hệ thống tài chính không nên hard-delete hợp đồng, thanh toán và audit log chỉ vì xóa tài khoản. Dữ liệu pháp lý, lịch sử quyết định, hợp đồng và chứng từ cần retention.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Chuyển sang soft delete | Thêm `users.status`, `deleted_at`, `disabled_at`; không xóa dữ liệu nghiệp vụ. |
| Giữ audit bất biến | Compliance/decision audit chỉ được append, không xóa. |
| Ẩn dữ liệu ở UI | Admin vô hiệu hóa tài khoản thay vì xóa vật lý. |
| Tách cleanup demo | Nếu cần reset demo, giữ trong initializer demo riêng, không dùng chung logic production. |

### P0-05. Cấu hình demo và bảo mật mặc định quá dễ dùng nhầm

Hiện trạng: `application.yml` bật `app.bootstrap.demo.enabled` mặc định `true`, admin bootstrap mặc định `true`, mật khẩu mặc định là `123456`, JWT secret có giá trị mặc định. `docker-compose.yml` không truyền `APP_BOOTSTRAP_DEMO_ENABLED=false`, nên demo data sẽ được tạo và cleanup khi backend khởi động.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/resources/application.yml` | Default JWT/admin/demo. |
| `docker-compose.yml` | Runtime env cho backend/frontend/mysql. |
| `backend/src/main/java/com/loanapproval/dss/demo/DemoDataBootstrapInitializer.java` | Xóa và tạo lại dữ liệu demo khi bật. |

Vì sao không ổn: với production, default credential, default JWT secret và demo bootstrap có thể gây mất dữ liệu, tạo tài khoản test hoặc làm token dễ đoán nếu người vận hành quên cấu hình.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Default production-safe | Đặt demo/admin bootstrap mặc định `false`. |
| Fail fast nếu thiếu secret | Không cho backend start nếu `APP_JWT_SECRET` là default ở profile non-dev. |
| Tách compose demo | Có `docker-compose.demo.yml` bật demo; compose thường không bật. |
| Đổi mật khẩu seed | Nếu vẫn seed demo, password lấy từ `.env` và không dùng `123456` trong môi trường thật. |

### P1-01. Logic trả nợ còn là mô phỏng, chưa có ledger kỳ hạn chuẩn

Hiện trạng: bảng `loan_repayments` lưu từng lần trả tiền, còn `RepaymentScheduleService` suy ra kỳ hiện tại bằng tổng tiền đã trả chia cho `monthlyPayment`.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/repayment/RepaymentScheduleService.java` | Suy ra kỳ hiện tại và số tiền đến hạn. |
| `backend/src/main/java/com/loanapproval/dss/repayment/RepaymentService.java` | Ghi nhận thanh toán và đổi trạng thái khoản vay. |
| `backend/src/main/resources/db/migration/V3__add_customer_repayments_and_rating.sql` | Bảng payment transaction hiện tại. |

Vì sao chưa đủ: app cho vay thật cần bảng lịch trả nợ theo từng kỳ. Hiện tại chưa có installment number cố định, chưa tách gốc/lãi/phí, chưa có phạt trễ, chưa có ân hạn, chưa xử lý hoàn tiền, chargeback, tất toán trước hạn hoặc phân bổ tiền trả vào kỳ cũ nhất.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Tạo bảng `loan_installments` | Cột nên có `installment_no`, `due_date`, `principal_due`, `interest_due`, `fee_due`, `amount_paid`, `status`. |
| Tạo bảng transaction riêng | `loan_payment_transactions` lưu từng giao dịch, còn installment lưu trạng thái kỳ. |
| Phân bổ thanh toán | Payment vào kỳ quá hạn/cũ nhất trước, sau đó đến kỳ hiện tại. |
| Khóa giao dịch | Dùng transaction + row lock để tránh hai request cùng lúc ghi vượt dư nợ. |
| Thêm idempotency | Chống click nhiều lần hoặc webhook gửi lại. |

### P1-02. Không có job tự động đánh dấu quá hạn

Hiện trạng: trạng thái `ON_TIME` hoặc `LATE` chỉ được xác định khi có một bản ghi thanh toán. Nếu khách hàng không trả tiền, hệ thống chưa có job nào tự đánh dấu kỳ đó là quá hạn.

Vì sao không ổn: hệ thống thu nợ cần biết khoản nào đã quá hạn dù khách hàng không thao tác gì. Nếu chỉ đánh dấu khi có payment, dashboard rủi ro và nhắc nợ sẽ bị thiếu.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Thêm scheduled job | Chạy hằng ngày, quét installment chưa trả và `due_date < today`. |
| Thêm trạng thái kỳ | `PENDING`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `WAIVED`, `REVERSED`. |
| Thêm thông báo | Tạo notification/email/SMS hoặc ít nhất dashboard “quá hạn”. |
| Tách điểm tín nhiệm | Trừ điểm khi kỳ chuyển sang overdue, không chờ đến lúc khách hàng trả muộn. |

### P1-03. Hàng đợi thẩm định đang lẫn hồ sơ đã qua thẩm định

Hiện trạng: `StaffReviewRepository.findReviewQueue` mặc định lấy cả `PENDING`, `APPOINTMENT_SCHEDULED`, `APPROVED`, `CONTRACTED`, `DISBURSED`, `ACTIVE`. UI gọi là “Hàng đợi thẩm định”.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/staff/StaffReviewRepository.java` | Query danh sách hàng đợi. |
| `backend/src/main/java/com/loanapproval/dss/staff/StaffReviewService.java` | `REVIEW_QUEUE_STATUSES` đang gồm nhiều trạng thái lifecycle. |
| `frontend/src/features/staff/pages/StaffRequestsPage.jsx` | UI “Hàng đợi thẩm định”. |

Vì sao không ổn: “hàng đợi thẩm định” về nghiệp vụ chỉ nên là hồ sơ cần nhân viên xử lý ở bước thẩm định, chủ yếu là `PENDING`. Hồ sơ `ACTIVE` là khoản vay đang thu nợ, không còn là hồ sơ chờ thẩm định.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Tách màn hình | Một màn “Hàng đợi thẩm định” chỉ lấy `PENDING`. Một màn “Quản lý hồ sơ vay” lấy toàn bộ lifecycle. |
| Đổi label nếu giữ tất cả | Nếu vẫn hiển thị nhiều trạng thái, đổi tên page thành “Quản lý hồ sơ vay”. |
| Bỏ `DISBURSED` nếu không dùng | Vì giải ngân hiện chuyển thẳng `ACTIVE`, nên `DISBURSED` nên được migration về `ACTIVE` hoặc bỏ khỏi filter. |

### P1-04. Danh sách thủ tục thế chấp đang hiển thị cả hồ sơ đã hoàn tất

Hiện trạng: `SecuredLoanProcedureRepository.findSecuredProcedureQueue` lấy mọi hồ sơ `SECURED` không `REJECTED` và không `CLOSED`. Điều này bao gồm cả hồ sơ đã `CONTRACTED`, `ACTIVE` và thủ tục `COMPLETED`.

Vì sao không ổn: danh sách thủ tục cần xử lý nên ưu tiên hồ sơ đã lên lịch hẹn nhưng chưa hoàn tất thủ tục. Hồ sơ đã ký hợp đồng hoặc đang vay nên chuyển sang màn theo dõi khác.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Lọc đúng queue | Chỉ lấy `loan.status IN ('APPOINTMENT_SCHEDULED', 'APPROVED')` và `procedure_status != 'COMPLETED'`. |
| Có tab lịch sử riêng | Hồ sơ `COMPLETED`, `CONTRACTED`, `ACTIVE` nên nằm ở tab lịch sử hoặc chi tiết khoản vay. |
| Đổi tên màn nếu cần | Nếu vẫn muốn thấy tất cả, đổi tên thành “Quản lý thủ tục thế chấp”. |

### P1-05. Khoản nợ hiện tại của khách hàng vẫn là dữ liệu tự khai báo

Hiện trạng: khách hàng tự thêm/xóa khoản nợ qua `CustomerDebtController` và `CustomerDebtService`. DTI được đồng bộ từ danh sách này.

File liên quan:

| File | Vai trò |
| --- | --- |
| `backend/src/main/java/com/loanapproval/dss/debt/CustomerDebtService.java` | CRUD khoản nợ và sync DTI. |
| `backend/src/main/java/com/loanapproval/dss/debt/CustomerDebtController.java` | Customer endpoint thêm/xóa nợ. |

Vì sao chưa đủ: DTI phụ thuộc vào nợ hiện có. Nếu khách hàng có thể tự xóa khoản nợ trước khi tạo hồ sơ, DTI sẽ đẹp hơn thực tế. Nhân viên có xem và xác minh, nhưng hệ thống chưa có trạng thái xác minh riêng cho từng khoản nợ.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Thêm trạng thái debt verification | `DECLARED`, `VERIFIED`, `REJECTED`, `STAFF_ADJUSTED`. |
| Không xóa vật lý nợ đang dùng | Dùng soft delete hoặc versioning để audit thay đổi. |
| DTI dùng dữ liệu đã xác minh | Khi đủ nghiệp vụ, chỉ dùng debt verified hoặc staff-adjusted cho final DSS. |
| Lưu bằng chứng | Cho phép upload sao kê/hợp đồng nợ hoặc ghi nguồn xác minh. |

### P1-06. DSS/risk engine là heuristic nội bộ, chưa có quản trị mô hình

Hiện trạng: `DecisionEngineService` dùng trọng số hard-code, ngưỡng DTI hard-code và điểm tín dụng nội bộ. `LoanEligibilityService` có `POLICY_VERSION`, nhưng chưa có bảng policy version hoặc khả năng truy vết thay đổi theo thời gian.

Vì sao chưa đủ: với hệ thống hỗ trợ quyết định cho vay, cần biết mỗi quyết định dùng phiên bản policy nào, ngưỡng nào, dữ liệu đầu vào nào và ai thay đổi policy. Hiện tại policy nằm trong code nên khó audit khi thay đổi.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Lưu snapshot input | Lưu toàn bộ dữ liệu đầu vào DSS tại thời điểm quyết định. |
| Version hóa policy | Tạo bảng `credit_policies` hoặc file config versioned. |
| Tách score tự khai báo | Không để customer tự cung cấp các biến rủi ro nhạy cảm như credit score nếu không có nguồn xác minh. |
| Thêm reason code chuẩn | Thay explanation text tự do bằng danh sách reason code có cấu trúc. |

### P1-07. Chứng từ hồ sơ vay lưu local nhưng Docker chưa mount volume cho loan documents

Hiện trạng: `LoanDocumentStorageService` lưu chứng từ vào `./storage/loan-documents`. Trong `docker-compose.yml`, backend chỉ mount `backend_profile_documents:/app/storage/profile-documents`, chưa mount `/app/storage/loan-documents`.

Vì sao không ổn: ảnh giấy tờ xe, biển số, CCCD hoặc ảnh mặt có thể mất khi container backend bị rebuild/recreate nếu thư mục không nằm trên volume.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Thêm volume | Mount `backend_loan_documents:/app/storage/loan-documents`. |
| Kiểm tra loại file thật | Không chỉ dựa vào extension; kiểm tra magic bytes/MIME. |
| Thêm antivirus scan nếu production | Với chứng từ khách hàng, nên có scan trước khi lưu dài hạn. |
| Tách object storage | Production nên dùng S3/MinIO/GCS với encryption và retention policy. |

### P1-08. Hợp đồng vay chưa có lịch khấu hao chuẩn

Hiện trạng: `LoanContractService` tính `monthlyPayment` bằng EMI và `totalInterest = monthlyPayment * termMonths - principal`. Hợp đồng chưa lưu breakdown gốc/lãi từng kỳ.

Vì sao chưa đủ: app cho vay cần lịch khấu hao để biết mỗi kỳ trả bao nhiêu gốc, bao nhiêu lãi, còn lại bao nhiêu gốc, xử lý tất toán trước hạn và phạt trễ ra sao.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Sinh amortization schedule | Khi tạo hợp đồng, tạo luôn `loan_installments`. |
| Lưu principal/interest từng kỳ | Mỗi kỳ có `principal_due`, `interest_due`, `remaining_principal_after_due`. |
| Xử lý rounding cuối kỳ | Dồn sai số rounding vào kỳ cuối. |
| Hỗ trợ tất toán trước hạn | Tính outstanding principal, accrued interest, fee nếu có. |

### P1-09. Audit có tồn tại nhưng chưa đủ bất biến và chưa đủ bao phủ

Hiện trạng: hệ thống có `decision_audits` và `compliance_audit_logs`. Tuy nhiên admin delete có thể xóa audit, một số trạng thái lifecycle chỉ cập nhật trạng thái mà không có bảng lịch sử trạng thái đầy đủ.

Vì sao chưa đủ: với khoản vay, cần truy vết được toàn bộ chuyển trạng thái, actor, timestamp, old/new value và lý do. Audit log không nên bị xóa bởi tác vụ quản trị thông thường.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Thêm `loan_status_history` | Lưu `from_status`, `to_status`, `actor_id`, `reason`, `created_at`. |
| Append-only audit | Không hard-delete audit. |
| Audit field-level changes | Với điều khoản vay, lưu old/new amount, term, rate, monthly payment. |
| Expose audit cho staff/admin | UI chi tiết hồ sơ nên có tab lịch sử quyết định và trạng thái. |

### P2-01. API và UI vẫn có vài điểm chưa nhất quán về ngôn ngữ/định danh

Hiện trạng: nhiều thông báo backend còn không dấu như `Khong tim thay ho so vay`, `Vui long...`. Một số DTO như `CreateRepaymentRequest.paidAt` còn tồn tại nhưng backend không dùng vì đã chuyển sang server time/header demo.

Vì sao chưa ổn: demo với thầy có thể vẫn ổn, nhưng người đọc API sẽ thấy field gây hiểu nhầm. Nếu môi trường terminal/editor đọc sai encoding, chuỗi tiếng Việt có thể hiện lỗi.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Chuẩn hóa message | Dùng tiếng Việt có dấu thống nhất hoặc dùng error code + frontend mapping. |
| Xóa field không dùng | Bỏ `paidAt` khỏi `CreateRepaymentRequest` nếu không dùng. |
| Bắt buộc UTF-8 | Đảm bảo Maven, source file, editorconfig và Docker đều dùng UTF-8. |
| Frontend map error code | Backend trả `code`, frontend quyết định text hiển thị. |

### P2-02. Pagination đã có API nhưng nhiều màn vẫn gọi list toàn bộ

Hiện trạng: backend có các endpoint `/paged` cho loans, payments, staff requests. Tuy nhiên một số UI vẫn gọi list toàn bộ như `getMyLoansApi`, `getMyPaymentsApi`, `getStaffRequestsApi`.

Vì sao chưa ổn: khi nhiều hồ sơ/thanh toán, UI sẽ tải chậm và backend trả payload lớn.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Dùng endpoint paged ở UI | Thêm phân trang ở các bảng loan/payment/staff request. |
| Thêm filter server-side | Filter theo trạng thái, ngày, customer, loan type. |
| Chuẩn hóa response | Các list chính nên dùng cùng `PageResponse`. |

### P2-03. Luồng xác minh trước khi tạo khoản vay có thể chưa đúng với app thực tế

Hiện trạng: khách hàng phải được nhân viên duyệt thông tin cá nhân trước khi tạo hồ sơ vay. Điều này làm luồng demo rõ ràng, nhưng trong nhiều app thật, khách hàng nộp hồ sơ vay trước, rồi hệ thống mới mở checklist xác minh cho chính hồ sơ đó.

Vì sao cần cân nhắc: nếu dùng app cho sản phẩm thật, việc bắt nhân viên duyệt profile trước mọi hồ sơ vay có thể tạo thêm một queue riêng, làm chậm onboarding và khó giải thích cho khách hàng.

Hướng xử lý:

| Việc cần làm | Gợi ý triển khai |
| --- | --- |
| Giữ nếu là yêu cầu bài toán | Nếu thầy yêu cầu xác minh profile trước, tài liệu nghiệp vụ nên ghi rõ đây là bước tiền điều kiện. |
| Hoặc chuyển verification theo loan | Tạo hồ sơ vay ở `DRAFT/PENDING_VERIFICATION`, sau đó staff xác minh theo từng khoản vay. |
| Không dùng profile verification thay thế loan verification | Nếu profile đổi sau này, cần biết hồ sơ vay nào đã dùng snapshot nào. |

## 5. Ưu tiên xử lý đề xuất

| Ưu tiên | Việc nên làm | Lý do |
| --- | --- | --- |
| P0 | Gate `X-Demo-Now` bằng `app.demo.enabled` | Tránh demo bypass chạy trong luồng thật. |
| P0 | Sửa thủ tục thế chấp để DSS đánh giá theo đúng lãi suất/thanh toán hợp đồng | Đây là bypass điều khoản vay còn lại. |
| P0 | Không để customer tự xác nhận thanh toán thật | Đây là sai khác lớn nhất so với app cho vay thực tế. |
| P0 | Đổi hard delete sang soft delete và giữ audit | Dữ liệu tài chính không nên bị xóa vật lý. |
| P1 | Tách hàng đợi thẩm định khỏi quản lý lifecycle | Giảm nhầm lẫn nghiệp vụ và UI. |
| P1 | Tạo installment ledger và job quá hạn | Cần cho logic thu nợ đúng. |
| P1 | Mount volume cho `loan-documents` | Tránh mất chứng từ khi rebuild container. |
| P2 | Chuẩn hóa message/API/UTF-8 | Tăng chất lượng demo và maintainability. |

## 6. Lộ trình sửa hợp lý

### Giai đoạn 1: Làm sạch luồng demo và UI nghiệp vụ

Thêm `app.demo.enabled`, ẩn demo controls khi tắt demo, chặn `X-Demo-Now` ở production, đổi “Hàng đợi thẩm định” để chỉ hiển thị hồ sơ thật sự cần xử lý hoặc đổi tên màn thành “Quản lý hồ sơ vay”.

Thêm volume `backend_loan_documents` vào Docker Compose và đặt rõ biến `APP_BOOTSTRAP_DEMO_ENABLED=true` chỉ trong môi trường demo.

### Giai đoạn 2: Khóa các bypass nghiệp vụ

Sửa `SecuredLoanProcedureService` để điều khoản hợp đồng cuối cùng phải là điều khoản vừa được DSS đánh giá. Backend tự tính `monthlyPaymentAmount`, hoặc nếu nhân viên nhập thì phải validate sai số so với công thức.

Tách customer payment request khỏi payment confirmation. Với demo, vẫn có thể giữ nút ghi nhận thanh toán nhưng đặt dưới endpoint demo hoặc vai trò staff/accounting.

### Giai đoạn 3: Nâng logic cho vay/thanh toán

Tạo bảng installment schedule khi hợp đồng active. Mọi thanh toán phân bổ vào installment cũ nhất. Thêm job đánh dấu quá hạn, thêm idempotency key và khóa giao dịch khi ghi nhận thanh toán.

Version hóa policy DSS và lưu snapshot dữ liệu đầu vào để người đọc có thể audit lại vì sao hồ sơ được duyệt/từ chối.

### Giai đoạn 4: Production hardening

Tắt default demo/admin ở production, bắt buộc cấu hình JWT secret riêng, rate limit login, soft delete user, append-only audit, object storage cho chứng từ, test integration cho toàn bộ lifecycle.

## 7. Checklist đọc project cho người tiếp theo

| Câu hỏi cần kiểm tra | File bắt đầu đọc |
| --- | --- |
| Khách hàng tạo khoản vay như thế nào? | `CustomerLoanService.java`, `CustomerLoanController.java` |
| DSS lấy dữ liệu gì để chấm điểm? | `DecisionEngineService.java`, `LoanEligibilityService.java` |
| Nhân viên duyệt/từ chối ra sao? | `StaffReviewService.java`, `StaffReviewRepository.java` |
| Vay thế chấp đi qua lịch hẹn thế nào? | `SecuredLoanProcedureService.java`, `SecuredLoanProcedureRepository.java` |
| Hợp đồng được tạo thế nào? | `LoanContractService.java`, `LoanContractRepository.java` |
| Thanh toán tính dư nợ ra sao? | `RepaymentService.java`, `RepaymentScheduleService.java`, `RepaymentRepository.java` |
| Dữ liệu demo được reset ở đâu? | `DemoDataBootstrapInitializer.java`, `application.yml`, `docker-compose.yml` |
| Audit và xóa tài khoản hoạt động thế nào? | `ComplianceAuditService.java`, `AdminUserRepository.java` |

## 8. Tóm tắt để trình bày ngày mai

Ứng dụng hiện phù hợp để demo một hệ thống hỗ trợ ra quyết định cho vay, nhưng chưa nên xem là app cho vay production. Các điểm đã cải thiện tốt là vay thế chấp không còn tự động duyệt, nhân viên sửa điều khoản thì DSS chạy lại, thu nhập đã xác minh được ưu tiên, thủ tục thế chấp có lịch hẹn và giá trị thẩm định, khoản vay sau giải ngân chuyển sang `ACTIVE`.

Các vấn đề cần nói rõ khi trình bày là: demo time đang là bypass nếu không khóa bằng cấu hình; khách hàng đang tự ghi nhận thanh toán; hợp đồng thế chấp vẫn có nguy cơ dùng lãi suất/thanh toán khác với điều khoản DSS đã đánh giá; hàng đợi thẩm định đang lẫn hồ sơ đã active; dữ liệu và audit bị hard-delete khi admin xóa user; logic thu nợ chưa có installment ledger và chưa tự đánh dấu quá hạn.

Hướng giải quyết là tách demo khỏi production, khóa lại điều khoản hợp đồng cuối cùng, chuyển thanh toán sang mô hình transaction/confirmation, tạo lịch trả nợ từng kỳ, soft delete dữ liệu tài chính, version hóa policy DSS và bổ sung audit/lifecycle history.
