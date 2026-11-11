"use client";

import { use, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Bell, FileClock, MessageSquare, Send, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { AiInvestigationPanel } from "@/components/ai/AiInvestigationPanel";
import { EmptyState, ErrorState } from "@/components/data/States";
import { RiskBreakdown, RiskScoreDial } from "@/components/charts/RiskBreakdown";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, Textarea } from "@/components/ui/input";
import { Skeleton, SkeletonText } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Badge,
  CaseStatusBadge,
  PriorityBadge,
  RiskLevelBadge,
  SeverityBadge,
  TransactionStatusBadge,
} from "@/components/ui/badge";
import { ResolveCaseDialog } from "@/components/cases/ResolveCaseDialog";
import { BehaviourSignals } from "@/components/cases/BehaviourSignals";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import {
  useAlert,
  useCase,
  useCaseComments,
  useCaseHistory,
  useOrganizationMembers,
  useRiskScore,
  useTransaction,
} from "@/lib/hooks/useApi";
import { dateTime, fullName, humanise, money, relativeTime, shortId } from "@/lib/format";
import type { CaseStatus } from "@/types";

const WORKING_STATUSES: CaseStatus[] = ["OPEN", "INVESTIGATING"];

export default function CaseWorkspacePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);

  const fraudCase = useCase(id);
  const alert = useAlert(fraudCase.data?.alertId ?? null);
  const transaction = useTransaction(alert.data?.transactionId ?? null);
  const riskScore = useRiskScore(alert.data?.transactionId ?? null);
  const comments = useCaseComments(id);
  const history = useCaseHistory(id);
  const { data: members } = useOrganizationMembers();

  const [updating, setUpdating] = useState(false);

  const memberName = (userId: string | null) => {
    if (!userId) return "Unassigned";
    const member = members?.find((candidate) => candidate.id === userId);
    return member ? fullName(member) : shortId(userId);
  };

  const changeStatus = async (status: CaseStatus) => {
    setUpdating(true);
    try {
      const updated = await apiClient.updateCaseStatus(id, status);
      await fraudCase.mutate(updated, { revalidate: false });
      await history.mutate();
      toast.success("Case updated", `Status is now ${humanise(status).toLowerCase()}.`);
    } catch (error) {
      toast.fromError(error, "Could not update the case");
    } finally {
      setUpdating(false);
    }
  };

  const assign = async (assigneeId: string) => {
    if (!assigneeId) return;
    setUpdating(true);
    try {
      const updated = await apiClient.assignCase(id, assigneeId);
      await fraudCase.mutate(updated, { revalidate: false });
      await history.mutate();
      toast.success("Case assigned", `Now owned by ${memberName(assigneeId)}.`);
    } catch (error) {
      toast.fromError(error, "Could not assign the case");
    } finally {
      setUpdating(false);
    }
  };

  if (fraudCase.error) {
    return (
      <AppShell title="Case">
        <Card>
          <ErrorState error={fraudCase.error} onRetry={() => fraudCase.mutate()} />
        </Card>
      </AppShell>
    );
  }

  const resolved =
    fraudCase.data?.status === "RESOLVED" || fraudCase.data?.status === "CLOSED";

  return (
    <AppShell
      title={fraudCase.data?.title ?? "Case"}
      subtitle={fraudCase.data ? `Case ${shortId(fraudCase.data.id)} · opened ${relativeTime(fraudCase.data.createdAt)}` : undefined}
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/cases">
              <ArrowLeft />
              Back to cases
            </Link>
          </Button>

          <div className="flex flex-wrap items-center gap-2">
            <Select
              aria-label="Assign case"
              className="w-48"
              value={fraudCase.data?.assignedTo ?? ""}
              disabled={updating || !members?.length}
              onChange={(event) => assign(event.target.value)}
            >
              <option value="">Unassigned</option>
              {members?.map((member) => (
                <option key={member.id} value={member.id}>
                  {fullName(member)}
                </option>
              ))}
            </Select>

            <Select
              aria-label="Case status"
              className="w-40"
              value={fraudCase.data?.status ?? "OPEN"}
              disabled={updating || !fraudCase.data || resolved}
              onChange={(event) => changeStatus(event.target.value as CaseStatus)}
            >
              {WORKING_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {humanise(status)}
                </option>
              ))}
            </Select>

            <ResolveCaseDialog
              caseId={id}
              disabled={!fraudCase.data || resolved}
              onResolved={async (updated) => {
                await fraudCase.mutate(updated, { revalidate: false });
                await history.mutate();
              }}
            />
          </div>
        </div>

        {/* Case summary strip */}
        <Card>
          <CardContent className="flex flex-wrap items-center gap-x-8 gap-y-4 py-4">
            {fraudCase.isLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : fraudCase.data ? (
              <>
                <Summary label="Status">
                  <CaseStatusBadge status={fraudCase.data.status} />
                </Summary>
                <Summary label="Priority">
                  <PriorityBadge priority={fraudCase.data.priority} />
                </Summary>
                <Summary label="Assigned to">
                  <span className="text-sm text-ink">{memberName(fraudCase.data.assignedTo)}</span>
                </Summary>
                <Summary label="Opened">
                  <span className="text-sm text-ink">{dateTime(fraudCase.data.createdAt)}</span>
                </Summary>
                {fraudCase.data.resolution ? (
                  <Summary label="Resolution">
                    <Badge tone={fraudCase.data.resolution === "FRAUD" ? "critical" : "good"}>
                      {humanise(fraudCase.data.resolution)}
                    </Badge>
                  </Summary>
                ) : null}
              </>
            ) : null}
          </CardContent>
        </Card>

        <div className="grid gap-4 xl:grid-cols-[1.5fr_1fr]">
          <div className="min-w-0 space-y-4">
            <AiInvestigationPanel caseId={id} />

            <Tabs defaultValue="evidence">
              <TabsList>
                <TabsTrigger value="evidence">Evidence</TabsTrigger>
                <TabsTrigger value="behaviour">Behaviour</TabsTrigger>
                <TabsTrigger value="discussion">
                  Discussion
                  {comments.data?.length ? (
                    <span className="ml-1.5 text-xs text-ink-muted tabular">
                      {comments.data.length}
                    </span>
                  ) : null}
                </TabsTrigger>
                <TabsTrigger value="history">History</TabsTrigger>
              </TabsList>

              <TabsContent value="evidence">
                <EvidenceTab
                  alertId={fraudCase.data?.alertId}
                  alert={alert}
                  transaction={transaction}
                />
              </TabsContent>

              <TabsContent value="behaviour">
                <BehaviourSignals
                  userId={transaction.data?.userId ?? null}
                  currency={transaction.data?.currency}
                  deviceId={transaction.data?.deviceId}
                />
              </TabsContent>

              <TabsContent value="discussion">
                <DiscussionTab caseId={id} comments={comments} memberName={memberName} />
              </TabsContent>

              <TabsContent value="history">
                <HistoryTab history={history} memberName={memberName} />
              </TabsContent>
            </Tabs>
          </div>

          <Card className="h-fit">
            <CardHeader className="border-b border-line pb-4">
              <CardTitle>Risk breakdown</CardTitle>
              <CardDescription>Signals behind the original score</CardDescription>
            </CardHeader>
            <CardContent className="space-y-5">
              {riskScore.isLoading ? (
                <div className="space-y-4">
                  <Skeleton className="mx-auto size-28 rounded-full" />
                  <SkeletonText lines={5} />
                </div>
              ) : riskScore.data ? (
                <>
                  <div className="flex flex-col items-center gap-3">
                    <RiskScoreDial score={riskScore.data.score} />
                    <RiskLevelBadge level={riskScore.data.level} />
                  </div>
                  <RiskBreakdown riskScore={riskScore.data} />
                </>
              ) : (
                <p className="py-4 text-center text-xs text-ink-muted">
                  No risk score recorded for this transaction.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </AppShell>
  );
}

function Summary({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</p>
      {children}
    </div>
  );
}

/* ---------- Tabs ---------- */

type AlertHook = ReturnType<typeof useAlert>;
type TransactionHook = ReturnType<typeof useTransaction>;
type CommentsHook = ReturnType<typeof useCaseComments>;
type HistoryHook = ReturnType<typeof useCaseHistory>;

function EvidenceTab({
  alertId,
  alert,
  transaction,
}: {
  alertId?: string;
  alert: AlertHook;
  transaction: TransactionHook;
}) {
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0 border-b border-line pb-4">
          <div className="space-y-1">
            <CardTitle>Originating alert</CardTitle>
            <CardDescription>What the rule engine saw</CardDescription>
          </div>
          {alertId ? (
            <Button variant="ghost" size="sm" asChild>
              <Link href={`/alerts/${alertId}`}>
                <Bell />
                Open alert
              </Link>
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-4">
          {alert.isLoading ? (
            <SkeletonText lines={3} />
          ) : alert.data ? (
            <>
              <div className="flex flex-wrap gap-2">
                <SeverityBadge severity={alert.data.severity} />
              </div>
              <p className="text-sm leading-relaxed text-ink-secondary">{alert.data.reason}</p>
              {alert.data.triggeredRules?.length ? (
                <div className="flex flex-wrap gap-1.5">
                  {alert.data.triggeredRules.map((rule) => (
                    <Badge key={rule} tone="serious">
                      {humanise(rule)}
                    </Badge>
                  ))}
                </div>
              ) : null}
            </>
          ) : (
            <p className="text-xs text-ink-muted">Alert details unavailable.</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Transaction</CardTitle>
          <CardDescription>The activity under investigation</CardDescription>
        </CardHeader>
        <CardContent>
          {transaction.isLoading ? (
            <SkeletonText lines={4} />
          ) : transaction.data ? (
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3.5 sm:grid-cols-3">
              <Field label="Amount">
                <span className="text-sm font-semibold text-ink">
                  {money(transaction.data.amount, transaction.data.currency)}
                </span>
              </Field>
              <Field label="Status">
                <TransactionStatusBadge status={transaction.data.status} />
              </Field>
              <Field label="Occurred">{dateTime(transaction.data.timestamp)}</Field>
              <Field label="Customer">{transaction.data.userId}</Field>
              <Field label="Merchant">{transaction.data.merchantId}</Field>
              <Field label="Country">{transaction.data.country ?? "—"}</Field>
              <Field label="Device">{transaction.data.deviceId ?? "—"}</Field>
              <Field label="IP address">{transaction.data.ipAddress ?? "—"}</Field>
              <Field label="External ID">{transaction.data.transactionExternalId}</Field>
            </dl>
          ) : (
            <p className="text-xs text-ink-muted">Transaction details unavailable.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DiscussionTab({
  caseId,
  comments,
  memberName,
}: {
  caseId: string;
  comments: CommentsHook;
  memberName: (userId: string | null) => string;
}) {
  const [draft, setDraft] = useState("");
  const [posting, setPosting] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content) return;

    setPosting(true);
    try {
      await apiClient.addCaseComment(caseId, content);
      setDraft("");
      await comments.mutate();
      toast.success("Comment added");
    } catch (error) {
      toast.fromError(error, "Could not add the comment");
    } finally {
      setPosting(false);
    }
  };

  return (
    <Card>
      <CardHeader className="border-b border-line pb-4">
        <CardTitle>Discussion</CardTitle>
        <CardDescription>Notes shared with the rest of the fraud team</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="space-y-2">
          <Textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Add what you found, or why you reached your decision…"
            aria-label="New comment"
            rows={3}
          />
          <div className="flex justify-end">
            <Button type="submit" size="sm" loading={posting} disabled={!draft.trim()}>
              {!posting && <Send />}
              Post comment
            </Button>
          </div>
        </form>

        {comments.isLoading ? (
          <SkeletonText lines={4} />
        ) : comments.error ? (
          <ErrorState error={comments.error} onRetry={() => comments.mutate()} />
        ) : comments.data?.length ? (
          <ul className="space-y-3 border-t border-line pt-4">
            {comments.data.map((comment) => (
              <li key={comment.id} className="flex gap-3">
                <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-surface-sunken text-[10px] font-semibold text-ink-secondary">
                  {memberName(comment.userId).slice(0, 2).toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline gap-2">
                    <p className="text-xs font-medium text-ink">{memberName(comment.userId)}</p>
                    <p className="text-[11px] text-ink-muted">{relativeTime(comment.createdAt)}</p>
                  </div>
                  <p className="mt-0.5 whitespace-pre-line text-sm leading-relaxed text-ink-secondary">
                    {comment.content}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState
            icon={MessageSquare}
            title="No comments yet"
            description="Record what you checked so the next analyst does not repeat the work."
          />
        )}
      </CardContent>
    </Card>
  );
}

function HistoryTab({
  history,
  memberName,
}: {
  history: HistoryHook;
  memberName: (userId: string | null) => string;
}) {
  return (
    <Card>
      <CardHeader className="border-b border-line pb-4">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-primary" />
          <CardTitle>Audit history</CardTitle>
        </div>
        <CardDescription>
          Append-only. Every state change on this case, with who made it and when.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {history.isLoading ? (
          <SkeletonText lines={5} />
        ) : history.error ? (
          <ErrorState error={history.error} onRetry={() => history.mutate()} />
        ) : history.data?.length ? (
          <ol className="relative space-y-5 border-l border-line pl-5">
            {history.data.map((entry) => (
              <li key={entry.id} className="relative">
                <span
                  className="absolute -left-[1.56rem] top-1.5 size-2 rounded-full bg-primary ring-2 ring-surface"
                  aria-hidden
                />
                <div className="flex flex-wrap items-baseline gap-x-2">
                  <p className="text-sm font-medium text-ink">{humanise(entry.action)}</p>
                  <p className="text-[11px] text-ink-muted">{dateTime(entry.performedAt)}</p>
                </div>
                <p className="mt-0.5 text-xs text-ink-secondary">
                  {entry.oldValue ? (
                    <>
                      {humanise(entry.oldValue)} <span className="text-ink-muted">to</span>{" "}
                      {humanise(entry.newValue)}
                    </>
                  ) : (
                    humanise(entry.newValue)
                  )}
                  <span className="text-ink-muted"> · by {memberName(entry.performedBy)}</span>
                </p>
              </li>
            ))}
          </ol>
        ) : (
          <EmptyState
            icon={FileClock}
            title="No history recorded"
            description="Entries appear as the case moves through its lifecycle."
          />
        )}
      </CardContent>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-0.5 truncate text-sm text-ink-secondary">{children}</dd>
    </div>
  );
}
