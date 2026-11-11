"use client";

import { useState } from "react";
import { Sparkles } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { EmptyState, ErrorState } from "@/components/data/States";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { usePatterns } from "@/lib/hooks/useApi";
import { exactNumber, relativeTime } from "@/lib/format";

function confidencePercent(value: number | null | undefined): number {
  if (value === null || value === undefined) return 0;
  return Math.round(value <= 1 ? value * 100 : value);
}

export default function PatternsPage() {
  const { data, error, isLoading, mutate } = usePatterns();
  const [analysing, setAnalysing] = useState(false);

  const runAnalysis = async () => {
    setAnalysing(true);
    try {
      const result = await apiClient.analyzePatterns();
      await mutate(result, { revalidate: false });
      toast.success(
        "Pattern analysis complete",
        `${result.length} pattern${result.length === 1 ? "" : "s"} across recent fraud activity.`
      );
    } catch (analysisError) {
      toast.fromError(analysisError, "Pattern analysis failed");
    } finally {
      setAnalysing(false);
    }
  };

  return (
    <AppShell
      title="Patterns"
      subtitle="Recurring fraud signatures discovered across confirmed activity"
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="max-w-2xl text-sm leading-relaxed text-ink-secondary">
            Patterns are mined from confirmed fraud and the rules that fired alongside it.
            They tell you which signatures are growing before the loss figures do.
          </p>
          <Button onClick={runAnalysis} loading={analysing} size="sm">
            {!analysing && <Sparkles />}
            Run analysis
          </Button>
        </div>

        {isLoading ? (
          <div className="grid gap-3 md:grid-cols-2">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-32 w-full" />
            ))}
          </div>
        ) : error ? (
          <Card>
            <ErrorState error={error} onRetry={() => mutate()} />
          </Card>
        ) : data?.length ? (
          <div className="grid gap-3 md:grid-cols-2">
            {data.map((pattern) => {
              const confidence = confidencePercent(pattern.confidence);
              return (
                <Card key={pattern.id}>
                  <CardHeader className="pb-3">
                    <CardTitle>{pattern.patternName}</CardTitle>
                    <CardDescription>{pattern.patternDescription}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3 pt-0">
                    <div className="flex items-end justify-between gap-4">
                      <div>
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">
                          Occurrences
                        </p>
                        <p className="text-lg font-semibold text-ink">
                          {exactNumber(pattern.occurrences)}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-[11px] uppercase tracking-wide text-ink-muted">
                          Last seen
                        </p>
                        <p className="text-xs text-ink-secondary">
                          {pattern.lastTriggered ? relativeTime(pattern.lastTriggered) : "—"}
                        </p>
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex items-baseline justify-between">
                        <span className="text-xs text-ink-secondary">Confidence</span>
                        <span className="text-xs font-medium text-ink tabular">{confidence}%</span>
                      </div>
                      <div
                        className="h-1.5 overflow-hidden rounded-full bg-seq-100"
                        role="meter"
                        aria-valuenow={confidence}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-label="Pattern confidence"
                      >
                        <div
                          className="h-full rounded-full bg-seq-550 transition-[width] duration-500"
                          style={{ width: `${Math.max(2, confidence)}%` }}
                        />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        ) : (
          <Card>
            <EmptyState
              icon={Sparkles}
              title="No patterns discovered yet"
              description="Patterns emerge once cases are resolved as confirmed fraud. Run the analysis to mine what is already in the database."
              action={
                <Button size="sm" onClick={runAnalysis} loading={analysing}>
                  Run analysis
                </Button>
              }
            />
          </Card>
        )}
      </div>
    </AppShell>
  );
}
