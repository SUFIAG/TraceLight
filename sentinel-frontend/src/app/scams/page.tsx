"use client";

import { useState } from "react";
import { Clock, PauseCircle, PlayCircle, ShieldAlert } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState, ErrorState } from "@/components/data/States";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
import { money, relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { usePaymentHolds, useScamSignals, useScamTypologies } from "@/lib/hooks/useApi";
import type { ScamAction } from "@/types";

const ACTION_TONE: Record<ScamAction, "neutral" | "info" | "warning" | "serious" | "critical"> = {
  ALLOW: "neutral",
  WARN: "info",
  WARN_AND_CONFIRM: "warning",
  COOLING_OFF: "serious",
  TRUSTED_CONTACT: "serious",
  HUMAN_REVIEW: "critical",
  BLOCK: "critical",
};

export default function ScamsPage() {
  const typologies = useScamTypologies(30);
  const signals = useScamSignals(0, 25);
  const holds = usePaymentHolds();
  const [busy, setBusy] = useState<string | null>(null);

  const resolveHold = async (id: string, release: boolean) => {
    setBusy(id);
    try {
      if (release) {
        await apiClient.releaseHold(id);
        toast.success("Payment released", "The customer's payment will proceed.");
      } else {
        await apiClient.cancelHold(id, false);
        toast.success("Payment cancelled", "The payment was stopped and will not leave the account.");
      }
      await holds.mutate();
    } catch (error) {
      toast.fromError(error, "Could not update the hold");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="Scam defence"
      subtitle="Authorized push payment scams, coaching detection, and cooling-off holds"
    >
      <div className="space-y-4">
        <p className="max-w-3xl text-sm leading-relaxed text-ink-secondary">
          In an authorized push payment scam the customer is genuine, the device is genuine, and the
          payment is genuinely authorized. What is wrong is the behaviour around it: a live call, a
          pasted payee, long hesitation. These are the payments where delay prevents more loss than
          refusal does.
        </p>

        <Tabs defaultValue="typologies">
          <TabsList>
            <TabsTrigger value="typologies">Typologies</TabsTrigger>
            <TabsTrigger value="signals">Recent assessments</TabsTrigger>
            <TabsTrigger value="holds">
              Cooling-off holds
              {holds.data?.length ? ` (${holds.data.length})` : ""}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="typologies" className="space-y-3">
            {typologies.isLoading ? (
              <div className="grid gap-3 md:grid-cols-2">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-28 w-full" />
                ))}
              </div>
            ) : typologies.error ? (
              <Card>
                <ErrorState error={typologies.error} onRetry={() => typologies.mutate()} />
              </Card>
            ) : typologies.data?.length ? (
              <div className="grid gap-3 md:grid-cols-2">
                {typologies.data.map((entry) => (
                  <Card key={entry.typology}>
                    <CardHeader className="pb-2">
                      <CardTitle>{entry.label}</CardTitle>
                      <CardDescription>{entry.description}</CardDescription>
                    </CardHeader>
                    <CardContent className="flex items-end justify-between gap-4 pt-0">
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Cases</p>
                        <p className="text-lg font-semibold tabular text-ink">{entry.count}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Exposure</p>
                        <p className="text-sm font-medium tabular text-ink">{money(entry.exposure)}</p>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <Card>
                <EmptyState
                  icon={ShieldAlert}
                  title="No scam assessments in the last 30 days"
                  description="Call POST /api/v1/scam/assess from the payment flow to start scoring coaching signals."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="signals">
            {signals.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : signals.error ? (
              <Card>
                <ErrorState error={signals.error} onRetry={() => signals.mutate()} />
              </Card>
            ) : signals.data?.content?.length ? (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Customer</TableHead>
                      <TableHead>Typology</TableHead>
                      <TableHead>Score</TableHead>
                      <TableHead>Coaching</TableHead>
                      <TableHead>Action</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {signals.data.content.map((signal) => (
                      <TableRow key={signal.id}>
                        <TableCell className="font-medium">{signal.userId}</TableCell>
                        <TableCell className="text-ink-secondary">{signal.typology}</TableCell>
                        <TableCell className="w-28">
                          <ScoreMeter value={signal.score} />
                        </TableCell>
                        <TableCell className="w-28">
                          <ScoreMeter value={signal.coachingScore} />
                        </TableCell>
                        <TableCell>
                          <Badge tone={ACTION_TONE[signal.action]}>{signal.action}</Badge>
                        </TableCell>
                        <TableCell className="text-xs text-ink-muted">
                          {relativeTime(signal.createdAt)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            ) : (
              <Card>
                <EmptyState
                  icon={ShieldAlert}
                  title="No assessments yet"
                  description="Scam assessments appear here as soon as the payment flow starts sending session signals."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="holds">
            {holds.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : holds.data?.length ? (
              <div className="space-y-2">
                {holds.data.map((hold) => (
                  <Card key={hold.id}>
                    <CardContent className="flex flex-wrap items-center justify-between gap-4 py-4">
                      <div className="min-w-0 space-y-1">
                        <p className="text-sm font-medium text-ink">
                          {hold.userId} → {hold.beneficiaryId ?? "unknown payee"}
                        </p>
                        <p className="text-xs text-ink-secondary">{hold.reason}</p>
                        <p className="flex items-center gap-1.5 text-xs text-ink-muted">
                          <Clock className="size-3" />
                          Releases {relativeTime(hold.releaseAt)}
                          {hold.trustedContactNotified && " · trusted contact notified"}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold tabular text-ink">
                          {money(hold.amount ?? 0)}
                        </span>
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={busy === hold.id}
                          onClick={() => resolveHold(hold.id, true)}
                        >
                          <PlayCircle />
                          Release
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy === hold.id}
                          onClick={() => resolveHold(hold.id, false)}
                        >
                          <PauseCircle />
                          Stop payment
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <Card>
                <EmptyState
                  icon={Clock}
                  title="Nothing on hold"
                  description="Payments appear here when a scam assessment scores high enough to warrant a cooling-off period."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
