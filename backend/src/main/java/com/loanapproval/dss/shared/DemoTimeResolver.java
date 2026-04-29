package com.loanapproval.dss.shared;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DemoTimeResolver {

    public Instant resolveEffectiveNow(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(headerValue.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian giả lập không hợp lệ. Vui lòng dùng định dạng ISO-8601.");
        }
    }
}

