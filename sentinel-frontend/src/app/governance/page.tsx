"use client";

import { useState } from "react";
import { Activity, FileText, Scale, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiClient } from "@/lib/api-client";
import { relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import {
  useDriftReports,
  useFairnessReports,
  useGovernanceStats,
  useModels,
} from "@/lib/hooks/useApi";

export default function GovernancePage() {
  const stats = useGovernanceStats();
  const models = useModels();
  const drift = useDriftReports(0, 5);
  const fairness = useFairnessReports(0, 5);
  const [busy, setBusy] = useState<string | null>(null);
  const [documentation, setDocumentation] = useState<string | null>(null);

  const runDrift = async () => {
    setBusy("drift");
    try {
      const report = await apiClient.detectDrift();
      toast.success(`Drift check: ${report.status.toLowerCase()}`, `PSI ${report.psi}.`);
      await Promise.all([drift.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Drift check failed");
    } finally {
      setBusy(null);
    }
  };

  const runFairness = async () => {
    setBusy("fairness");
    try {
      const report = await apiClient.testFairness();
      toast.success(`Fairness: ${report.verdict.toLowerCase()}`, report.interpretation);
      await Promise.all([fairness.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Fairness test failed");
    } finally {
      setBusy(null);
    }
  };

  const showDocumentation = async (id: string) => {
    setBusy(id);
    try {
      const result = await apiClient.getModelDocumentation(id);
      setDocumentation(result.documentation);
    } catch (error) {
      toast.fromError(error, "Could not generate documentation");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="Model governance"
      subtitle="Model inventory, drift, disparate impact, and regulator-ready documentation"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="In production"
            numeric={Number(stats.data?.modelsInProduction ?? 0)}
            loading={stats.isLoading}
          />
          <StatTile
            label="Awaiting validation"
            numeric={Number(stats.data?.modelsAwaitingApproval ?? 0)}
            tone="warning"
            loading={stats.isLoading}
          />
          <StatTile
            label="Drifted"
            numeric={Number(stats.data?.driftedModels ?? 0)}
            tone="critical"
            loading={stats.isLoading}
            footnote="PSI above 0.25"
          />
          <StatTile
            label="Fairness failures"
            numeric={Number(stats.data?.fairnessFailures ?? 0)}
            tone="critical"
            loading={stats.isLoading}
            footnote="Below the four-fifths screen"
          />
        </div>

        <p className="max-w-3xl text-sm leading-relaxed text-ink-secondary">
          A weighted scorecard that decides whether a customer&apos;s payment is blocked is a model
          under SR 11-7 and the EU AI Act, whether or not anything was trained. Documentation is
          generated from this registry so it is never older than the data it describes.
        </p>

        <Tabs defaultValue="models">
          <TabsList>
            <TabsTrigger value="models">Inventory</TabsTrigger>
            <TabsTrigger value="drift">Drift</TabsTrigger>
            <TabsTrigger value="fairness">Fairness</TabsTrigger>
          </TabsList>

          <TabsContent value="models" className="space-y-2">
            {models.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : models.data?.length ? (
              models.data.map((model) => (
                <Card key={model.id}>
                  <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                    <div className="min-w-0 space-y-1">
                      <p className="text-sm font-medium text-ink">
                        {model.modelName} <span className="text-ink-muted">v{model.modelVersion}</span>
                      </p>
                      <p className="text-xs text-ink-secondary">{model.purpose ?? "No purpose documented"}</p>
                      <p className="text-xs text-ink-muted">
                        {model.modelType.toLowerCase()} · owner {model.ownerName}
                        {model.nextReviewAt ? ` · review ${relativeTime(model.nextReviewAt)}` : ""}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge tone={model.riskTier === "HIGH" ? "warning" : "neutral"}>
                        {model.riskTier.toLowerCase()} risk
                      </Badge>
                      <Badge tone={model.status === "PRODUCTION" ? "good" : "info"}>
                        {model.status.toLowerCase()}
                      </Badge>
                      <Button
                        size="sm"
                        variant="secondary"
                        loading={busy === model.id}
                        onClick={() => showDocumentation(model.id)}
                      >
                        <FileText />
                        Documentation
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={ShieldCheck}
                  title="No models registered"
                  description="Register the scoring engine and any custom models through POST /api/v1/governance/models."
                />
              </Card>
            )}

            {documentation && (
              <Card>
                <CardHeader className="pb-2">
                  <div className="flex items-center justify-between gap-2">
                    <CardTitle className="text-sm">Generated documentation</CardTitle>
                    <Button size="sm" variant="ghost" onClick={() => setDocumentation(null)}>
                      Close
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="pt-0">
                  <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded border border-line bg-surface-sunken p-3 text-xs leading-relaxed text-ink-secondary">
                    {documentation}
                  </pre>
                </CardContent>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="drift" className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <p className="max-w-2xl text-sm text-ink-secondary">
                Population stability compares the score distribution now against the one the model was
                calibrated on. Per-bucket contributions locate the shift rather than just reporting it.
              </p>
              <Button size="sm" onClick={runDrift} loading={busy === "drift"}>
                <Activity />
                Run drift check
              </Button>
            </div>
            {drift.isLoading ? (
              <Skeleton className="h-40 w-full" />
            ) : drift.data?.content?.length ? (
              drift.data.content.map((report) => (
                <Card key={report.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-sm">{report.modelName}</CardTitle>
                      <Badge
                        tone={
                          report.status === "DRIFTED"
                            ? "critical"
                            : report.status === "WATCH"
                              ? "warning"
                              : "good"
                        }
                      >
                        PSI {report.psi} · {report.status.toLowerCase()}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="pt-0">
                    <p className="text-sm leading-relaxed text-ink-secondary">{report.interpretation}</p>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Activity}
                  title="No drift measurements"
                  description="Run a check to compare the last fortnight of scores against the preceding baseline."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="fairness" className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <p className="max-w-2xl text-sm text-ink-secondary">
                A model that never sees a protected attribute can still produce very different outcomes
                by segment. The four-fifths screen is a trigger to investigate, not a verdict.
              </p>
              <Button size="sm" onClick={runFairness} loading={busy === "fairness"}>
                <Scale />
                Test disparate impact
              </Button>
            </div>
            {fairness.isLoading ? (
              <Skeleton className="h-40 w-full" />
            ) : fairness.data?.content?.length ? (
              fairness.data.content.map((report) => (
                <Card key={report.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-sm">
                        {report.modelName} by {report.attribute.toLowerCase()}
                      </CardTitle>
                      <Badge
                        tone={
                          report.verdict === "FAIL"
                            ? "critical"
                            : report.verdict === "REVIEW"
                              ? "warning"
                              : "good"
                        }
                      >
                        ratio {report.disparityRatio} · {report.verdict.toLowerCase()}
                      </Badge>
                    </div>
                    <CardDescription>
                      {report.groupsTested} groups · {report.sampleSize} decisions
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="pt-0">
                    <p className="text-sm leading-relaxed text-ink-secondary">{report.interpretation}</p>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Scale}
                  title="No fairness testing recorded"
                  description="Test approval rates by country or currency to see whether any segment is treated materially worse."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
