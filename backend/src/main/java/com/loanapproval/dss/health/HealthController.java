package com.loanapproval.dss.health;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final Environment environment;

    @Value("${spring.application.name:loan-dss-backend}")
    private String applicationName;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", applicationName);
        response.put("profiles", environment.getActiveProfiles());
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
