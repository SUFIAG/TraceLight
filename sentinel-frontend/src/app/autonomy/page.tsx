"use client";

import { useState } from "react";
import { Bot, PlayCircle, Shield, Swords, ThumbsDown, ThumbsUp } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableShell,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiClient } from "@/lib/api-client";
import { relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import {
  useAgentRuns,
  useAutonomyPolicies,
  useAutonomyStats,
  useRedTeamResults,
} from "@/lib/hooks/useApi";
import type { AgentVerdict, AutonomyLevel } from "@/types";

const VERDICT_TONE: Record<AgentVerdict, "good" | "info" | "warning" | "serious" | "critical"> = {
  BENIGN: "good",
  NEEDS_REVIEW: "info",
  SUSPICIOUS: "warning",
  FRAUDULENT: "critical",
  INSUFFICIENT_EVIDENCE: "serious",
};

const LEVEL_TONE: Record<AutonomyLevel, "neutral" | "info" | "warning" | "good"> = {
  OFF: "neutral",
  SUGGEST_ONLY: "info",
  ACT_ON_LOW_RISK: "warning",
  ACT_WITHIN_LIMITS: "warning",
  FULL_AUTONOMY: "good",
};

export default function AutonomyPage() {
  const stats = useAutonomyStats(30);
  const runs = useAgentRuns(0, 25);
  const policies = useAutonomyPolicies();
  const redTeam = useRedTeamResults(0, 20);
  const [busy, setBusy] = useState<string | null>(null);

  const triage = async () => {
    setBusy("triage");
    try {
      const result = await apiClient.runTriage(25);
      toast.success(
        "Triage complete",
        `${result.processed} alerts read, ${result.resolvedAutonomously} resolved without a human.`
      );
      await Promise.all([runs.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Triage failed");
    } finally {
      setBusy(null);
    }
  };

  const attack = async () => {
    setBusy("redteam");
    try {
      const results = await apiClient.runRedTeam();
      const failures = results.filter((item) => item.outcome !== "PASS").length;
      toast.success(
        "Red team complete",
        `${results.length} scenarios replayed, ${failures} with gaps.`
      );
      await redTeam.mutate();
    } catch (error) {
      toast.fromError(error, "Red team run failed");
    } finally {
      setBusy(null);
    }
  };

  const review = async (runId: string, agreed: boolean) => {
    try {
      await apiClient.reviewAgentRun(runId, agreed);
      toast.success(agreed ? "Marked as correct" : "Marked as incorrect");
      await Promise.all([runs.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not record the review");
    }
  };

  return (
    <AppShell
      title="AI operations"
      subtitle="Autonomous triage, outreach, red teaming, and the limits they work within"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Agent runs"
            numeric={Number(stats.data?.agentRuns ?? 0)}
            loading={stats.isLoading}
            footnote="Last 30 days"
          />
          <StatTile
            label="Resolved autonomously"
            numeric={Number(stats.data?.actionsTaken ?? 0)}
            tone="good"
            loading={stats.isLoading}
            footnote={`${stats.data?.automationRate ?? 0}% of work`}
          />
          <StatTile
            label="Agreement with humans"
            value={stats.data?.agreementRate == null ? "—" : `${stats.data.agreementRate}%`}
            tone="default"
            loading={stats.isLoading}
            footnote="On reviewed decisions"
          />
          <StatTile
            label="Control gaps"
            numeric={Number(stats.data?.controlGaps ?? 0)}
            tone="critical"
            loading={stats.isLoading}
            footnote="Red team scenarios that walked through"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="max-w-2xl text-sm leading-relaxed text-ink-secondary">
            Agents gather evidence, reach a verdict, and act only within limits the institution sets.
            Where policy stopped an action, the reason is recorded — that record is the evidence for
            widening or tightening autonomy later.
          </p>
          <div className="flex gap-2">
            <Button size="sm" onClick={triage} loading={busy === "triage"}>
              <PlayCircle />
              Run triage
            </Button>
            <Button size="sm" variant="secondary" onClick={attack} loading={busy === "redteam"}>
              <Swords />
              Attack our controls
            </Button>
          </div>
        </div>

        <Tabs defaultValue="runs">
          <TabsList>
            <TabsTrigger value="runs">Agent decisions</TabsTrigger>
            <TabsTrigger value="policies">Autonomy limits</TabsTrigger>
            <TabsTrigger value="redteam">Red team</TabsTrigger>
          </TabsList>

          <TabsContent value="runs" className="space-y-2">
            {runs.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : runs.data?.content?.length ? (
              runs.data.content.map((run) => (
                <Card key={run.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-sm">
                        {run.agentName} · {run.targetType.toLowerCase()} {run.targetId.slice(0, 8)}
                      </CardTitle>
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge tone={VERDICT_TONE[run.verdict]}>{run.verdict.toLowerCase()}</Badge>
                        <Badge tone="neutral">{run.confidence}% confident</Badge>
                        {run.actionExecuted ? (
                          <Badge tone="good">{run.proposedAction?.replaceAll("_", " ").toLowerCase()}</Badge>
                        ) : (
                          <Badge tone="warning">held for a human</Badge>
                        )}
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-2 pt-0">
                    <p className="text-sm leading-relaxed text-ink-secondary">{run.rationale}</p>
                    {run.suppressedReason && (
                      <p className="rounded border border-warning/40 bg-warning-wash px-2 py-1 text-xs text-ink">
                        Policy stopped this: {run.suppressedReason}
                      </p>
                    )}
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-xs text-ink-muted">
                        {run.durationMs}ms · {relativeTime(run.createdAt)}
                        {run.llmAssisted ? " · language model assisted" : " · deterministic"}
                      </p>
                      {run.humanAgreed == null ? (
                        <div className="flex gap-2">
                          <Button size="sm" variant="ghost" onClick={() => review(run.id, true)}>
                            <ThumbsUp />
                            Agree
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => review(run.id, false)}>
                            <ThumbsDown />
                            Disagree
                          </Button>
                        </div>
                      ) : (
                        <Badge tone={run.humanAgreed ? "good" : "critical"}>
                          {run.humanAgreed ? "human agreed" : "human disagreed"}
                        </Badge>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Bot}
                  title="No agent activity yet"
                  description="Run triage to let the investigator agent work the open alert queue."
                  action={
                    <Button size="sm" onClick={triage} loading={busy === "triage"}>
                      Run triage
                    </Button>
                  }
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="policies">
            {policies.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Action</TableHead>
                      <TableHead>Autonomy</TableHead>
                      <TableHead>Max amount</TableHead>
                      <TableHead>Max risk</TableHead>
                      <TableHead>Min confidence</TableHead>
                      <TableHead>Maker-checker</TableHead>
                      <TableHead>Daily cap</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {policies.data?.map((policy) => (
                      <TableRow key={policy.actionType}>
                        <TableCell className="font-medium">
                          {policy.actionType.replaceAll("_", " ").toLowerCase()}
                        </TableCell>
                        <TableCell>
                          <Badge tone={LEVEL_TONE[policy.level]}>
                            {policy.level.replaceAll("_", " ").toLowerCase()}
                          </Badge>
                        </TableCell>
                        <TableCell className="tabular">{policy.maxAmount ?? "—"}</TableCell>
                        <TableCell className="tabular">{policy.maxRiskScore ?? "—"}</TableCell>
                        <TableCell className="tabular">{policy.minConfidence ?? "—"}</TableCell>
                        <TableCell>{policy.requiresMakerChecker ? "Required" : "—"}</TableCell>
                        <TableCell className="tabular">{policy.dailyActionCap ?? "—"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            )}
            <p className="pt-2 text-xs text-ink-muted">
              Agents may stop work far more freely than they may act on a customer. A wrongly closed
              alert costs a missed case; a wrongly frozen account costs someone their rent.
            </p>
          </TabsContent>

          <TabsContent value="redteam" className="space-y-2">
            {redTeam.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : redTeam.data?.content?.length ? (
              redTeam.data.content.map((run) => (
                <Card key={run.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-sm">{run.scenarioName}</CardTitle>
                      <Badge
                        tone={
                          run.outcome === "PASS" ? "good" : run.outcome === "PARTIAL" ? "warning" : "critical"
                        }
                      >
                        {run.outcome} · {Math.round(run.detectionRate * 100)}% caught
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-2 pt-0">
                    <p className="text-sm text-ink-secondary">{run.description}</p>
                    <ul className="space-y-0.5 text-xs text-ink-muted">
                      {run.gaps.map((gap, index) => (
                        <li key={index}>• {gap}</li>
                      ))}
                    </ul>
                    {run.recommendations[0] !== "No action needed" && (
                      <div className="rounded border border-line bg-surface-sunken p-2">
                        <p className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">
                          Recommended
                        </p>
                        <ul className="space-y-0.5 pt-1 text-xs text-ink-secondary">
                          {run.recommendations.map((item, index) => (
                            <li key={index}>• {item}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Shield}
                  title="Controls have not been tested"
                  description="Replay every known attack pattern against the live rule set to find what walks through."
                  action={
                    <Button size="sm" onClick={attack} loading={busy === "redteam"}>
                      Attack our controls
                    </Button>
                  }
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
