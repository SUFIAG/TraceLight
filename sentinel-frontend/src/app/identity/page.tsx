"use client";

import { Check, UserCheck, X } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState, ErrorState } from "@/components/data/States";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
import { useIdentityStats, useScreeningQueue, useVerifications } from "@/lib/hooks/useApi";
import type { VerificationDecision } from "@/types";

const DECISION_TONE: Record<VerificationDecision, "good" | "info" | "warning" | "serious" | "critical"> = {
  APPROVED: "good",
  APPROVED_WITH_LIMITS: "info",
  ADDITIONAL_EVIDENCE_REQUIRED: "warning",
  MANUAL_REVIEW: "serious",
  REJECTED: "critical",
};

export default function IdentityPage() {
  const stats = useIdentityStats();
  const verifications = useVerifications(0, 25);
  const screening = useScreeningQueue("OPEN", 0, 25);

  const decideHit = async (hitId: string, status: string) => {
    try {
      await apiClient.decideScreeningHit(hitId, status);
      toast.success("Screening hit updated", `Marked as ${status.toLowerCase()}.`);
      await Promise.all([screening.mutate(), stats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Could not update the screening hit");
    }
  };

  return (
    <AppShell
      title="Identity & KYC"
      subtitle="Document forensics, deepfake detection, synthetic identity, and list screening"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Approved"
            numeric={Number(stats.data?.approved ?? 0)}
            tone="good"
            loading={stats.isLoading}
          />
          <StatTile
            label="Awaiting review"
            numeric={Number(stats.data?.manual_review ?? 0)}
            tone="warning"
            loading={stats.isLoading}
          />
          <StatTile
            label="Open screening hits"
            numeric={Number(stats.data?.openScreeningHits ?? 0)}
            tone="serious"
            loading={stats.isLoading}
            footnote="Name matches needing a human"
          />
          <StatTile
            label="Auto-cleared matches"
            numeric={Number(stats.data?.autoDismissedHits ?? 0)}
            tone="good"
            loading={stats.isLoading}
            footnote="Ruled out on a hard discriminator"
          />
        </div>

        <p className="max-w-3xl text-sm leading-relaxed text-ink-secondary">
          A synthetic identity has no victim to complain, so it survives onboarding and surfaces two
          years later as a bust-out. Component scores are kept separately because a forged document
          and a cloned face fail in different ways — and because a rejected applicant is entitled to
          know which one it was.
        </p>

        <Tabs defaultValue="verifications">
          <TabsList>
            <TabsTrigger value="verifications">Verifications</TabsTrigger>
            <TabsTrigger value="screening">
              Screening queue
              {screening.data?.totalElements ? ` (${screening.data.totalElements})` : ""}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="verifications">
            {verifications.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : verifications.error ? (
              <Card>
                <ErrorState error={verifications.error} onRetry={() => verifications.mutate()} />
              </Card>
            ) : verifications.data?.content?.length ? (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Subject</TableHead>
                      <TableHead>Decision</TableHead>
                      <TableHead>Risk</TableHead>
                      <TableHead>Document</TableHead>
                      <TableHead>Deepfake</TableHead>
                      <TableHead>Synthetic</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {verifications.data.content.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell className="font-medium">
                          {item.subjectName ?? item.subjectId}
                        </TableCell>
                        <TableCell>
                          <Badge tone={DECISION_TONE[item.decision]}>
                            {item.decision.replaceAll("_", " ").toLowerCase()}
                          </Badge>
                        </TableCell>
                        <TableCell className="w-24">
                          <ScoreMeter value={item.riskScore} />
                        </TableCell>
                        <TableCell className="w-24">
                          <ScoreMeter value={item.documentScore} />
                        </TableCell>
                        <TableCell className="w-24">
                          <ScoreMeter value={item.deepfakeScore} />
                        </TableCell>
                        <TableCell className="w-24">
                          <ScoreMeter value={item.syntheticScore} />
                        </TableCell>
                        <TableCell className="text-xs text-ink-muted">
                          {relativeTime(item.createdAt)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            ) : (
              <Card>
                <EmptyState
                  icon={UserCheck}
                  title="No verifications yet"
                  description="Post applicant signals to /api/v1/identity/verify to score documents, liveness, and footprint."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="screening" className="space-y-2">
            {screening.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : screening.data?.content?.length ? (
              screening.data.content.map((hit) => (
                <Card key={hit.id}>
                  <CardContent className="flex flex-wrap items-center justify-between gap-4 py-4">
                    <div className="min-w-0 space-y-1">
                      <p className="text-sm font-medium text-ink">
                        {hit.subjectId} matched {hit.matchedName}
                      </p>
                      <p className="text-xs text-ink-secondary">
                        {hit.listType} · {hit.listName} · {hit.matchScore}% name similarity
                        {hit.matchedDob ? ` · list DOB ${hit.matchedDob}` : ""}
                        {hit.matchedCountry ? ` · ${hit.matchedCountry}` : ""}
                      </p>
                      {hit.dismissalRationale && (
                        <p className="text-xs text-ink-muted">{hit.dismissalRationale}</p>
                      )}
                    </div>
                    <div className="flex gap-2">
                      <Button size="sm" variant="secondary" onClick={() => decideHit(hit.id, "DISMISSED")}>
                        <X />
                        Not a match
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => decideHit(hit.id, "CONFIRMED")}>
                        <Check />
                        Confirm match
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={UserCheck}
                  title="Screening queue is clear"
                  description="Matches ruled out by a hard discriminator — a different date of birth or country — never reach this queue."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
