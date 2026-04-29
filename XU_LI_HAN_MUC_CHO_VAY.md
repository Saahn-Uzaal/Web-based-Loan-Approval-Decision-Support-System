# Xử Lý Hạn Mức Cho Vay

Tài liệu này tổng hợp cách tính hạn mức theo nghiệp vụ cho vay, những gì project hiện còn thiếu, và cách xác minh thu nhập để có thể dùng đúng cho việc xét hạn mức.

Ngày tổng hợp: 14/04/2026

## 1. Nguyên tắc nghiệp vụ chung

Không có một công thức duy nhất cho mọi ngân hàng hoặc nền tảng cho vay. Thực tế, hạn mức cuối cùng thường là giá trị nhỏ nhất trong nhiều trần kiểm soát:

```text
Hạn mức cuối = min(
  trần theo khả năng trả nợ,
  trần theo bội số thu nhập,
  trần theo tài sản bảo đảm,
  trần theo chính sách sản phẩm,
  trần theo mức rủi ro nội bộ
)
```

Các thành phần thường gặp:

- `Trần theo khả năng trả nợ`: dựa trên thu nhập được công nhận và nghĩa vụ nợ hiện hữu.
- `Trần theo bội số thu nhập`: ví dụ tối đa một số lần lương/tháng hoặc thu nhập bình quân.
- `Trần theo tài sản bảo đảm`: áp dụng với khoản vay có thế chấp, theo `LTV`.
- `Trần theo sản phẩm`: ví dụ sản phẩm chỉ cho vay tối đa đến một số tiền cố định.
- `Trần theo rủi ro`: điều chỉnh xuống nếu CIC, lịch sử trả nợ, nghề nghiệp hoặc hồ sơ xác minh không tốt.

## 2. Công thức triển khai thực tế

### 2.1. Thu nhập được công nhận

Không nên dùng trực tiếp số khách hàng tự kê khai. Cần tách:

- `declared income`: thu nhập khách tự khai
- `verified income`: thu nhập đã xác minh

Một công thức bảo thủ:

```text
verified_income =
  lương cố định bình quân 3 tháng
  + 50% đến 70% phần thu nhập biến động bình quân 6 tháng
```

Gợi ý:

- Nếu thu nhập ổn định: lấy `median` hoặc `average` 3 tháng gần nhất.
- Nếu có OT, hoa hồng, thưởng: không cộng 100% toàn bộ phần biến động.
- Không lấy tháng cao nhất làm chuẩn.

### 2.2. Trần theo khả năng trả nợ

```text
monthly_capacity =
  verified_income x tỷ lệ an toàn
  - nghĩa vụ nợ hiện hữu hàng tháng
```

Trong nhiều mô hình thực tế, tỷ lệ an toàn thường được khống chế quanh mức mà tổng tiền trả nợ hàng tháng không chiếm quá cao trong thu nhập.

Sau đó quy đổi từ `monthly_capacity` sang số tiền vay tối đa theo:

- kỳ hạn
- lãi suất áp dụng
- phương thức trả nợ

```text
max_by_cashflow = PV(monthly_capacity, annual_rate, term_months)
```

### 2.3. Trần theo bội số thu nhập

```text
max_by_income_multiple = verified_income x income_multiple
```

Ví dụ chính sách nội bộ có thể quy định:

- vay tín chấp: tối đa `8x`, `10x`, `12x` thu nhập tháng
- nhóm rủi ro cao hơn: giảm xuống `4x` hoặc `6x`

### 2.4. Trần theo tài sản bảo đảm

```text
max_by_ltv = collateral_value x max_ltv
```

Ví dụ:

- vay mua nhà: `70%` đến `85%` giá trị tài sản tùy chính sách
- vay mua xe: khoảng `70%` đến `80%`

### 2.5. Hạn mức đủ điều kiện

```text
eligible_limit = min(
  max_by_cashflow,
  max_by_income_multiple,
  max_by_ltv,
  product_cap
)
```

## 3. Project hiện đang thiếu gì

Hiện tại project chủ yếu đang:

- chấm điểm DSS
- tính DTI
- gợi ý `APPROVE / REJECT`

Project chưa thật sự tính ra `hạn mức được cấp`.

### 3.1. Thiếu đầu ra hạn mức

Hệ thống hiện chưa có các trường như:

- `eligible_limit`
- `approved_amount`
- `approved_term_months`
- `approved_annual_rate`
- `assessed_income`

Hiện `loan_requests` mới lưu số tiền khách yêu cầu, chưa lưu số tiền được đề xuất hoặc được cấp.

### 3.2. Thiếu nguồn thu nhập đã xác minh

Project hiện đã có upload phiếu lương, nhưng mới lưu file làm chứng từ. Chưa có:

- OCR/trích xuất dữ liệu phiếu lương
- đối chiếu sao kê lương
- thu nhập xác minh cuối cùng do staff chốt

Nếu dùng `monthly_income` mà không rõ đó là số tự khai hay số đã xác minh thì nghiệp vụ hạn mức sẽ không đáng tin.

### 3.3. Thiếu policy theo sản phẩm

Cần có bảng hoặc cấu hình nghiệp vụ riêng cho từng sản phẩm:

- `max_term_months`
- `max_income_multiple`
- `max_dsr_or_dti`
- `max_ltv`
- `product_cap`
- `rate_rule`
- `risk_adjustment_rule`

Hiện project đang dùng lãi suất mặc định chung, chưa đủ cho việc tính hạn mức đúng sản phẩm.

### 3.4. Thiếu luồng phản đề xuất

Nghiệp vụ thực tế thường có trường hợp:

- khách yêu cầu `500 triệu`
- hệ thống chỉ đủ điều kiện `350 triệu`

Khi đó cần:

- `approve with reduced amount`
- hoặc `counter-offer`

Project hiện chủ yếu duyệt hoặc từ chối toàn bộ yêu cầu.

## 4. Cách xác minh rằng khách hàng kê khai thu nhập chính xác

Không có cách nào xác minh 100% chỉ bằng một phiếu lương. Cách đúng là `đối chiếu nhiều nguồn`.

### 4.1. Bộ hồ sơ nên yêu cầu

- Hợp đồng lao động hoặc xác nhận công tác
- Phiếu lương
- Sao kê tài khoản nhận lương 3 đến 6 tháng
- Thông tin BHXH qua VssID hoặc dữ liệu tương đương
- Dữ liệu thuế TNCN qua eTax Mobile hoặc dữ liệu tương đương
- CIC để đối chiếu nghĩa vụ tín dụng hiện hữu

### 4.2. Rule đối chiếu

- Tên công ty trên phiếu lương phải khớp với hợp đồng/xác nhận công tác
- Tài khoản nhận lương trên sao kê phải có dòng tiền phù hợp với phiếu lương
- Chu kỳ trả lương phải hợp lý theo tháng
- Quá trình đóng BHXH phải phù hợp với nơi làm việc và thời gian công tác
- Thuế TNCN phải không quá lệch so với thu nhập khai báo
- CIC phải phù hợp với các khoản nợ khách đã khai

### 4.3. Dấu hiệu nghi ngờ

- Mới nhận lương 1 tháng nhưng khai thu nhập ổn định lâu dài
- Phiếu lương và sao kê không khớp nhau
- Nhiều phiếu lương trùng kỳ nhưng số liệu bất thường
- Thu nhập tăng đột biến ngay trước khi vay
- File phiếu lương có dấu hiệu chỉnh sửa
- CIC cho thấy nghĩa vụ nợ cao hơn nhiều so với phần khách tự kê khai

### 4.4. Cách chốt thu nhập xác minh

Sau khi đối chiếu, staff hoặc engine nên chốt:

- `verifiedMonthlyIncome`
- `incomeVerificationStatus`
- `incomeVerificationMethod`
- `incomeVerificationNote`
- `incomeVerifiedAt`
- `incomeVerifiedBy`

Đây mới là nguồn dữ liệu dùng để tính hạn mức.

## 5. Đề xuất field nên bổ sung

### 5.1. Customer profile / income verification

- `declaredMonthlyIncome`
- `verifiedMonthlyIncome`
- `incomeVerificationStatus`
- `incomeVerificationMethod`
- `incomeVerificationNote`
- `employerName`
- `salaryBankName`
- `salaryBankAccount`
- `employmentType`
- `employmentStartDate`

### 5.2. Loan request / loan decision

- `requestedAmount`
- `eligibleLimit`
- `approvedAmount`
- `approvedTermMonths`
- `approvedAnnualRate`
- `approvedMonthlyPayment`
- `decisionPolicyVersion`
- `decisionSnapshot`

## 6. Thứ tự triển khai hợp lý cho project

### Giai đoạn 1: Chuẩn hóa dữ liệu đầu vào

- Bổ sung thông tin nghề nghiệp, công ty, thu nhập khai báo
- Tách `declared income` và `verified income`
- Cho phép staff chốt thu nhập xác minh

### Giai đoạn 2: Policy sản phẩm

- Tạo bảng `loan_product_policy`
- Khai báo rule riêng cho:
  - vay tín chấp
  - vay mua xe
  - vay mua nhà
  - vay kinh doanh

### Giai đoạn 3: Eligibility engine

- Viết `LoanEligibilityService`
- Tính:
  - `max_by_cashflow`
  - `max_by_income_multiple`
  - `max_by_ltv`
  - `eligible_limit`

### Giai đoạn 4: Luồng phê duyệt

- Cho phép `approve with reduced amount`
- Cho phép `counter-offer`
- Lưu `approvedAmount` thay vì chỉ lưu `amount` khách yêu cầu

### Giai đoạn 5: Giao diện

- Ở màn hình khách hàng:
  - hiển thị hạn mức tạm tính
  - cảnh báo nếu số tiền yêu cầu vượt hạn mức
- Ở màn hình staff:
  - hiển thị giải thích từng trần hạn mức
  - hiển thị thu nhập xác minh và nguồn xác minh

## 7. Kết luận

Để project đúng với nghiệp vụ xử lý hạn mức cho vay, điều quan trọng nhất là:

1. Không dùng trực tiếp số thu nhập khách tự khai để tính hạn mức
2. Phải có `verified income`
3. Phải có `eligibility engine` riêng, không chỉ DSS scoring
4. Phải lưu được `eligible_limit` và `approved_amount`
5. Phải cho phép duyệt với số tiền thấp hơn số khách yêu cầu

## 8. Nguồn tham khảo chính thức

- VIB vay tín chấp: https://www.vib.com.vn/vn/vay-tieu-dung/%21ut/p/z1/04_Sj9CPykssy0xPLMnMz0vMAfIjo8zivRxNPQ2dLYy8LUz9LQwcXT09_VxCPY1CnU31w9EUhFi6ABX4Gni7-_sZGwSa6kcRo98AB3A0IE4_HgVR-I0P149CswLTB4TMKMgNDY0wyHQEACclygI%21/
- VIB vay mua xe: https://www.vib.com.vn/vn/vay-mua-xe
- HSBC unsecured loan: https://www.hsbc.com.vn/en-vn/loans/what-is-unsecured-loan/
- HSBC home loan: https://www.hsbc.com.vn/en-vn/loans/products/home/
- CIMB vay theo hạn mức qua F88: https://www.cimbbank.com.vn/content/dam/cimbvn/personal/documents/cecl/f88/faq/20250730-CIMB-F88-CECL-FAQs-Clean.pdf
- BHXH Việt Nam / VssID: https://baohiemxahoi.gov.vn/tintuc/Pages/chuyen-doi-so.aspx?CateID=52&ItemID=15723
- Tổng cục Thuế / eTax Mobile: http://www.gdt.gov.vn/wps/portal/!ut/p/z1/vVJNc4IwFPwrXjgyeSRAwlFtFRw_xlpFcmEQEWklaM1A---NttObYMexOeTlzezbnd0XxFFg2YwQg9oULREXUZmlkcwKEe1UH3A7BNPxhmwxm3TcOQXPoo7FnnqkP6XIvwC6_bZr0iEAM_sAntmZjN3u1ACPIH7LPFw5bWiaXyCOeCzkXm5RkK5lKy6ETITU4BiFqr9UkVTH30eYKEiRf2mAAWMN5DYSacvQgFDs0MTZ6LHKQzdxjHWH2oa6gK7YJqaryDyr7eNsjYKb0H5TfLze_ADxdFesvheRvR0OvK3cnh1-SrT8B7v-2XCNBbB_AHU_oMlloFKiVyVsG_llllRoLoqPXEUx--MS3EYFcqdCPf0IHkuPH0tv3Emf811vUI5Zmfv6-wurXjfbNA9Hz8S6lOoEjJKmPw!!/dz/d5/L2dBISEvZ0FBIS9nQSEh/?1dmy=&current=true&urile=wcm%3Apath%3A%2Fgdt%2Bcontent%2Fsa_gdt%2Fsa_news%2Fsa_news_tax%2F2022%2Fthang%2B3%2F7b142c1d-ec02-4312-856c-350180d7ede3
