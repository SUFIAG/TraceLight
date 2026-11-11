"use client";

import { useState } from "react";
import { LogOut, MonitorSmartphone, ShieldAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/data/States";
import { apiClient } from "@/lib/api-client";
import { dateTime, humanise, relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useSecurityEvents, useSessions } from "@/lib/hooks/useApi";
import type { SecuritySeverity } from "@/types";

const SEVERITY_TONE: Record<SecuritySeverity, "neutral" | "info" | "warning" | "serious" | "critical"> = {
  INFO: "neutral",
  LOW: "info",
  MEDIUM: "warning",
  HIGH: "serious",
  CRITICAL: "critical",
};

/**
 * Where this account is signed in, and what has happened to it.
 *
 * <p>Both halves exist for the same reason: an account takeover is usually
 * visible before it is damaging — an unfamiliar device, a sign-in from
 * somewhere the user has never been — but only to someone who can see it.
 */
export function SessionsTab() {
  const sessions = useSessions();
  const events = useSecurityEvents(0, 25);
  const [working, setWorking] = useState(false);

  const revoke = async (sessionId: string) => {
    setWorking(true);
    try {
      await apiClient.revokeSession(sessionId);
      await sessions.mutate();
      toast.success("Session ended");
    } catch (error) {
      toast.fromError(error, "Could not end that session");
    } finally {
      setWorking(false);
    }
  };

  const revokeOthers = async () => {
    setWorking(true);
    try {
      const result = await apiClient.revokeOtherSessions();
      await sessions.mutate();
      toast.success(
        "Other devices signed out",
        `${result.sessionsEnded} ${result.sessionsEnded === 1 ? "session" : "sessions"} ended.`
      );
    } catch (error) {
      toast.fromError(error, "Could not end the other sessions");
    } finally {
      setWorking(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <CardTitle>Signed-in devices</CardTitle>
              <CardDescription>End anything you do not recognise</CardDescription>
            </div>
            <Button
              size="sm"
              variant="secondary"
              onClick={revokeOthers}
              loading={working}
              disabled={(sessions.data?.length ?? 0) < 2}
            >
              <LogOut />
              Sign out other devices
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-2">
          {sessions.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : sessions.error ? (
            <ErrorState error={sessions.error} onRetry={() => sessions.mutate()} />
          ) : sessions.data?.length ? (
            sessions.data.map((session) => (
              <div
                key={session.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-line px-3 py-2.5"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <MonitorSmartphone className="size-4 shrink-0 text-ink-muted" />
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 text-sm text-ink">
                      {session.device ?? "Unknown device"}
                      {session.current ? <Badge tone="good">This device</Badge> : null}
                    </p>
                    <p className="truncate text-xs text-ink-muted">
                      {session.ipAddress ?? "Unknown address"} · active {relativeTime(session.lastSeenAt)}
                      {session.mfaAt ? " · second factor used" : ""}
                    </p>
                  </div>
                </div>
                {session.current ? (
                  <span className="text-xs text-ink-muted">Ends when you sign out</span>
                ) : (
                  <Button size="sm" variant="ghost" onClick={() => revoke(session.id)} disabled={working}>
                    End session
                  </Button>
                )}
              </div>
            ))
          ) : (
            <EmptyState
              icon={MonitorSmartphone}
              title="No other devices"
              description="This is the only device signed in to the account."
            />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Recent security activity</CardTitle>
          <CardDescription>Sign-ins, failures, and changes to this account</CardDescription>
        </CardHeader>
        <CardContent className="space-y-1.5">
          {events.isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : events.data?.content?.length ? (
            events.data.content.map((event) => (
              <div
                key={event.id}
                className="flex flex-wrap items-center justify-between gap-2 border-b border-line py-2 last:border-0"
              >
                <div className="flex items-center gap-2">
                  <Badge tone={SEVERITY_TONE[event.severity]}>{humanise(event.severity)}</Badge>
                  <span className="text-sm text-ink">{humanise(event.eventType)}</span>
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
              description="Sign-ins and security changes will appear here."
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
