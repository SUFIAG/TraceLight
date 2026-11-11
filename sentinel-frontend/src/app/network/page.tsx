"use client";

import { useState } from "react";
import { Network, RefreshCw, Search, ShieldOff } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState, ErrorState } from "@/components/data/States";
import { GraphExplorer } from "@/components/platform/GraphExplorer";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
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
import { money } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import {
  useBeneficiaries,
  useGraphNeighbourhood,
  useGraphStats,
  useMuleStats,
  useRings,
} from "@/lib/hooks/useApi";
import type { MuleBand } from "@/types";

const BAND_TONE: Record<MuleBand, "neutral" | "warning" | "serious" | "critical"> = {
  CLEAN: "neutral",
  WATCH: "warning",
  SUSPECTED: "serious",
  CONFIRMED: "critical",
};

export default function NetworkPage() {
  const muleStats = useMuleStats();
  const graphStats = useGraphStats();
  const beneficiaries = useBeneficiaries(0, 25);
  const rings = useRings(0, 20);

  const [entityQuery, setEntityQuery] = useState("");
  const [entityId, setEntityId] = useState<string | null>(null);
  const graph = useGraphNeighbourhood(entityId, 2);
  const [working, setWorking] = useState(false);

  const rebuild = async () => {
    setWorking(true);
    try {
      const result = await apiClient.rebuildGraph();
      toast.success(
        "Network rebuilt",
        `${result.edges ?? 0} distinct links, ${result.rings ?? 0} rings detected.`
      );
      await Promise.all([graphStats.mutate(), rings.mutate()]);
    } catch (error) {
      toast.fromError(error, "Rebuild failed");
    } finally {
      setWorking(false);
    }
  };

  const sweepMules = async () => {
    setWorking(true);
    try {
      const scored = await apiClient.sweepBeneficiaries(200);
      toast.success("Mule sweep complete", `${scored.length} beneficiaries rescored.`);
      await Promise.all([beneficiaries.mutate(), muleStats.mutate()]);
    } catch (error) {
      toast.fromError(error, "Sweep failed");
    } finally {
      setWorking(false);
    }
  };

  const confirmMule = async (beneficiaryId: string) => {
    try {
      await apiClient.confirmMule(beneficiaryId, "ANALYST");
      toast.success("Beneficiary confirmed", "Payments to this account will now be refused.");
      await beneficiaries.mutate();
    } catch (error) {
      toast.fromError(error, "Could not confirm the beneficiary");
    }
  };

  return (
    <AppShell
      title="Networks & mules"
      subtitle="Entity resolution, ring detection, and receiving-account risk"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Confirmed mules"
            numeric={Number(muleStats.data?.confirmed ?? 0)}
            tone="critical"
            loading={muleStats.isLoading}
            footnote="Payments refused outright"
          />
          <StatTile
            label="Suspected mules"
            numeric={Number(muleStats.data?.suspected ?? 0)}
            tone="serious"
            loading={muleStats.isLoading}
          />
          <StatTile
            label="Graph links"
            numeric={Number(graphStats.data?.edges ?? 0)}
            loading={graphStats.isLoading}
            footnote="Shared devices, payees, and addresses"
          />
          <StatTile
            label="Rings detected"
            numeric={Number(graphStats.data?.detectedRings ?? 0)}
            tone="warning"
            loading={graphStats.isLoading}
          />
        </div>

        <div className="flex flex-wrap gap-2">
          <Button size="sm" onClick={rebuild} loading={working}>
            <RefreshCw />
            Rebuild network
          </Button>
          <Button size="sm" variant="secondary" onClick={sweepMules} loading={working}>
            <ShieldOff />
            Sweep beneficiaries
          </Button>
        </div>

        <Tabs defaultValue="beneficiaries">
          <TabsList>
            <TabsTrigger value="beneficiaries">Beneficiary risk</TabsTrigger>
            <TabsTrigger value="rings">Rings</TabsTrigger>
            <TabsTrigger value="explorer">Link explorer</TabsTrigger>
          </TabsList>

          <TabsContent value="beneficiaries">
            {beneficiaries.isLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : beneficiaries.error ? (
              <Card>
                <ErrorState error={beneficiaries.error} onRetry={() => beneficiaries.mutate()} />
              </Card>
            ) : beneficiaries.data?.content?.length ? (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Beneficiary</TableHead>
                      <TableHead>Band</TableHead>
                      <TableHead>Score</TableHead>
                      <TableHead>Fan-in</TableHead>
                      <TableHead>Credited</TableHead>
                      <TableHead>Top reason</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {beneficiaries.data.content.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell className="font-medium">{item.beneficiaryId}</TableCell>
                        <TableCell>
                          <Badge tone={BAND_TONE[item.band]}>{item.band}</Badge>
                        </TableCell>
                        <TableCell className="w-28">
                          <ScoreMeter value={item.score} />
                        </TableCell>
                        <TableCell className="tabular">{item.fanIn}</TableCell>
                        <TableCell className="tabular">{money(item.totalCredited)}</TableCell>
                        <TableCell className="max-w-xs truncate text-xs text-ink-secondary">
                          {item.reasons?.[0] ?? "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          {item.band !== "CONFIRMED" && (
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => confirmMule(item.beneficiaryId)}
                            >
                              Confirm mule
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            ) : (
              <Card>
                <EmptyState
                  icon={ShieldOff}
                  title="No beneficiaries scored yet"
                  description="Run a sweep to score every receiving account with meaningful inbound traffic."
                  action={
                    <Button size="sm" onClick={sweepMules} loading={working}>
                      Sweep beneficiaries
                    </Button>
                  }
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="rings" className="space-y-2">
            {rings.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : rings.data?.content?.length ? (
              rings.data.content.map((ring) => (
                <Card key={ring.id}>
                  <CardHeader className="pb-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle>{ring.label}</CardTitle>
                      <Badge tone={ring.riskScore >= 70 ? "critical" : "warning"}>
                        Risk {ring.riskScore}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-3 pt-0">
                    <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Members</p>
                        <p className="tabular text-ink">{ring.memberCount}</p>
                      </div>
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Links</p>
                        <p className="tabular text-ink">{ring.edgeCount}</p>
                      </div>
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Density</p>
                        <p className="tabular text-ink">{(ring.density * 100).toFixed(0)}%</p>
                      </div>
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">Exposure</p>
                        <p className="tabular text-ink">{money(ring.totalExposure)}</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {ring.members.slice(0, 10).map((member) => (
                        <button
                          key={member}
                          type="button"
                          onClick={() => {
                            setEntityQuery(member);
                            setEntityId(member);
                          }}
                          className="rounded border border-line bg-surface-sunken px-2 py-0.5 text-xs text-ink-secondary hover:text-ink"
                        >
                          {member}
                        </button>
                      ))}
                      {ring.members.length > 10 && (
                        <span className="px-2 py-0.5 text-xs text-ink-muted">
                          +{ring.members.length - 10} more
                        </span>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Network}
                  title="No rings detected"
                  description="Rebuild the network to resolve shared devices, addresses, and payees into connected components."
                  action={
                    <Button size="sm" onClick={rebuild} loading={working}>
                      Rebuild network
                    </Button>
                  }
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="explorer" className="space-y-3">
            <form
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                setEntityId(entityQuery.trim() || null);
              }}
            >
              <Input
                value={entityQuery}
                onChange={(event) => setEntityQuery(event.target.value)}
                placeholder="Customer or account identifier"
                aria-label="Entity identifier"
              />
              <Button type="submit" size="sm">
                <Search />
                Explore
              </Button>
            </form>

            {!entityId ? (
              <Card>
                <EmptyState
                  icon={Network}
                  title="Pick an entity"
                  description="Enter a customer identifier, or select a ring member, to see everything connected to it."
                />
              </Card>
            ) : graph.isLoading ? (
              <Skeleton className="h-[520px] w-full" />
            ) : graph.data?.nodes?.length ? (
              <Card>
                <CardContent className="pt-4">
                  <GraphExplorer graph={graph.data} onSelect={(id) => setEntityId(id)} />
                  <p className="pt-2 text-xs text-ink-muted">
                    {graph.data.nodes.length} entities and {graph.data.edges.length} links within two
                    hops. Node size is link count; colour is risk.
                  </p>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <EmptyState
                  icon={Network}
                  title="No links found"
                  description="This entity shares no device, address, or payee with any other customer in the last 90 days."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
