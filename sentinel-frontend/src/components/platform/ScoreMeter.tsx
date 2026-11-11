"use client";

import { cn } from "@/lib/utils";

/**
 * A 0-100 bar used across the platform consoles.
 *
 * Colour is bound to the number rather than passed in, so the same score
 * always reads the same way on every screen. An analyst moving between the
 * scam console and the mule console should not have to relearn what amber
 * means.
 */
export function ScoreMeter({
  value,
  label,
  className,
  invert = false,
}: {
  value: number;
  label?: string;
  className?: string;
  /** True when a high number is good, as with detection coverage. */
  invert?: boolean;
}) {
  const clamped = Math.max(0, Math.min(100, Math.round(value)));
  const severity = invert ? 100 - clamped : clamped;

  const fill =
    severity >= 80
      ? "bg-critical"
      : severity >= 60
        ? "bg-serious"
        : severity >= 35
          ? "bg-warning"
          : "bg-good";

  return (
    <div className={cn("space-y-1", className)}>
      {label && (
        <div className="flex items-baseline justify-between">
          <span className="text-xs text-ink-secondary">{label}</span>
          <span className="text-xs font-medium tabular text-ink">{clamped}</span>
        </div>
      )}
      <div
        className="h-1.5 overflow-hidden rounded-full bg-surface-sunken"
        role="meter"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label ?? "Score"}
      >
        <div
          className={cn("h-full rounded-full transition-[width] duration-500", fill)}
          style={{ width: `${Math.max(2, clamped)}%` }}
        />
      </div>
    </div>
  );
}
