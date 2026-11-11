"use client";

import Link from "next/link";
import { ArrowDownRight, ArrowRight, ArrowUpRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Sparkline } from "@/components/dashboard/Sparkline";
import { useCountUp } from "@/lib/hooks/useCountUp";
import { cn } from "@/lib/utils";

export interface StatTileProps {
  label: string;
  /** Pre-formatted display value. Used when `numeric` is not supplied. */
  value?: string;
  /**
   * The raw figure. When present the tile animates between values and formats
   * through `format`, so a number that moves under a polling refresh is visible.
   */
  numeric?: number;
  format?: (value: number) => string;
  /** Short clarifier under the value — what period, what denominator. */
  footnote?: string;
  icon?: React.ComponentType<{ className?: string }>;
  tone?: "default" | "good" | "warning" | "serious" | "critical";
  loading?: boolean;
  href?: string;
  /** Recent history, oldest first. Rendered as a trend line behind the figure. */
  trend?: number[];
  /**
   * Period-over-period change as a percentage. Direction is coloured by
   * `deltaIsGood`, because "up" is good for cleared volume and bad for fraud.
   */
  delta?: number;
  deltaIsGood?: "up" | "down";
}

const toneAccent: Record<NonNullable<StatTileProps["tone"]>, string> = {
  default: "text-primary bg-primary-wash",
  good: "text-good bg-good-wash",
  warning: "text-warning bg-warning-wash",
  serious: "text-serious bg-serious-wash",
  critical: "text-critical bg-critical-wash",
};

const toneStroke: Record<NonNullable<StatTileProps["tone"]>, string> = {
  default: "var(--primary)",
  good: "var(--good)",
  warning: "var(--warning)",
  serious: "var(--serious)",
  critical: "var(--critical)",
};

export function StatTile({
  label,
  value,
  numeric,
  format,
  footnote,
  icon: Icon,
  tone = "default",
  loading = false,
  href,
  trend,
  delta,
  deltaIsGood = "down",
}: StatTileProps) {
  const animated = useCountUp(numeric);
  const display =
    numeric !== undefined ? (format ? format(animated) : Math.round(animated).toLocaleString()) : value ?? "—";

  const body = (
    <Card
      className={cn(
        "group relative overflow-hidden p-4 transition-all duration-200",
        "hover:-translate-y-0.5 hover:shadow-raised",
        href && "cursor-pointer"
      )}
    >
      {/* Ambient lift on hover. Pointer-events-none so it never intercepts clicks. */}
      <div className="surface-glow pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

      <div className="relative flex items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <p className="truncate text-xs text-ink-muted">{label}</p>

          {loading ? (
            <Skeleton className="h-7 w-20" />
          ) : (
            <div className="flex items-baseline gap-2">
              <p className="text-stat font-semibold text-ink numeric">{display}</p>
              {delta !== undefined && Number.isFinite(delta) && <DeltaPill delta={delta} good={deltaIsGood} />}
            </div>
          )}

          {footnote ? <p className="truncate text-[11px] text-ink-muted">{footnote}</p> : null}
        </div>

        {Icon ? (
          <span
            className={cn(
              "grid size-8 shrink-0 place-items-center rounded-md transition-transform duration-200 group-hover:scale-110",
              toneAccent[tone]
            )}
          >
            <Icon className="size-4" />
          </span>
        ) : null}
      </div>

      {trend && trend.length > 1 && !loading ? (
        <div className="relative mt-3 -mb-1">
          <Sparkline values={trend} stroke={toneStroke[tone]} width={240} height={30} className="w-full" />
        </div>
      ) : null}
    </Card>
  );

  if (!href) return body;

  return (
    <Link href={href} className="block rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
      {body}
    </Link>
  );
}

function DeltaPill({ delta, good }: { delta: number; good: "up" | "down" }) {
  const flat = Math.abs(delta) < 0.05;
  const rising = delta > 0;
  const favourable = flat ? null : (rising && good === "up") || (!rising && good === "down");

  const Icon = flat ? ArrowRight : rising ? ArrowUpRight : ArrowDownRight;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 rounded px-1 py-0.5 text-[11px] font-medium numeric",
        favourable === null && "bg-surface-sunken text-ink-muted",
        favourable === true && "bg-good-wash text-good",
        favourable === false && "bg-critical-wash text-critical"
      )}
      title={`${rising ? "Up" : "Down"} ${Math.abs(delta).toFixed(1)}% on the previous period`}
    >
      <Icon className="size-3" aria-hidden="true" />
      {Math.abs(delta).toFixed(1)}%
    </span>
  );
}
