"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { FilterBar, type FilterOption } from "@/components/data/FilterBar";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/data/States";
import { Pagination } from "@/components/data/Pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableShell,
} from "@/components/ui/table";
import { AlertStatusBadge, Badge, SeverityBadge } from "@/components/ui/badge";
import { useAlerts } from "@/lib/hooks/useApi";
import { humanise, relativeTime, shortId } from "@/lib/format";
import type { AlertStatus } from "@/types";

const PAGE_SIZE = 20;

const STATUS_FILTERS: FilterOption<AlertStatus>[] = [
  { value: "ALL", label: "All" },
  { value: "OPEN", label: "Open" },
  { value: "INVESTIGATING", label: "Investigating" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "FALSE_POSITIVE", label: "False positive" },
];

export default function AlertsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<AlertStatus | "ALL">("OPEN");

  const { data, error, isLoading, mutate } = useAlerts(
    page,
    PAGE_SIZE,
    status === "ALL" ? undefined : status
  );

  const onFilterChange = (next: AlertStatus | "ALL") => {
    setStatus(next);
    setPage(0);
  };

  return (
    <AppShell
      title="Alert queue"
      subtitle="Flagged transactions ranked by severity, newest first"
    >
      <div className="space-y-4">
        <FilterBar
          label="Filter alerts by status"
          options={STATUS_FILTERS}
          value={status}
          onChange={onFilterChange}
        >
          {data ? (
            <span className="text-xs text-ink-muted tabular">
              {data.totalElements} alert{data.totalElements === 1 ? "" : "s"}
            </span>
          ) : null}
        </FilterBar>

        <TableShell>
          {isLoading ? (
            <TableSkeleton rows={8} columns={5} />
          ) : error ? (
            <ErrorState error={error} onRetry={() => mutate()} />
          ) : data?.content.length ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-24">Alert</TableHead>
                    <TableHead className="w-32">Severity</TableHead>
                    <TableHead>Why it fired</TableHead>
                    <TableHead className="w-64">Triggered rules</TableHead>
                    <TableHead className="w-36">Status</TableHead>
                    <TableHead className="w-28 text-right">Raised</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((alert) => (
                    <TableRow
                      key={alert.id}
                      interactive
                      tabIndex={0}
                      onClick={() => router.push(`/alerts/${alert.id}`)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") router.push(`/alerts/${alert.id}`);
                      }}
                    >
                      <TableCell className="font-medium tabular">{shortId(alert.id)}</TableCell>
                      <TableCell>
                        <SeverityBadge severity={alert.severity} />
                      </TableCell>
                      <TableCell className="max-w-md">
                        <p className="line-clamp-2 text-ink-secondary">{alert.reason}</p>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {alert.triggeredRules?.slice(0, 2).map((rule) => (
                            <Badge key={rule}>{humanise(rule)}</Badge>
                          ))}
                          {alert.triggeredRules && alert.triggeredRules.length > 2 && (
                            <Badge>+{alert.triggeredRules.length - 2}</Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <AlertStatusBadge status={alert.status} />
                      </TableCell>
                      <TableCell className="text-right text-xs text-ink-muted">
                        {relativeTime(alert.createdAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination
                page={data.number}
                size={data.size}
                totalElements={data.totalElements}
                totalPages={data.totalPages}
                onPageChange={setPage}
              />
            </>
          ) : (
            <EmptyState
              icon={CheckCircle2}
              title={status === "ALL" ? "No alerts yet" : `No ${humanise(status).toLowerCase()} alerts`}
              description="Alerts appear when a scored transaction crosses the risk threshold. Load the demo dataset to see the queue populated."
            />
          )}
        </TableShell>
      </div>
    </AppShell>
  );
}
