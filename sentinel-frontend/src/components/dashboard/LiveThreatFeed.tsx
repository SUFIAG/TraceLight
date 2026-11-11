"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Activity, Pause, Play, ShieldAlert } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/data/States";
import { SeverityBadge } from "@/components/ui/badge";
import { useAlerts } from "@/lib/hooks/useApi";
import { relativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { Alert, AlertSeverity } from "@/types";

const SEVERITY_EDGE: Record<AlertSeverity, string> = {
  CRITICAL: "text-critical",
  HIGH: "text-serious",
  MEDIUM: "text-warning",
};

/**
 * A rolling view of the alerts the engine is raising.
 *
 * Alerts that were not in the previous poll are marked for one animation cycle,
 * so an analyst watching the board sees what is new without re-reading the list.
 * The feed can be paused: an auto-scrolling list that moves while someone is
 * reading it is worse than no feed at all.
 */
export function LiveThreatFeed({ limit = 8 }: { limit?: number }) {
  const [paused, setPaused] = useState(false);
  const { data, error, isLoading, mutate } = useAlerts(0, limit, undefined);

  const alerts = useMemo(() => data?.content ?? [], [data]);

  // Ids present in the previous render. Anything outside this set is new.
  const seenRef = useRef<Set<string>>(new Set());
  const [fresh, setFresh] = useState<Set<string>>(new Set());
  const [sinceLoad, setSinceLoad] = useState(0);

  useEffect(() => {
    if (paused || alerts.length === 0) return;

    const incoming = alerts.filter((alert) => !seenRef.current.has(alert.id));

    // The first poll populates the baseline; everything would otherwise be
    // "new" on mount and the whole list would animate at once.
    const isFirstFill = seenRef.current.size === 0;
    alerts.forEach((alert) => seenRef.current.add(alert.id));

    if (isFirstFill || incoming.length === 0) return;

    setSinceLoad((count) => count + incoming.length);
    setFresh(new Set(incoming.map((alert) => alert.id)));

    const timer = setTimeout(() => setFresh(new Set()), 1400);
    return () => clearTimeout(timer);
  }, [alerts, paused]);

  useEffect(() => {
    if (paused) return;
    const timer = setInterval(() => mutate(), 10_000);
    return () => clearInterval(timer);
  }, [paused, mutate]);

  return (
    <Card className="flex flex-col overflow-hidden">
      <CardHeader className="flex-row items-start justify-between space-y-0">
        <div className="space-y-1">
          <CardTitle className="flex items-center gap-2">
            Live detections
            <span className="relative flex size-2" aria-hidden="true">
              {!paused && (
                <span className="absolute inline-flex size-full animate-pulse-ring rounded-full bg-good" />
              )}
              <span className={cn("relative inline-flex size-2 rounded-full", paused ? "bg-ink-muted" : "bg-good")} />
            </span>
          </CardTitle>
          <CardDescription>
            {paused
              ? "Paused — the feed is not refreshing"
              : sinceLoad > 0
                ? `${sinceLoad} new since you opened this page`
                : "Streaming alerts as the engine raises them"}
          </CardDescription>
        </div>

        <button
          type="button"
          onClick={() => setPaused((current) => !current)}
          className="focus-ring inline-flex items-center gap-1.5 rounded-md border border-line px-2 py-1 text-xs text-ink-secondary transition-colors hover:bg-surface-sunken"
          aria-pressed={paused}
        >
          {paused ? <Play className="size-3" /> : <Pause className="size-3" />}
          {paused ? "Resume" : "Pause"}
        </button>
      </CardHeader>

      <CardContent className="flex-1 pt-2">
        {isLoading && alerts.length === 0 ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-14 w-full" />
            ))}
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={() => mutate()} />
        ) : alerts.length === 0 ? (
          <EmptyState
            icon={ShieldAlert}
            title="Nothing detected yet"
            description="Alerts appear here the moment a scored transaction crosses the flagging threshold."
          />
        ) : (
          <ul className="space-y-1.5">
            {alerts.map((alert) => (
              <FeedRow key={alert.id} alert={alert} isNew={fresh.has(alert.id)} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function FeedRow({ alert, isNew }: { alert: Alert; isNew: boolean }) {
  return (
    <li>
      <Link
        href={`/alerts/${alert.id}`}
        className={cn(
          "edge-accent relative block overflow-hidden rounded-md border border-line py-2 pl-3.5 pr-3",
          "transition-all duration-200 hover:border-line-strong hover:bg-surface-sunken",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
          SEVERITY_EDGE[alert.severity],
          isNew && "animate-rise-in border-line-strong"
        )}
      >
        {/* One pass of light marks the row as newly arrived, then never repeats. */}
        {isNew && (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 left-0 w-1/3 animate-sweep-once bg-gradient-to-r from-transparent via-primary-wash to-transparent"
          />
        )}

        <div className="relative flex items-start justify-between gap-2">
          <p className="line-clamp-2 text-xs leading-relaxed text-ink-secondary">{alert.reason}</p>
          <SeverityBadge severity={alert.severity} />
        </div>

        <div className="relative mt-1.5 flex items-center gap-2 text-[11px] text-ink-muted">
          <Activity className="size-3" aria-hidden="true" />
          <span>{relativeTime(alert.createdAt)}</span>
          {alert.triggeredRules?.length ? (
            <>
              <span aria-hidden="true">·</span>
              <span className="truncate">
                {alert.triggeredRules.length} rule{alert.triggeredRules.length === 1 ? "" : "s"}:{" "}
                {alert.triggeredRules.slice(0, 2).join(", ").replaceAll("_", " ").toLowerCase()}
                {alert.triggeredRules.length > 2 ? "…" : ""}
              </span>
            </>
          ) : null}
        </div>
      </Link>
    </li>
  );
}
