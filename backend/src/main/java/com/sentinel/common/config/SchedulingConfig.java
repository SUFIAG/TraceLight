package com.sentinel.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the background sweeps: cooling-off hold expiry, perpetual KYC
 * re-verification, mule rescoring, drift checks, and webhook retries.
 *
 * <p>Gated on a flag so a second instance can be started with scheduling off
 * when the deployment does not want every node running the same sweep.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sentinel.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
