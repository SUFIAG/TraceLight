"use client";

import { useState } from "react";
import { KeyRound, Plug, Webhook } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState } from "@/components/data/States";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiClient } from "@/lib/api-client";
import { relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useApiKeys, useConnectors, useWebhooks } from "@/lib/hooks/useApi";

const EVENTS = [
  "ALERT_CREATED",
  "CASE_RESOLVED",
  "SCAM_DETECTED",
  "MULE_CONFIRMED",
  "SAR_FILED",
  "RING_DETECTED",
];

export default function IntegrationsPage() {
  const webhooks = useWebhooks();
  const keys = useApiKeys();
  const connectors = useConnectors();

  const [url, setUrl] = useState("");
  const [name, setName] = useState("");
  const [keyName, setKeyName] = useState("");
  const [secret, setSecret] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const createWebhook = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy("webhook");
    try {
      const result = await apiClient.createWebhook({
        name: name || "Unnamed webhook",
        targetUrl: url,
        events: EVENTS,
      });
      setSecret(String(result.secret ?? ""));
      setUrl("");
      setName("");
      toast.success("Webhook created", "Copy the signing secret now — it is not recoverable.");
      await webhooks.mutate();
    } catch (error) {
      toast.fromError(error, "Could not create the webhook");
    } finally {
      setBusy(null);
    }
  };

  const issueKey = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy("key");
    try {
      const result = await apiClient.issueApiKey({ name: keyName || "Scoring key", scopes: ["SCORE"] });
      setSecret(String(result.apiKey ?? ""));
      setKeyName("");
      toast.success("API key issued", "Copy it now — only its hash is stored.");
      await keys.mutate();
    } catch (error) {
      toast.fromError(error, "Could not issue the key");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      title="Integrations"
      subtitle="Webhooks, machine credentials, and the connector catalogue"
    >
      <div className="space-y-4">
        {secret && (
          <Card className="border-warning/40">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Copy this now</CardTitle>
              <CardDescription>
                Shown once. The platform stores only a hash, so it cannot be recovered or re-displayed.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex items-center justify-between gap-3 pt-0">
              <code className="truncate rounded bg-surface-sunken px-2 py-1 text-xs text-ink">{secret}</code>
              <Button size="sm" variant="ghost" onClick={() => setSecret(null)}>
                Dismiss
              </Button>
            </CardContent>
          </Card>
        )}

        <Tabs defaultValue="webhooks">
          <TabsList>
            <TabsTrigger value="webhooks">Webhooks</TabsTrigger>
            <TabsTrigger value="keys">API keys</TabsTrigger>
            <TabsTrigger value="connectors">Connectors</TabsTrigger>
          </TabsList>

          <TabsContent value="webhooks" className="space-y-3">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-sm">Add an endpoint</CardTitle>
                <CardDescription>
                  Every delivery is persisted, signed with HMAC-SHA256, and retried with exponential
                  backoff. An integration that drops events on a transient failure is one nobody can
                  build a workflow on.
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0">
                <form className="flex flex-wrap items-end gap-2" onSubmit={createWebhook}>
                  <div className="min-w-48 flex-1 space-y-1.5">
                    <Label htmlFor="hook-name">Name</Label>
                    <Input
                      id="hook-name"
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      placeholder="Case management bridge"
                    />
                  </div>
                  <div className="min-w-64 flex-[2] space-y-1.5">
                    <Label htmlFor="hook-url">Endpoint URL</Label>
                    <Input
                      id="hook-url"
                      value={url}
                      onChange={(event) => setUrl(event.target.value)}
                      placeholder="https://example.com/hooks/fraud"
                      required
                    />
                  </div>
                  <Button type="submit" size="sm" loading={busy === "webhook"}>
                    <Webhook />
                    Create
                  </Button>
                </form>
              </CardContent>
            </Card>

            {webhooks.isLoading ? (
              <Skeleton className="h-32 w-full" />
            ) : webhooks.data?.length ? (
              <div className="space-y-2">
                {webhooks.data.map((hook) => (
                  <Card key={hook.id}>
                    <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-ink">{hook.name}</p>
                        <p className="truncate text-xs text-ink-secondary">{hook.targetUrl}</p>
                        <p className="text-xs text-ink-muted">
                          {hook.events.length} event types
                          {hook.lastDeliveryAt ? ` · last delivery ${relativeTime(hook.lastDeliveryAt)}` : ""}
                        </p>
                      </div>
                      <Badge tone={hook.active ? "good" : "critical"}>
                        {hook.active ? "active" : hook.disabledReason ?? "disabled"}
                      </Badge>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <Card>
                <EmptyState
                  icon={Webhook}
                  title="No webhooks configured"
                  description="Push alerts, confirmed mules, and filed reports into the systems your team already works in."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="keys" className="space-y-3">
            <Card>
              <CardContent className="pt-4">
                <form className="flex flex-wrap items-end gap-2" onSubmit={issueKey}>
                  <div className="min-w-64 flex-1 space-y-1.5">
                    <Label htmlFor="key-name">Key name</Label>
                    <Input
                      id="key-name"
                      value={keyName}
                      onChange={(event) => setKeyName(event.target.value)}
                      placeholder="Payment gateway scoring"
                    />
                  </div>
                  <Button type="submit" size="sm" loading={busy === "key"}>
                    <KeyRound />
                    Issue key
                  </Button>
                </form>
              </CardContent>
            </Card>

            {keys.isLoading ? (
              <Skeleton className="h-32 w-full" />
            ) : keys.data?.length ? (
              <div className="space-y-2">
                {keys.data.map((key) => (
                  <Card key={key.id}>
                    <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                      <div>
                        <p className="text-sm font-medium text-ink">{key.name}</p>
                        <p className="text-xs text-ink-secondary">
                          {key.keyPrefix}… · {key.scopes.join(", ").toLowerCase()} ·{" "}
                          {key.rateLimitPerMinute}/min
                        </p>
                        <p className="text-xs text-ink-muted">
                          {key.lastUsedAt ? `Last used ${relativeTime(key.lastUsedAt)}` : "Never used"}
                        </p>
                      </div>
                      <Badge tone={key.active ? "good" : "neutral"}>
                        {key.active ? "active" : "revoked"}
                      </Badge>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <Card>
                <EmptyState
                  icon={KeyRound}
                  title="No API keys"
                  description="Issue a scoped key for server-to-server scoring. Only the hash is stored."
                />
              </Card>
            )}
          </TabsContent>

          <TabsContent value="connectors">
            {connectors.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : (
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {connectors.data?.map((connector) => (
                  <Card key={connector.key}>
                    <CardContent className="space-y-1.5 py-4">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-medium text-ink">{connector.name}</p>
                        <Plug className="size-4 text-ink-muted" />
                      </div>
                      <p className="text-xs text-ink-secondary">
                        {connector.category.replaceAll("_", " ").toLowerCase()} · {connector.transport}
                      </p>
                      <p className="text-xs text-ink-muted">{connector.entities.join(", ").toLowerCase()}</p>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
