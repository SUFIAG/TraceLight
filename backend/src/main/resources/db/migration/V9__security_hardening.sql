-- Security hardening: account protection, MFA, verification, sessions, and a
-- security event log. Everything here is per-user state that must survive a
-- restart; nothing security-relevant is kept in memory alone, because an
-- attacker who can cause a restart must not be able to clear a lockout.

-- Per-user security posture. One row per user, created on demand.
CREATE TABLE user_security (
    user_id              UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    organization_id      UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

    -- Brute-force protection
    failed_attempts      INT NOT NULL DEFAULT 0,
    last_failed_at       TIMESTAMP,
    locked_until         TIMESTAMP,
    lockout_count        INT NOT NULL DEFAULT 0,

    -- Multi-factor authentication
    mfa_enabled          BOOLEAN NOT NULL DEFAULT false,
    mfa_method           VARCHAR(20),
    mfa_secret           VARCHAR(255),
    mfa_enrolled_at      TIMESTAMP,
    backup_codes         JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Contact verification
    email_verified       BOOLEAN NOT NULL DEFAULT false,
    email_verified_at    TIMESTAMP,
    phone_number         VARCHAR(32),
    phone_verified       BOOLEAN NOT NULL DEFAULT false,
    phone_verified_at    TIMESTAMP,

    -- Credential lifecycle
    password_changed_at  TIMESTAMP,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    password_history     JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Bumped to invalidate every access token already issued to this user.
    token_version        INT NOT NULL DEFAULT 1,

    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_security_org ON user_security(organization_id);
CREATE INDEX idx_user_security_locked ON user_security(locked_until) WHERE locked_until IS NOT NULL;

-- Single-use, short-lived secrets: email verification, password reset, SMS
-- one-time codes, and the handle for a half-finished MFA sign-in. Only the
-- hash is stored, so a database read does not yield a usable code.
CREATE TABLE auth_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL,
    token_type      VARCHAR(32) NOT NULL,
    token_hash      VARCHAR(128) NOT NULL,
    destination     VARCHAR(255),
    expires_at      TIMESTAMP NOT NULL,
    consumed_at     TIMESTAMP,
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    ip_address      VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_tokens_lookup ON auth_tokens(token_hash);
CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id, token_type);
CREATE INDEX idx_auth_tokens_expiry ON auth_tokens(expires_at);

-- One row per signed-in device. The refresh token is stored hashed; presenting
-- a refresh token that was already rotated away is treated as theft.
CREATE TABLE user_sessions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id    UUID NOT NULL,
    refresh_token_hash VARCHAR(128) NOT NULL UNIQUE,
    -- The hash this session rotated away from. Presenting it means the old
    -- token was copied before rotation, so the whole family is killed.
    previous_token_hash VARCHAR(128),
    ip_address         VARCHAR(64),
    user_agent         VARCHAR(512),
    device_label       VARCHAR(160),
    mfa_at             TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMP NOT NULL,
    revoked_at         TIMESTAMP,
    revoked_reason     VARCHAR(64)
);

CREATE INDEX idx_sessions_user ON user_sessions(user_id, revoked_at);
CREATE INDEX idx_sessions_expiry ON user_sessions(expires_at);
CREATE INDEX idx_sessions_previous ON user_sessions(previous_token_hash);

-- Access tokens surrendered before their natural expiry. Rows are discarded
-- once the token would have expired anyway, so this stays small.
CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    user_id    UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason     VARCHAR(64)
);

CREATE INDEX idx_revoked_tokens_expiry ON revoked_tokens(expires_at);

-- Security-relevant events, kept separately from the business audit trail so a
-- SIEM can consume one stream without filtering the other.
CREATE TABLE security_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID,
    user_id         UUID,
    email           VARCHAR(255),
    event_type      VARCHAR(48) NOT NULL,
    severity        VARCHAR(16) NOT NULL,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    detail          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_events_org_time ON security_events(organization_id, created_at DESC);
CREATE INDEX idx_security_events_user ON security_events(user_id, created_at DESC);
CREATE INDEX idx_security_events_type ON security_events(event_type, created_at DESC);

-- Existing accounts get a posture row. They were created before verification
-- existed, so treating them as verified avoids locking out an operator who
-- upgrades an installed instance; new accounts verify normally.
INSERT INTO user_security (user_id, organization_id, email_verified, email_verified_at, password_changed_at)
SELECT id, organization_id, true, CURRENT_TIMESTAMP, created_at FROM users
ON CONFLICT (user_id) DO NOTHING;
