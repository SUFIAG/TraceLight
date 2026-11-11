package com.sentinel.security.domain.model;

/** How much attention an event deserves. Ordered from quietest to loudest. */
public enum SecuritySeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
