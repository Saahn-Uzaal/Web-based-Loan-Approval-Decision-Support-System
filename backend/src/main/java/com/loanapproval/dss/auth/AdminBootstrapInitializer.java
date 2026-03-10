package com.loanapproval.dss.auth;

import com.loanapproval.dss.shared.Role;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final String LEGACY_DEFAULT_ADMIN_EMAIL = "admin@loan.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapInitializer(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap.admin.enabled:true}") boolean enabled,
        @Value("${app.bootstrap.admin.email:admin@gmail.com}") String adminEmail,
        @Value("${app.bootstrap.admin.password:123456}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            logger.warn("Skipping admin bootstrap because admin credentials are empty");
            return;
        }

        if (tryMigrateLegacyDefaultAdmin(normalizedEmail, adminPassword)) {
            return;
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }

        userRepository.create(normalizedEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
        logger.info("Bootstrapped default admin account: {}", normalizedEmail);
    }

    private boolean tryMigrateLegacyDefaultAdmin(String normalizedEmail, String rawPassword) {
        if (LEGACY_DEFAULT_ADMIN_EMAIL.equals(normalizedEmail)) {
            return false;
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            return false;
        }

        return userRepository.findByEmail(LEGACY_DEFAULT_ADMIN_EMAIL)
            .filter(account -> account.role() == Role.ADMIN)
            .map(account -> {
                int updated = userRepository.updateEmailAndPassword(
                    account.id(),
                    normalizedEmail,
                    passwordEncoder.encode(rawPassword)
                );
                if (updated > 0) {
                    logger.info("Migrated legacy default admin account from {} to {}", LEGACY_DEFAULT_ADMIN_EMAIL, normalizedEmail);
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}
