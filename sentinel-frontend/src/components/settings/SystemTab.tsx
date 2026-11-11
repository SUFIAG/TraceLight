"use client";

import { Activity, ShieldAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { StatTile } from "@/components/dashboard/StatTile";
import { EmptyState } from "@/components/data/States";
import { API_BASE_URL } from "@/lib/api-client";
import { dateTime, humanise } from "@/lib/format";
import { useOrganizationSecurityEvents, useSecurityStats } from "@/lib/hooks/useApi";
import type { SecuritySeverity } from "@/types";

const SEVERITY_TONE: Record<SecuritySeverity, "neutral" | "info" | "warning" | "serious" | "critical"> = {
  INFO: "neutral",
  LOW: "info",
  MEDIUM: "warning",
  HIGH: "serious",
  CRITICAL: "critical",
};

/**
 * The organization's security picture, for administrators.
 *
 * <p>The counters are the ones worth watching daily: failed sign-ins and
 * lockouts show a password-guessing run as it happens, and a replayed refresh
 * token means a session token left the device it was issued to.
 */
export function SystemTab() {
  const stats = useSecurityStats();
  const events = useOrganizationSecurityEvents(0, 50);

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Failed sign-ins"
          numeric={Number(stats.data?.failedSignIns ?? 0)}
          tone="warning"
          loading={stats.isLoading}
          footnote="Last 7 days"
        />
        <StatTile
          label="Account lockouts"
          numeric={Number(stats.data?.lockouts ?? 0)}
          tone="serious"
          loading={stats.isLoading}
          footnote="Three failures locks an account"
        />
        <StatTile
          label="Rejected second factors"
          numeric={Number(stats.data?.mfaFailures ?? 0)}
          loading={stats.isLoading}
        />
        <StatTile
          label="Replayed session tokens"
          numeric={Number(stats.data?.tokenReplays ?? 0)}
          tone="critical"
          loading={stats.isLoading}
          footnote="Each one ended every session for that user"
        />
      </div>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex items-center gap-2">
            <Activity className="size-4 text-primary" />
            <div>
              <CardTitle>Security log</CardTitle>
              <CardDescription>
                Every sign-in, lockout, and permission change in this organisation
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-1">
          {events.isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : events.data?.content?.length ? (
            events.data.content.map((event) => (
              <div
                key={event.id}
                className="flex flex-wrap items-center justify-between gap-2 border-b border-line py-2 last:border-0"
              >
                <div className="flex min-w-0 items-center gap-2">
                  <Badge tone={SEVERITY_TONE[event.severity]}>{humanise(event.severity)}</Badge>
                  <span className="truncate text-sm text-ink">{humanise(event.eventType)}</span>
                  {event.email ? (
                    <span className="truncate text-xs text-ink-muted">{event.email}</span>
                  ) : null}
                </div>
                <span className="text-xs text-ink-muted">
                  {event.ipAddress ? `${event.ipAddress} · ` : ""}
                  {dateTime(event.createdAt)}
                </span>
              </div>
            ))
          ) : (
            <EmptyState
              icon={ShieldAlert}
              title="Nothing recorded yet"
              description="Security events across the organisation will appear here."
            />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Deployment</CardTitle>
          <CardDescription>Where this console is pointed</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div>
            <p className="text-[11px] uppercase tracking-wide text-ink-muted">API base URL</p>
            <code className="mt-1 block overflow-x-auto rounded bg-surface-sunken px-2.5 py-2 text-xs text-ink-secondary">
              {API_BASE_URL}
            </code>
          </div>
          <p className="text-xs leading-relaxed text-ink-secondary">
            Set with <span className="font-medium text-ink">NEXT_PUBLIC_API_URL</span>. The backend
            reports its own security posture at startup and refuses to run under the
            <span className="font-medium text-ink"> prod</span> profile with development defaults
            still in place.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
