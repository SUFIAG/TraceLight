"use client";

import { Fingerprint, Gauge, Globe } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge, TrustBadge } from "@/components/ui/badge";
import { SkeletonText } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/data/States";
import { useDevices, useVelocity } from "@/lib/hooks/useApi";
import { dateTime, exactNumber, money, relativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * The behavioural evidence an analyst would otherwise gather by hand: how fast
 * this customer is transacting, across how many merchants and countries, and
 * whether the device is one we have seen before.
 */
export function BehaviourSignals({
  userId,
  currency = "USD",
  deviceId,
}: {
  userId: string | null;
  currency?: string;
  deviceId?: string | null;
}) {
  const velocity = useVelocity(userId);
  const devices = useDevices(userId);

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex items-center gap-2">
            <Gauge className="size-4 text-primary" />
            <CardTitle>Velocity</CardTitle>
          </div>
          <CardDescription>How fast this customer is transacting</CardDescription>
        </CardHeader>
        <CardContent>
          {velocity.isLoading ? (
            <SkeletonText lines={4} />
          ) : velocity.data ? (
            <div className="space-y-4">
              {velocity.data.rapidActivityDetected ? (
                <Badge tone="critical" icon={Gauge}>
                  Rapid activity detected
                </Badge>
              ) : (
                <Badge tone="good" icon={Gauge}>
                  Normal pace
                </Badge>
              )}

              <dl className="grid grid-cols-3 gap-x-4 gap-y-3">
                <Metric label="10 min" value={exactNumber(velocity.data.count10Min)} />
                <Metric label="1 hour" value={exactNumber(velocity.data.count1Hour)} />
                <Metric label="24 hours" value={exactNumber(velocity.data.count24Hours)} />
              </dl>

              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 border-t border-line pt-3">
                <Metric
                  label="Spend, 1 hour"
                  value={money(velocity.data.amount1Hour, currency)}
                />
                <Metric
                  label="Spend, 24 hours"
                  value={money(velocity.data.amount24Hours, currency)}
                />
                <Metric
                  label="Distinct merchants, 1h"
                  value={exactNumber(velocity.data.distinctMerchants1Hour)}
                />
                <Metric
                  label="Distinct countries, 1h"
                  value={exactNumber(velocity.data.distinctCountries1Hour)}
                  icon={Globe}
                />
              </dl>
            </div>
          ) : (
            <EmptyState
              title="No velocity data"
              description="Velocity is computed from this customer's transaction history."
            />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex items-center gap-2">
            <Fingerprint className="size-4 text-primary" />
            <CardTitle>Devices</CardTitle>
          </div>
          <CardDescription>Hardware seen on this customer's account</CardDescription>
        </CardHeader>
        <CardContent>
          {devices.isLoading ? (
            <SkeletonText lines={4} />
          ) : devices.data?.length ? (
            <ul className="space-y-2.5">
              {devices.data.map((device) => {
                const isCurrent = deviceId != null && device.fingerprint === deviceId;
                return (
                  <li
                    key={device.id}
                    className={cn(
                      "rounded-md border p-3",
                      isCurrent ? "border-primary bg-primary-wash" : "border-line"
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ink">
                          {device.fingerprint}
                        </p>
                        <p className="mt-0.5 text-[11px] text-ink-muted">
                          {exactNumber(device.usageCount ?? 0)} use
                          {device.usageCount === 1 ? "" : "s"} · first seen{" "}
                          {dateTime(device.firstSeen)}
                        </p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-1">
                        <TrustBadge level={device.trustLevel} />
                        {isCurrent ? (
                          <span className="text-[10px] font-medium uppercase tracking-wide text-primary">
                            This transaction
                          </span>
                        ) : null}
                      </div>
                    </div>
                    <p className="mt-1.5 text-[11px] text-ink-muted">
                      Last seen {relativeTime(device.lastSeen)}
                    </p>
                  </li>
                );
              })}
            </ul>
          ) : (
            <EmptyState
              icon={Fingerprint}
              title="No devices recorded"
              description="Devices appear once transactions carry a device identifier."
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Metric({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon?: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="min-w-0">
      <dt className="flex items-center gap-1 text-[11px] uppercase tracking-wide text-ink-muted">
        {Icon ? <Icon className="size-3" /> : null}
        {label}
      </dt>
      <dd className="mt-0.5 truncate text-sm font-medium text-ink tabular">{value}</dd>
    </div>
  );
}
