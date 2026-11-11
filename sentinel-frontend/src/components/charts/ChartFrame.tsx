"use client";

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export interface SeriesKey {
  key: string;
  label: string;
  color: string;
}

/**
 * A legend is always present for two or more series, so identity never depends
 * on colour matching alone. A single-series chart gets none: its title already
 * names what is plotted.
 */
export function ChartLegend({ series }: { series: SeriesKey[] }) {
  if (series.length < 2) return null;
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1.5">
      {series.map((item) => (
        <li key={item.key} className="flex items-center gap-1.5">
          <span
            className="h-0.5 w-4 rounded-full"
            style={{ backgroundColor: item.color }}
            aria-hidden
          />
          <span className="text-xs text-ink-secondary">{item.label}</span>
        </li>
      ))}
    </ul>
  );
}

export function ChartFrame({
  title,
  description,
  series,
  action,
  children,
  className,
}: {
  title: string;
  description?: string;
  series?: SeriesKey[];
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn("flex flex-col", className)}>
      <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
        <div className="space-y-1">
          <CardTitle>{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </div>
        {action}
      </CardHeader>
      {series && series.length > 1 ? (
        <div className="px-5 pt-3">
          <ChartLegend series={series} />
        </div>
      ) : null}
      <CardContent className="flex-1 pt-4">{children}</CardContent>
    </Card>
  );
}

/** Shared tooltip surface for every chart form. */
export function TooltipSurface({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-md border border-line bg-surface-raised px-3 py-2 shadow-popover">
      {children}
    </div>
  );
}

export function TooltipRow({
  color,
  label,
  value,
}: {
  color?: string;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center justify-between gap-6 py-0.5 text-xs">
      <span className="flex items-center gap-1.5 text-ink-secondary">
        {color ? (
          <span
            className="size-2 rounded-full"
            style={{ backgroundColor: color }}
            aria-hidden
          />
        ) : null}
        {label}
      </span>
      <span className="font-medium text-ink tabular">{value}</span>
    </div>
  );
}
