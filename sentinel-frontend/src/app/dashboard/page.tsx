"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
  Activity,
  AlertOctagon,
  Bell,
  CheckCircle2,
  Clock,
  Database,
  FileClock,
  Gauge,
  Globe2,
  Percent,
  ShieldCheck,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { StatTile } from "@/components/dashboard/StatTile";
import { RiskGauge } from "@/components/dashboard/RiskGauge";
import { LiveThreatFeed } from "@/components/dashboard/LiveThreatFeed";
import { ThreatMap } from "@/components/dashboard/ThreatMap";
import { ChartFrame } from "@/components/charts/ChartFrame";
import { FraudTrendChart, TREND_SERIES } from "@/components/charts/FraudTrendChart";
import { MerchantRiskChart } from "@/components/charts/MerchantRiskChart";
import { EmptyState, ErrorState } from "@/components/data/States";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { useDashboard, useFraudTrend, useGeographicRisk, useTopMerchants } from "@/lib/hooks/useApi";
import { compactNumber, exactNumber, percent } from "@/lib/format";
import type { FraudTrendPoint } from "@/types";

export default function DashboardPage() {
  const dashboard = useDashboard();
  const trend = useFraudTrend(14);
  const merchants = useTopMerchants(8);
  const geography = useGeographicRisk();

  const stats = dashboard.data;
  const loading = dashboard.isLoading;
  const points = trend.data?.points ?? [];

  // Sparklines and deltas are derived from the trend series rather than
  // requested separately: the backend already returns the daily buckets, and a
  // second round trip for the same numbers would only add latency.
  const series = useMemo(() => deriveSeries(points), [points]);

  return (
    <AppShell title="Overview" subtitle="Portfolio health and the queue waiting on your team">
      <div className="space-y-6">
        {dashboard.error ? (
          <Card>
            <ErrorState error={dashboard.error} onRetry={() => dashboard.mutate()} />
          </Card>
        ) : (
          <>
            {/* ---------- Hero: the single number that decides the shift ---------- */}
            <section className="relative overflow-hidden rounded-lg border border-line bg-surface">
              <div className="surface-grid pointer-events-none absolute inset-0" aria-hidden="true" />
              <div className="surface-glow pointer-events-none absolute inset-0" aria-hidden="true" />

              <div className="relative grid gap-6 p-5 lg:grid-cols-[auto_1fr] lg:items-center lg:p-6">
                <div className="flex items-center justify-center lg:justify-start">
                  <RiskGauge score={stats?.averageRiskScore} size={180} />
                </div>

                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  <StatTile
                    label="Transactions scored"
                    numeric={stats?.totalTransactions}
                    format={(value) => compactNumber(Math.round(value))}
                    footnote="All time, this organisation"
                    icon={Activity}
                    loading={loading}
                    trend={series.total}
                    delta={series.deltaTotal}
                    deltaIsGood="up"
                    href="/transactions"
                  />
                  <StatTile
                    label="Flagged"
                    numeric={stats?.flaggedTransactions}
                    format={(value) => compactNumber(Math.round(value))}
                    footnote="Held for review"
                    icon={AlertOctagon}
                    tone="serious"
                    loading={loading}
                    trend={series.flagged}
                    delta={series.deltaFlagged}
                    deltaIsGood="down"
                    href="/transactions"
                  />
                  <StatTile
                    label="Fraud rate"
                    numeric={stats?.fraudRate}
                    format={(value) => `${value.toFixed(2)}%`}
                    footnote="Flagged ÷ scored"
                    icon={Percent}
                    tone="warning"
                    loading={loading}
                    trend={series.rate}
                    delta={series.deltaRate}
                    deltaIsGood="down"
                  />
                  <StatTile
                    label="Open alerts"
                    numeric={stats?.openAlerts}
                    format={(value) => exactNumber(Math.round(value))}
                    footnote="Awaiting an analyst"
                    icon={Bell}
                    tone={stats && stats.openAlerts > 0 ? "critical" : "good"}
                    loading={loading}
                    href="/alerts"
                  />
                </div>
              </div>
            </section>

            {/* ---------- Secondary figures ---------- */}
            <section aria-label="Supporting figures" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatTile
                label="Auto-cleared"
                numeric={stats?.approvedTransactions}
                format={(value) => compactNumber(Math.round(value))}
                footnote="Passed without an analyst"
                icon={CheckCircle2}
                tone="good"
                loading={loading}
              />
              <StatTile
                label="Awaiting analysis"
                numeric={stats?.pendingTransactions}
                format={(value) => compactNumber(Math.round(value))}
                footnote="Ingested, not yet scored"
                icon={Clock}
                loading={loading}
              />
              <StatTile
                label="Total alerts raised"
                numeric={stats?.totalAlerts}
                format={(value) => compactNumber(Math.round(value))}
                footnote="Open and closed"
                icon={ShieldCheck}
                loading={loading}
                href="/alerts"
              />
              <StatTile
                label="Audit events"
                numeric={stats?.totalAuditEvents}
                format={(value) => compactNumber(Math.round(value))}
                footnote="Immutable action record"
                icon={FileClock}
                loading={loading}
              />
            </section>

            {stats && stats.totalTransactions === 0 && (
              <Card>
                <EmptyState
                  icon={Database}
                  title="No transactions yet"
                  description="Load the demo dataset to populate this organisation with realistic transactions, alerts and cases generated through the live fraud pipeline, or upload a CSV from the Transactions page."
                  action={
                    <p className="text-xs text-ink-muted">
                      Press <kbd className="rounded border border-line px-1">Ctrl</kbd>
                      <kbd className="ml-0.5 rounded border border-line px-1">K</kbd> and choose{" "}
                      <span className="font-medium text-ink">Load demo data</span>.
                    </p>
                  }
                />
              </Card>
            )}

            {/* ---------- Trend + live feed ---------- */}
            <section className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
              <ChartFrame
                title="Volume and flagged subset"
                description="Last 14 days, bucketed by transaction time"
                series={TREND_SERIES}
              >
                {trend.isLoading ? (
                  <Skeleton className="h-[260px] w-full" />
                ) : trend.error ? (
                  <ErrorState error={trend.error} onRetry={() => trend.mutate()} />
                ) : points.length ? (
                  <FraudTrendChart points={points} />
                ) : (
                  <EmptyState
                    title="No trend data yet"
                    description="Once transactions are scored, the daily trend appears here."
                  />
                )}
              </ChartFrame>

              <LiveThreatFeed limit={7} />
            </section>

            {/* ---------- Merchants + geography ---------- */}
            <section className="grid gap-4 xl:grid-cols-2">
              <ChartFrame title="Highest-risk merchants" description="Ranked by flagged transaction count">
                {merchants.isLoading ? (
                  <Skeleton className="h-[220px] w-full" />
                ) : merchants.error ? (
                  <ErrorState error={merchants.error} onRetry={() => merchants.mutate()} />
                ) : merchants.data?.length ? (
                  <MerchantRiskChart merchants={merchants.data} />
                ) : (
                  <EmptyState
                    title="No flagged merchants"
                    description="Merchants appear here once transactions are flagged."
                  />
                )}
              </ChartFrame>

              <Card className="flex flex-col">
                <CardHeader className="flex-row items-start justify-between space-y-0">
                  <div className="space-y-1">
                    <CardTitle className="flex items-center gap-2">
                      <Globe2 className="size-4 text-ink-muted" aria-hidden="true" />
                      Geographic exposure
                    </CardTitle>
                    <CardDescription>Marker size is volume, colour is fraud rate</CardDescription>
                  </div>
                  <Link
                    href="/analytics"
                    className="focus-ring rounded-md px-2 py-1 text-xs text-primary hover:underline"
                  >
                    Analytics
                  </Link>
                </CardHeader>
                <CardContent className="flex-1 pt-2">
                  {geography.isLoading ? (
                    <Skeleton className="h-[300px] w-full" />
                  ) : geography.error ? (
                    <ErrorState error={geography.error} onRetry={() => geography.mutate()} />
                  ) : geography.data?.length ? (
                    <ThreatMap rows={geography.data} />
                  ) : (
                    <EmptyState
                      title="No geographic data"
                      description="Countries appear once transactions carry a country code."
                    />
                  )}
                </CardContent>
              </Card>
            </section>
          </>
        )}
      </div>
    </AppShell>
  );
}

/**
 * Turns the daily trend into sparkline series and period-over-period deltas.
 *
 * The comparison splits the window in half rather than comparing the last two
 * days: a single day is noisy enough that the arrow would flip constantly and
 * stop meaning anything.
 */
function deriveSeries(points: FraudTrendPoint[]) {
  const total = points.map((point) => point.totalTransactions);
  const flagged = points.map((point) => point.flaggedTransactions);
  const rate = points.map((point) => point.fraudRate);

  const changeBetweenHalves = (values: number[]) => {
    if (values.length < 4) return undefined;
    const middle = Math.floor(values.length / 2);
    const sum = (slice: number[]) => slice.reduce((carry, value) => carry + value, 0);
    const earlier = sum(values.slice(0, middle));
    const later = sum(values.slice(middle));
    if (earlier === 0) return undefined;
    return ((later - earlier) / earlier) * 100;
  };

  return {
    total,
    flagged,
    rate,
    deltaTotal: changeBetweenHalves(total),
    deltaFlagged: changeBetweenHalves(flagged),
    deltaRate: changeBetweenHalves(rate),
  };
}
