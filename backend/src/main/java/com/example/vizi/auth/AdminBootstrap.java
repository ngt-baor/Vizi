package com.example.vizi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a local admin account exists for development.
 * Override with VIZI_ADMIN_EMAIL / VIZI_ADMIN_PASSWORD env vars.
 * Never hardcode production secrets - only bootstrap when configured.
 */
@Component
@Profile("local")
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${vizi.admin.email:admin@vizi.local}") String adminEmail,
            @Value("${vizi.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Local admin bootstrap skipped because VIZI_ADMIN_PASSWORD is not configured");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        userRepository.save(new User(
                adminEmail.trim().toLowerCase(),
                passwordEncoder.encode(adminPassword),
                "Vizi Admin",
                "ADMIN"
        ));
        log.info("Bootstrap admin account created: {}", adminEmail);
    }
}
