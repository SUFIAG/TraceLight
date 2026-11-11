import type {
  Alert,
  AlertStatus,
  ApiEnvelope,
  BulkUploadResponse,
  CaseComment,
  CaseHistoryEntry,
  CaseResolution,
  CaseStatus,
  CaseSummary,
  DashboardStats,
  Device,
  Explanation,
  FraudCase,
  FraudEventRecord,
  FraudPattern,
  FraudTrend,
  GeographicRisk,
  IngestTransactionRequest,
  Investigation,
  LoginRequest,
  LoginResponse,
  MerchantRisk,
  Page,
  Recommendation,
  RegisterRequest,
  RiskScore,
  SeedResult,
  Transaction,
  TransactionStatus,
  User,
  UserProfile,
  VelocitySnapshot,
} from "@/types";
import type {
  AgentRun,
  AiAgentIdentity,
  AmlFinding,
  ApiKeySummary,
  AttackSimulation,
  AutonomyPolicy,
  BeneficiaryRisk,
  Connector,
  Dispute,
  DriftReport,
  FairnessReport,
  ForecastResult,
  FraudRing,
  FrictionPolicy,
  GraphView,
  IdentityVerification,
  MediaAnalysis,
  ModelRegistryEntry,
  ParsedRule,
  PaymentHold,
  PhishingDomain,
  RetroResult,
  RoiSummary,
  RuleBacktest,
  RuleCandidate,
  RuleDefinition,
  SanctionsHit,
  SarFiling,
  ScamAssessment,
  ScamSignal,
  StatMap,
  TuningResult,
  TypologyBreakdown,
  WebhookSubscription,
} from "@/types/platform";
import type {
  DeviceSession,
  InvitedUser,
  MfaActivation,
  MfaEnrolment,
  PasswordPolicy,
  RoleOption,
  SecurityEvent,
  SecurityOverview,
  SecurityStats,
} from "@/types/security";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081/api/v1";

const ACCESS_TOKEN_KEY = "tracelight.accessToken";
const REFRESH_TOKEN_KEY = "tracelight.refreshToken";

/**
 * Error carrying the backend's own code and message so the UI can show the
 * real reason rather than a generic failure string.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(message: string, status: number, code = "UNKNOWN") {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

function buildQuery(params?: Query): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.append(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

class ApiClient {
  private readonly baseUrl: string;
  /** Prevents a burst of 401s from firing several refresh calls at once. */
  private refreshInFlight: Promise<boolean> | null = null;

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  /* ---------- Token handling ---------- */

  get accessToken(): string | null {
    if (typeof window === "undefined") return null;
    return window.localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  setSession(accessToken: string, refreshToken?: string) {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) {
      window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
  }

  clearSession() {
    if (typeof window === "undefined") return;
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.accessToken !== null;
  }

  private async refreshSession(): Promise<boolean> {
    if (typeof window === "undefined") return false;
    const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) return false;

    if (!this.refreshInFlight) {
      this.refreshInFlight = (async () => {
        try {
          const response = await fetch(`${this.baseUrl}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
          });
          if (!response.ok) return false;
          const envelope = (await response.json()) as ApiEnvelope<LoginResponse>;
          if (!envelope.success || !envelope.data) return false;
          this.setSession(envelope.data.accessToken, envelope.data.refreshToken);
          return true;
        } catch {
          return false;
        } finally {
          this.refreshInFlight = null;
        }
      })();
    }
    return this.refreshInFlight;
  }

  /* ---------- Core request ---------- */

  private async request<T>(
    path: string,
    init: RequestInit = {},
    retryOnUnauthorized = true
  ): Promise<T> {
    const headers = new Headers(init.headers);
    if (!(init.body instanceof FormData)) {
      headers.set("Content-Type", "application/json");
    }
    const token = this.accessToken;
    if (token) headers.set("Authorization", `Bearer ${token}`);

    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, { ...init, headers });
    } catch {
      throw new ApiError(
        "Cannot reach the Tracelight API. Check that the backend is running.",
        0,
        "NETWORK_ERROR"
      );
    }

    if (response.status === 401 && retryOnUnauthorized) {
      const refreshed = await this.refreshSession();
      if (refreshed) return this.request<T>(path, init, false);
      this.clearSession();
      if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
      throw new ApiError("Your session has expired. Sign in again.", 401, "SESSION_EXPIRED");
    }

    if (response.status === 204) {
      return undefined as T;
    }

    const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

    if (!response.ok) {
      // The backend closes the platform to an account on a one-time or retired
      // password. Sending the user to the one page that can fix it beats
      // letting every screen fail with the same message.
      if (
        payload?.error?.code === "PASSWORD_CHANGE_REQUIRED" &&
        typeof window !== "undefined" &&
        !window.location.pathname.startsWith("/settings")
      ) {
        window.location.href = "/settings?tab=security";
      }

      throw new ApiError(
        payload?.error?.message ?? `Request failed with status ${response.status}`,
        response.status,
        payload?.error?.code ?? "HTTP_ERROR"
      );
    }

    if (!payload || payload.success === false) {
      throw new ApiError(
        payload?.error?.message ?? "The API returned an unsuccessful response.",
        response.status,
        payload?.error?.code ?? "UNSUCCESSFUL"
      );
    }

    // Every backend controller wraps its result in ApiResponse, so the caller
    // always wants the inner data, never the envelope.
    return payload.data as T;
  }

  private get<T>(path: string, params?: Query) {
    return this.request<T>(`${path}${buildQuery(params)}`, { method: "GET" });
  }

  private post<T>(path: string, body?: unknown, params?: Query) {
    return this.request<T>(`${path}${buildQuery(params)}`, {
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  private patch<T>(path: string, body: unknown) {
    return this.request<T>(path, { method: "PATCH", body: JSON.stringify(body) });
  }

  private delete<T>(path: string) {
    return this.request<T>(path, { method: "DELETE" });
  }

  /* ---------- Auth ---------- */

  /**
   * Exchanges credentials for a session.
   *
   * <p>A successful call does not always carry tokens: when the account has a
   * second factor the response is a challenge instead, and the caller must
   * finish with {@link verifyMfa}. Only a response that actually contains a
   * token is stored.
   */
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const result = await this.post<LoginResponse>("/auth/login", credentials);
    if (result.accessToken) this.setSession(result.accessToken, result.refreshToken);
    return result;
  }

  async verifyMfa(challengeId: string, code: string): Promise<LoginResponse> {
    const result = await this.post<LoginResponse>("/auth/mfa/verify", { challengeId, code });
    if (result.accessToken) this.setSession(result.accessToken, result.refreshToken);
    return result;
  }

  sendMfaSms(challengeId: string) {
    return this.post<{ sentTo: string; developmentCode?: string }>("/auth/mfa/sms", { challengeId });
  }

  register(request: RegisterRequest) {
    return this.post<User>("/auth/register", request);
  }

  currentUser() {
    return this.get<User>("/auth/me");
  }

  organizationMembers() {
    return this.get<User[]>("/auth/users");
  }

  /**
   * Ends the session on the server, then clears local tokens.
   *
   * <p>Clearing the tokens alone would leave them valid until they expired;
   * the server call is what actually revokes them. A failure is swallowed
   * because the user asked to be signed out, and the local state must end up
   * signed out either way.
   */
  async logout() {
    try {
      await this.post<{ signedOut: boolean }>("/auth/logout", {});
    } catch {
      // Already expired or unreachable — nothing more to revoke.
    } finally {
      this.clearSession();
    }
  }

  /* ---------- Password ---------- */

  changePassword(currentPassword: string, newPassword: string) {
    return this.post<{ changed: boolean; message: string }>("/auth/password/change", {
      currentPassword,
      newPassword,
    });
  }

  forgotPassword(email: string) {
    return this.post<{ message: string; developmentToken?: string }>("/auth/password/forgot", {
      email,
    });
  }

  resetPassword(token: string, newPassword: string) {
    return this.post<{ reset: boolean; message: string }>("/auth/password/reset", {
      token,
      newPassword,
    });
  }

  /** Fetches the rules, and scores a candidate when one is supplied. */
  passwordPolicy(password?: string) {
    return this.post<PasswordPolicy>("/auth/password/policy", password ? { password } : {});
  }

  /* ---------- Email verification ---------- */

  verifyEmail(code: string) {
    return this.post<User>("/auth/email/verify", { code });
  }

  resendEmailVerification() {
    return this.post<{ sent: boolean; developmentCode?: string }>("/auth/email/resend", {});
  }

  /* ---------- Account security ---------- */

  securityOverview() {
    return this.get<SecurityOverview>("/security/overview");
  }

  beginMfaEnrolment() {
    return this.post<MfaEnrolment>("/security/mfa/enrol", {});
  }

  activateMfa(code: string) {
    return this.post<MfaActivation>("/security/mfa/activate", { code });
  }

  disableMfa(password: string, code: string) {
    return this.post<{ enabled: boolean }>("/security/mfa/disable", { password, code });
  }

  regenerateBackupCodes(password: string) {
    return this.post<{ backupCodes: string[]; warning: string }>("/security/mfa/backup-codes", {
      password,
    });
  }

  /** Re-confirms identity so a step-up-protected action can go through. */
  stepUp(payload: { code?: string; password?: string }) {
    return this.post<{ confirmed: boolean; validForMinutes: number }>("/security/step-up", payload);
  }

  sendPhoneCode(phoneNumber: string) {
    return this.post<{ sentTo: string; expiresInMinutes: number; developmentCode?: string }>(
      "/security/phone/send",
      { phoneNumber }
    );
  }

  verifyPhone(code: string) {
    return this.post<{ verified: boolean; phoneNumber: string }>("/security/phone/verify", { code });
  }

  removePhone() {
    return this.delete<{ removed: boolean }>("/security/phone");
  }

  sessions() {
    return this.get<DeviceSession[]>("/security/sessions");
  }

  revokeSession(sessionId: string) {
    return this.delete<{ revoked: boolean }>(`/security/sessions/${sessionId}`);
  }

  revokeOtherSessions() {
    return this.post<{ sessionsEnded: number }>("/security/sessions/revoke-others", {});
  }

  securityEvents(page = 0, size = 20) {
    return this.get<Page<SecurityEvent>>("/security/events", { page, size });
  }

  organizationSecurityEvents(page = 0, size = 50) {
    return this.get<Page<SecurityEvent>>("/security/events/organization", { page, size });
  }

  securityStats() {
    return this.get<SecurityStats>("/security/stats");
  }

  /* ---------- Profile ---------- */

  updateProfile(patch: {
    firstName?: string;
    lastName?: string;
    jobTitle?: string;
    timezone?: string;
  }) {
    return this.patch<User>("/me", patch);
  }

  async uploadAvatar(file: File) {
    const form = new FormData();
    form.append("file", file);
    return this.request<{ updatedAt: string }>("/me/avatar", { method: "POST", body: form });
  }

  removeAvatar() {
    return this.delete<{ removed: boolean }>("/me/avatar");
  }

  /**
   * Fetches a picture as an object URL.
   *
   * <p>Avatars require a token, so they cannot be used as a plain `src`. The
   * caller owns the returned URL and must revoke it.
   */
  async avatarObjectUrl(userId: string): Promise<string | null> {
    const token = this.accessToken;
    if (!token) return null;
    try {
      const response = await fetch(`${this.baseUrl}/me/avatar/${userId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) return null;
      return URL.createObjectURL(await response.blob());
    } catch {
      return null;
    }
  }

  /* ---------- Administration ---------- */

  adminUsers() {
    return this.get<User[]>("/admin/users");
  }

  adminRoles() {
    return this.get<RoleOption[]>("/admin/users/roles");
  }

  inviteUser(payload: { email: string; firstName?: string; lastName?: string; role?: string }) {
    return this.post<InvitedUser>("/admin/users", payload);
  }

  changeUserRole(userId: string, role: string) {
    return this.patch<User>(`/admin/users/${userId}/role`, { role });
  }

  setUserEnabled(userId: string, enabled: boolean) {
    return this.patch<User>(`/admin/users/${userId}/status`, { enabled });
  }

  unlockUser(userId: string) {
    return this.post<User>(`/admin/users/${userId}/unlock`, {});
  }

  forceUserPasswordReset(userId: string) {
    return this.post<User>(`/admin/users/${userId}/force-password-reset`, {});
  }

  revokeUserSessions(userId: string) {
    return this.post<{ sessionsEnded: number }>(`/admin/users/${userId}/revoke-sessions`, {});
  }

  resetUserMfa(userId: string) {
    return this.post<User>(`/admin/users/${userId}/reset-mfa`, {});
  }

  /* ---------- Transactions ---------- */

  listTransactions(params: { page?: number; size?: number; status?: TransactionStatus }) {
    return this.get<Page<Transaction>>("/transactions", params);
  }

  getTransaction(id: string) {
    return this.get<Transaction>(`/transactions/${id}`);
  }

  ingestTransaction(request: IngestTransactionRequest) {
    return this.post<Transaction>("/transactions", request);
  }

  /**
   * @param analyze run every accepted row through the fraud pipeline. Defaults
   *                to true, because an upload that produces no risk scores and
   *                no alerts leaves the platform looking empty.
   */
  uploadTransactionsCsv(file: File, analyze = true) {
    const form = new FormData();
    form.append("file", file);
    return this.request<BulkUploadResponse>(
      `/transactions/upload${buildQuery({ analyze })}`,
      { method: "POST", body: form }
    );
  }

  /* ---------- Fraud & risk ---------- */

  analyzeTransaction(transactionId: string) {
    return this.post<Record<string, string>>(`/fraud/analyze/${transactionId}`);
  }

  getRiskScore(transactionId: string) {
    return this.get<RiskScore>(`/fraud/risk-score/${transactionId}`);
  }

  /* ---------- Alerts ---------- */

  listAlerts(params: { page?: number; size?: number; status?: AlertStatus }) {
    return this.get<Page<Alert>>("/alerts", params);
  }

  getAlert(id: string) {
    return this.get<Alert>(`/alerts/${id}`);
  }

  updateAlertStatus(id: string, status: AlertStatus) {
    return this.patch<Alert>(`/alerts/${id}/status`, { status });
  }

  /* ---------- Cases ---------- */

  listCases(params: { page?: number; size?: number; status?: CaseStatus; assignedTo?: string }) {
    return this.get<Page<FraudCase>>("/cases", params);
  }

  getCase(id: string) {
    return this.get<FraudCase>(`/cases/${id}`);
  }

  createCase(request: { alertId: string; title?: string; priority?: string }) {
    return this.post<FraudCase>("/cases", request);
  }

  updateCaseStatus(id: string, status: CaseStatus, resolution?: CaseResolution) {
    return this.patch<FraudCase>(`/cases/${id}/status`, { status, resolution });
  }

  assignCase(id: string, assigneeId: string) {
    return this.patch<FraudCase>(`/cases/${id}/assign`, { assigneeId });
  }

  listCaseComments(id: string) {
    return this.get<CaseComment[]>(`/cases/${id}/comments`);
  }

  addCaseComment(id: string, content: string) {
    return this.post<CaseComment>(`/cases/${id}/comments`, { content });
  }

  getCaseHistory(id: string) {
    return this.get<CaseHistoryEntry[]>(`/cases/${id}/history`);
  }

  /* ---------- AI agents ---------- */

  runInvestigation(caseId: string) {
    return this.post<Investigation>(`/cases/${caseId}/investigate`);
  }

  getInvestigation(caseId: string) {
    return this.get<Investigation>(`/cases/${caseId}/investigation`);
  }

  runRecommendation(caseId: string) {
    return this.post<Recommendation>(`/cases/${caseId}/recommend`);
  }

  getCaseSummary(caseId: string) {
    return this.get<CaseSummary>(`/cases/${caseId}/summary`);
  }

  getAlertExplanation(alertId: string) {
    return this.get<Explanation>(`/explanations/alert/${alertId}`);
  }

  listPatterns() {
    return this.get<FraudPattern[]>("/patterns");
  }

  analyzePatterns() {
    return this.post<FraudPattern[]>("/patterns/analyze");
  }

  /* ---------- Behavioural ---------- */

  listDevices(userId: string) {
    return this.get<Device[]>(`/devices/${encodeURIComponent(userId)}`);
  }

  blacklistDevice(deviceId: string) {
    return this.post<Device>(`/devices/${encodeURIComponent(deviceId)}/blacklist`);
  }

  getProfile(userId: string) {
    return this.get<UserProfile>(`/profiles/${encodeURIComponent(userId)}`);
  }

  analyzeProfile(userId: string) {
    return this.post<UserProfile>(`/profiles/${encodeURIComponent(userId)}/analyze`);
  }

  getVelocity(userId: string) {
    return this.get<VelocitySnapshot>(`/velocity/${encodeURIComponent(userId)}`);
  }

  /* ---------- Analytics ---------- */

  getDashboard() {
    return this.get<DashboardStats>("/dashboard");
  }

  getDashboardSummary() {
    return this.get<DashboardStats>("/dashboard/summary");
  }

  getFraudTrend(days = 14) {
    return this.get<FraudTrend>("/dashboard/fraud-trend", { days });
  }

  getTopMerchants(limit = 8) {
    return this.get<MerchantRisk[]>("/dashboard/top-merchants", { limit });
  }

  getGeographicRisk() {
    return this.get<GeographicRisk[]>("/dashboard/geographic");
  }

  /* ---------- Events ---------- */

  listEvents(aggregateId: string) {
    return this.get<FraudEventRecord[]>(`/events/${aggregateId}`);
  }

  /* ---------- Scam and APP fraud ---------- */

  assessScam(request: Record<string, unknown>) {
    return this.post<ScamAssessment>("/scam/assess", request);
  }

  listScamSignals(params: { page?: number; size?: number; typology?: string }) {
    return this.get<Page<ScamSignal>>("/scam/signals", params);
  }

  getScamTypologies(days = 30) {
    return this.get<TypologyBreakdown[]>("/scam/typologies", { days });
  }

  listPaymentHolds() {
    return this.get<PaymentHold[]>("/scam/holds");
  }

  releaseHold(id: string) {
    return this.post<PaymentHold>(`/scam/holds/${id}/release`);
  }

  cancelHold(id: string, byCustomer = false) {
    return this.post<PaymentHold>(`/scam/holds/${id}/cancel`, undefined, { byCustomer });
  }

  /* ---------- Mule and beneficiary ---------- */

  listBeneficiaries(params: { page?: number; size?: number }) {
    return this.get<Page<BeneficiaryRisk>>("/beneficiaries", params);
  }

  sweepBeneficiaries(limit = 200) {
    return this.post<BeneficiaryRisk[]>("/beneficiaries/sweep", undefined, { limit });
  }

  confirmMule(beneficiaryId: string, source = "ANALYST") {
    return this.post<BeneficiaryRisk>(
      `/beneficiaries/${encodeURIComponent(beneficiaryId)}/confirm`,
      { source }
    );
  }

  getBeneficiaryPayers(beneficiaryId: string) {
    return this.get<string[]>(`/beneficiaries/${encodeURIComponent(beneficiaryId)}/payers`);
  }

  getMuleStats() {
    return this.get<StatMap>("/beneficiaries/stats");
  }

  /* ---------- Graph ---------- */

  rebuildGraph() {
    return this.post<StatMap>("/graph/rebuild");
  }

  getGraphNeighbourhood(entityId: string, depth = 2) {
    return this.get<GraphView>(`/graph/entity/${encodeURIComponent(entityId)}`, { depth });
  }

  listRings(params: { page?: number; size?: number }) {
    return this.get<Page<FraudRing>>("/graph/rings", params);
  }

  detectRings() {
    return this.post<FraudRing[]>("/graph/rings/detect");
  }

  updateRingStatus(ringId: string, status: string) {
    return this.patch<FraudRing>(`/graph/rings/${ringId}/status`, { status });
  }

  getGraphStats() {
    return this.get<StatMap>("/graph/stats");
  }

  /* ---------- Identity ---------- */

  verifyIdentity(request: Record<string, unknown>) {
    return this.post<IdentityVerification>("/identity/verify", request);
  }

  listVerifications(params: { page?: number; size?: number; decision?: string }) {
    return this.get<Page<IdentityVerification>>("/identity/verifications", params);
  }

  listScreeningHits(params: { status?: string; page?: number; size?: number }) {
    return this.get<Page<SanctionsHit>>("/identity/screening", params);
  }

  decideScreeningHit(hitId: string, status: string, note?: string) {
    return this.patch<SanctionsHit>(`/identity/screening/${hitId}`, { status, note });
  }

  getIdentityStats() {
    return this.get<StatMap>("/identity/stats");
  }

  /* ---------- AML ---------- */

  listAmlFindings(params: { page?: number; size?: number; status?: string }) {
    return this.get<Page<AmlFinding>>("/aml/findings", params);
  }

  sweepAml(days = 90, maxSubjects = 500) {
    return this.post<StatMap>("/aml/sweep", undefined, { days, maxSubjects });
  }

  draftSar(request: { subjectId: string; subjectName?: string; jurisdiction?: string; findingIds?: string[] }) {
    return this.post<SarFiling>("/aml/sar/draft", request);
  }

  listSars(params: { page?: number; size?: number; status?: string }) {
    return this.get<Page<SarFiling>>("/aml/sar", params);
  }

  approveSar(id: string) {
    return this.post<SarFiling>(`/aml/sar/${id}/approve`);
  }

  fileSar(id: string) {
    return this.post<SarFiling>(`/aml/sar/${id}/file`);
  }

  getAmlStats() {
    return this.get<StatMap>("/aml/stats");
  }

  /* ---------- Autonomy ---------- */

  listAutonomyPolicies() {
    return this.get<AutonomyPolicy[]>("/autonomy/policies");
  }

  saveAutonomyPolicy(policy: AutonomyPolicy) {
    return this.request<AutonomyPolicy>("/autonomy/policies", {
      method: "PUT",
      body: JSON.stringify(policy),
    });
  }

  runTriage(limit = 25) {
    return this.post<{ processed: number; resolvedAutonomously: number; suppressed: number; runs: AgentRun[] }>(
      "/autonomy/triage/run",
      undefined,
      { limit }
    );
  }

  listAgentRuns(params: { page?: number; size?: number; agent?: string }) {
    return this.get<Page<AgentRun>>("/autonomy/runs", params);
  }

  reviewAgentRun(runId: string, agreed: boolean) {
    return this.post<AgentRun>(`/autonomy/runs/${runId}/review`, { agreed });
  }

  runRedTeam(scenario?: string) {
    return this.post<AttackSimulation[]>("/autonomy/redteam/run", undefined, { scenario });
  }

  listRedTeamResults(params: { page?: number; size?: number }) {
    return this.get<Page<AttackSimulation>>("/autonomy/redteam/results", params);
  }

  getAutonomyStats(days = 30) {
    return this.get<StatMap>("/autonomy/stats", { days });
  }

  /* ---------- Strategy ---------- */

  parseRule(sentence: string) {
    return this.post<ParsedRule>("/strategy/rules/parse", { sentence });
  }

  createRule(request: Record<string, unknown>) {
    return this.post<RuleDefinition>("/strategy/rules", request);
  }

  listRules(params: { page?: number; size?: number }) {
    return this.get<Page<RuleDefinition>>("/strategy/rules", params);
  }

  promoteRule(id: string, status: string, trafficPercent = 10) {
    return this.post<RuleDefinition>(`/strategy/rules/${id}/promote`, { status, trafficPercent });
  }

  backtestRule(id: string, days = 90, sample = 2000) {
    return this.post<RuleBacktest>(`/strategy/rules/${id}/backtest`, undefined, { days, sample });
  }

  simulateRule(request: Record<string, unknown>) {
    return this.post<RuleBacktest>("/strategy/simulate", request);
  }

  discoverRules(days = 90, sample = 2000) {
    return this.post<RuleCandidate[]>("/strategy/discover", undefined, { days, sample });
  }

  getThresholdTuning(days = 90) {
    return this.get<TuningResult>("/strategy/tuning", { days });
  }

  runRetroAnalysis(days = 365, sample = 5000) {
    return this.post<RetroResult>("/strategy/retro", undefined, { days, sample });
  }

  getStrategyStats() {
    return this.get<StatMap>("/strategy/stats");
  }

  /* ---------- Generative-AI defence ---------- */

  analyseMedia(request: Record<string, unknown>) {
    return this.post<MediaAnalysis>("/genai/media/analyse", request);
  }

  listMediaAnalyses(params: { page?: number; size?: number; mediaType?: string }) {
    return this.get<Page<MediaAnalysis>>("/genai/media", params);
  }

  listAiAgents(params: { page?: number; size?: number }) {
    return this.get<Page<AiAgentIdentity>>("/genai/agents", params);
  }

  registerAiAgent(request: Record<string, unknown>) {
    return this.post<AiAgentIdentity>("/genai/agents", request);
  }

  revokeAiAgent(id: string, reason: string) {
    return this.post<AiAgentIdentity>(`/genai/agents/${id}/revoke`, { reason });
  }

  assessBrandDomains(brandTerms: string[], domains: string[]) {
    return this.post<PhishingDomain[]>("/genai/brand/assess", { brandTerms, domains });
  }

  listPhishingDomains(params: { page?: number; size?: number }) {
    return this.get<Page<PhishingDomain>>("/genai/brand/domains", params);
  }

  getGenAiStats() {
    return this.get<StatMap>("/genai/stats");
  }

  /* ---------- Disputes ---------- */

  listDisputes(params: { page?: number; size?: number; status?: string }) {
    return this.get<Page<Dispute>>("/disputes", params);
  }

  draftRepresentment(id: string) {
    return this.post<Dispute>(`/disputes/${id}/draft`);
  }

  submitDispute(id: string) {
    return this.post<Dispute>(`/disputes/${id}/submit`);
  }

  getDisputeStats(days = 90) {
    return this.get<StatMap>("/disputes/stats", { days });
  }

  /* ---------- Governance ---------- */

  listModels() {
    return this.get<ModelRegistryEntry[]>("/governance/models");
  }

  getModelDocumentation(id: string) {
    return this.get<{ documentation: string }>(`/governance/models/${id}/documentation`);
  }

  detectDrift(model = "RiskScoringService", baselineDays = 60, currentDays = 14) {
    return this.post<DriftReport>("/governance/drift/detect", undefined, {
      model,
      baselineDays,
      currentDays,
    });
  }

  listDriftReports(params: { page?: number; size?: number }) {
    return this.get<Page<DriftReport>>("/governance/drift", params);
  }

  testFairness(model = "RiskScoringService", attribute = "COUNTRY", days = 90) {
    return this.post<FairnessReport>("/governance/fairness/test", undefined, {
      model,
      attribute,
      days,
    });
  }

  listFairnessReports(params: { page?: number; size?: number }) {
    return this.get<Page<FairnessReport>>("/governance/fairness", params);
  }

  getGovernanceStats() {
    return this.get<StatMap>("/governance/stats");
  }

  /* ---------- Friction ---------- */

  listFrictionPolicies() {
    return this.get<FrictionPolicy[]>("/friction/policies");
  }

  getFrictionStats(days = 30) {
    return this.get<StatMap>("/friction/stats", { days });
  }

  /* ---------- Integrations ---------- */

  listWebhooks() {
    return this.get<WebhookSubscription[]>("/integrations/webhooks");
  }

  createWebhook(request: { name: string; targetUrl: string; events: string[] }) {
    return this.post<Record<string, unknown>>("/integrations/webhooks", request);
  }

  listApiKeys() {
    return this.get<ApiKeySummary[]>("/integrations/api-keys");
  }

  issueApiKey(request: { name: string; scopes?: string[] }) {
    return this.post<Record<string, unknown>>("/integrations/api-keys", request);
  }

  listConnectors() {
    return this.get<Connector[]>("/integrations/connectors");
  }

  /* ---------- Consortium ---------- */

  queryConsortium(identifier: string, identifierType = "ACCOUNT") {
    return this.post<{ matches: unknown[]; risk: number; known: boolean }>("/consortium/query", {
      identifier,
      identifierType,
    });
  }

  getConsortiumSummary() {
    return this.get<Array<Record<string, unknown>>>("/consortium/summary");
  }

  getConsortiumStats() {
    return this.get<StatMap>("/consortium/stats");
  }

  /* ---------- Return on investment ---------- */

  getRoi(days = 30) {
    return this.get<RoiSummary>("/roi", { days });
  }

  getForecast(historyDays = 60, horizonDays = 14) {
    return this.get<ForecastResult>("/roi/forecast", { historyDays, horizonDays });
  }

  /* ---------- Case operations ---------- */

  bulkUpdateAlerts(alertIds: string[], status: AlertStatus) {
    return this.post<StatMap>("/case-ops/alerts/bulk-status", { alertIds, status });
  }

  takedownRing(members: string[], source = "RING_TAKEDOWN") {
    return this.post<StatMap>("/case-ops/rings/takedown", { members, source });
  }

  drawQaSample(size = 20) {
    return this.post<unknown[]>("/case-ops/qa/sample", undefined, { size });
  }

  getQaScorecard() {
    return this.get<Record<string, unknown>>("/case-ops/qa/scorecard");
  }

  /* ---------- Demo ---------- */

  seedDemoData(force = false) {
    return this.post<SeedResult>("/demo/seed", undefined, { force });
  }
}

export const apiClient = new ApiClient();
