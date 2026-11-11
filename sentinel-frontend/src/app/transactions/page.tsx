"use client";

import { useRef, useState } from "react";
import { Receipt, Upload } from "lucide-react";
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
import { Button } from "@/components/ui/button";
import { TransactionStatusBadge } from "@/components/ui/badge";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { useTransactions } from "@/lib/hooks/useApi";
import { dateTime, humanise, money } from "@/lib/format";
import type { TransactionStatus } from "@/types";

const PAGE_SIZE = 25;

const STATUS_FILTERS: FilterOption<TransactionStatus>[] = [
  { value: "ALL", label: "All" },
  { value: "FLAGGED", label: "Flagged" },
  { value: "APPROVED", label: "Approved" },
  { value: "PENDING", label: "Pending" },
  { value: "REJECTED", label: "Rejected" },
];

export default function TransactionsPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<TransactionStatus | "ALL">("ALL");
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const { data, error, isLoading, mutate } = useTransactions(
    page,
    PAGE_SIZE,
    status === "ALL" ? undefined : status
  );

  const uploadCsv = async (file: File) => {
    setUploading(true);
    try {
      const result = await apiClient.uploadTransactionsCsv(file);
      await mutate();
      if (result.totalFailed > 0) {
        toast.info(
          "Upload finished with errors",
          `${result.totalProcessed} accepted, ${result.totalFailed} rejected. First error: ${result.errors?.[0] ?? "unknown"}`
        );
      } else {
        toast.success("Upload complete", `${result.totalProcessed} transactions ingested.`);
      }
    } catch (uploadError) {
      toast.fromError(uploadError, "Upload failed");
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  return (
    <AppShell
      title="Transactions"
      subtitle="Every transaction ingested into this organisation"
    >
      <div className="space-y-4">
        <FilterBar
          label="Filter transactions by status"
          options={STATUS_FILTERS}
          value={status}
          onChange={(next) => {
            setStatus(next);
            setPage(0);
          }}
        >
          <div className="flex items-center gap-3">
            {data ? (
              <span className="text-xs text-ink-muted tabular">
                {data.totalElements} transaction{data.totalElements === 1 ? "" : "s"}
              </span>
            ) : null}
            <input
              ref={fileInput}
              type="file"
              accept=".csv,text/csv"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void uploadCsv(file);
              }}
            />
            <Button
              variant="secondary"
              size="sm"
              loading={uploading}
              onClick={() => fileInput.current?.click()}
            >
              {!uploading && <Upload />}
              Upload CSV
            </Button>
          </div>
        </FilterBar>

        <TableShell>
          {isLoading ? (
            <TableSkeleton rows={10} columns={6} />
          ) : error ? (
            <ErrorState error={error} onRetry={() => mutate()} />
          ) : data?.content.length ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Reference</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Merchant</TableHead>
                    <TableHead className="w-20">Country</TableHead>
                    <TableHead className="w-32">Status</TableHead>
                    <TableHead className="w-44 text-right">Occurred</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((transaction) => (
                    <TableRow key={transaction.id}>
                      <TableCell className="max-w-[12rem] truncate font-medium">
                        {transaction.transactionExternalId}
                      </TableCell>
                      <TableCell className="text-right font-semibold tabular">
                        {money(transaction.amount, transaction.currency)}
                      </TableCell>
                      <TableCell className="text-ink-secondary">{transaction.userId}</TableCell>
                      <TableCell className="max-w-[12rem] truncate text-ink-secondary">
                        {transaction.merchantId}
                      </TableCell>
                      <TableCell className="text-ink-secondary">
                        {transaction.country ?? "—"}
                      </TableCell>
                      <TableCell>
                        <TransactionStatusBadge status={transaction.status} />
                      </TableCell>
                      <TableCell className="text-right text-xs text-ink-muted">
                        {dateTime(transaction.timestamp)}
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
              icon={Receipt}
              title={status === "ALL" ? "No transactions yet" : `No ${humanise(status).toLowerCase()} transactions`}
              description="Upload a CSV with transactionExternalId, userId, merchantId, amount, currency and timestamp columns, or load the demo dataset."
            />
          )}
        </TableShell>
      </div>
    </AppShell>
  );
}
