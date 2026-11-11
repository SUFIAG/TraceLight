/**
 * Account security, as the console sees it.
 *
 * <p>Nothing here is a secret the backend holds: a TOTP seed appears exactly
 * once, during enrolment, and recovery codes exactly once, when they are
 * generated. Everything else is a boolean, a timestamp, or a masked value.
 */

export type MfaMethod = "TOTP" | "SMS" | "BACKUP_CODE";

export interface SecurityOverview {
  mfaEnabled: boolean;
  mfaMethod: MfaMethod | null;
  mfaEnrolledAt: string | null;
  backupCodesRemaining: number;
  emailVerified: boolean;
  /** Masked, e.g. `a***y@bank.com`. */
  email: string;
  phoneVerified: boolean;
  /** Masked, e.g. `••••••89`, or `—` when none. */
  phoneNumber: string;
  passwordChangedAt: string | null;
  passwordAgeDays: number | null;
  mustChangePassword: boolean;
  activeSessions: number;
  stepUpFreshnessMinutes: number;
  /** 0-100. Shown with `advice`, never on its own. */
  score: number;
  advice: string[];
}

export interface DeviceSession {
  id: string;
  device: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  mfaAt: string | null;
  /** The device making this request. Cannot be ended from the session list. */
  current: boolean;
}

export type SecuritySeverity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface SecurityEvent {
  id: string;
  organizationId: string | null;
  userId: string | null;
  email: string | null;
  eventType: string;
  severity: SecuritySeverity;
  ipAddress: string | null;
  userAgent: string | null;
  detail: Record<string, unknown>;
  createdAt: string;
}

export interface MfaEnrolment {
  secret: string;
  /** `otpauth://` URI for the QR code. */
  provisioningUri: string;
  issuer: string;
  account: string;
  next: string;
}

export interface MfaActivation {
  enabled: boolean;
  backupCodes: string[];
  warning: string;
}

export interface PasswordPolicy {
  minLength: number;
  maxLength: number;
  historySize: number;
  requirements: string[];
  /** Present only when a candidate password was sent for scoring. */
  strength?: number;
}

export interface SecurityStats {
  eventsLast7Days: number;
  failedSignIns: number;
  lockouts: number;
  mfaFailures: number;
  tokenReplays: number;
}

export interface RoleOption {
  value: string;
  description: string;
}

export interface InvitedUser {
  user: import("./index").User;
  temporaryPassword: string;
  note: string;
}
