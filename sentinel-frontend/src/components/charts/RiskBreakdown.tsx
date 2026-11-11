"use client";

import { cn } from "@/lib/utils";
import type { RiskScore } from "@/types";

const FACTORS = [
  { key: "amountRisk", label: "Amount", weight: "30%" },
  { key: "velocityRisk", label: "Velocity", weight: "20%" },
  { key: "locationRisk", label: "Location", weight: "20%" },
  { key: "deviceRisk", label: "Device", weight: "15%" },
  { key: "historyRisk", label: "History", weight: "15%" },
] as const;

function meterColor(value: number): string {
  if (value >= 81) return "var(--critical)";
  if (value >= 61) return "var(--serious)";
  if (value >= 31) return "var(--warning)";
  return "var(--good)";
}

/**
 * The factors behind a risk score. A meter per factor, with the value printed —
 * the number is never left to the bar length alone.
 */
export function RiskBreakdown({ riskScore }: { riskScore: RiskScore }) {
  return (
    <div className="space-y-3.5">
      {FACTORS.map((factor) => {
        const value = riskScore[factor.key];
        return (
          <div key={factor.key} className="space-y-1.5">
            <div className="flex items-baseline justify-between gap-3">
              <span className="text-xs text-ink-secondary">
                {factor.label}
                <span className="ml-1.5 text-[10px] text-ink-muted">weight {factor.weight}</span>
              </span>
              <span className="text-xs font-medium text-ink tabular">{value}</span>
            </div>
            <div
              className="h-1.5 overflow-hidden rounded-full bg-seq-100"
              role="meter"
              aria-valuenow={value}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={`${factor.label} risk`}
            >
              <div
                className="h-full rounded-full transition-[width] duration-500 ease-out"
                style={{ width: `${Math.min(100, Math.max(2, value))}%`, backgroundColor: meterColor(value) }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

/** The composite score, shown as the one number the case leads with. */
export function RiskScoreDial({ score, className }: { score: number; className?: string }) {
  const circumference = 2 * Math.PI * 42;
  const offset = circumference * (1 - Math.min(100, Math.max(0, score)) / 100);

  return (
    <div className={cn("relative grid size-28 place-items-center", className)}>
      <svg viewBox="0 0 100 100" className="size-28 -rotate-90">
        <circle cx="50" cy="50" r="42" fill="none" stroke="var(--seq-100)" strokeWidth="8" />
        <circle
          cx="50"
          cy="50"
          r="42"
          fill="none"
          stroke={meterColor(score)}
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: "stroke-dashoffset 700ms cubic-bezier(0.16, 1, 0.3, 1)" }}
        />
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-stat font-semibold text-ink">{score}</span>
        <span className="text-[10px] uppercase tracking-wide text-ink-muted">risk score</span>
      </div>
    </div>
  );
}
