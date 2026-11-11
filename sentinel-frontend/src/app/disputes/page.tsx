"use client";

import { useState } from "react";
import { FileText, Gavel, Send } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { apiClient } from "@/lib/api-client";
import { money, relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useDisputeStats, useDisputes } from "@/lib/hooks/useApi";
import type { DisputeStatus } from "@/types";

const STATUS_TONE: Record<DisputeStatus, "neutral" | "info" | "warning" | "good" | "critical"> = {
  RECEIVED: "neutral",
  EVIDENCE_GATHERING: "info",
  REPRESENTMENT_DRAFTED: "warning",
  SUBMITTED: "info",
  WON: "good",
  LOST: "critical",
  ACCEPTED: "neutral",
  EXPIRED: "critical",
};

export default function DisputesPage() {
  const stats = useDisputeStats(90);
  const disputes = useDisputes(0, 20);
  const [busy, setBusy] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);

  const act = async (id: string, action: "draft" | "submit") => {
    setBusy(id);
    try {
      if (action === "draft") {
        const result = await apiClient.draftRepresentment(id);
        setPreview(result.representmentText ?? null);
        toast.success("Representment drafted", "Evidence assembled from device, history, and risk.");
      } else {
        await apiClient.submitDispute(id);
        toast.success("Dispute submitted", "The case is now with the network.");
      }
      await Promise.all([disputes.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not update the dispute");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="Disputes"
      subtitle="Chargeback defence, first-party fraud scoring, and automated representment"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Open"
            numeric={Number(stats.data?.evidence_gathering ?? 0)}
            tone="warning"
            loading={stats.isLoading}
          />
          <StatTile
            label="Submitted"
            numeric={Number(stats.data?.submitted ?? 0)}
            tone="default"
            loading={stats.isLoading}
          />
          <StatTile
            label="Win rate"
            value={stats.data?.winRate == null ? "—" : `${stats.data.winRate}%`}
            tone="good"
            loading={stats.isLoading}
          />
          <StatTile
            label="Recovered"
            value={money(Number(stats.data?.totalRecovered ?? 0))}
            tone="good"
            loading={stats.isLoading}
          />
        </div>

        <p className="max-w-3xl text-sm leading-relaxed text-ink-secondary">
          Disputes are usually worked on a different system from the one that scored the original
          payment, which is why a customer can file their sixth &quot;I did not authorise this&quot; of
          the year unnoticed. Holding them beside the transaction history is what makes first-party
          fraud scoring possible at all.
        </p>

        {disputes.isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : disputes.data?.content?.length ? (
          <div className="space-y-2">
            {disputes.data.content.map((dispute) => (
              <Card key={dispute.id}>
                <CardHeader className="pb-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <CardTitle className="text-sm">
                      {dispute.network} {dispute.reasonCode} · {money(dispute.amount)}{" "}
                      {dispute.currency}
                    </CardTitle>
                    <div className="flex items-center gap-2">
                      <Badge tone={STATUS_TONE[dispute.status]}>
                        {dispute.status.replaceAll("_", " ").toLowerCase()}
                      </Badge>
                      {dispute.aiDrafted && <Badge tone="info">AI drafted</Badge>}
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3 pt-0">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <ScoreMeter value={dispute.firstPartyFraudScore} label="First-party fraud" />
                    <ScoreMeter value={dispute.winProbability} label="Win probability" invert />
                  </div>
                  <ul className="space-y-0.5 text-xs text-ink-secondary">
                    {dispute.evidence.slice(0, 4).map((item, index) => (
                      <li key={index}>• {item}</li>
                    ))}
                  </ul>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-xs text-ink-muted">
                      {dispute.userId}
                      {dispute.respondBy ? ` · respond by ${relativeTime(dispute.respondBy)}` : ""}
                    </p>
                    <div className="flex gap-2">
                      {dispute.status !== "SUBMITTED" && dispute.status !== "WON" && (
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={busy === dispute.id}
                          onClick={() => act(dispute.id, "draft")}
                        >
                          <FileText />
                          Draft representment
                        </Button>
                      )}
                      {dispute.status === "REPRESENTMENT_DRAFTED" && (
                        <Button size="sm" loading={busy === dispute.id} onClick={() => act(dispute.id, "submit")}>
                          <Send />
                          Submit
                        </Button>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <Card>
            <EmptyState
              icon={Gavel}
              title="No disputes"
              description="Post a chargeback to /api/v1/disputes and the platform assembles the evidence automatically."
            />
          </Card>
        )}

        {preview && (
          <Card>
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="text-sm">Representment</CardTitle>
                <Button size="sm" variant="ghost" onClick={() => setPreview(null)}>
                  Close
                </Button>
              </div>
            </CardHeader>
            <CardContent className="pt-0">
              <pre className="max-h-80 overflow-auto whitespace-pre-wrap rounded border border-line bg-surface-sunken p-3 text-xs leading-relaxed text-ink-secondary">
                {preview}
              </pre>
            </CardContent>
          </Card>
        )}
      </div>
    </AppShell>
  );
}
