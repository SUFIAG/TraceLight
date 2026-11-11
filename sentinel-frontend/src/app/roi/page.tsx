"use client";

import { CloudSun, TrendingUp } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { compactNumber, money, shortDate } from "@/lib/format";
import { useForecast, useRoi } from "@/lib/hooks/useApi";

export default function RoiPage() {
  const roi = useRoi(30);
  const forecast = useForecast(60, 14);

  const automation = roi.data?.automation ?? {};
  const detection = roi.data?.detection ?? {};
  const experience = roi.data?.customerExperience ?? {};
  const recovery = roi.data?.recovery ?? {};

  const peak = Math.max(
    1,
    ...(forecast.data?.forecast ?? []).map((point) => point.upperBound)
  );

  return (
    <AppShell
      title="Value & forecast"
      subtitle="What the platform returned, and what is coming next"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Analyst hours returned"
            numeric={Number(automation.analystHoursReturned ?? 0)}
            tone="good"
            loading={roi.isLoading}
            footnote="Last 30 days"
          />
          <StatTile
            label="Cost avoided"
            value={money(Number(automation.analystCostAvoided ?? 0))}
            tone="good"
            loading={roi.isLoading}
            footnote="At the stated hourly rate"
          />
          <StatTile
            label="Work handled by agents"
            value={`${automation.automationRate ?? 0}%`}
            tone="default"
            loading={roi.isLoading}
          />
          <StatTile
            label="Customers passed untouched"
            numeric={Number(experience.customersPassedWithoutFriction ?? 0)}
            tone="good"
            loading={roi.isLoading}
            footnote={`${experience.frictionlessRate ?? 0}% of decisions`}
          />
        </div>

        <div className="grid gap-3 lg:grid-cols-2">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Detection</CardTitle>
              <CardDescription>Measured from recorded activity over the last 30 days.</CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-3 pt-0">
              <Figure label="Transactions scored" value={compactNumber(Number(detection.transactionsScored ?? 0))} />
              <Figure label="Flagged" value={compactNumber(Number(detection.transactionsFlagged ?? 0))} />
              <Figure label="Alerts raised" value={compactNumber(Number(detection.alertsRaised ?? 0))} />
              <Figure label="Scam interventions" value={compactNumber(Number(detection.scamInterventions ?? 0))} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Recovery and experience</CardTitle>
              <CardDescription>
                The two figures a renewal conversation turns on: money back, and customers not
                needlessly interrupted.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-3 pt-0">
              <Figure label="Disputes recovered" value={money(Number(recovery.disputeValueRecovered ?? 0))} />
              <Figure
                label="False positives closed"
                value={compactNumber(Number(experience.falsePositivesClosed ?? 0))}
              />
              <Figure
                label="Friction decisions"
                value={compactNumber(Number(experience.frictionDecisions ?? 0))}
              />
              <Figure label="Agent runs" value={compactNumber(Number(automation.agentRuns ?? 0))} />
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2">
              <CloudSun className="size-4 text-primary" />
              Fraud weather forecast
            </CardTitle>
            <CardDescription>
              Projected flagged volume for the next fortnight, with the calendar pressure behind each
              day.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 pt-0">
            {forecast.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : forecast.data?.forecast?.length ? (
              <>
                <p className="text-sm leading-relaxed text-ink-secondary">{forecast.data.summary}</p>
                <div
                  className="flex items-end gap-1.5"
                  role="img"
                  aria-label="Forecast of flagged transactions per day"
                >
                  {forecast.data.forecast.map((point) => (
                    <div key={point.date} className="flex flex-1 flex-col items-center gap-1">
                      <div
                        className="w-full rounded-t bg-seq-300"
                        style={{ height: Math.max(3, (point.upperBound / peak) * 120) }}
                        title={`${point.date}: ${point.lowerBound}–${point.upperBound} expected`}
                      />
                      <div
                        className="-mt-[1px] w-full rounded-t bg-primary"
                        style={{ height: Math.max(3, (point.expectedFlagged / peak) * 120) }}
                        title={`${point.date}: ${point.expectedFlagged} expected${
                          point.drivers.length ? ` — ${point.drivers.join("; ")}` : ""
                        }`}
                      />
                      <span className="text-[9px] text-ink-muted">{shortDate(point.date)}</span>
                    </div>
                  ))}
                </div>
                <p className="text-xs text-ink-muted">
                  Confidence: {forecast.data.confidence.toLowerCase()}. The pale bar is the upper
                  bound; uncertainty widens with the horizon.
                </p>
              </>
            ) : (
              <EmptyState
                icon={TrendingUp}
                title="Not enough history to project"
                description={forecast.data?.summary ?? "At least seven days of scored activity are needed."}
              />
            )}
          </CardContent>
        </Card>

        {roi.data?.assumptions && (
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Assumptions</CardTitle>
            </CardHeader>
            <CardContent className="pt-0">
              <ul className="space-y-0.5 text-xs text-ink-secondary">
                {Object.entries(roi.data.assumptions).map(([key, value]) => (
                  <li key={key}>
                    <span className="text-ink-muted">{key}:</span> {String(value)}
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}
      </div>
    </AppShell>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</p>
      <p className="text-lg font-semibold tabular text-ink">{value}</p>
    </div>
  );
}
