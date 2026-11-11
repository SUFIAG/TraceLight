"use client";

import { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { ChartFrame } from "@/components/charts/ChartFrame";
import { FraudTrendChart, TREND_SERIES } from "@/components/charts/FraudTrendChart";
import { MerchantRiskChart } from "@/components/charts/MerchantRiskChart";
import { EmptyState, ErrorState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { FilterBar, type FilterOption } from "@/components/data/FilterBar";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableShell,
} from "@/components/ui/table";
import { useDashboard, useFraudTrend, useGeographicRisk, useTopMerchants } from "@/lib/hooks/useApi";
import { compactNumber, exactNumber, percent, shortDate } from "@/lib/format";

type Range = "7" | "14" | "30" | "90";

const RANGES: FilterOption<Range>[] = [
  { value: "7", label: "7 days" },
  { value: "14", label: "14 days" },
  { value: "30", label: "30 days" },
  { value: "90", label: "90 days" },
];

export default function AnalyticsPage() {
  const [range, setRange] = useState<Range>("30");

  const dashboard = useDashboard();
  const trend = useFraudTrend(Number(range));
  const merchants = useTopMerchants(10);
  const geography = useGeographicRisk();

  const points = trend.data?.points ?? [];
  const totals = points.reduce(
    (accumulator, point) => ({
      total: accumulator.total + point.totalTransactions,
      flagged: accumulator.flagged + point.flaggedTransactions,
    }),
    { total: 0, flagged: 0 }
  );
  const periodFraudRate = totals.total > 0 ? (totals.flagged / totals.total) * 100 : 0;
  const worstDay = points.reduce<typeof points[number] | null>(
    (worst, point) => (!worst || point.fraudRate > worst.fraudRate ? point : worst),
    null
  );

  return (
    <AppShell title="Analytics" subtitle="Where fraud is concentrating, and how that is moving">
      <div className="space-y-5">
        <FilterBar
          label="Reporting period"
          options={RANGES}
          value={range}
          onChange={(next) => setRange(next as Range)}
        />

        <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatTile
            label="Transactions in period"
            value={compactNumber(totals.total)}
            footnote={`Last ${range} days`}
            loading={trend.isLoading}
          />
          <StatTile
            label="Flagged in period"
            value={compactNumber(totals.flagged)}
            footnote={`Last ${range} days`}
            tone="serious"
            loading={trend.isLoading}
          />
          <StatTile
            label="Fraud rate in period"
            value={percent(periodFraudRate)}
            footnote="Flagged ÷ total"
            tone="warning"
            loading={trend.isLoading}
          />
          <StatTile
            label="Worst day"
            value={worstDay ? percent(worstDay.fraudRate, 1) : "—"}
            footnote={worstDay ? shortDate(worstDay.date) : "No data in range"}
            tone="critical"
            loading={trend.isLoading}
          />
        </section>

        <ChartFrame
          title="Transaction volume and flagged subset"
          description={`Last ${range} days, bucketed by transaction time`}
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
              title="No activity in this period"
              description="Widen the range, or load the demo dataset to populate the charts."
            />
          )}
        </ChartFrame>

        <div className="grid gap-4 xl:grid-cols-2">
          <ChartFrame
            title="Highest-risk merchants"
            description="Ranked by flagged transaction count"
          >
            {merchants.isLoading ? (
              <Skeleton className="h-[280px] w-full" />
            ) : merchants.error ? (
              <ErrorState error={merchants.error} onRetry={() => merchants.mutate()} />
            ) : merchants.data?.length ? (
              <MerchantRiskChart merchants={merchants.data} />
            ) : (
              <EmptyState title="No flagged merchants" />
            )}
          </ChartFrame>

          <Card className="flex flex-col">
            <CardHeader>
              <CardTitle>Geographic exposure</CardTitle>
              <CardDescription>Every country with recorded activity</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 pt-2">
              {geography.isLoading ? (
                <Skeleton className="h-[280px] w-full" />
              ) : geography.error ? (
                <ErrorState error={geography.error} onRetry={() => geography.mutate()} />
              ) : geography.data?.length ? (
                <TableShell className="border-0 shadow-none">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Country</TableHead>
                        <TableHead className="text-right">Total</TableHead>
                        <TableHead className="text-right">Flagged</TableHead>
                        <TableHead className="text-right">Fraud rate</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {geography.data.map((row) => (
                        <TableRow key={row.country}>
                          <TableCell className="font-medium">{row.country}</TableCell>
                          <TableCell className="text-right tabular text-ink-secondary">
                            {exactNumber(row.totalTransactions)}
                          </TableCell>
                          <TableCell className="text-right tabular text-ink-secondary">
                            {exactNumber(row.flaggedTransactions)}
                          </TableCell>
                          <TableCell className="text-right tabular">
                            {percent(row.fraudRate, 1)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableShell>
              ) : (
                <EmptyState title="No geographic data" />
              )}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Portfolio snapshot</CardTitle>
            <CardDescription>Live counters for the last 24 hours</CardDescription>
          </CardHeader>
          <CardContent>
            {dashboard.isLoading ? (
              <Skeleton className="h-16 w-full" />
            ) : dashboard.data ? (
              <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3 lg:grid-cols-5">
                <Metric label="Total alerts" value={exactNumber(dashboard.data.totalAlerts)} />
                <Metric label="Open alerts" value={exactNumber(dashboard.data.openAlerts)} />
                <Metric
                  label="Average risk score"
                  value={dashboard.data.averageRiskScore.toFixed(1)}
                />
                <Metric label="Fraud rate" value={percent(dashboard.data.fraudRate)} />
                <Metric
                  label="Audit events"
                  value={exactNumber(dashboard.data.totalAuditEvents)}
                />
              </dl>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-1 text-lg font-semibold text-ink">{value}</dd>
    </div>
  );
}
