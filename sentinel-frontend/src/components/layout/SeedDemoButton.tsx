"use client";

import { useState } from "react";
import { useSWRConfig } from "swr";
import { Database } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";

/**
 * Loads a demo dataset through the live ingestion and fraud pipeline, so a
 * demo shows real rows from PostgreSQL rather than fixtures.
 */
export function SeedDemoButton() {
  const [loading, setLoading] = useState(false);
  const { mutate } = useSWRConfig();

  const seed = async () => {
    setLoading(true);
    try {
      const result = await apiClient.seedDemoData(false);
      if (result.skipped) {
        toast.info("Demo data already present", result.message);
      } else {
        toast.success(
          "Demo data loaded",
          `${result.transactionsCreated} transactions scored · ${result.alertsCreated} alerts · ${result.casesCreated} cases.`
        );
      }
      // Revalidate every cached view so the new rows appear immediately.
      await mutate(() => true, undefined, { revalidate: true });
    } catch (error) {
      toast.fromError(error, "Could not load demo data");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button variant="secondary" size="sm" onClick={seed} loading={loading} className="hidden sm:inline-flex">
      {!loading && <Database />}
      Load demo data
    </Button>
  );
}
