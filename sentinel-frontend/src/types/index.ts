/**
 * Types mirroring the backend DTOs exactly. When a backend DTO changes, change
 * it here in the same commit — this file is the contract.
 */

/* ---------- Envelope ---------- */

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: ErrorDetails;
  meta?: MetaData;
}

export interface ErrorDetails {
  code: string;
  message: string;
  /** Field-level validation failures, keyed by field name. */
  details?: Record<string, string>;
  timestamp?: string;
  requestId?: string;
  path?: string;
}

export interface MetaData {
  timestamp?: string;
  page?: number;
  totalPages?: number;
  totalElements?: number;
}

/** Spring Data's Page shape, as serialised by Jackson. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/* ---------- Auth ---------- */

export type UserRole = "ADMIN" | "ANALYST" | "REVIEWER" | "AUDITOR" | "USER";

export interface User {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: UserRole;
  enabled: boolean;
  lastLogin: string | null;
  createdAt: string;
  jobTitle?: string | null;
  timezone?: string | null;
  /** Set when the user has a picture; also the cache key for fetching it. */
  avatarUpdatedAt?: string | null;

  /* Security posture. Present on /auth/me and the admin team list. */
  mfaEnabled?: boolean;
  mfaMethod?: string | null;
  emailVerified?: boolean;
  phoneVerified?: boolean;
  /** Masked. The full number never leaves the backend. */
  phoneNumber?: string | null;
  locked?: boolean;
  lockedUntil?: string | null;
  mustChangePassword?: boolean;
  passwordChangedAt?: string | null;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
  /** True when the password was accepted but a second factor is still needed. */
  mfaRequired?: boolean;
  challengeId?: string;
  mfaMethods?: string[];
  mustChangePassword?: boolean;
  emailVerified?: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

/* ---------- Transactions ---------- */

export type TransactionStatus = "PENDING" | "FLAGGED" | "APPROVED" | "REJECTED";

export interface Transaction {
  id: string;
  transactionExternalId: string;
  userId: string;
  merchantId: string;
  amount: number;
  currency: string;
  country: string | null;
  deviceId: string | null;
  ipAddress: string | null;
  status: TransactionStatus;
  timestamp: string;
  createdAt: string;
}

export interface IngestTransactionRequest {
  transactionExternalId: string;
  userId: string;
  merchantId: string;
  amount: number;
  currency: string;
  country?: string;
  deviceId?: string;
  ipAddress?: string;
  timestamp: string;
}

export interface BulkUploadResponse {
  totalProcessed: number;
  totalFailed: number;
  /** Rows put through the fraud pipeline. Zero when uploaded with analyze=false. */
  totalAnalyzed: number;
  errors: string[] | null;
}

/* ---------- Risk ---------- */

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface RiskScore {
  id: string;
  transactionId: string;
  score: number;
  level: RiskLevel;
  amountRisk: number;
  velocityRisk: number;
  locationRisk: number;
  deviceRisk: number;
  historyRisk: number;
  createdAt: string;
}

/* ---------- Alerts ---------- */

export type AlertSeverity = "MEDIUM" | "HIGH" | "CRITICAL";
export type AlertStatus = "OPEN" | "INVESTIGATING" | "RESOLVED" | "FALSE_POSITIVE";

export interface Alert {
  id: string;
  transactionId: string;
  severity: AlertSeverity;
  status: AlertStatus;
  reason: string;
  triggeredRules: string[];
  createdAt: string;
  updatedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
}

/* ---------- Cases ---------- */

export type CaseStatus = "OPEN" | "INVESTIGATING" | "RESOLVED" | "CLOSED";
export type CasePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type CaseResolution = "FRAUD" | "FALSE_POSITIVE" | "MANUAL_REVIEW";

export interface FraudCase {
  id: string;
  alertId: string;
  title: string;
  priority: CasePriority;
  status: CaseStatus;
  assignedTo: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
  resolution: CaseResolution | null;
}

export interface CaseComment {
  id: string;
  caseId: string;
  userId: string;
  content: string;
  createdAt: string;
}

export interface CaseHistoryEntry {
  id: string;
  caseId: string;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  performedBy: string | null;
  performedAt: string;
}

/* ---------- AI ---------- */

export type RecommendedAction =
  | "AUTO_APPROVE"
  | "AUTO_BLOCK"
  | "REQUEST_VERIFICATION"
  | "ESCALATE"
  | "MONITOR";

export interface Investigation {
  caseId: string;
  likelyFraud: boolean;
  confidence: number;
  keyIndicators: string[];
  narrative: string;
  provider: string;
}

export interface Recommendation {
  caseId: string;
  action: RecommendedAction;
  confidence: number;
  reasoning: string;
  provider: string;
}

export interface CaseSummary {
  caseId: string;
  summary: string;
  provider: string;
}

export interface Explanation {
  id: string;
  alertId: string;
  cause: string;
  explanation: string;
  suggestion: string;
  confidence: number;
  provider: string;
  createdAt: string;
}

export interface FraudPattern {
  id: string;
  patternName: string;
  patternDescription: string;
  occurrences: number;
  confidence: number;
  lastTriggered: string | null;
}

/* ---------- Behavioural ---------- */

export type TrustLevel = "TRUSTED" | "UNKNOWN" | "SUSPICIOUS" | "BLACKLISTED";

export interface Device {
  id: string;
  userId: string;
  fingerprint: string;
  firstSeen: string;
  lastSeen: string;
  usageCount: number | null;
  riskScore: number | null;
  blacklisted: boolean;
  trustLevel: TrustLevel;
}

export interface UserProfile {
  id: string;
  userId: string;
  averageTransactionAmount: number;
  transactionCount: number | null;
  countriesUsed: string[] | null;
  devicesUsed: string[] | null;
  typicalTransactionHour: number | null;
  riskScore: number | null;
  lastUpdated: string;
}

export interface VelocitySnapshot {
  userId: string;
  count10Min: number;
  count1Hour: number;
  count24Hours: number;
  amount10Min: number;
  amount1Hour: number;
  amount24Hours: number;
  distinctMerchants1Hour: number;
  distinctCountries1Hour: number;
  velocityRisk: number;
  rapidActivityDetected: boolean;
}

/* ---------- Analytics ---------- */

export interface DashboardStats {
  totalTransactions: number;
  flaggedTransactions: number;
  approvedTransactions: number;
  pendingTransactions: number;
  totalAlerts: number;
  openAlerts: number;
  averageRiskScore: number;
  fraudRate: number;
  totalAuditEvents: number;
}

export interface FraudTrendPoint {
  date: string;
  totalTransactions: number;
  flaggedTransactions: number;
  fraudRate: number;
}

export interface FraudTrend {
  points: FraudTrendPoint[];
}

export interface MerchantRisk {
  merchantId: string;
  flaggedCount: number;
}

export interface GeographicRisk {
  country: string;
  totalTransactions: number;
  flaggedTransactions: number;
  fraudRate: number;
}

/* ---------- Events ---------- */

export interface FraudEventRecord {
  eventId: string;
  aggregateId: string;
  eventType: string;
  occurredAt: string;
  payload: Record<string, unknown> | null;
  actor: string | null;
}

/* ---------- Demo ---------- */

export interface SeedResult {
  transactionsCreated: number;
  transactionsFlagged: number;
  alertsCreated: number;
  casesCreated: number;
  skipped: boolean;
  message: string;
}

/* Phase 7 platform surfaces: scams, networks, identity, AML, autonomy,
   strategy, generative-AI defence, disputes, governance, integrations. */
export * from "./platform";
export * from "./security";
