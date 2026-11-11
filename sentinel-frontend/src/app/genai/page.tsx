"use client";

import { useState } from "react";
import { Bot, Globe, Mic, ShieldX } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { ScoreMeter } from "@/components/platform/ScoreMeter";
import { StatTile } from "@/components/dashboard/StatTile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
import { useAiAgents, useGenAiStats, useMediaAnalyses, usePhishingDomains } from "@/lib/hooks/useApi";

export default function GenAiPage() {
  const stats = useGenAiStats();
  const media = useMediaAnalyses(0, 20);
  const agents = useAiAgents(0, 20);
  const domains = usePhishingDomains(0, 20);
  const [busy, setBusy] = useState<string | null>(null);

  const revoke = async (id: string) => {
    setBusy(id);
    try {
      await apiClient.revokeAiAgent(id, "Revoked from the console");
      toast.success("Delegation revoked", "The agent can no longer act for this customer.");
      await agents.mutate();
    } catch (error) {
      toast.fromError(error, "Could not revoke the agent");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="GenAI defence"
      subtitle="Synthetic media, agent authorisation, and brand impersonation"
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Synthetic media found"
            numeric={Number(stats.data?.syntheticMedia ?? 0)}
            tone="critical"
            loading={stats.isLoading}
            footnote="Cloned voices, faces, documents"
          />
          <StatTile
            label="Active AI agents"
            numeric={Number(stats.data?.activeAgents ?? 0)}
            tone="default"
            loading={stats.isLoading}
            footnote="Holding a customer delegation"
          />
          <StatTile
            label="Agent requests denied"
            numeric={Number(stats.data?.agentDenials ?? 0)}
            tone="warning"
            loading={stats.isLoading}
          />
          <StatTile
            label="Lookalike domains"
            numeric={Number(stats.data?.phishingDetected ?? 0)}
            tone="serious"
            loading={stats.isLoading}
          />
        </div>

        <p className="max-w-3xl text-sm leading-relaxed text-ink-secondary">
          Generative tools removed two assumptions every remote control rested on: that a voice on a
          call belongs to a person, and that a document that looks right was issued by somebody. The
          third problem is newer — when a shopping agent pays for groceries, every behavioural signal
          reads as &quot;bot&quot;, because it is one. The answer is not detection but delegation.
        </p>

        <Tabs defaultValue="media">
          <TabsList>
            <TabsTrigger value="media">Synthetic media</TabsTrigger>
            <TabsTrigger value="agents">Know Your Agent</TabsTrigger>
            <TabsTrigger value="brand">Brand protection</TabsTrigger>
          </TabsList>

          <TabsContent value="media">
            {media.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : media.data?.content?.length ? (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Type</TableHead>
                      <TableHead>Channel</TableHead>
                      <TableHead>Subject</TableHead>
                      <TableHead>Synthetic score</TableHead>
                      <TableHead>Verdict</TableHead>
                      <TableHead>Action</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {media.data.content.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell className="font-medium">{item.mediaType}</TableCell>
                        <TableCell className="text-ink-secondary">{item.channel}</TableCell>
                        <TableCell className="text-ink-secondary">{item.subjectId ?? "—"}</TableCell>
                        <TableCell className="w-32">
                          <ScoreMeter value={item.syntheticScore} />
                        </TableCell>
                        <TableCell>
                          <Badge
                            tone={
                              item.verdict === "SYNTHETIC"
                                ? "critical"
                                : item.verdict === "SUSPECTED_SYNTHETIC"
                                  ? "warning"
                                  : "neutral"
                            }
                          >
                            {item.verdict.replaceAll("_", " ").toLowerCase()}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs text-ink-secondary">{item.action}</TableCell>
                        <TableCell className="text-xs text-ink-muted">
                          {relativeTime(item.createdAt)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            ) : (
              <Card>
                <EmptyState
                  icon={Mic}
                  title="No media analysed"
                  description="Send capture signals to /api/v1/genai/media/analyse from the call centre or video KYC pipeline."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="agents" className="space-y-2">
            {agents.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : agents.data?.content?.length ? (
              agents.data.content.map((agent) => (
                <Card key={agent.id}>
                  <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                    <div className="min-w-0 space-y-1">
                      <p className="text-sm font-medium text-ink">
                        {agent.agentName} <span className="text-ink-muted">by {agent.operator}</span>
                      </p>
                      <p className="text-xs text-ink-secondary">
                        Acting for {agent.principalUserId} · {agent.scopes.join(", ").toLowerCase()}
                      </p>
                      <p className="text-xs text-ink-muted">
                        {agent.transactionCount} requests
                        {agent.perTransactionLimit
                          ? ` · ${money(agent.perTransactionLimit)} per payment`
                          : ""}
                        {agent.dailyLimit ? ` · ${money(agent.dailyLimit)} daily` : ""}
                        {agent.expiresAt ? ` · expires ${relativeTime(agent.expiresAt)}` : ""}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      {agent.behaviourDriftScore > 0 && (
                        <div className="w-24">
                          <ScoreMeter value={agent.behaviourDriftScore} label="drift" />
                        </div>
                      )}
                      <Badge tone={agent.status === "ACTIVE" ? "good" : "critical"}>
                        {agent.status.toLowerCase()}
                      </Badge>
                      {agent.status === "ACTIVE" && (
                        <Button
                          size="sm"
                          variant="danger"
                          loading={busy === agent.id}
                          onClick={() => revoke(agent.id)}
                        >
                          <ShieldX />
                          Revoke
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              <Card>
                <EmptyState
                  icon={Bot}
                  title="No AI agents registered"
                  description="Register agents that transact for customers so their authority can be scoped, limited, and revoked."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="brand">
            {domains.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : domains.data?.content?.length ? (
              <TableShell>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Domain</TableHead>
                      <TableHead>Imitates</TableHead>
                      <TableHead>Technique</TableHead>
                      <TableHead>Confidence</TableHead>
                      <TableHead>Live form</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {domains.data.content.map((domain) => (
                      <TableRow key={domain.id}>
                        <TableCell className="font-medium">{domain.domain}</TableCell>
                        <TableCell className="text-ink-secondary">{domain.brandTerm}</TableCell>
                        <TableCell className="text-xs text-ink-secondary">{domain.technique}</TableCell>
                        <TableCell className="w-28">
                          <ScoreMeter value={domain.similarityScore} />
                        </TableCell>
                        <TableCell>
                          {domain.hasLoginForm ? (
                            <Badge tone="critical">phishing live</Badge>
                          ) : (
                            <span className="text-xs text-ink-muted">parked</span>
                          )}
                        </TableCell>
                        <TableCell className="text-xs text-ink-secondary">{domain.status}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableShell>
            ) : (
              <Card>
                <EmptyState
                  icon={Globe}
                  title="No lookalike domains flagged"
                  description="Feed candidate registrations to /api/v1/genai/brand/assess to catch homoglyph and combosquat impersonation."
                />
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
