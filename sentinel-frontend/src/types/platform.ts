/**
 * Types for the financial-crime platform surfaces added in phase 7:
 * scams, mule networks, graph intelligence, identity, AML, autonomy,
 * strategy, generative-AI defence, disputes, governance, friction,
 * integrations, and return on investment.
 *
 * Kept apart from `types/index.ts` so the original core model stays
 * readable; both are re-exported through the index.
 */

/* ---------- Scam and APP fraud ---------- */

export type ScamTypology =
  | "ROMANCE"
  | "INVESTMENT"
  | "IMPERSONATION"
  | "PIG_BUTCHERING"
  | "INVOICE_REDIRECT"
  | "PURCHASE"
  | "ADVANCE_FEE"
  | "SAFE_ACCOUNT"
  | "CRYPTO_RAMP"
  | "UNKNOWN";

export type ScamAction =
  | "ALLOW"
  | "WARN"
  | "WARN_AND_CONFIRM"
  | "COOLING_OFF"
  | "TRUSTED_CONTACT"
  | "HUMAN_REVIEW"
  | "BLOCK";

export interface ScamSignal {
  id: string;
  transactionId?: string;
  userId: string;
  beneficiaryId?: string;
  amount?: number;
  typology: ScamTypology;
  score: number;
  coachingScore: number;
  beneficiaryScore: number;
  vulnerabilityScore: number;
  indicators: string[];
  action: ScamAction;
  createdAt: string;
}

export interface ScamAssessment {
  signalId: string;
  score: number;
  coachingScore: number;
  beneficiaryScore: number;
  vulnerabilityScore: number;
  typology: ScamTypology;
  typologyLabel: string;
  action: ScamAction;
  indicators: string[];
  warning?: string;
  holdId?: string;
  releaseAt?: string;
  trustedContactNotified: boolean;
}

export interface TypologyBreakdown {
  typology: ScamTypology;
  label: string;
  description: string;
  count: number;
  exposure: number;
}

export type HoldStatus =
  | "HELD"
  | "RELEASED"
  | "CANCELLED_BY_CUSTOMER"
  | "CANCELLED_BY_ANALYST"
  | "EXPIRED_AND_RELEASED";

export interface PaymentHold {
  id: string;
  transactionId?: string;
  userId: string;
  beneficiaryId?: string;
  amount?: number;
  status: HoldStatus;
  reason: string;
  releaseAt: string;
  resolvedAt?: string;
  trustedContactNotified: boolean;
  createdAt: string;
}

/* ---------- Mule and beneficiary ---------- */

export type MuleBand = "CLEAN" | "WATCH" | "SUSPECTED" | "CONFIRMED";

export interface BeneficiaryRisk {
  id: string;
  beneficiaryId: string;
  beneficiaryName?: string;
  score: number;
  band: MuleBand;
  fanIn: number;
  creditCount: number;
  totalCredited: number;
  smallCreditRatio: number;
  activityWindowDays: number;
  distinctCountries: number;
  externallyConfirmed: boolean;
  reasons: string[];
  firstSeen?: string;
  lastSeen?: string;
}

/* ---------- Graph ---------- */

export interface GraphNode {
  id: string;
  type: string;
  label: string;
  risk: number;
  root: boolean;
  degree: number;
}

export interface GraphEdge {
  source: string;
  target: string;
  type: string;
  label: string;
  strength: number;
  via?: string;
  observations: number;
}

export interface GraphView {
  rootId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export type RingStatus =
  | "DETECTED"
  | "UNDER_INVESTIGATION"
  | "CONFIRMED"
  | "DISMISSED"
  | "TAKEN_DOWN";

export interface FraudRing {
  id: string;
  ringKey: string;
  label: string;
  members: string[];
  linkTypes: string[];
  memberCount: number;
  edgeCount: number;
  density: number;
  riskScore: number;
  totalExposure: number;
  status: RingStatus;
  detectedAt: string;
}

/* ---------- Identity ---------- */

export type VerificationDecision =
  | "APPROVED"
  | "APPROVED_WITH_LIMITS"
  | "MANUAL_REVIEW"
  | "ADDITIONAL_EVIDENCE_REQUIRED"
  | "REJECTED";

export interface IdentityVerification {
  id: string;
  subjectId: string;
  subjectName?: string;
  type: string;
  decision: VerificationDecision;
  riskScore: number;
  documentScore: number;
  livenessScore: number;
  deepfakeScore: number;
  syntheticScore: number;
  footprintScore: number;
  telcoScore: number;
  sanctionsHit: boolean;
  reasons: string[];
  imposedLimits?: string[];
  nextReviewAt?: string;
  createdAt: string;
}

export interface SanctionsHit {
  id: string;
  subjectId: string;
  listType: string;
  listName: string;
  matchedName: string;
  matchScore: number;
  matchedDob?: string;
  matchedCountry?: string;
  status: string;
  dismissedByAi: boolean;
  dismissalRationale?: string;
  createdAt: string;
}

/* ---------- AML ---------- */

export type AmlTypologyKey =
  | "STRUCTURING"
  | "RAPID_MOVEMENT"
  | "ROUND_TRIPPING"
  | "LAYERING"
  | "HIGH_RISK_JURISDICTION"
  | "DORMANT_REACTIVATION"
  | "TRADE_BASED"
  | "CASH_INTENSIVE"
  | "THIRD_PARTY_FUNDING";

export interface AmlFinding {
  id: string;
  subjectId: string;
  typology: AmlTypologyKey;
  score: number;
  narrative: string;
  evidence: string[];
  transactionIds?: string[];
  totalValue?: number;
  windowStart?: string;
  windowEnd?: string;
  status: string;
  createdAt: string;
}

export type SarStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "FILED"
  | "REJECTED"
  | "WITHDRAWN";

export interface SarFiling {
  id: string;
  referenceNumber: string;
  subjectId: string;
  subjectName?: string;
  jurisdiction: string;
  status: SarStatus;
  narrative: string;
  typologies: string[];
  totalValue?: number;
  aiDrafted: boolean;
  deadlineAt?: string;
  filedAt?: string;
  createdAt: string;
}

/* ---------- Autonomy ---------- */

export type AutonomyLevel =
  | "OFF"
  | "SUGGEST_ONLY"
  | "ACT_ON_LOW_RISK"
  | "ACT_WITHIN_LIMITS"
  | "FULL_AUTONOMY";

export interface AutonomyPolicy {
  id?: string;
  actionType: string;
  level: AutonomyLevel;
  maxAmount?: number;
  maxRiskScore?: number;
  minConfidence?: number;
  requiresMakerChecker: boolean;
  dailyActionCap?: number;
}

export type AgentVerdict =
  | "BENIGN"
  | "NEEDS_REVIEW"
  | "SUSPICIOUS"
  | "FRAUDULENT"
  | "INSUFFICIENT_EVIDENCE";

export interface AgentRun {
  id: string;
  agentName: string;
  targetType: string;
  targetId: string;
  verdict: AgentVerdict;
  confidence: number;
  proposedAction?: string;
  actionExecuted: boolean;
  suppressedReason?: string;
  rationale: string;
  evidence: string[];
  durationMs: number;
  llmAssisted: boolean;
  humanAgreed?: boolean;
  createdAt: string;
}

export interface AttackSimulation {
  id: string;
  scenarioKey: string;
  scenarioName: string;
  description: string;
  transactionsSimulated: number;
  detectedCount: number;
  detectionRate: number;
  peakScore: number;
  outcome: "PASS" | "PARTIAL" | "FAIL";
  gaps: string[];
  recommendations: string[];
  createdAt: string;
}

/* ---------- Strategy ---------- */

export type RuleStatus = "DRAFT" | "SHADOW" | "CHALLENGER" | "CHAMPION" | "RETIRED";

export interface RuleDefinition {
  id: string;
  name: string;
  description?: string;
  naturalLanguage?: string;
  definition: Record<string, unknown>;
  riskPoints: number;
  status: RuleStatus;
  version: number;
  trafficPercent: number;
  hitCount: number;
  truePositiveCount: number;
  falsePositiveCount: number;
  createdAt: string;
}

export interface RuleBacktest {
  id: string;
  ruleName: string;
  transactionsEvaluated: number;
  hitCount: number;
  truePositives: number;
  falsePositives: number;
  falseNegatives: number;
  precision: number;
  recall: number;
  valueCaught?: number;
  valueDisrupted?: number;
  monthlyReviewHours: number;
  windowDays: number;
  sampleHits?: string[];
  recommendation: "SHIP" | "SHIP_IN_SHADOW" | "DO_NOT_SHIP";
  recommendationReason: string;
  createdAt: string;
}

export interface ParsedRule {
  definition: Record<string, unknown>;
  explanation: string;
  llmAssisted: boolean;
}

export interface RuleCandidate {
  name: string;
  sentence: string;
  definition: Record<string, unknown>;
  riskPoints: number;
  precision: number;
  lift: number;
  fraudHits: number;
  cleanHits: number;
  rationale: string;
}

export interface ThresholdCost {
  threshold: number;
  fraudLoss: number;
  frictionCost: number;
  operatingCost: number;
  totalCost: number;
  alertVolume: number;
  genuineStopped: number;
  fraudCaught: number;
  fraudMissed: number;
}

export interface TuningResult {
  recommended?: ThresholdCost;
  curve: ThresholdCost[];
  rationale: string;
}

export interface RetroFinding {
  transactionId: string;
  externalId: string;
  userId: string;
  merchantId: string;
  amount: number;
  timestamp: string;
  score: number;
  reasons: string[];
}

export interface RetroResult {
  transactionsReplayed: number;
  wouldFlagCount: number;
  exposure: number;
  rulesApplied: number;
  findings: RetroFinding[];
  summary: string;
}

/* ---------- Generative-AI defence ---------- */

export interface MediaAnalysis {
  id: string;
  mediaType: string;
  subjectId?: string;
  channel: string;
  syntheticScore: number;
  verdict: string;
  indicators: string[];
  action: string;
  createdAt: string;
}

export interface AiAgentIdentity {
  id: string;
  agentKey: string;
  agentName: string;
  operator: string;
  principalUserId: string;
  scopes: string[];
  allowedMerchants?: string[];
  perTransactionLimit?: number;
  dailyLimit?: number;
  status: string;
  expiresAt?: string;
  transactionCount: number;
  behaviourDriftScore: number;
  lastSeenAt?: string;
}

export interface PhishingDomain {
  id: string;
  domain: string;
  brandTerm: string;
  technique: string;
  similarityScore: number;
  hasLoginForm: boolean;
  sslIssued: boolean;
  status: string;
  evidence: string[];
}

/* ---------- Disputes ---------- */

export type DisputeStatus =
  | "RECEIVED"
  | "EVIDENCE_GATHERING"
  | "REPRESENTMENT_DRAFTED"
  | "SUBMITTED"
  | "WON"
  | "LOST"
  | "ACCEPTED"
  | "EXPIRED";

export interface Dispute {
  id: string;
  transactionId?: string;
  userId: string;
  merchantId?: string;
  network: string;
  reasonCode: string;
  reasonDescription?: string;
  amount: number;
  currency: string;
  status: DisputeStatus;
  firstPartyFraudScore: number;
  winProbability: number;
  evidence: string[];
  representmentText?: string;
  aiDrafted: boolean;
  respondBy?: string;
  recoveredAmount?: number;
  createdAt: string;
}

/* ---------- Governance ---------- */

export interface ModelRegistryEntry {
  id: string;
  modelName: string;
  modelVersion: string;
  modelType: string;
  status: string;
  riskTier: string;
  purpose?: string;
  ownerName: string;
  limitations?: string[];
  inputFeatures?: string[];
  approvedAt?: string;
  nextReviewAt?: string;
}

export interface DriftReport {
  id: string;
  modelName: string;
  metric: string;
  psi: number;
  status: "STABLE" | "WATCH" | "DRIFTED";
  baselineCount: number;
  currentCount: number;
  buckets: Array<Record<string, unknown>>;
  interpretation: string;
  createdAt: string;
}

export interface FairnessReport {
  id: string;
  modelName: string;
  attribute: string;
  groups: Array<Record<string, unknown>>;
  disparityRatio: number;
  verdict: "PASS" | "REVIEW" | "FAIL";
  groupsTested: number;
  sampleSize: number;
  interpretation: string;
  createdAt: string;
}

/* ---------- Friction ---------- */

export interface FrictionPolicy {
  id: string;
  name: string;
  channel: string;
  minScore: number;
  maxScore: number;
  minAmount?: number;
  method: string;
  vulnerableOnly: boolean;
  skipIfTrustedDevice: boolean;
  priority: number;
  enabled: boolean;
}

/* ---------- Integrations ---------- */

export interface WebhookSubscription {
  id: string;
  name: string;
  targetUrl: string;
  events: string[];
  active: boolean;
  failureCount: number;
  disabledReason?: string;
  lastDeliveryAt?: string;
}

export interface ApiKeySummary {
  id: string;
  name: string;
  keyPrefix: string;
  scopes: string[];
  rateLimitPerMinute: number;
  active: boolean;
  lastUsedAt?: string;
  expiresAt?: string;
}

export interface Connector {
  key: string;
  name: string;
  category: string;
  entities: string[];
  transport: string;
  status: string;
}

/* ---------- Return on investment ---------- */

export interface RoiSummary {
  windowDays: number;
  detection: Record<string, number>;
  automation: Record<string, number>;
  customerExperience: Record<string, number | null>;
  recovery: Record<string, number>;
  assumptions: Record<string, unknown>;
}

export interface ForecastPoint {
  date: string;
  expectedFlagged: number;
  lowerBound: number;
  upperBound: number;
  calendarMultiplier: number;
  drivers: string[];
}

export interface ForecastResult {
  history: Array<{ date: string; total: number; flagged: number }>;
  forecast: ForecastPoint[];
  confidence: string;
  trendPerDay?: number;
  summary: string;
}

/* ---------- Shared ---------- */

export type StatMap = Record<string, number | string | null>;
