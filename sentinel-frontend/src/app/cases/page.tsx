"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { FolderKanban } from "lucide-react";
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
import { CaseStatusBadge, PriorityBadge } from "@/components/ui/badge";
import { useCases, useOrganizationMembers } from "@/lib/hooks/useApi";
import { fullName, humanise, relativeTime, shortId } from "@/lib/format";
import type { CaseStatus } from "@/types";

const PAGE_SIZE = 20;

const STATUS_FILTERS: FilterOption<CaseStatus>[] = [
  { value: "ALL", label: "All" },
  { value: "OPEN", label: "Open" },
  { value: "INVESTIGATING", label: "Investigating" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "CLOSED", label: "Closed" },
];

export default function CasesPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<CaseStatus | "ALL">("ALL");

  const { data, error, isLoading, mutate } = useCases(
    page,
    PAGE_SIZE,
    status === "ALL" ? undefined : status
  );
  const { data: members } = useOrganizationMembers();

  const assigneeName = (userId: string | null) => {
    if (!userId) return "Unassigned";
    const member = members?.find((candidate) => candidate.id === userId);
    return member ? fullName(member) : shortId(userId);
  };

  return (
    <AppShell title="Cases" subtitle="Investigations opened from the alert queue">
      <div className="space-y-4">
        <FilterBar
          label="Filter cases by status"
          options={STATUS_FILTERS}
          value={status}
          onChange={(next) => {
            setStatus(next);
            setPage(0);
          }}
        >
          {data ? (
            <span className="text-xs text-ink-muted tabular">
              {data.totalElements} case{data.totalElements === 1 ? "" : "s"}
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
                    <TableHead className="w-24">Case</TableHead>
                    <TableHead>Title</TableHead>
                    <TableHead className="w-32">Priority</TableHead>
                    <TableHead className="w-36">Status</TableHead>
                    <TableHead className="w-44">Assigned to</TableHead>
                    <TableHead className="w-28 text-right">Opened</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((fraudCase) => (
                    <TableRow
                      key={fraudCase.id}
                      interactive
                      tabIndex={0}
                      onClick={() => router.push(`/cases/${fraudCase.id}`)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") router.push(`/cases/${fraudCase.id}`);
                      }}
                    >
                      <TableCell className="font-medium tabular">{shortId(fraudCase.id)}</TableCell>
                      <TableCell className="max-w-md">
                        <p className="truncate text-ink">{fraudCase.title}</p>
                        {fraudCase.resolution ? (
                          <p className="mt-0.5 text-xs text-ink-muted">
                            Resolved as {humanise(fraudCase.resolution).toLowerCase()}
                          </p>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <PriorityBadge priority={fraudCase.priority} />
                      </TableCell>
                      <TableCell>
                        <CaseStatusBadge status={fraudCase.status} />
                      </TableCell>
                      <TableCell className="truncate text-xs text-ink-secondary">
                        {assigneeName(fraudCase.assignedTo)}
                      </TableCell>
                      <TableCell className="text-right text-xs text-ink-muted">
                        {relativeTime(fraudCase.createdAt)}
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
              icon={FolderKanban}
              title={status === "ALL" ? "No cases yet" : `No ${humanise(status).toLowerCase()} cases`}
              description="Open a case from an alert in the queue, or load the demo dataset to see the workspace populated."
            />
          )}
        </TableShell>
      </div>
    </AppShell>
  );
}
