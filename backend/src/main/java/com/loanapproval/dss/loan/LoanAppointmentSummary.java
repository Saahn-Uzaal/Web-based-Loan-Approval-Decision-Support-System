package com.loanapproval.dss.loan;

import java.time.Instant;

public class LoanAppointmentSummary {
    private final Long id;
    private final Instant scheduledAt;
    private final String location;
    private final String note;
    private final String status;
    private final Instant createdAt;

    public LoanAppointmentSummary(
            Long id,
            Instant scheduledAt,
            String location,
            String note,
            String status,
            Instant createdAt) {
        this.id = id;
        this.scheduledAt = scheduledAt;
        this.location = location;
        this.note = note;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getLocation() {
        return location;
    }

    public String getNote() {
        return note;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
