package com.sentinel.security.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the deployment's own configuration at startup.
 *
 * <p>Most production incidents in a system like this are not clever attacks;
 * they are a development default that survived the move to a real
 * environment — the shared signing key, the seeded accounts, the verification
 * codes echoed in API responses.
 *
 * <p>Outside production these are warnings, because they are what makes a
 * local instance usable. With the {@code prod} profile active they stop the
 * application: refusing to start is recoverable, running unprotected is not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityStartupCheck implements ApplicationRunner {

    private static final String DEFAULT_JWT_SECRET =
            "SentinelAI-Development-Only-Secret-Key-Replace-In-Production-At-Least-256-Bits";

    private final Environment environment;
    private final SecurityProperties properties;
    private final NotificationSender notificationSender;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${sentinel.demo-accounts.enabled:true}")
    private boolean demoAccountsEnabled;

    @Value("${sentinel.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        List<String> problems = new ArrayList<>();

        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET is the published development key. Anybody can mint a valid token.");
        }
        if (jwtSecret != null && jwtSecret.getBytes().length < 32) {
            problems.add("JWT_SECRET is shorter than 256 bits.");
        }
        if (demoAccountsEnabled) {
            problems.add("Demo accounts are seeded with a published password "
                    + "(SENTINEL_DEMO_ACCOUNTS_ENABLED=false to stop).");
        }
        if (properties.isExposeVerificationCodes()) {
            problems.add("Verification codes are returned in API responses "
                    + "(sentinel.security.expose-verification-codes=false to stop).");
        }
        for (String origin : allowedOrigins) {
            if ("*".equals(origin)) {
                problems.add("CORS allows any origin.");
            }
        }
        if (notificationSender instanceof NotificationSender.LoggingNotificationSender) {
            problems.add("No mail or SMS provider is configured; codes are written to the log.");
        }

        if (problems.isEmpty()) {
            log.info("Security configuration check passed");
            return;
        }

        boolean production = List.of(environment.getActiveProfiles()).contains("prod");
        if (production) {
            problems.forEach(problem -> log.error("SECURITY: {}", problem));
            throw new IllegalStateException(
                    "Refusing to start with " + problems.size()
                            + " insecure setting(s) under the prod profile. See the errors above.");
        }

        log.warn("Security configuration is set up for development, not production:");
        problems.forEach(problem -> log.warn("  - {}", problem));
    }
}
