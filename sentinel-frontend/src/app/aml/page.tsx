"use client";

import { useState } from "react";
import { FileCheck2, PlayCircle, ScrollText, Send } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState, ErrorState } from "@/components/data/States";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiClient } from "@/lib/api-client";
import { money, relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useAmlFindings, useAmlStats, useSars } from "@/lib/hooks/useApi";
import type { SarStatus } from "@/types";

const SAR_TONE: Record<SarStatus, "neutral" | "info" | "warning" | "good" | "critical"> = {
  DRAFT: "neutral",
  PENDING_APPROVAL: "warning",
  APPROVED: "info",
  FILED: "good",
  REJECTED: "critical",
  WITHDRAWN: "neutral",
};

export default function AmlPage() {
  const stats = useAmlStats();
  const findings = useAmlFindings(0, 25, "OPEN");
  const sars = useSars(0, 20);
  const [busy, setBusy] = useState<string | null>(null);

  const sweep = async () => {
    setBusy("sweep");
    try {
      const result = await apiClient.sweepAml(90, 500);
      toast.success("Monitoring sweep complete", `${result.findings ?? 0} findings raised.`);
      await Promise.all([findings.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Sweep failed");
    } finally {
      setBusy(null);
    }
  };

  const draftSar = async (subjectId: string) => {
    setBusy(subjectId);
    try {
      const filing = await apiClient.draftSar({ subjectId, jurisdiction: "FINCEN" });
      toast.success(
        "Report drafted",
        `${filing.referenceNumber} is ready for a compliance officer to review.`
      );
      await Promise.all([sars.mutate(), findings.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not draft the report");
    } finally {
      setBusy(null);
    }
  };

  const advance = async (id: string, action: "approve" | "file") => {
    setBusy(id);
    try {
      if (action === "approve") {
        await apiClient.approveSar(id);
        toast.success("Report approved", "It can now be filed with the regulator.");
      } else {
        await apiClient.fileSar(id);
        toast.success("Report filed", "The filing is recorded with its approver and timestamp.");
      }
      await Promise.all([sars.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not update the report");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="AML & reporting"
      subtitle="Transaction monitoring typologies and suspicious activity reports"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Open findings"
            numeric={Number(stats.data?.openFindings ?? 0)}
            tone="warning"
            loading={stats.isLoading}
          />
          <StatTile
            label="Escalated"
            numeric={Number(stats.data?.escalatedFindings ?? 0)}
            tone="serious"
            loading={stats.isLoading}
          />
          <StatTile
            label="Awaiting approval"
            numeric={Number(stats.data?.sarPending_approval ?? 0)}
            tone="warning"
            loading={stats.isLoading}
            footnote="Drafted, needs a compliance officer"
          />
          <StatTile
            label="Filed"
            numeric={Number(stats.data?.sarFiled ?? 0)}
            tone="good"
            loading={stats.isLoading}
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="max-w-2xl text-sm leading-relaxed text-ink-secondary">
            Narratives are drafted from the recorded evidence, which turns a one-to-three hour writing
            job into a review. Filing always requires a named human approver — no jurisdiction permits
            a machine to file on its own authority.
          </p>
          <Button size="sm" onClick={sweep} loading={busy === "sweep"}>
            <PlayCircle />
            Run monitoring sweep
          </Button>
        </div>

        <Tabs defaultValue="findings">
          <TabsList>
            <TabsTrigger value="findings">Findings</TabsTrigger>
            <TabsTrigger value="sars">Reports</TabsTrigger>
          </TabsList>

          <TabsContent value="findings" className="space-y-2">
            {findings.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : findings.error ? (
              <Card>
                <ErrorState error={findings.error} onRetry={() => findings.mutate()} />
              </Card>
            ) : findings.data?.content?.length ? (
              findings.data.content.map((finding) => (
                <Card key={finding.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle>
                        {finding.subjectId} · {finding.typology.replaceAll("_", " ").toLowerCase()}
                      </CardTitle>
                      <div className="flex items-center gap-3">
                        <div className="w-28">
                          <ScoreMeter value={finding.score} />
                        </div>
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={busy === finding.subjectId}
                          onClick={() => draftSar(finding.subjectId)}
                        >
                          <FileCheck2 />
                          Draft report
                        </Button>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-2 pt-0">
                    <p className="text-sm text-ink-secondary">{finding.narrative}</p>
                    <ul className="space-y-0.5 text-xs text-ink-muted">
                      {finding.evidence.slice(0, 4).map((item, index) => (
                        <li key={index}>• {item}</li>
                      ))}
                    </ul>
                    {finding.totalValue != null && (
                      <p className="text-xs text-ink-secondary">
                        Aggregate value {money(finding.totalValue)}
                      </p>
                    )}
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={ScrollText}
                  title="No open findings"
                  description="Run a monitoring sweep to test every active customer against the typology library."
                  action={
                    <Button size="sm" onClick={sweep} loading={busy === "sweep"}>
                      Run monitoring sweep
                    </Button>
                  }
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="sars" className="space-y-2">
            {sars.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : sars.data?.content?.length ? (
              sars.data.content.map((filing) => (
                <Card key={filing.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle>
                        {filing.referenceNumber} · {filing.subjectName ?? filing.subjectId}
                      </CardTitle>
                      <div className="flex items-center gap-2">
                        <Badge tone={SAR_TONE[filing.status]}>
                          {filing.status.replaceAll("_", " ").toLowerCase()}
                        </Badge>
                        {filing.aiDrafted && <Badge tone="info">AI drafted</Badge>}
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-3 pt-0">
                    <p className="text-xs text-ink-muted">
                      {filing.jurisdiction} · {filing.typologies.join(", ").toLowerCase()}
                      {filing.deadlineAt ? ` · due ${relativeTime(filing.deadlineAt)}` : ""}
                    </p>
                    <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded border border-line bg-surface-sunken p-3 text-xs leading-relaxed text-ink-secondary">
                      {filing.narrative}
                    </pre>
                    <div className="flex gap-2">
                      {filing.status === "PENDING_APPROVAL" && (
                        <Button
                          size="sm"
                          loading={busy === filing.id}
                          onClick={() => advance(filing.id, "approve")}
                        >
                          <FileCheck2 />
                          Approve
                        </Button>
                      )}
                      {filing.status === "APPROVED" && (
                        <Button
                          size="sm"
                          loading={busy === filing.id}
                          onClick={() => advance(filing.id, "file")}
                        >
                          <Send />
                          File with regulator
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={ScrollText}
                  title="No reports drafted"
                  description="Draft a report from any finding. The narrative is assembled from the evidence already recorded."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
