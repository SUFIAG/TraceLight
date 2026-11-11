"use client";

import { useState } from "react";
import { Brain, FlaskConical, Lightbulb, Rocket, Sliders } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiClient } from "@/lib/api-client";
import { money } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useRules, useStrategyStats, useThresholdTuning } from "@/lib/hooks/useApi";
import type { ParsedRule, RuleBacktest, RuleCandidate, RuleStatus } from "@/types";

const STATUS_TONE: Record<RuleStatus, "neutral" | "info" | "warning" | "good"> = {
  DRAFT: "neutral",
  SHADOW: "info",
  CHALLENGER: "warning",
  CHAMPION: "good",
  RETIRED: "neutral",
};

export default function StrategyPage() {
  const stats = useStrategyStats();
  const rules = useRules(0, 20);
  const tuning = useThresholdTuning(90);

  const [sentence, setSentence] = useState(
    "Block transfers over 5000 to new payees with more than 3 payments in 24 hours"
  );
  const [parsed, setParsed] = useState<ParsedRule | null>(null);
  const [backtest, setBacktest] = useState<RuleBacktest | null>(null);
  const [candidates, setCandidates] = useState<RuleCandidate[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const parse = async () => {
    setBusy("parse");
    try {
      const result = await apiClient.parseRule(sentence);
      setParsed(result);
      setBacktest(null);
    } catch (error) {
      toast.fromError(error, "Could not read that as a rule");
    } finally {
      setBusy(null);
    }
  };

  const simulate = async () => {
    setBusy("simulate");
    try {
      const result = await apiClient.simulateRule({ sentence, days: 90, sample: 2000 });
      setBacktest(result);
    } catch (error) {
      toast.fromError(error, "Backtest failed");
    } finally {
      setBusy(null);
    }
  };

  const save = async () => {
    setBusy("save");
    try {
      await apiClient.createRule({
        name: sentence.slice(0, 60),
        sentence,
        riskPoints: 60,
      });
      toast.success("Rule saved as a draft", "Run it in shadow before it affects any customer.");
      await Promise.all([rules.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not save the rule");
    } finally {
      setBusy(null);
    }
  };

  const promote = async (id: string, status: string) => {
    try {
      await apiClient.promoteRule(id, status, 10);
      toast.success("Rule promoted", `Now ${status.toLowerCase()}.`);
      await rules.mutate();
    } catch (error) {
      toast.fromError(error, "Promotion refused");
    }
  };

  const discover = async () => {
    setBusy("discover");
    try {
      const found = await apiClient.discoverRules(90, 2000);
      setCandidates(found);
      toast.success("Discovery complete", `${found.length} candidate rules proposed.`);
    } catch (error) {
      toast.fromError(error, "Discovery failed");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="Rule studio"
      subtitle="Write rules in English, backtest them, run them in shadow, then promote"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile label="Champion" numeric={Number(stats.data?.champion ?? 0)} tone="good" loading={stats.isLoading} />
          <StatTile label="Challenger" numeric={Number(stats.data?.challenger ?? 0)} tone="warning" loading={stats.isLoading} />
          <StatTile label="In shadow" numeric={Number(stats.data?.shadow ?? 0)} tone="default" loading={stats.isLoading} />
          <StatTile label="Draft" numeric={Number(stats.data?.draft ?? 0)} loading={stats.isLoading} />
        </div>

        <Tabs defaultValue="author">
          <TabsList>
            <TabsTrigger value="author">Author</TabsTrigger>
            <TabsTrigger value="rules">Live rules</TabsTrigger>
            <TabsTrigger value="discover">Discovery</TabsTrigger>
            <TabsTrigger value="tuning">Threshold</TabsTrigger>
          </TabsList>

          <TabsContent value="author" className="space-y-3">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle>Describe the rule</CardTitle>
                <CardDescription>
                  Plain English. The platform reads it back as conditions over named features, so you
                  can confirm it says what you meant before anything goes live.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 pt-0">
                <div className="space-y-1.5">
                  <Label htmlFor="rule-sentence">Rule</Label>
                  <Input
                    id="rule-sentence"
                    value={sentence}
                    onChange={(event) => setSentence(event.target.value)}
                  />
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" onClick={parse} loading={busy === "parse"}>
                    <Brain />
                    Read it back
                  </Button>
                  <Button size="sm" variant="secondary" onClick={simulate} loading={busy === "simulate"}>
                    <FlaskConical />
                    Backtest on history
                  </Button>
                  <Button size="sm" variant="ghost" onClick={save} loading={busy === "save"}>
                    Save as draft
                  </Button>
                </div>

                {parsed && (
                  <div className="rounded border border-line bg-surface-sunken p-3">
                    <p className="text-sm text-ink">{parsed.explanation}</p>
                    <pre className="mt-2 overflow-auto text-[11px] leading-relaxed text-ink-muted">
                      {JSON.stringify(parsed.definition, null, 2)}
                    </pre>
                    <p className="pt-1 text-[11px] text-ink-muted">
                      {parsed.llmAssisted ? "Interpreted by the language model" : "Parsed deterministically"}
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>

            {backtest && (
              <Card>
                <CardHeader className="pb-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <CardTitle>Backtest</CardTitle>
                    <Badge
                      tone={
                        backtest.recommendation === "SHIP"
                          ? "good"
                          : backtest.recommendation === "SHIP_IN_SHADOW"
                            ? "warning"
                            : "critical"
                      }
                    >
                      {backtest.recommendation.replaceAll("_", " ").toLowerCase()}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3 pt-0">
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <Metric label="Hits" value={backtest.hitCount} />
                    <Metric label="Precision" value={`${Math.round(backtest.precision * 100)}%`} />
                    <Metric label="Caught" value={money(backtest.valueCaught ?? 0)} />
                    <Metric label="Review load" value={`${backtest.monthlyReviewHours}h / month`} />
                  </div>
                  <p className="text-sm leading-relaxed text-ink-secondary">
                    {backtest.recommendationReason}
                  </p>
                  {backtest.sampleHits?.length ? (
                    <details className="text-xs text-ink-muted">
                      <summary className="cursor-pointer text-ink-secondary">Sample hits</summary>
                      <ul className="space-y-0.5 pt-1">
                        {backtest.sampleHits.map((hit, index) => (
                          <li key={index}>• {hit}</li>
                        ))}
                      </ul>
                    </details>
                  ) : null}
                </CardContent>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="rules" className="space-y-2">
            {rules.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : rules.data?.content?.length ? (
              rules.data.content.map((rule) => (
                <Card key={rule.id}>
                  <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                    <div className="min-w-0 space-y-1">
                      <p className="text-sm font-medium text-ink">
                        {rule.name} <span className="text-ink-muted">v{rule.version}</span>
                      </p>
                      <p className="text-xs text-ink-secondary">{rule.description}</p>
                      <p className="text-xs text-ink-muted">
                        {rule.hitCount} hits · {rule.riskPoints} risk points
                        {rule.status === "CHALLENGER" ? ` · ${rule.trafficPercent}% of traffic` : ""}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge tone={STATUS_TONE[rule.status]}>{rule.status.toLowerCase()}</Badge>
                      {rule.status === "DRAFT" && (
                        <Button size="sm" variant="secondary" onClick={() => promote(rule.id, "SHADOW")}>
                          Run in shadow
                        </Button>
                      )}
                      {rule.status === "SHADOW" && (
                        <Button size="sm" onClick={() => promote(rule.id, "CHAMPION")}>
                          <Rocket />
                          Promote
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Brain}
                  title="No rules yet"
                  description="Describe one in English on the Author tab. It is backtested before it can go live."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="discover" className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="max-w-2xl text-sm leading-relaxed text-ink-secondary">
                Discovery compares the feature distribution over confirmed fraud against legitimate
                traffic and proposes thresholds where the separation is widest. Candidates arrive
                measured; nothing deploys itself.
              </p>
              <Button size="sm" onClick={discover} loading={busy === "discover"}>
                <Lightbulb />
                Mine confirmed fraud
              </Button>
            </div>

            {candidates?.length ? (
              <div className="space-y-2">
                {candidates.map((candidate, index) => (
                  <Card key={index}>
                    <CardContent className="space-y-2 py-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-ink">{candidate.sentence}</p>
                        <div className="flex gap-2">
                          <Badge tone="info">{Math.round(candidate.precision * 100)}% precision</Badge>
                          <Badge tone="warning">{candidate.lift.toFixed(1)}× lift</Badge>
                        </div>
                      </div>
                      <p className="text-xs text-ink-secondary">{candidate.rationale}</p>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : candidates ? (
              <Card>
                <EmptyState
                  icon={Lightbulb}
                  title="No candidates found"
                  description="Discovery needs at least five confirmed fraud examples before any separation is meaningful."
                />
              </Card>
            ) : null}
          </TabsContent>

          <TabsContent value="tuning" className="space-y-3">
            {tuning.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : tuning.data?.recommended ? (
              <>
                <Card>
                  <CardHeader className="pb-2">
                    <div className="flex items-center justify-between gap-2">
                      <CardTitle>Recommended cut-off</CardTitle>
                      <Badge tone="good">Score {tuning.data.recommended.threshold}</Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-3 pt-0">
                    <p className="text-sm leading-relaxed text-ink-secondary">
                      {tuning.data.rationale}
                    </p>
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <Metric label="Fraud loss" value={money(tuning.data.recommended.fraudLoss)} />
                      <Metric label="Friction cost" value={money(tuning.data.recommended.frictionCost)} />
                      <Metric label="Review cost" value={money(tuning.data.recommended.operatingCost)} />
                      <Metric label="Alerts" value={tuning.data.recommended.alertVolume} />
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="flex items-center gap-2 text-sm">
                      <Sliders className="size-4" />
                      Cost curve
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="pt-0">
                    <div className="flex items-end gap-1" role="img" aria-label="Total cost by threshold">
                      {tuning.data.curve.map((point) => {
                        const max = Math.max(...tuning.data!.curve.map((p) => p.totalCost), 1);
                        const height = Math.max(4, (point.totalCost / max) * 140);
                        const best = point.threshold === tuning.data!.recommended!.threshold;
                        return (
                          <div key={point.threshold} className="flex flex-1 flex-col items-center gap-1">
                            <div
                              className={best ? "w-full rounded-t bg-good" : "w-full rounded-t bg-seq-300"}
                              style={{ height }}
                              title={`Cut-off ${point.threshold}: total cost ${point.totalCost}`}
                            />
                            <span className="text-[9px] tabular text-ink-muted">{point.threshold}</span>
                          </div>
                        );
                      })}
                    </div>
                  </CardContent>
                </Card>
              </>
            ) : (
              <Card>
                <EmptyState
                  icon={Sliders}
                  title="Not enough scored history"
                  description={tuning.data?.rationale ?? "Score some transactions, then tune the cut-off against their outcomes."}
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</p>
      <p className="text-sm font-medium tabular text-ink">{value}</p>
    </div>
  );
}
