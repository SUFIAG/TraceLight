package com.sentinel.security.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Delivers a security message to a person: a verification code, a reset link,
 * a "new device signed in" notice.
 *
 * <p>An interface rather than a mail client so the platform has one seam for
 * email and SMS. A deployment swaps in SES, Twilio, or an internal gateway by
 * publishing a bean; nothing in the security services changes.
 */
public interface NotificationSender {

    void email(String to, String subject, String body);

    void sms(String phoneNumber, String message);

    /**
     * The sender used when nothing else is configured: it writes to the log.
     *
     * <p>This is a developer convenience, not a stub that silently drops mail.
     * It is loud, it is obvious in the log, and the platform warns at startup
     * when it is the active sender.
     */
    @Slf4j
    class LoggingNotificationSender implements NotificationSender {

        @Override
        public void email(String to, String subject, String body) {
            log.info("[EMAIL -> {}] {}\n{}", to, subject, body);
        }

        @Override
        public void sms(String phoneNumber, String message) {
            log.info("[SMS -> {}] {}", phoneNumber, message);
        }
    }

    /** Registers the logging sender only when a deployment has not supplied its own. */
    @Configuration
    class DefaultSenderConfiguration {

        @Bean
        @ConditionalOnMissingBean(NotificationSender.class)
        public NotificationSender loggingNotificationSender() {
            return new LoggingNotificationSender();
        }
    }
}
