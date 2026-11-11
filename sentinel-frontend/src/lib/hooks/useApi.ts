"use client";

import useSWR, { type SWRConfiguration } from "swr";
import { apiClient } from "@/lib/api-client";
import type {
  Alert,
  AlertStatus,
  CaseStatus,
  TransactionStatus,
} from "@/types";

const LIVE: SWRConfiguration = {
  revalidateOnFocus: true,
  // The dashboard is a monitoring surface; a 30s pulse keeps it current
  // without hammering the API.
  refreshInterval: 30_000,
  keepPreviousData: true,
};

const STATIC: SWRConfiguration = {
  revalidateOnFocus: false,
  keepPreviousData: true,
};

/* ---------- Analytics ---------- */

export const useDashboard = () =>
  useSWR("dashboard", () => apiClient.getDashboard(), LIVE);

export const useFraudTrend = (days = 14) =>
  useSWR(["fraud-trend", days], () => apiClient.getFraudTrend(days), LIVE);

export const useTopMerchants = (limit = 8) =>
  useSWR(["top-merchants", limit], () => apiClient.getTopMerchants(limit), LIVE);

export const useGeographicRisk = () =>
  useSWR("geographic-risk", () => apiClient.getGeographicRisk(), LIVE);

/* ---------- Alerts ---------- */

export const useAlerts = (page: number, size: number, status?: AlertStatus) =>
  useSWR(["alerts", page, size, status ?? "ALL"], () =>
    apiClient.listAlerts({ page, size, status })
  , LIVE);

export const useAlert = (id: string | null) =>
  useSWR(id ? ["alert", id] : null, () => apiClient.getAlert(id as string), STATIC);

export const useAlertExplanation = (alertId: string | null) =>
  useSWR(
    alertId ? ["explanation", alertId] : null,
    () => apiClient.getAlertExplanation(alertId as string),
    { ...STATIC, shouldRetryOnError: false }
  );

/* ---------- Transactions ---------- */

export const useTransactions = (page: number, size: number, status?: TransactionStatus) =>
  useSWR(["transactions", page, size, status ?? "ALL"], () =>
    apiClient.listTransactions({ page, size, status })
  , LIVE);

export const useTransaction = (id: string | null) =>
  useSWR(id ? ["transaction", id] : null, () => apiClient.getTransaction(id as string), STATIC);

export const useRiskScore = (transactionId: string | null) =>
  useSWR(
    transactionId ? ["risk-score", transactionId] : null,
    () => apiClient.getRiskScore(transactionId as string),
    { ...STATIC, shouldRetryOnError: false }
  );

/* ---------- Cases ---------- */

export const useCases = (page: number, size: number, status?: CaseStatus) =>
  useSWR(["cases", page, size, status ?? "ALL"], () =>
    apiClient.listCases({ page, size, status })
  , LIVE);

export const useCase = (id: string | null) =>
  useSWR(id ? ["case", id] : null, () => apiClient.getCase(id as string), STATIC);

export const useCaseComments = (id: string | null) =>
  useSWR(id ? ["case-comments", id] : null, () => apiClient.listCaseComments(id as string), STATIC);

export const useCaseHistory = (id: string | null) =>
  useSWR(id ? ["case-history", id] : null, () => apiClient.getCaseHistory(id as string), STATIC);

export const useInvestigation = (caseId: string | null) =>
  useSWR(
    caseId ? ["investigation", caseId] : null,
    () => apiClient.getInvestigation(caseId as string),
    // A case with no investigation yet returns 404; that is an expected state,
    // not an error worth retrying.
    { ...STATIC, shouldRetryOnError: false }
  );

export const useCaseSummary = (caseId: string | null) =>
  useSWR(
    caseId ? ["case-summary", caseId] : null,
    () => apiClient.getCaseSummary(caseId as string),
    { ...STATIC, shouldRetryOnError: false }
  );

/* ---------- Behavioural ---------- */

export const useDevices = (userId: string | null) =>
  useSWR(userId ? ["devices", userId] : null, () => apiClient.listDevices(userId as string), STATIC);

export const useProfile = (userId: string | null) =>
  useSWR(
    userId ? ["profile", userId] : null,
    () => apiClient.getProfile(userId as string),
    { ...STATIC, shouldRetryOnError: false }
  );

export const useVelocity = (userId: string | null) =>
  useSWR(
    userId ? ["velocity", userId] : null,
    () => apiClient.getVelocity(userId as string),
    { ...STATIC, shouldRetryOnError: false }
  );

/* ---------- Patterns & org ---------- */

export const usePatterns = () =>
  useSWR("patterns", () => apiClient.listPatterns(), STATIC);

export const useOrganizationMembers = () =>
  useSWR("org-members", () => apiClient.organizationMembers(), STATIC);

/** Resolves the alert behind a case so the workspace can show the signals. */
export const useCaseAlert = (alertId: string | null | undefined) =>
  useSWR(alertId ? ["alert", alertId] : null, () => apiClient.getAlert(alertId as string), STATIC);

export type { Alert };

/* =================================================================
 * Phase 7: financial crime platform surfaces.
 *
 * Operational queues use LIVE so a console left open stays current;
 * catalogues and reports use STATIC because they change only when
 * somebody runs something.
 * ================================================================= */

/* ---------- Scam and APP fraud ---------- */

export const useScamTypologies = (days = 30) =>
  useSWR(["scam-typologies", days], () => apiClient.getScamTypologies(days), LIVE);

export const useScamSignals = (page: number, size: number, typology?: string) =>
  useSWR(["scam-signals", page, size, typology ?? "ALL"], () =>
    apiClient.listScamSignals({ page, size, typology })
  , LIVE);

export const usePaymentHolds = () =>
  useSWR("payment-holds", () => apiClient.listPaymentHolds(), LIVE);

/* ---------- Mule and network ---------- */

export const useBeneficiaries = (page: number, size: number) =>
  useSWR(["beneficiaries", page, size], () => apiClient.listBeneficiaries({ page, size }), LIVE);

export const useMuleStats = () =>
  useSWR("mule-stats", () => apiClient.getMuleStats(), LIVE);

export const useRings = (page: number, size: number) =>
  useSWR(["rings", page, size], () => apiClient.listRings({ page, size }), LIVE);

export const useGraphStats = () =>
  useSWR("graph-stats", () => apiClient.getGraphStats(), LIVE);

export const useGraphNeighbourhood = (entityId: string | null, depth = 2) =>
  useSWR(
    entityId ? ["graph", entityId, depth] : null,
    () => apiClient.getGraphNeighbourhood(entityId as string, depth),
    STATIC
  );

/* ---------- Identity ---------- */

export const useVerifications = (page: number, size: number, decision?: string) =>
  useSWR(["verifications", page, size, decision ?? "ALL"], () =>
    apiClient.listVerifications({ page, size, decision })
  , LIVE);

export const useScreeningQueue = (status = "OPEN", page = 0, size = 20) =>
  useSWR(["screening", status, page, size], () =>
    apiClient.listScreeningHits({ status, page, size })
  , LIVE);

export const useIdentityStats = () =>
  useSWR("identity-stats", () => apiClient.getIdentityStats(), LIVE);

/* ---------- AML ---------- */

export const useAmlFindings = (page: number, size: number, status?: string) =>
  useSWR(["aml-findings", page, size, status ?? "ALL"], () =>
    apiClient.listAmlFindings({ page, size, status })
  , LIVE);

export const useSars = (page: number, size: number, status?: string) =>
  useSWR(["sars", page, size, status ?? "ALL"], () =>
    apiClient.listSars({ page, size, status })
  , LIVE);

export const useAmlStats = () =>
  useSWR("aml-stats", () => apiClient.getAmlStats(), LIVE);

/* ---------- Autonomy ---------- */

export const useAutonomyPolicies = () =>
  useSWR("autonomy-policies", () => apiClient.listAutonomyPolicies(), STATIC);

export const useAgentRuns = (page: number, size: number, agent?: string) =>
  useSWR(["agent-runs", page, size, agent ?? "ALL"], () =>
    apiClient.listAgentRuns({ page, size, agent })
  , LIVE);

export const useAutonomyStats = (days = 30) =>
  useSWR(["autonomy-stats", days], () => apiClient.getAutonomyStats(days), LIVE);

export const useRedTeamResults = (page = 0, size = 20) =>
  useSWR(["redteam", page, size], () => apiClient.listRedTeamResults({ page, size }), STATIC);

/* ---------- Strategy ---------- */

export const useRules = (page: number, size: number) =>
  useSWR(["rules", page, size], () => apiClient.listRules({ page, size }), STATIC);

export const useStrategyStats = () =>
  useSWR("strategy-stats", () => apiClient.getStrategyStats(), STATIC);

export const useThresholdTuning = (days = 90) =>
  useSWR(["tuning", days], () => apiClient.getThresholdTuning(days), STATIC);

/* ---------- Generative-AI defence ---------- */

export const useMediaAnalyses = (page: number, size: number, mediaType?: string) =>
  useSWR(["media", page, size, mediaType ?? "ALL"], () =>
    apiClient.listMediaAnalyses({ page, size, mediaType })
  , LIVE);

export const useAiAgents = (page = 0, size = 20) =>
  useSWR(["ai-agents", page, size], () => apiClient.listAiAgents({ page, size }), LIVE);

export const usePhishingDomains = (page = 0, size = 20) =>
  useSWR(["phishing", page, size], () => apiClient.listPhishingDomains({ page, size }), STATIC);

export const useGenAiStats = () =>
  useSWR("genai-stats", () => apiClient.getGenAiStats(), LIVE);

/* ---------- Disputes ---------- */

export const useDisputes = (page: number, size: number, status?: string) =>
  useSWR(["disputes", page, size, status ?? "ALL"], () =>
    apiClient.listDisputes({ page, size, status })
  , LIVE);

export const useDisputeStats = (days = 90) =>
  useSWR(["dispute-stats", days], () => apiClient.getDisputeStats(days), LIVE);

/* ---------- Governance ---------- */

export const useModels = () =>
  useSWR("models", () => apiClient.listModels(), STATIC);

export const useDriftReports = (page = 0, size = 10) =>
  useSWR(["drift", page, size], () => apiClient.listDriftReports({ page, size }), STATIC);

export const useFairnessReports = (page = 0, size = 10) =>
  useSWR(["fairness", page, size], () => apiClient.listFairnessReports({ page, size }), STATIC);

export const useGovernanceStats = () =>
  useSWR("governance-stats", () => apiClient.getGovernanceStats(), STATIC);

/* ---------- Integrations and consortium ---------- */

export const useWebhooks = () =>
  useSWR("webhooks", () => apiClient.listWebhooks(), STATIC);

export const useApiKeys = () =>
  useSWR("api-keys", () => apiClient.listApiKeys(), STATIC);

export const useConnectors = () =>
  useSWR("connectors", () => apiClient.listConnectors(), STATIC);

export const useConsortiumSummary = () =>
  useSWR("consortium-summary", () => apiClient.getConsortiumSummary(), STATIC);

export const useConsortiumStats = () =>
  useSWR("consortium-stats", () => apiClient.getConsortiumStats(), STATIC);

/* ---------- Return on investment ---------- */

export const useRoi = (days = 30) =>
  useSWR(["roi", days], () => apiClient.getRoi(days), LIVE);

export const useForecast = (historyDays = 60, horizonDays = 14) =>
  useSWR(["forecast", historyDays, horizonDays], () =>
    apiClient.getForecast(historyDays, horizonDays)
  , STATIC);

/* ---------- Friction ---------- */

export const useFrictionStats = (days = 30) =>
  useSWR(["friction-stats", days], () => apiClient.getFrictionStats(days), LIVE);

export const useFrictionPolicies = () =>
  useSWR("friction-policies", () => apiClient.listFrictionPolicies(), STATIC);

/* ---------- Account security ---------- */

export const useSecurityOverview = () =>
  useSWR("security-overview", () => apiClient.securityOverview(), STATIC);

export const useSessions = () =>
  useSWR("security-sessions", () => apiClient.sessions(), STATIC);

export const useSecurityEvents = (page = 0, size = 20) =>
  useSWR(["security-events", page, size], () => apiClient.securityEvents(page, size), STATIC);

/** Organization-wide security log. Administrators, reviewers, and auditors only. */
export const useOrganizationSecurityEvents = (page = 0, size = 50, enabled = true) =>
  useSWR(
    enabled ? ["org-security-events", page, size] : null,
    () => apiClient.organizationSecurityEvents(page, size),
    STATIC
  );

export const useSecurityStats = (enabled = true) =>
  useSWR(enabled ? "security-stats" : null, () => apiClient.securityStats(), STATIC);

export const usePasswordPolicy = () =>
  useSWR("password-policy", () => apiClient.passwordPolicy(), {
    ...STATIC,
    // The rules do not change while somebody is reading them.
    revalidateIfStale: false,
  });

/* ---------- Administration ---------- */

export const useAdminUsers = (enabled = true) =>
  useSWR(enabled ? "admin-users" : null, () => apiClient.adminUsers(), STATIC);

export const useAdminRoles = (enabled = true) =>
  useSWR(enabled ? "admin-roles" : null, () => apiClient.adminRoles(), STATIC);
