"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, FolderPlus, Lightbulb, Receipt } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { ErrorState } from "@/components/data/States";
import { RiskBreakdown, RiskScoreDial } from "@/components/charts/RiskBreakdown";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/input";
import { Skeleton, SkeletonText } from "@/components/ui/skeleton";
import {
  AlertStatusBadge,
  Badge,
  RiskLevelBadge,
  SeverityBadge,
  TransactionStatusBadge,
} from "@/components/ui/badge";
import { apiClient, ApiError } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { useAlert, useAlertExplanation, useRiskScore, useTransaction } from "@/lib/hooks/useApi";
import { dateTime, humanise, money, shortId } from "@/lib/format";
import type { AlertStatus } from "@/types";

const STATUSES: AlertStatus[] = ["OPEN", "INVESTIGATING", "RESOLVED", "FALSE_POSITIVE"];

export default function AlertDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();

  const alert = useAlert(id);
  const transaction = useTransaction(alert.data?.transactionId ?? null);
  const riskScore = useRiskScore(alert.data?.transactionId ?? null);
  const explanation = useAlertExplanation(id);

  const [updating, setUpdating] = useState(false);
  const [creatingCase, setCreatingCase] = useState(false);

  const changeStatus = async (status: AlertStatus) => {
    setUpdating(true);
    try {
      const updated = await apiClient.updateAlertStatus(id, status);
      await alert.mutate(updated, { revalidate: false });
      toast.success("Alert updated", `Status is now ${humanise(status).toLowerCase()}.`);
    } catch (error) {
      toast.fromError(error, "Could not update the alert");
    } finally {
      setUpdating(false);
    }
  };

  const escalateToCase = async () => {
    setCreatingCase(true);
    try {
      const created = await apiClient.createCase({
        alertId: id,
        title: `${humanise(alert.data?.severity ?? "")} risk review — alert ${shortId(id)}`,
      });
      toast.success("Case opened", "Investigate it in the case workspace.");
      router.push(`/cases/${created.id}`);
    } catch (error) {
      if (error instanceof ApiError && error.code === "CASE_EXISTS") {
        toast.info("Case already exists", "Open it from the Cases list.");
      } else {
        toast.fromError(error, "Could not open a case");
      }
    } finally {
      setCreatingCase(false);
    }
  };

  if (alert.error) {
    return (
      <AppShell title="Alert">
        <Card>
          <ErrorState error={alert.error} onRetry={() => alert.mutate()} />
        </Card>
      </AppShell>
    );
  }

  return (
    <AppShell
      title={alert.data ? `Alert ${shortId(alert.data.id)}` : "Alert"}
      subtitle={alert.data ? dateTime(alert.data.createdAt) : undefined}
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/alerts">
              <ArrowLeft />
              Back to queue
            </Link>
          </Button>

          <div className="flex flex-wrap items-center gap-2">
            <Select
              aria-label="Alert status"
              className="w-44"
              value={alert.data?.status ?? "OPEN"}
              disabled={updating || !alert.data}
              onChange={(event) => changeStatus(event.target.value as AlertStatus)}
            >
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {humanise(status)}
                </option>
              ))}
            </Select>
            <Button onClick={escalateToCase} loading={creatingCase} size="sm">
              {!creatingCase && <FolderPlus />}
              Open a case
            </Button>
          </div>
        </div>

        <div className="grid gap-4 xl:grid-cols-[1.4fr_1fr]">
          <div className="space-y-4">
            <Card>
              <CardHeader className="border-b border-line pb-4">
                <div className="flex flex-wrap items-center gap-2">
                  {alert.data ? (
                    <>
                      <SeverityBadge severity={alert.data.severity} />
                      <AlertStatusBadge status={alert.data.status} />
                    </>
                  ) : (
                    <Skeleton className="h-5 w-40" />
                  )}
                </div>
                <CardTitle className="pt-2 text-base">Why this fired</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {alert.isLoading ? (
                  <SkeletonText lines={3} />
                ) : (
                  <>
                    <p className="text-sm leading-relaxed text-ink-secondary">
                      {alert.data?.reason}
                    </p>
                    {alert.data?.triggeredRules?.length ? (
                      <div className="space-y-2">
                        <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">
                          Triggered rules
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {alert.data.triggeredRules.map((rule) => (
                            <Badge key={rule} tone="serious">
                              {humanise(rule)}
                            </Badge>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex-row items-center justify-between space-y-0 border-b border-line pb-4">
                <div className="space-y-1">
                  <CardTitle>Transaction</CardTitle>
                  <CardDescription>The activity that raised this alert</CardDescription>
                </div>
                {transaction.data ? (
                  <Button variant="ghost" size="sm" asChild>
                    <Link href={`/transactions?focus=${transaction.data.id}`}>
                      <Receipt />
                      View in ledger
                    </Link>
                  </Button>
                ) : null}
              </CardHeader>
              <CardContent>
                {transaction.isLoading ? (
                  <SkeletonText lines={4} />
                ) : transaction.error ? (
                  <ErrorState error={transaction.error} onRetry={() => transaction.mutate()} />
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
                ) : null}
              </CardContent>
            </Card>

            {explanation.data ? (
              <Card>
                <CardHeader className="border-b border-line pb-4">
                  <div className="flex items-center gap-2">
                    <Lightbulb className="size-4 text-primary" />
                    <CardTitle>Plain-language explanation</CardTitle>
                  </div>
                  <CardDescription>
                    Generated by {explanation.data.provider} when the alert was raised
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">Cause</p>
                    <p className="mt-1 text-sm text-ink-secondary">{explanation.data.cause}</p>
                  </div>
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">
                      What it means
                    </p>
                    <p className="mt-1 text-sm leading-relaxed text-ink-secondary">
                      {explanation.data.explanation}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">
                      Suggested next step
                    </p>
                    <p className="mt-1 text-sm leading-relaxed text-ink-secondary">
                      {explanation.data.suggestion}
                    </p>
                  </div>
                </CardContent>
              </Card>
            ) : null}
          </div>

          <Card className="h-fit">
            <CardHeader className="border-b border-line pb-4">
              <CardTitle>Risk breakdown</CardTitle>
              <CardDescription>How the composite score was reached</CardDescription>
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-0.5 truncate text-sm text-ink-secondary">{children}</dd>
    </div>
  );
}
