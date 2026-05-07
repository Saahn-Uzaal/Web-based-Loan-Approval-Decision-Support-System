package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentDueReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentDueReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean createIfMissing(
            Long loanRequestId,
            Long customerId,
            Integer installmentNumber,
            LocalDate dueDate,
            BigDecimal amountDue) {
        int updated = jdbcTemplate.update(
                """
                INSERT IGNORE INTO loan_payment_due_reminders (
                    loan_request_id,
                    customer_id,
                    installment_number,
                    due_date,
                    amount_due,
                    reminder_sent_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                loanRequestId,
                customerId,
                installmentNumber,
                java.sql.Date.valueOf(dueDate),
                amountDue);
        return updated > 0;
    }
}
