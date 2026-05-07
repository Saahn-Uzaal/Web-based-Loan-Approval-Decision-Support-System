package com.loanapproval.dss.shared;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DemoTimeResolver {

    private static final Logger log = LoggerFactory.getLogger(DemoTimeResolver.class);

    private final boolean demoEnabled;

    public DemoTimeResolver(@Value("${app.demo.enabled:false}") boolean demoEnabled) {
        this.demoEnabled = demoEnabled;
        if (demoEnabled) {
            log.warn("Demo time resolver is ENABLED — X-Demo-Now header will be accepted. "
                    + "Disable this in production by setting app.demo.enabled=false");
        }
    }

    public Instant resolveEffectiveNow(String headerValue) {
        if (!demoEnabled || headerValue == null || headerValue.isBlank()) {
            return Instant.now();
        }

        try {
            Instant parsed = Instant.parse(headerValue.trim());
            log.debug("Demo time override: effectiveNow={}", parsed);
            return parsed;
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian giả lập không hợp lệ. Vui lòng dùng định dạng ISO-8601.");
        }
    }
}

