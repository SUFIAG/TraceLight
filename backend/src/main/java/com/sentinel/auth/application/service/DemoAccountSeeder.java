package com.sentinel.auth.application.service;

import com.sentinel.auth.adapter.out.persistence.UserRepository;
import com.sentinel.auth.domain.model.User;
import com.sentinel.auth.domain.model.UserRole;
import com.sentinel.security.adapter.out.persistence.UserSecurityRepository;
import com.sentinel.security.domain.model.UserSecurity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates one sign-in account per role on first start.
 *
 * <p>Registration through the API only ever mints an ANALYST, so without this a
 * fresh database has nobody who can approve a model, promote a rule, raise an
 * autonomy level, or issue an API key — half the platform would be unreachable
 * on a demo machine.
 *
 * <p>These are development accounts with a published password. The seeder is
 * governed by {@code sentinel.demo-accounts.enabled}; turn it off for any
 * deployment that is not a local demo, and set
 * {@code SENTINEL_DEMO_PASSWORD} if the default must not be used.
 *
 * <p>Every start re-asserts the password and role of these four addresses, so
 * the documented credentials always work even on a database seeded by an
 * earlier build. Only these four addresses are touched; accounts created
 * through registration are never modified.
 */
@Configuration
@ConditionalOnProperty(name = "sentinel.demo-accounts.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoAccountSeeder {

    private record Seed(String email, String firstName, String lastName, UserRole role) {
    }

    private static final List<Seed> ACCOUNTS = List.of(
            new Seed("admin@sentinel.ai", "Avery", "Admin", UserRole.ADMIN),
            new Seed("reviewer@sentinel.ai", "Rowan", "Reviewer", UserRole.REVIEWER),
            new Seed("analyst@sentinel.ai", "Alex", "Analyst", UserRole.ANALYST),
            new Seed("auditor@sentinel.ai", "Ash", "Auditor", UserRole.AUDITOR)
    );

    @Bean
    ApplicationRunner seedDemoAccounts(UserRepository userRepository,
                                       UserSecurityRepository userSecurityRepository,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${sentinel.default-organization-id}") UUID organizationId,
                                       @Value("${sentinel.demo-accounts.password:Sentinel@123}") String password) {
        return args -> {
            for (Seed seed : ACCOUNTS) {
                User user = userRepository.findByEmailAndOrganizationId(seed.email(), organizationId)
                        .orElseGet(() -> User.builder()
                                .organizationId(organizationId)
                                .email(seed.email())
                                .firstName(seed.firstName())
                                .lastName(seed.lastName())
                                .build());
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setRole(seed.role());
                user.setEnabled(true);
                User saved = userRepository.save(user);

                // Demo accounts arrive verified and unlocked. Nobody can click a
                // link in a mailbox that does not exist, and a locked demo
                // account looks like a broken build rather than a working control.
                UserSecurity security = userSecurityRepository.findById(saved.getId())
                        .orElseGet(() -> UserSecurity.builder()
                                .userId(saved.getId())
                                .organizationId(organizationId)
                                .build());
                security.setOrganizationId(organizationId);
                security.setEmailVerified(true);
                if (security.getEmailVerifiedAt() == null) security.setEmailVerifiedAt(Instant.now());
                if (security.getPasswordChangedAt() == null) security.setPasswordChangedAt(Instant.now());
                security.setMustChangePassword(false);
                security.setFailedAttempts(0);
                security.setLockedUntil(null);

                // Demo accounts also lose any second factor on restart. An
                // authenticator enrolled against a shared demo account is a
                // trap: the next person to use the account cannot sign in, and
                // there is no second administrator to reset it for them.
                security.setMfaEnabled(false);
                security.setMfaSecret(null);
                security.setMfaMethod(null);
                security.setBackupCodes(new ArrayList<>());
                userSecurityRepository.save(security);
            }
            log.warn("{} demo accounts are active with a shared, published password. " +
                    "Set sentinel.demo-accounts.enabled=false outside local development.", ACCOUNTS.size());
        };
    }
}
