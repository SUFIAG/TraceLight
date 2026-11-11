"use client";

import { useState } from "react";
import { Search, Share2, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { useConsortiumStats, useConsortiumSummary } from "@/lib/hooks/useApi";

export default function ConsortiumPage() {
  const stats = useConsortiumStats();
  const summary = useConsortiumSummary();
  const [identifier, setIdentifier] = useState("");
  const [result, setResult] = useState<{ risk: number; known: boolean; matches: unknown[] } | null>(null);
  const [busy, setBusy] = useState(false);

  const lookup = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!identifier.trim()) return;
    setBusy(true);
    try {
      const response = await apiClient.queryConsortium(identifier.trim(), "ACCOUNT");
      setResult(response);
    } catch (error) {
      toast.fromError(error, "Lookup failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <AppShell
      title="Consortium"
      subtitle="Cross-institution intelligence, shared without sharing identities"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <StatTile
            label="Shared signals"
            numeric={Number(stats.data?.sharedSignals ?? 0)}
            tone="default"
            loading={stats.isLoading}
            footnote="Reported by two or more institutions"
          />
          <StatTile
            label="Your contributions"
            numeric={Number(stats.data?.yourContributions ?? 0)}
            loading={stats.isLoading}
          />
          <StatTile
            label="Sharing threshold"
            numeric={Number(stats.data?.minContributorsToShare ?? 2)}
            loading={stats.isLoading}
            footnote="Contributors before a signal is readable"
          />
        </div>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm">
              <ShieldCheck className="size-4 text-good" />
              How privacy is enforced
            </CardTitle>
            <CardDescription>In code, not in a contract.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-1.5 pt-0 text-sm text-ink-secondary">
            <p>• Identifiers are HMAC-hashed under a platform pepper before storage — a plain digest of an account number is trivially enumerable.</p>
            <p>• Attribution lives in a separate table, so a lookup can never reveal which institution reported what.</p>
            <p>• A signal reported by one institution stays that institution&apos;s private intelligence.</p>
            <p>• Signals expire. A device compromised two years ago says nothing about today.</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Check an identifier</CardTitle>
            <CardDescription>
              The value is hashed in the browser&apos;s request and never stored in the clear.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 pt-0">
            <form className="flex gap-2" onSubmit={lookup}>
              <Input
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                placeholder="Account, device, or wallet identifier"
                aria-label="Identifier to check"
              />
              <Button type="submit" size="sm" loading={busy}>
                <Search />
                Check
              </Button>
            </form>

            {result && (
              <div className="rounded border border-line bg-surface-sunken p-3">
                {result.known ? (
                  <div className="flex items-center gap-2">
                    <Badge tone={result.risk >= 80 ? "critical" : "warning"}>
                      Known to the network · severity {result.risk}
                    </Badge>
                    <span className="text-xs text-ink-secondary">
                      {result.matches.length} matching signal(s)
                    </span>
                  </div>
                ) : (
                  <p className="text-sm text-ink-secondary">
                    Not known to the network. Either it is clean, or only one institution has seen it.
                  </p>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Network signal mix</CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            {summary.isLoading ? (
              <Skeleton className="h-24 w-full" />
            ) : summary.data?.length ? (
              <div className="space-y-2">
                {summary.data.map((entry, index) => (
                  <div key={index} className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-ink">
                      {String(entry.signalType).replaceAll("_", " ").toLowerCase()}
                    </span>
                    <span className="text-xs text-ink-secondary">
                      {String(entry.count)} signals · severity {String(entry.averageSeverity)}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={Share2}
                title="No shared signals yet"
                description="Contribute confirmed mule accounts and scam beneficiaries. A signal becomes readable once a second institution reports it."
              />
            )}
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
