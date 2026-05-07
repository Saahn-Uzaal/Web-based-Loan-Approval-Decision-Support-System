package com.loanapproval.dss.staff.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StaffAppointmentRequest(
        Instant scheduledAt,
        @Size(max = 255) String location,
        @Size(max = 1000) String note) {
}
